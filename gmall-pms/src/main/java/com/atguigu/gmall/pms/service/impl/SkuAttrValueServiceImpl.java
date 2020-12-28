package com.atguigu.gmall.pms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.pms.vo.TestVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.springframework.util.CollectionUtils;


@Service("skuAttrValueService")
public class SkuAttrValueServiceImpl extends ServiceImpl<SkuAttrValueMapper, SkuAttrValueEntity> implements SkuAttrValueService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Autowired
    private AttrMapper attrMapper;

    @Override
    public List<SkuAttrValueEntity> querySearchSkuAttrValueBySkuIdAndCategoryId(Long skuId, Long categoryId) {
        // 1、根据categoryId，以及检索属性的search_type = 1，去attr表中获取--检索类型的属性集合
        QueryWrapper<AttrEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("search_type", 1).eq("category_id", categoryId);
        List<AttrEntity> attrEntities = this.attrMapper.selectList(queryWrapper);

        // 进行判空--若获取的attr属性集合为空，则显然没有符合条件的值，直接返回即可
        if (CollectionUtils.isEmpty(attrEntities)) {
            return null;
        }
        // 根据attrEntity集合，获取attr_id集合
        List<Long> attrIds = attrEntities.stream().map(attrEntity -> attrEntity.getId()).collect(Collectors.toList());

        // 2、根据检索类型的属性的attrId，以及skuId，去sku_attr_value表中获取当前skuId对应的销售属性的值
        QueryWrapper<SkuAttrValueEntity> skuAttrValueEntityQueryWrapper = new QueryWrapper<>();
        skuAttrValueEntityQueryWrapper.eq("sku_id", skuId).in("attr_id", attrIds);

        return this.list(skuAttrValueEntityQueryWrapper);
    }

    @Autowired
    private SkuMapper skuMapper;

    /**
     * 根据spuId查询所有的sku销售属性
     * @param spuId
     * @return
     */
    @Override
    public List<SaleAttrValueVo> queryAllSkuAttrsBuSpuId(Long spuId) {
        // 1、首先根据spuId，在pms_sku表中查询出所有对应的sku信息
        QueryWrapper<SkuEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("spu_id", spuId);
        List<SkuEntity> skuEntityList = this.skuMapper.selectList(queryWrapper);

        if ( skuEntityList == null) return null;    // 判空，才能继续进行

        // 2、获取skuId
        List<Long> skuIdList = skuEntityList.stream().map(SkuEntity::getId).collect(Collectors.toList());

        if ( skuIdList == null) return null;

        // 3、根据skuId，查询得到sku的销售属性信息
        List<SkuAttrValueEntity> skuAttrValueEntityList =
                this.list(new QueryWrapper<SkuAttrValueEntity>().in("sku_id", skuIdList));

        if ( skuAttrValueEntityList == null) return null;   // 判空，才能继续

        // 4、将得到的销售属性封装进List<SaleAttrValueVo>中：[{attrId: 4, attrName: 颜色, attrValues: ["暗夜黑", "白天白"]}, {}..]
        // 4.1 故需要根据attrId对规格参数进行分组
        // 得到的Map, 以attrId作为key；以行的集合，即List<SkuAttrValueEntity>作为value
        // {3:[SkuAttrValueEntity,SkuAttrValueEntity], 4:[], 5:[]}
        Map<Long, List<SkuAttrValueEntity>> map =
                skuAttrValueEntityList.stream().collect(Collectors.groupingBy(SkuAttrValueEntity::getAttrId));
        // 4.2 将Map，转为List<SaleAttrValueVo>
        List<SaleAttrValueVo> attrValueVoList = new ArrayList<>();
        map.forEach( (attrId, skuAttrValueEntities) -> {    // Map的forEach遍历需要两个参数：key、value
            SaleAttrValueVo saleAttrValueVo = new SaleAttrValueVo();

            saleAttrValueVo.setAttrId(attrId);

            if ( skuAttrValueEntities != null) {
                saleAttrValueVo.setAttrName( skuAttrValueEntities.get(0).getAttrName());

                // 使用set来接收--attrName需要去重。多个SkuAttrValueEntity的attrName是相同的。
                Set<String> skuAttrValueSet = skuAttrValueEntities.stream().map(SkuAttrValueEntity::getAttrValue).collect(Collectors.toSet());
                saleAttrValueVo.setAttrValues(skuAttrValueSet);
            }

            attrValueVoList.add(saleAttrValueVo);
        });

        return attrValueVoList;
    }

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;
    /**
     * 查询sku的attrs与skuId的映射关系
     * @return
     */
    public String querySaleAttrsMappingSkuId(Long spuId) {
        // 1、根据skuId查询attr的字段名与值的关系
        // [{sku_id=16, attr_values=金色,镀金,6G,256G}, {sku_id=17, attr_values=6G,256G,黑色,镀金}]
        List<Map<String, Object>> maps = this.skuAttrValueMapper.querySaleAttrsMappingSkuId(spuId);

        if ( CollectionUtils.isEmpty(maps)) return null;
        
        // 2、将maps转为一个Map
        // Collectors.toMap()中有两个参数--Function类型接口。第一个是关于key的处理；第二个是关于value的处理。
        Map<String, Long> mapping = maps.stream().
                                    collect(Collectors.toMap(map -> map.get("attr_values").toString(),
                                                             map -> (Long)map.get("sku_id")));
        // 最终得到的mapping:{"256G,金色,镀金,6G":16,"黑色,镀金,6G,256G":17}
        return JSON.toJSONString(mapping);
    }

    public String testMapping(Long spuId) {
        Map<String, TestVo> testVoMap = this.skuAttrValueMapper.testMapping(spuId);

        Map<String, Long> result = new HashMap<>();
        testVoMap.forEach( (key, value) -> {
            result.put(key.toString(), value.getSkuId());
        });
        // 最终得到的result：{"256G,金色,镀金,6G":16,"256G,黑色,镀金,6G":17}
        return JSON.toJSONString(result);
    }
}