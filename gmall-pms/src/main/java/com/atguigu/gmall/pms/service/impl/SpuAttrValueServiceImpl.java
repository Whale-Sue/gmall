package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.service.SpuAttrValueService;
import org.springframework.util.CollectionUtils;


@Service("spuAttrValueService")
public class SpuAttrValueServiceImpl extends ServiceImpl<SpuAttrValueMapper, SpuAttrValueEntity> implements SpuAttrValueService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Autowired
    private AttrMapper attrMapper;

    @Override
    public List<SpuAttrValueEntity> querySearchSpuAttrValueBySpuIdAndCategoryId(Long spuId, Long categoryId) {
        // 1、根据categoryId、search_type = 1，去attr表中获取--检索类型的属性的集合
        QueryWrapper<AttrEntity> attrEntityQueryWrapper = new QueryWrapper<>();
        attrEntityQueryWrapper.eq("category_id", categoryId).eq("search_type", 1);
        List<AttrEntity> attrEntities = this.attrMapper.selectList(attrEntityQueryWrapper);

        // 判空
        if (CollectionUtils.isEmpty(attrEntities)) {
            return null;
        }
        // 获取attrIds
        List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());

        // 2、根据attrIds、spuId，去spu_attr表中获取--当前spuId对应的属性的集合
        QueryWrapper<SpuAttrValueEntity> spuAttrValueEntityQueryWrapper = new QueryWrapper<>();
        spuAttrValueEntityQueryWrapper.eq("spu_id", spuId).in("attr_id", attrIds);
        return this.list(spuAttrValueEntityQueryWrapper);
    }


}