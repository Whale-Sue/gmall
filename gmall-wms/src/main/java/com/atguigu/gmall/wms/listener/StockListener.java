package com.atguigu.gmall.wms.listener;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.entity.SkuLockVo;
import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class StockListener {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "stock:lock";

    @Autowired
    private WareSkuMapper wareSkuMapper;

    /**
     * 解锁库存--MySQL、Redis
     * @param orderToken
     * @param channel
     * @param message
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WMS_UNLOCK_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"stock.unlock"}
    ))
    public void unLock(String orderToken, Channel channel, Message message) {
        try {
            if (StringUtils.isBlank(orderToken)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            // 获取Redis中锁定的订单库存
            String orderJSON = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);

            if ( StringUtils.isBlank(orderJSON)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            List<SkuLockVo> skuLockVoList = JSON.parseArray(orderJSON, SkuLockVo.class);
            if (CollectionUtils.isEmpty(skuLockVoList)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 解锁MySQL的库存
            skuLockVoList.forEach( skuLockVo -> {
                this.wareSkuMapper.unLock(skuLockVo.getWareSkuId(), skuLockVo.getCount());
            });

            // 解锁Redis中的锁定库存--删除该记录即可
            this.redisTemplate.expire(KEY_PREFIX + orderToken, 1, TimeUnit.SECONDS);
            this.redisTemplate.delete(KEY_PREFIX + orderToken);

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WMS_MINUS_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"stock.minus"}
    ))
    public void minus(String orderToken, Channel channel, Message message) {
        try {
            if (StringUtils.isBlank(orderToken)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            // 获取Redis中锁定的订单库存
            String orderJSON = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);

            if ( StringUtils.isBlank(orderJSON)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            List<SkuLockVo> skuLockVoList = JSON.parseArray(orderJSON, SkuLockVo.class);
            if (CollectionUtils.isEmpty(skuLockVoList)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 解锁MySQL的库存
            skuLockVoList.forEach( skuLockVo -> {
                this.wareSkuMapper.minus(skuLockVo.getWareSkuId(), skuLockVo.getCount());
            });

            // 解锁Redis中的锁定库存--删除该记录即可
            this.redisTemplate.expire(KEY_PREFIX + orderToken, 1, TimeUnit.SECONDS);
            this.redisTemplate.delete(KEY_PREFIX + orderToken);

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
