package com.atguigu.gmall.pms.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.CategoryMapper;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.service.CategoryService;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity> implements CategoryService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<CategoryEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<CategoryEntity> queryCategoriesByParentId(Long parentId) {
        QueryWrapper<CategoryEntity> queryWrapper = new QueryWrapper<>();
        // parentId为-1，则查询所有；！=-1，则进行设置，查询相应分类。
        queryWrapper = parentId == -1 ? queryWrapper : queryWrapper.eq("parent_id", parentId);
        return baseMapper.selectList(queryWrapper);
    }

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public List<CategoryEntity> queryLv2CategoriesWithSubsByParentId(Long parentId) {

        return this.categoryMapper.queryLv2CategoriesWithSubsByParentId(parentId);
    }

    @Override
    public List<CategoryEntity> queryAllLvCategoriesByCategoryId3(Long categoryId) {
        // 1、根据三级分类Id查询三级分类
        CategoryEntity categoryEntityLv3 = this.getById(categoryId);
        if ( categoryEntityLv3 == null) return null;

        // 2、根据二级分类Id查询二级分类
        CategoryEntity categoryEntityLv2 = this.getById(categoryEntityLv3.getParentId());
        if ( categoryEntityLv2 == null) return null;

        // 3、根据一级分类Id查询一级分类
        CategoryEntity categoryEntityLv1 = this.getById(categoryEntityLv2.getParentId());
        return Arrays.asList(categoryEntityLv1, categoryEntityLv2, categoryEntityLv3);
    }

}