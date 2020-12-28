package com.atguigu.gmall.oms.controller;

import java.util.List;

import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.PageParamVo;

/**
 * 订单
 *
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-11-22 19:32:04
 */
@Api(tags = "订单 管理")
@RestController
@RequestMapping("oms/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * payment模块间接调用本接口--根据orderToken，userId，查询是否存在该订单
     * @param orderToken
     * @param userId
     * @return
     */
    @GetMapping(value = "/query/{orderToken}")
    public ResponseVo<OrderEntity> queryOrder(
            @PathVariable(value = "orderToken") String orderToken,
            @RequestParam(value = "userId") Long userId) {

        OrderEntity orderEntity = this.orderService.getOne(new QueryWrapper<OrderEntity>()
                                                            .eq("order_sn", orderToken)
                                                            .eq("user_id", userId));
        return ResponseVo.ok(orderEntity);
    }

    @PostMapping(value = "/submit/{userId}")
    public ResponseVo<OrderEntity> saveOrder(
            @RequestBody OrderSubmitVo submitVo,
            @PathVariable("userId") Long userId) {
        OrderEntity orderEntity =  this.orderService.saveOrder(submitVo, userId);
        return ResponseVo.ok(orderEntity);
    }

    /**
     * 列表
     */
    @GetMapping
    @ApiOperation("分页查询")
    public ResponseVo<PageResultVo> queryOrderByPage(PageParamVo paramVo){
        PageResultVo pageResultVo = orderService.queryPage(paramVo);

        return ResponseVo.ok(pageResultVo);
    }


    /**
     * 信息
     */
    @GetMapping("{id}")
    @ApiOperation("详情查询")
    public ResponseVo<OrderEntity> queryOrderById(@PathVariable("id") Long id){
		OrderEntity order = orderService.getById(id);

        return ResponseVo.ok(order);
    }

    /**
     * 保存
     */
    @PostMapping
    @ApiOperation("保存")
    public ResponseVo<Object> save(@RequestBody OrderEntity order){
		orderService.save(order);

        return ResponseVo.ok();
    }

    /**
     * 修改
     */
    @PostMapping("/update")
    @ApiOperation("修改")
    public ResponseVo update(@RequestBody OrderEntity order){
		orderService.updateById(order);

        return ResponseVo.ok();
    }

    /**
     * 删除
     */
    @PostMapping("/delete")
    @ApiOperation("删除")
    public ResponseVo delete(@RequestBody List<Long> ids){
		orderService.removeByIds(ids);

        return ResponseVo.ok();
    }

}
