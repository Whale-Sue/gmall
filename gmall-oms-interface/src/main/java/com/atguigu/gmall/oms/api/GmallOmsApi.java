package com.atguigu.gmall.oms.api;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import org.springframework.web.bind.annotation.*;

public interface GmallOmsApi {

    @PostMapping(value = "oms/order/submit/{userId}")
    public ResponseVo<OrderEntity> saveOrder(
            @RequestBody OrderSubmitVo submitVo,
            @PathVariable("userId") Long userId);

    @GetMapping(value = "oms/order/query/{orderToken}")
    public ResponseVo<OrderEntity> queryOrder(
            @PathVariable(value = "orderToken") String orderToken,
            @RequestParam(value = "userId") Long userId);
}
