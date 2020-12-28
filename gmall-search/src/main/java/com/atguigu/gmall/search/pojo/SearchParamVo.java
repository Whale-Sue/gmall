package com.atguigu.gmall.search.pojo;


import lombok.Data;

import java.util.List;


/**
 * 查询URL：
 *  search.gmall.com/search?keyword=手机&brandId=1,2&categoryId=225&priceFrom=100&priceTo=7000&store=false&props=4:8G-12G&sort=1&pageNum=1
 *
 *
 *  用于接收搜索参数的数据模型
 */
@Data
public class SearchParamVo {

    // 搜索关键字
    private String keyword;

    // 品牌过滤条件--brandIds
    private List<Long> brandId;

    // 分类过滤条件--categoryIds
    private List<Long> categoryId;

    // 规格参数过滤条件--props，&props=4:8G-12G&props=5:128G-256G
    private List<String> props;

    // 排序字段--sort，1-价格升序 2-价格降序 3-销量降序 4-新品降序  默认0，使用得分排序
    private Integer sort;

    // 价格区间过滤条件
    private Double priceTo;
    private Double priceFrom;

    // 是否有库存
    private Boolean store;

    // 分页条件
    private Integer pageNum = 1; // 页码，默认第一页
    private Integer pageSize = 20; // 每页含有的Goods数目
}
