package com.atguigu.gmall.order.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItmVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.SkuLockVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallOmsClient omsClient;

    private static final String KEY_PREFIX = "order:token:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public OrderConfirmVo confirm() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();

        // 1、获取登录信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        // 2、获取用户选中的购物车信息
        ResponseVo<List<Cart>> checkedCartListResponseVo = this.cartClient.queryCheckedCarts(userId);
        List<Cart> checkedCartList = checkedCartListResponseVo.getData();

        // 3、判断选中的购物车是否为空
        if (CollectionUtils.isEmpty(checkedCartList)) throw new OrderException("您没有选中的购物车");

        // 4、若不为空，则将购物车转为订单详情
        List<OrderItmVo> orderItmVoList = checkedCartList.stream().map(cart -> {
            OrderItmVo orderItmVo = new OrderItmVo();

            orderItmVo.setCount(cart.getCount());

            orderItmVo.setSkuId(cart.getSkuId());
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if ( skuEntity != null) {
                orderItmVo.setTitle(skuEntity.getTitle());
                orderItmVo.setDefaultImage(skuEntity.getDefaultImage());
                orderItmVo.setWeight(skuEntity.getWeight());
                orderItmVo.setPrice(skuEntity.getPrice());
            }

            ResponseVo<List<WareSkuEntity>> wareSkuEntityListResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntityList = wareSkuEntityListResponseVo.getData();
            if ( !CollectionUtils.isEmpty(wareSkuEntityList)) {
                orderItmVo.setStore( wareSkuEntityList.stream() // 只需判断有货、无货，故只需 > 0
                                        .anyMatch( wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            ResponseVo<List<SkuAttrValueEntity>> skuAttrValueListResponseVo = this.pmsClient.querySaleAttrsBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntityList = skuAttrValueListResponseVo.getData();
            orderItmVo.setSaleAttrs(skuAttrValueEntityList);

            ResponseVo<List<ItemSaleVo>> itemSaleVoListResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVoList = itemSaleVoListResponseVo.getData();
            orderItmVo.setSales(itemSaleVoList);

            return orderItmVo;
        }).collect(Collectors.toList());
        confirmVo.setOrderItems(orderItmVoList);

        // 5、获取用户的收货地址列表
        ResponseVo<List<UserAddressEntity>> userAddressEntityListRespsonseVo = this.umsClient.queryAddressByUserId(userId);
        List<UserAddressEntity> userAddressEntityList = userAddressEntityListRespsonseVo.getData();
        confirmVo.setAddressees(userAddressEntityList);

        // 6、根据用户Id查询用户信息--积分
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if ( userEntity != null) confirmVo.setBound(userEntity.getIntegration());

        // 7、生成orderToken--防重
        String orderToken = IdWorker.getTimeId();
        confirmVo.setOrderToken(orderToken);
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, "2333", 24, TimeUnit.HOURS);

        return confirmVo;
    }

    public String submit(OrderSubmitVo submitVo) {
        // 1、防重--判断Redis中是否有token，若有则删除。要求判断和删除具备原子性--避免高并发带来的重复提交
        String orderToken = submitVo.getOrderToken();
        // 若请求来自postman测试，则orderToken为空，所以进行判断；页面停留时间过长导致orderToken过期
        if (StringUtils.isBlank(orderToken)) throw new OrderException("请求不合法");

        String script = "if(redis.call('exists', KEYS[1]) == 1) then return redis.call('del', KEYS[1]) else  return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), "");
        if ( !flag)throw new OrderException("页面已过期或您已提交");

        // 2、验总价
        // 2.1 获取页面上的价格
        BigDecimal currTotalPrice = submitVo.getTotalPrice();

        // 2.2 查询数据库中的价格
        List<OrderItmVo> orderItemVoList = submitVo.getItems();
        if ( !CollectionUtils.isEmpty(orderItemVoList)) throw new OrderException("您没有选中商品，请重新选购");
        BigDecimal totalPriceFromDB = orderItemVoList.stream().map(orderItmVo -> {
            Long skuId = orderItmVo.getSkuId();
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();

            if (skuEntity != null) return skuEntity.getPrice().multiply(orderItmVo.getCount());

            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();
        if ( currTotalPrice.compareTo(totalPriceFromDB) != 0) throw new OrderException("页面已过期，请刷新后重试");

        // 3、验库存并锁库存--定时解锁库存的操作放在wms中。
        List<SkuLockVo> skuLockVoList = orderItemVoList.stream().map( orderItmVo -> {
            SkuLockVo skuLockVo = new SkuLockVo();

            skuLockVo.setSkuId(orderItmVo.getSkuId());
            skuLockVo.setCount(orderItmVo.getCount().intValue());

            return skuLockVo;
        }).collect(Collectors.toList());
        ResponseVo<List<SkuLockVo>> skuLockVoListResponseVo = this.wmsClient.checkAndLock(skuLockVoList, orderToken);
        List<SkuLockVo> skuLockList = skuLockVoListResponseVo.getData();
        if ( !CollectionUtils.isEmpty(skuLockList)) throw new OrderException(JSON.toJSONString(skuLockList));

        // 4、创建订单
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        try {
            this.omsClient.saveOrder(submitVo, userId);

            // 若订单正常创建，则发送消息，进行后续的定时关单操作
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.ttl", orderToken);

        } catch (Exception e) {
            e.printStackTrace();

            // TODO：对于远程调用超时、响应超时的情况，异步标记为无效订单--将订单状态由0变为5，并解锁库存
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.invalid", orderToken);
            throw new OrderException("服务器错误，创建订单失败！");
        }

        // 5、删除购物车，异步删除
        Map<String, Object> map = new HashMap<>();  // map中存放待删除的用户的购物车信息，故需要userId，skuId集合
        map.put("userId", userId);
        List<Long> skuIdList = orderItemVoList.stream().map(OrderItmVo::getSkuId).collect(Collectors.toList());
        map.put("skuIds", JSON.toJSONString(skuIdList));    // 要将List转换为JSON字符串，因为List在Map内，内层的复杂数据类型在序列化、反序列化的传输中会丢失
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "cart.delete", map);

        // 返回订单编号
        return orderToken;
    }
}
