package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallUmsClient;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.service.OrderItemService;
import com.atguigu.gmall.oms.vo.OrderItmVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.entity.SpuDescEntity;
import com.atguigu.gmall.pms.entity.SpuEntity;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private OrderItemService orderItemService;


    @Transactional
    @Override
    public OrderEntity saveOrder(OrderSubmitVo submitVo, Long userId) {
        // 1、保存订单表
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(userId);
        orderEntity.setOrderSn(submitVo.getOrderToken());
        orderEntity.setCreateTime(new Date());
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if ( userEntity != null) {
            orderEntity.setUsername(userEntity.getUsername());
        }
        orderEntity.setTotalAmount(submitVo.getTotalPrice());
        orderEntity.setPayAmount(submitVo.getTotalPrice());
        orderEntity.setPayType(submitVo.getPayType());
        orderEntity.setSourceType(0);
        orderEntity.setStatus(0);
        orderEntity.setDeliveryCompany(submitVo.getDeliveryCompany());
        UserAddressEntity address = submitVo.getAddress();
        if ( address != null) {
            orderEntity.setReceiverAddress(address.getAddress());
            orderEntity.setReceiverRegion(address.getRegion());
            orderEntity.setReceiverProvince(address.getProvince());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverName(address.getName());
        }
        orderEntity.setDeleteStatus(0);
        orderEntity.setUseIntegration(submitVo.getBounds());
        this.save(orderEntity);

        Long orderId = orderEntity.getId();         // 获取orderId，用于保存订单详情表

        // 2、保存订单详情表
        List<OrderItmVo> orderItmVoList = submitVo.getItems();

        // 将OrderItemVo集合，转换为OrderItemEntity集合，实现订单详情的保存
        List<OrderItemEntity> orderItemEntityList = orderItmVoList.stream().map( orderItmVo -> {

            OrderItemEntity orderItemEntity = new OrderItemEntity();

            orderItemEntity.setOrderId(orderId);

            orderItemEntity.setOrderSn(submitVo.getOrderToken());

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(orderItmVo.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if ( skuEntity != null) {
                orderItemEntity.setSkuId(skuEntity.getId());
                orderItemEntity.setSkuPic(skuEntity.getDefaultImage());
                orderItemEntity.setSkuPrice(skuEntity.getPrice());
                orderItemEntity.setSkuQuantity(orderItmVo.getCount().intValue());
                orderItemEntity.setSkuName(skuEntity.getName());
            }

            ResponseVo<List<SkuAttrValueEntity>> skuAttrValueEntityListResponseVo = this.pmsClient.querySaleAttrsBySkuId(orderItmVo.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntityList = skuAttrValueEntityListResponseVo.getData();
            orderItemEntity.setSkuAttrsVals(JSON.toJSONString(skuAttrValueEntityList));

            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if ( spuEntity!= null) {
                orderItemEntity.setSpuId(spuEntity.getId());
                orderItemEntity.setSpuName(spuEntity.getName());
                orderItemEntity.setCategoryId(spuEntity.getCategoryId());
            }

            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if ( spuDescEntity != null) {
                orderItemEntity.setSpuPic(spuDescEntity.getDecript());
            }

            // TODO:积分、品牌
            return orderItemEntity;

        }).collect(Collectors.toList());

        this.orderItemService.saveBatch(orderItemEntityList);

        return orderEntity;
    }

}