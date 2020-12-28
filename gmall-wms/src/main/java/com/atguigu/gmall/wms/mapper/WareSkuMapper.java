package com.atguigu.gmall.wms.mapper;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品库存
 * 
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-10-30 18:45:06
 */
@Mapper
public interface WareSkuMapper extends BaseMapper<WareSkuEntity> {

    public List<WareSkuEntity> checkLock(@Param("skuId") Long skuId, @Param("count") Integer count);

    public int lock(@Param("id") Long id, @Param("count") Integer count);

    public int unLock(@Param("id") Long id, @Param("count") Integer count);

    void minus(Long wareSkuId, Integer count);
}
