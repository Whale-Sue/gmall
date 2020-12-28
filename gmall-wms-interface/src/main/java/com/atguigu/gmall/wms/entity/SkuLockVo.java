package com.atguigu.gmall.wms.entity;


import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId; // 锁定的商品Id

    private Integer count; // 锁定的商品数量

    private Boolean lock;   // 锁定状态--是否锁定成功

    private Long wareSkuId; // s锁定成功时锁定的仓库Id

    private String orderToken; // 以订单为单位，缓存订单的锁定信息
}
