package com.atguigu.gmall.search.pojo;


import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {

    // 品牌过滤集合--brandId、brandName、logo
    private List<BrandEntity> brands;

    // 分类过滤集合--categoryId、categoryName
    private List<CategoryEntity> categories;

    // 规格参数过滤集合--每一个元素，对应着一行过滤条件
    private List<SearchResponseAttrVo> filters;

    //  分页响应结果集
    private Integer pageNum;    // 页码
    private Integer pageSize;   // 每页大小
    private Long total;      // 总记录数

    // 展示页面内的Goods列表
    private List<Goods> goodsList;

}
