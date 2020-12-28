package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.wms.entity.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;



@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "stock:lock:";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Transactional
    @Override
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> skuLockVoList, String orderToken) {
        if ( CollectionUtils.isEmpty(skuLockVoList)) throw new OrderException("没有要购买的商品");

        // 一次性遍历所有送货清单，验库存并锁库存
        skuLockVoList.forEach( lockVo -> {
            this.checkLock(lockVo);
        });

        // 若这些商品中，有锁定失败的，则那些已经锁定成功的需要解锁
        if ( skuLockVoList.stream().anyMatch(lockVo -> !lockVo.getLock())) {    // 若有商品锁定失败
            // 获取锁定成的商品列表
            List<SkuLockVo> successLockVos = skuLockVoList.stream().filter(SkuLockVo::getLock).collect(Collectors.toList());
            successLockVos.forEach( lockVo -> {
                this.wareSkuMapper.unLock(lockVo.getWareSkuId(), lockVo.getCount());
            });

            // 返回值不空，说明有锁定失败
            return skuLockVoList;
        }

        // 在锁定成功的情况下， 为了方便将来关单时解锁库存，需要把该订单对应的锁定库存信息缓存到Redis中
        // key是orderToken，value是lockVOs
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, JSON.toJSONString(skuLockVoList));

        // 发送消息到延时队列，定时解锁库存
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.ttl", orderToken);

        // 返回值为null，则说明全都锁定成功
        return null;
    }

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    public void checkLock(SkuLockVo lockVo) {
        RLock fairLock = this.redissonClient.getFairLock("stock:" + lockVo.getSkuId());
        fairLock.lock();

        // 1、查询库存信息
        List<WareSkuEntity> wareSkuEntityList = this.wareSkuMapper.checkLock(lockVo.getSkuId(), lockVo.getCount());
        if ( !CollectionUtils.isEmpty(wareSkuEntityList)) {
            fairLock.unlock();
            lockVo.setLock(false);      // 没有仓库能满足此购买数量， 因此商品锁定失败

            return;
        }

        // 2、锁定库存信息
        WareSkuEntity wareSkuEntity = wareSkuEntityList.get(0);
        if ( this.wareSkuMapper.lock(wareSkuEntity.getId(), lockVo.getCount()) == 1) {
            lockVo.setLock(true);

            lockVo.setWareSkuId(wareSkuEntity.getId());     // 记录锁定成功的仓库Id
        }

        fairLock.unlock();
    }

}