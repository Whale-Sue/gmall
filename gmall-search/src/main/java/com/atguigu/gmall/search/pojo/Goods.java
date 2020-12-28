package com.atguigu.gmall.search.pojo;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "goods", type = "info", shards = 3, replicas = 2)
public class Goods {
    // 分析商品搜索结果页面，可以得知页面由三个部分组成—搜索条件聚集结果；排序区；商品展示区

    // 1、商品列表所需字段
    @Id
    @Field(type = FieldType.Long)
    private Long skuId;
    @Field(type = FieldType.Keyword, index = false)
    private String defaultImage;
    @Field(type = FieldType.Double)
    private Double price;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;
    @Field(type = FieldType.Keyword, index = false)
    private String subTitle;

    // 2、排序分页所需字段
    @Field(type = FieldType.Long)
    private Long sales = 0L;
    @Field(type = FieldType.Date)
    private Date createTime;
    @Field(type = FieldType.Boolean)
    private Boolean store = false;

    // 3、过滤所需字段
    @Field(type = FieldType.Long)
    private Long brandId;
    @Field(type = FieldType.Keyword)
    private String brandName;
    @Field(type = FieldType.Keyword)
    private String logo;

    // 4、分类所需字段
    @Field(type = FieldType.Long)
    private Long categoryId;
    @Field(type = FieldType.Keyword)
    private String categoryName;

    // 5、基础属性
    @Field(type = FieldType.Nested)
    private List<SearchAttrValue> searchAttrs;
}
