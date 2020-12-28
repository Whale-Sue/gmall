package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;

import java.util.List;
import java.util.Map;

/**
 * sku销售属性&值
 *
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-10-28 16:54:26
 */
public interface SkuAttrValueService extends IService<SkuAttrValueEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<SkuAttrValueEntity> querySearchSkuAttrValueBySkuIdAndCategoryId(Long skuId, Long categoryId);

    List<SaleAttrValueVo> queryAllSkuAttrsBuSpuId(Long spuId);

    public String querySaleAttrsMappingSkuId(Long spuId);
}

