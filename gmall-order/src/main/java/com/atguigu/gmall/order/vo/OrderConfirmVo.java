package com.atguigu.gmall.order.vo;


import com.atguigu.gmall.oms.vo.OrderItmVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    // 收货地址列表
    private List<UserAddressEntity> addressees;

    // 订单详情--购物车数据
    private List<OrderItmVo> orderItems;

    // 折扣
    private Integer bound;

    // 防重--当到达结算页面时返回一个orderToken，并将其插入到Redis中。
    // 提交订单前先查询Redis中是否有orderToken，有则提交订单并将其删除。没有则说明已经提交过订单
    private String orderToken;
}
