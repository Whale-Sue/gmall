package com.atguigu.gmall.cart.listener;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class CartListener {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String PRICE_PREFIX = "cart:price:";

    private static final String KEY_PREFIX = "cart:info:";

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService asyncService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "CART_PRICE_QUEUE", durable = "true"),
            exchange = @Exchange(value = "PMS_SPU_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"item.update"}
    ))
    public void cartListener(Long spuId, Channel channel, Message message) {
        try {
            if ( spuId == null) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }

            // 查询spuId对应的所有sku
            ResponseVo<List<SkuEntity>> skuEntityListResponseVo = this.pmsClient.querySkusBySpuId(spuId);
            List<SkuEntity> skuEntityList = skuEntityListResponseVo.getData();

            if ( !CollectionUtils.isEmpty(skuEntityList)) {
                skuEntityList.forEach( skuEntity -> {
                    this.redisTemplate.opsForValue().setIfPresent(PRICE_PREFIX + skuEntity.getId(), skuEntity.getPrice().toString());
                });
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 监听消息队列，取到消息后删除Redis、MySQL中的购物车
     * @param map
     * @param channel
     * @param message
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "CART_DELETE_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"cart.delete"}
    ))
    public void deleteCart(Map<String, Object> map, Channel channel, Message message) {
        try {
            if ( !CollectionUtils.isEmpty(map)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            String userId = map.get("userId").toString();
            String skuIdsJSON = map.get("skuIds").toString();
            if (StringUtils.isBlank(skuIdsJSON)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            List<String> skuIds = JSON.parseArray(skuIdsJSON, String.class);

            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
            // 删除Redis
            hashOps.delete(skuIds.toArray());

            // 异步删除MySQL
            this.asyncService.deleteCartByUserIdAndSkuIds(userId, skuIds);

            // 消费消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
