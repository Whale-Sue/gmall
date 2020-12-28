package com.atguigu.gmall.wms.service;

import com.atguigu.gmall.wms.entity.SkuLockVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;

import java.util.List;

/**
 * 商品库存
 *
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-10-30 18:45:06
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<SkuLockVo> checkAndLock(List<SkuLockVo> skuLockVoList, String orderToken);
}

