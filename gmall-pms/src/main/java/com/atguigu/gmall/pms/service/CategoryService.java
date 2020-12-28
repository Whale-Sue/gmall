package com.atguigu.gmall.pms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.CategoryEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品三级分类
 *
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-10-28 16:54:26
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<CategoryEntity> queryCategoriesByParentId(Long parentId);

    List<CategoryEntity> queryLv2CategoriesWithSubsByParentId(Long parentId);

    List<CategoryEntity> queryAllLvCategoriesByCategoryId3(Long categoryId);
}

