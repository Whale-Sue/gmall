package com.atguigu.gmall.pms.mapper;

import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.vo.TestVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * sku销售属性&值
 * 
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-10-28 16:54:26
 */
@Mapper
public interface SkuAttrValueMapper extends BaseMapper<SkuAttrValueEntity> {

    public List<Map<String, Object>> querySaleAttrsMappingSkuId(Long spuId);

    // 若使用@Mapkey注解指定attrValues作Map的key，则需要建立一个Vo，用于接收map的value。随后也需要转换。
    @MapKey(value = "attrValues")
    Map<String, TestVo> testMapping(Long spuId);
}
