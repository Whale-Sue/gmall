package com.atguigu.gmall.search;


import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    /**
     * 查询MySQL数据库中的商品信息(来源自sku、spu、仓库表)，存入Goods集合，并将Goods批量保存
     */
    /*@Test
    void contextLoads() {
        // 1、初始化索引库和映射
        this.restTemplate.createIndex(Goods.class);
        this.restTemplate.putMapping(Goods.class);

        // 2、循环查询spu，并遍历得到spu下对应的sku，将相应属性放入Goods中，最后保存
        Integer pageNum = 1, pageSize = 100;
        do {
            // 2、1 构建PageParamVo对象，查询spu
            PageParamVo pageParamVo = new PageParamVo();
            pageParamVo.setPageNum(pageNum);
            pageParamVo.setPageSize(pageSize);

            ResponseVo<List<SpuEntity>>spuResponseVo = this.pmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = spuResponseVo.getData();
            // 若spu集合为空，则说明没有要查询的商品，直接退出
            if (CollectionUtils.isEmpty(spuEntities)) break;

            // 2、2 遍历spuEntity，查询sku集合，将得到的商品信息保存入Goods集合中，最终saveAll()
            spuEntities.forEach( spuEntity -> {
                // 2.2.1 查询sku集合，并将属性存入Goods中
                Long spuId = spuEntity.getId();
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuId);
                List<SkuEntity> skuEntities = skuResponseVo.getData();

                if ( !CollectionUtils.isEmpty(skuEntities)) {// 进行判空
                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Long skuId = skuEntity.getId();
                        Goods goods = new Goods();

                        // 存入sku信息
                        goods.setSkuId(skuEntity.getId());
                        goods.setTitle(skuEntity.getTitle());
                        goods.setSubTitle(skuEntity.getSubtitle());
                        goods.setPrice(skuEntity.getPrice().doubleValue());
                        goods.setDefaultImage(skuEntity.getDefaultImage());

                        // 存入spu中的创建时间
                        goods.setCreateTime(spuEntity.getCreateTime());

                        // 查询并保存库存信息
                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuId);
                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                            // 只要有一个仓库的库存>0，库存就有效
                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));

                            goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                        }

                        // 查询品牌
                        Long brandId = skuEntity.getBrandId();
                        ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(brandId);
                        BrandEntity brandEntity = brandEntityResponseVo.getData();
                        if (brandEntity != null) {
                            goods.setBrandId(brandId);
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }

                        // 查询分类
                        Long catagoryId = skuEntity.getCatagoryId();
                        ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(catagoryId);
                        CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                        if (categoryEntity != null) {
                            goods.setCategoryId(catagoryId);
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        // 查询attrs
                        List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                        // 查询销售属性
                        ResponseVo<List<SkuAttrValueEntity>> skuAttrResponseVo = this.pmsClient.querySearchSkuAttrValueBySkuIdAndCategoryId(skuId, catagoryId);
                        List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrResponseVo.getData();
                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                            List<SearchAttrValue> skuAttrValueList = skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue = new SearchAttrValue();
                                BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValue);
                                return searchAttrValue;
                            }).collect(Collectors.toList());

                            searchAttrValues.addAll(skuAttrValueList);
                        }

                        // 查询基本属性
                        ResponseVo<List<SpuAttrValueEntity>> spuAttrResponseVo = this.pmsClient.querySearchSpuAttrValueBySpuIdAndCategoryId(spuId, catagoryId);
                        List<SpuAttrValueEntity> spuAttrValueEntities = spuAttrResponseVo.getData();
                        if (spuAttrValueEntities != null) {
                            List<SearchAttrValue> spuAttrValueList = spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue = new SearchAttrValue();
                                BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValue);
                                return searchAttrValue;
                            }).collect(Collectors.toList());

                            searchAttrValues.addAll(spuAttrValueList);
                        }
                        goods.setSearchAttrs(searchAttrValues);

                        return goods;
                    }).collect(Collectors.toList());

                    // 完成保存
                    this.goodsRepository.saveAll(goodsList);
                }
            });

            // 3、更新循环条件--出口参数
            pageSize = spuEntities.size();
            pageNum++;

        } while (pageSize == 100);
    }*/

    @Test
    void contextLoads() {

        // 初始化索引库和映射
        this.restTemplate.createIndex(Goods.class);
        this.restTemplate.putMapping(Goods.class);

        Integer pageNum = 1;
        Integer pageSize = 100;
        do{
            PageParamVo pageParamVo = new PageParamVo();
            pageParamVo.setPageNum(pageNum);
            pageParamVo.setPageSize(pageSize);
            // 分页查询spu
            ResponseVo<List<SpuEntity>> listResponseVo = this.pmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = listResponseVo.getData();
            if (CollectionUtils.isEmpty(spuEntities)){
                break;
            }
            // 遍历spu，查询spu下所有sku集合，转化成goods集合 saveAll
            spuEntities.forEach(spuEntity -> {
                // 查询spu下sku
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuEntities)){
                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();

                        // sku相关信息设置进来
                        goods.setSkuId(skuEntity.getId());
                        goods.setTitle(skuEntity.getTitle());
                        goods.setSubTitle(skuEntity.getSubtitle());
                        goods.setPrice(skuEntity.getPrice().doubleValue());
                        goods.setDefaultImage(skuEntity.getDefaultImage());

                        // spu中的创建时间
                        goods.setCreateTime(spuEntity.getCreateTime());

                        // 查询库存相关信息并setter
                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuEntity.getId());
                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)){
                            // 只要有任何一个仓库的库存-锁定库存大于0
                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                            goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                        }

                        // 查询品牌
                        ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
                        BrandEntity brandEntity = brandEntityResponseVo.getData();
                        if (brandEntity != null) {
                            goods.setBrandId(brandEntity.getId());
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }

                        // 查询分类
                        ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(skuEntity.getCatagoryId());
                        CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                        if (categoryEntity != null) {
                            goods.setCategoryId(categoryEntity.getId());
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        // 查询规格参数
                        List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                        // 销售类型的检索规格参数及值
                        ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResponseVo = this.pmsClient.querySearchSkuAttrValueBySkuIdAndCategoryId(skuEntity.getId(), skuEntity.getCatagoryId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();
                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                            searchAttrValues.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue = new SearchAttrValue();
                                BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValue);
                                return searchAttrValue;
                            }).collect(Collectors.toList()));
                        }

                        // 基本类型的检索规格参数及值
                        ResponseVo<List<SpuAttrValueEntity>> spuAttrValueResponseVo = this.pmsClient.querySearchSpuAttrValueBySpuIdAndCategoryId(skuEntity.getSpuId(), skuEntity.getCatagoryId());
                        List<SpuAttrValueEntity> spuAttrValueEntities = spuAttrValueResponseVo.getData();
                        if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                            searchAttrValues.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue = new SearchAttrValue();
                                BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValue);
                                return searchAttrValue;
                            }).collect(Collectors.toList()));
                        }
                        goods.setSearchAttrs(searchAttrValues);

                        return goods;
                    }).collect(Collectors.toList());

                    this.goodsRepository.saveAll(goodsList);
                }
            });

            pageSize = spuEntities.size();
            pageNum++;
        } while (pageSize == 100);
    }
}
