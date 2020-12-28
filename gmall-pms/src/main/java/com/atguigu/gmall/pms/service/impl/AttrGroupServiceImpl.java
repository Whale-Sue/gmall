package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.GroupVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;
import org.springframework.util.CollectionUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupMapper, AttrGroupEntity> implements AttrGroupService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrGroupEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageResultVo(page);
    }

    @Autowired
    private AttrMapper attrMapper;

    @Override
    public List<AttrGroupEntity> queryGroupsWithAttrsByCategoryId(Long categoryId) {
        // 1、根据categoryId，查询属性分组列表。
        QueryWrapper<AttrGroupEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_id", categoryId);
        List<AttrGroupEntity> attrGroupEntities = this.list(queryWrapper);

        // 2、遍历得到的分组，查询每个分组下的attr列表。
        if ( !CollectionUtils.isEmpty(attrGroupEntities)) {
            attrGroupEntities.forEach( attrGroup -> {
                List<AttrEntity> attrEntities =
                        this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", attrGroup.getId()).eq("type", 1));
                attrGroup.setAttrEntities(attrEntities);
            });
        }
        return attrGroupEntities;
    }


    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SpuAttrValueMapper spuAttrValueMapper;

    @Override
    public List<GroupVo> queryGroupAndAttrsByCategoryIdAndSpuIdAndSkuId(Long categoryId, Long spuId, Long skuId) {
        // 1、根据分类id, 到attr_group表中查询分组
        List<AttrGroupEntity> attrGroupEntities = this.list(
                                                    new QueryWrapper<AttrGroupEntity>().eq("category_id", categoryId));
        if ( attrGroupEntities == null) return null;

        // 2、遍历分组，查询分组下的属性参数
         return attrGroupEntities.stream().map( attrGroupEntity -> {
            GroupVo groupVo = new GroupVo();

            groupVo.setGroupId( attrGroupEntity.getId());
            groupVo.setGroupName( attrGroupEntity.getName());

            // 2.1 根据groupId, 到attr表中查询attr_id
            List<AttrEntity> attrEntityList = this.attrMapper.selectList(
                                                new QueryWrapper<AttrEntity>().eq("group_id", attrGroupEntity.getId()));

            // 2.2 拿到attr_id，分别到sku_attr_value、spu_attr_value表中，获取attr_value
            if ( !CollectionUtils.isEmpty(attrEntityList)) {
                List<Long> attrIdList = attrEntityList.stream().map(AttrEntity::getId).collect(Collectors.toList());

                List<AttrValueVo> attrValueVos = new ArrayList<>();

                // 2.3、到sku_attr_value中，查询分组中销售属性的值
                List<SkuAttrValueEntity> skuAttrValueEntityList = this.skuAttrValueMapper.selectList(
                                                                    new QueryWrapper<SkuAttrValueEntity>()
                                                                            .in("attr_id", attrIdList)
                                                                            .eq("sku_id", skuId));
                if ( !CollectionUtils.isEmpty(skuAttrValueEntityList)) {
                    attrValueVos.addAll(skuAttrValueEntityList.stream().map( skuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();

                        BeanUtils.copyProperties(skuAttrValueEntity, attrValueVo);

                        return attrValueVo;
                    }).collect(Collectors.toList()));
                }

                // 2.4、到spu_attr_value中，查询分组中基本属性的值
                List<SpuAttrValueEntity> spuAttrValueEntityList = this.spuAttrValueMapper.selectList(
                                                                    new QueryWrapper<SpuAttrValueEntity>()
                                                                            .in("attr_id", attrIdList)
                                                                            .eq("spu_id", spuId));
                if ( !CollectionUtils.isEmpty(spuAttrValueEntityList)) {
                    attrValueVos.addAll(spuAttrValueEntityList.stream().map( spuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();

                        BeanUtils.copyProperties(spuAttrValueEntity, attrValueVo);

                        return attrValueVo;
                    }).collect(Collectors.toList()));
                }

                groupVo.setAttrs(attrValueVos);
            }

            return groupVo;
        }).collect(Collectors.toList());
    }

}