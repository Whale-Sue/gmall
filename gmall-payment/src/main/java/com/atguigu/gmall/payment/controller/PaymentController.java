package com.atguigu.gmall.payment.controller;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.entity.PaymentInfoEntity;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Date;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping(value = "/alipay.html")
    public String toAliPay(@RequestParam("orderToken")String orderToken, Model model) {
        OrderEntity orderEntity = this.paymentService.queryOrder(orderToken);

        if ( orderEntity == null || orderEntity.getStatus() != 0) {
            throw new OrderException("该用户要支付的订单不合法");
        }

        model.addAttribute("orderEntity", orderEntity);
        return "pay";
    }

    @Autowired
    private AlipayTemplate alipayTemplate;

    /**
     * 调用alipay的支付接口
     * @return
     */
    @GetMapping(value = "/alipay.html")
    @ResponseBody // 以其他视图的形式渲染方法的返回结果集，通常渲染成json
    public String toAlipay(@RequestParam("orderToken") String orderToken) {

        try {
            // 检查当前订单号是否合法
            OrderEntity orderEntity = this.paymentService.queryOrder(orderToken);
            if ( orderEntity == null || orderEntity.getStatus() != 0) {
                throw new OrderException("该用户要支付的订单不合法");
            }

            // 生成对账记录
            Long payId = this.paymentService.savePaymentInfo(orderEntity);

            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderToken);
            payVo.setTotal_amount("0.01");  // 这里一定不要取订单中的金额--因为会真的支付！建议取0.01。保留两位小数
            payVo.setSubject("谷粒商城支付平台");

            payVo.setPassback_params(payId.toString());     // 将对账Id作为回传参数放入，方便调用支付宝后对参数进行检查

            String form = this.alipayTemplate.pay(payVo);
            return form;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 支付宝异步回调接口，在一天内回调多次
     * 用来更新订单状态
     * @return
     */
    @PostMapping(value = "/pay/success")
    public String paySuccess(PayAsyncVo payAsyncVo) {
        // 1、验签--确保请求来自支付宝
        Boolean flag = this.alipayTemplate.checkSignature(payAsyncVo);
        if ( !flag) return "failure";

        // 2、校验商家及订单的业务参数--确保订单唯一，而不是不同商家
        // app_id、out_trade_no、total_amount
        String app_id = payAsyncVo.getApp_id();
        String out_trade_no = payAsyncVo.getOut_trade_no();
        String total_amount = payAsyncVo.getTotal_amount();

        // 获取回传参数--对账Id.根据对账Id，查询数据库获取参数，用来校验
        String payId = payAsyncVo.getPassback_params();
        PaymentInfoEntity paymentInfo = this.paymentService.query(payId);
        if (!org.apache.commons.lang3.StringUtils.equals(app_id, this.alipayTemplate.getApp_id()) ||
                !StringUtils.equals(out_trade_no, paymentInfo.getOutTradeNo()) ||
                paymentInfo.getTotalAmount().compareTo(new BigDecimal(total_amount)) != 0
        ){
            return "failure";
        }

        // 3、校验支付状态：TRADE_SUCCESS
        String trade_status = payAsyncVo.getTrade_status();
        if (!StringUtils.equals("TRADE_SUCCESS", trade_status)){
            return "failure";
        }

        // 4、更新对账信息
        paymentInfo.setPaymentStatus(1);
        paymentInfo.setTradeNo(payAsyncVo.getTrade_no());
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setCallbackContent(JSON.toJSONString(payAsyncVo));
        this.paymentService.update(paymentInfo);

        // 5、发送消息给oms更新订单状态为待发货，并发送消息给wms减库存
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.success", out_trade_no);

        // 6、响应成功内容给支付宝--这样后续支付宝就不用继续发起异步回调
        return "success";
    }

    /**
     * 支付宝同步回调接口，通过用于跳转到支付成功页面
     * @return
     */
    @GetMapping(value = "/pay/ok")
    public String payOk() {
        return "paysuccess";
    }
}
