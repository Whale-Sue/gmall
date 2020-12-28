package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient highLevelClient;

    @Autowired
    public static final ObjectMapper objectMapper = new ObjectMapper();

    public SearchResponseVo search(SearchParamVo searchParamVo) {

        try {
            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, buildDsl(searchParamVo));
            SearchResponse searchResponse = this.highLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            System.out.println("searchResponse = " + searchResponse);

            // 解析响应结果：将SearchResponse解析为SearchResponseVo，并返回。
            SearchResponseVo searchResponseVo = parseSearchResponse(searchResponse);

            // 从请求参数对象中获取分页参数
            searchResponseVo.setPageNum(searchParamVo.getPageNum());
            searchResponseVo.setPageSize(searchParamVo.getPageSize());
            return searchResponseVo;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 解析响应结果集
    private SearchResponseVo parseSearchResponse(SearchResponse searchResponse) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();

        // 1、解析hits命中对象
        SearchHits hits = searchResponse.getHits();

        // 1.1 解析、设置总记录数
        long totalHits = hits.getTotalHits();
        searchResponseVo.setTotal(totalHits);

        // 1.2 解析、设置当前页的Goods
        SearchHit[] hitsHits = hits.getHits();      // hitsHits是hits内的命中对象，要将hitsHits转换为Goods集合
        if ( hitsHits == null || hitsHits.length == 0) {
            // TODO:当前页没有符合条件的商品--可以打广告
            return null;
        }
        List<Goods> goodsList = Arrays.stream(hitsHits).map(hitsHit -> {
            try {
                String json = hitsHit.getSourceAsString();
                // 可以使用alibaba的fastJson进行解析，但是有漏洞：
                // Goods goods = JSON.parseObject(json, Goods.class);

                // 使用jackson
                Goods goods = objectMapper.readValue(json, Goods.class);

                // 设置高亮title给Goods
                Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
                HighlightField highlightField = highlightFields.get("title");
                Text[] title = highlightField.getFragments();
                goods.setTitle(title[0].string());

                return goods;
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            return null;
        }).collect(Collectors.toList());
        searchResponseVo.setGoodsList(goodsList);

        // 2、解析聚合结果集
        // 在aggregations下，有多个聚合。每个聚合都以key: value的方式存在，所以使用Map结构接收很方便
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();

        // 2.1 解析品牌聚合
        // 2.1.1 根据聚合名，获取相应的聚合
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregationMap.get("brandIdAgg");
        // 2.2.2 获取当前聚合中，所有的桶。并从每个桶中获取所需信息
        List<? extends Terms.Bucket> brandIdBuckets = brandIdAgg.getBuckets();
        if ( !CollectionUtils.isEmpty(brandIdBuckets)) {
            List<BrandEntity> brandEntities = brandIdBuckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();

                // 获取当前桶中的key--对应brandId
                brandEntity.setId(bucket.getKeyAsNumber().longValue());

                // 获取brand的各个子聚合
                Map<String, Aggregation> brandAggMap = bucket.getAggregations().asMap();
                // 获取brandNameAgg子聚合
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandAggMap.get("brandNameAgg");
                List<? extends Terms.Bucket> brandNameAggBuckets = brandNameAgg.getBuckets();
                // 获取第一个桶中的元素
                if (!CollectionUtils.isEmpty(brandNameAggBuckets)) {
                    brandEntity.setName(brandNameAggBuckets.get(0).getKeyAsString());
                }

                // 获取logo子聚合
                ParsedStringTerms logoAgg = (ParsedStringTerms) brandAggMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)) {
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }

                return brandEntity;
            }).collect(Collectors.toList());

            searchResponseVo.setBrands(brandEntities);
        }

        // 2.2 解析分类聚合
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if ( !CollectionUtils.isEmpty(categoryIdAggBuckets)) {
            List<CategoryEntity> categoryEntities = categoryIdAggBuckets.stream().map(categoryIdBucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();

                // 获取并设置categoryId
                categoryEntity.setId(categoryIdBucket.getKeyAsNumber().longValue());

                // 从子聚合中获取并设置categoryName
                // 首先还是先获取子聚合--由于只有一个子聚合，所以不再通过Map接收，而是直接使用get("xxx")接收
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms) categoryIdBucket.getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> buckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(buckets)) {
                    categoryEntity.setName(buckets.get(0).getKeyAsString());
                }

                return categoryEntity;
            }).collect(Collectors.toList());

            searchResponseVo.setCategories(categoryEntities);
        }

        // 2.3 解析规格参数聚合
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // 2.3.1 获取嵌套聚合中的子聚合--attrIdAgg，由于只有这一个子聚合所以不再使用Map结构来接收
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
        // 2.3.2 获取attrIdAgg聚合中的各个桶
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if ( !CollectionUtils.isEmpty(buckets)) {       // 在buckets不为空的情况下，将桶集合转化为List<SearchResponseAttrVo>集合
            List<SearchResponseAttrVo> filters = buckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();

                // 获取attrVo的Id
                searchResponseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());

                // 获取当前bucket中的所有子聚合
                Map<String, Aggregation> attrAggMap = bucket.getAggregations().asMap();
                // 获取attrNameAgg子聚合
                ParsedStringTerms attrNameAgg = (ParsedStringTerms) attrAggMap.get("attrNameAgg");
                List<? extends Terms.Bucket> attrNameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrNameAggBuckets)) {
                    // 应该只有一个桶，故只需获取第一个桶的key
                    searchResponseAttrVo.setAttrName(attrNameAggBuckets.get(0).getKeyAsString());
                }

                // 获取attrValueAgg子聚合
                ParsedStringTerms attrValueAgg = (ParsedStringTerms) attrAggMap.get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)) {
                    // List<String> attrValue--故需将attrValueAggBuckets集合，转为List<String>
                    List<String> stringList = attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    searchResponseAttrVo.setAttrValues(stringList);
                }

                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setFilters(filters);
        }

        return searchResponseVo;
    }

    // 拼接查询、过滤、聚合条件，返回sourceBuilder
    private SearchSourceBuilder buildDsl(SearchParamVo searchParamVo) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        String keyword = searchParamVo.getKeyword();
        if (StringUtils.isBlank(keyword)) {     // 若关键字为空，则后续的参数拼接都没有必要
            // TODO:打广告
            return searchSourceBuilder;
        }

        // 1、构建查询及过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        searchSourceBuilder.query(boolQueryBuilder);

        // 1.1 构建匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        // 1.2 构建过滤条件
        // 1.2.1 构建brandId过滤
        List<Long> brandIds = searchParamVo.getBrandId();
        if ( !CollectionUtils.isEmpty(brandIds)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandIds));
        }
        // 1.2.2 构建categoryId过滤
        List<Long> categoryIds = searchParamVo.getCategoryId();
        if ( !CollectionUtils.isEmpty(categoryIds)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryIds));
        }

        // 1.2.3 构建价格区间过滤
        Double priceFrom = searchParamVo.getPriceFrom();
        Double priceTo = searchParamVo.getPriceTo();
        if ( priceFrom != null || priceTo != null) {
            RangeQueryBuilder priceRangeQuery = QueryBuilders.rangeQuery("price");
            if ( priceFrom != null) {
                priceRangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                priceRangeQuery.lte(priceTo);
            }

            boolQueryBuilder.filter(priceRangeQuery);
        }

        // 1.2.4 构建是否有货过滤
        Boolean store = searchParamVo.getStore();
        if ( store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 1.2.5 构建规格参数的嵌套过滤--[4:8G-12G, 5:128G-256G,   ,]
        List<String> props = searchParamVo.getProps();
        if ( !CollectionUtils.isEmpty(props)) {
            props.forEach( prop -> {
                String[] attrs = StringUtils.split(prop, ":");
                if ( attrs != null && attrs.length == 2) {
                    BoolQueryBuilder boolQueryBuilderOfProps = QueryBuilders.boolQuery();
                    boolQueryBuilderOfProps.must(QueryBuilders.termQuery("searchAttrs.attrId", attrs[0]));  // 4

                    String attrValue = attrs[1];
                    String[] attrValues = StringUtils.split(attrValue, "-");
                    boolQueryBuilderOfProps.must(QueryBuilders.termQuery("searchAttrs.attrValue", attrValues)); // 8G-12G

                    // 嵌套过滤
                    NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery("searchAttrs", boolQueryBuilderOfProps, ScoreMode.None);
                    boolQueryBuilder.filter(nestedQueryBuilder);
                }
            });
        }

        // 2、构建排序条件：1-价格升序 2-价格降序 3-销量降序 4-新品降序  默认0，使用得分排序
        Integer sort = searchParamVo.getSort();
        if ( sort != null) {
            switch(sort) {
                case 1:
                    searchSourceBuilder.sort("price", SortOrder.ASC);
                    break;
                case 2:
                    searchSourceBuilder.sort("price", SortOrder.DESC);
                    break;
                case 3:
                    searchSourceBuilder.sort("sales", SortOrder.DESC);
                    break;
                case 4:
                    searchSourceBuilder.sort("createTime", SortOrder.DESC);
                    break;
                default:
                    searchSourceBuilder.sort("_score", SortOrder.DESC);
                    break;
            }
        }

        // 3、构建分页条件
        Integer pageNum = searchParamVo.getPageNum();
        Integer pageSize = searchParamVo.getPageSize();
        searchSourceBuilder.from((pageNum - 1) * pageSize);
        searchSourceBuilder.size(pageSize);

        // 4、构建高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder()
                                            .field("title")
                                            .preTags("<font style='color:red;'>")
                                            .postTags("</font>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 5、构建聚合查询
        // 5.1 构建brandId聚合
        TermsAggregationBuilder brandIdAgg = AggregationBuilders.terms("brandIdAgg").field("brandId");
        // brandIdAgg，有两个子聚合brandNameAgg、logoAgg--这俩同级
        brandIdAgg.subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"));
        brandIdAgg.subAggregation(AggregationBuilders.terms("logoAgg").field("logo"));
        searchSourceBuilder.aggregation(brandIdAgg);

        // 5.2 构建categoryId聚合
        TermsAggregationBuilder categoryIdAgg = AggregationBuilders.terms("categoryIdAgg").field("categoryId");
        categoryIdAgg.subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName"));
        searchSourceBuilder.aggregation(categoryIdAgg);

        // 5.3 构建attrAgg聚合
        // 三级聚合--attrNameAgg、attrValueAgg；二级聚合--attrIdAgg
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                                            .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                                            .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"));
        // 一级聚合--attrAgg
        NestedAggregationBuilder attrAgg = AggregationBuilders.nested("attrAgg", "searchAttrs");
        attrAgg.subAggregation(attrIdAgg);

        searchSourceBuilder.aggregation(attrAgg);

        // 6、结果集过滤
        searchSourceBuilder.fetchSource(new String[]{"skuId", "title", "subTitle", "defaultImage", "price"}, null);

        System.out.println("searchSourceBuilder = " + searchSourceBuilder);
        return searchSourceBuilder;
    }
}
