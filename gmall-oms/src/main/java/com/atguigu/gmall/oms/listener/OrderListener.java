package com.atguigu.gmall.oms.listener;


import com.atguigu.gmall.oms.mapper.OrderMapper;
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
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 监听OMS_INVALID_QUEUE消息队列，将订单标记为无效订单
     * @param orderToken
     * @param channel
     * @param message
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "OMS_INVALID_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.invalid"}
    ))
    public void invalid(String orderToken, Channel channel, Message message) {
        try {
            if (StringUtils.isBlank(orderToken)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 1、更新订单状态为无效订单
            if (this.orderMapper.updateStatus(orderToken, 0, 5) == 1) { // 更新时要确保当前状态为
                // 2、发送消息给wms，解锁库存消息
                this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.unlock", orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RabbitListener(queues = "ORDER_DEAD_QUEUE")
    public void close(String orderToken, Channel channel, Message message) {
        try {
            if ( StringUtils.isBlank(orderToken)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }

            // 1、更新订单状态为：已關閉订单
            // 订单状态必须从0，改变为4；0代表订单未支付
            if ( this.orderMapper.updateStatus(orderToken, 0, 4) == 1) {
                // 2、发送消息给wms，解锁库存消息
                this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.unlock", orderToken);
            }

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * payment模快調用，更新訂單狀態為待發貨狀態
     * @param orderToken
     * @param channel
     * @param message
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "OMS_SUCCESS_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.success"}
    ))
    public void success(String orderToken, Channel channel, Message message) {
        try {
            if (StringUtils.isBlank(orderToken)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 1、更新订单状态为待发货
            if (this.orderMapper.updateStatus(orderToken, 0, 1) == 1) { // 更新时要确保当前状态为
                // 2、发送消息给wms，解锁库存消息
                this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.minus", orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
