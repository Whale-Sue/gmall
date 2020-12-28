package com.atguigu.gmall.item.service;


import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.api.GmallWmsApi;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();

        // 0、根据skuId，查询sku信息
        CompletableFuture<SkuEntity> skuEntityCompletableFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();

            if (skuEntity == null) return null;

            itemVo.setSkuId(skuEntity.getId());
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            itemVo.setWeight(skuEntity.getWeight());

            Long categoryId = skuEntity.getCatagoryId();
            Long spuId = skuEntity.getSpuId();

            return skuEntity;
        }, threadPoolExecutor);

        // 1、根据sku信息，查询面包屑的相关信息
        // 1.1 根据CatagoryId查询一二三级分类
        CompletableFuture<Void> catesCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<List<CategoryEntity>> categoriesEntityListResponseVo = this.pmsClient.queryAllLvCategoriesByCategoryId3(skuEntity.getCatagoryId());
            List<CategoryEntity> categoryEntityList = categoriesEntityListResponseVo.getData();
            itemVo.setCategories(categoryEntityList);
        }, threadPoolExecutor);


        // 1.2 根据brandId，查询品牌信息
        CompletableFuture<Void> brandCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        // 1.3 根据spuId，查询spu信息
        CompletableFuture<Void> spuCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, threadPoolExecutor);

        // 2、根据sku信息，查询sku的详细信息
        // 2.1 根据skuId查询sku图片列表
        CompletableFuture<Void> imageCompletableFuture = CompletableFuture.runAsync(() -> {

            ResponseVo<List<SkuImagesEntity>> imagesListResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntityList = imagesListResponseVo.getData();
            itemVo.setImages(skuImagesEntityList);
        }, threadPoolExecutor);

        // 2.2 根据skuId查询sku营销信息
        CompletableFuture<Void> skuSalesCompletableFuture = CompletableFuture.runAsync(() -> {

            ResponseVo<List<ItemSaleVo>> itemSaleVoListResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> itemSaleVoList = itemSaleVoListResponseVo.getData();
            itemVo.setSales(itemSaleVoList);
        }, threadPoolExecutor);

        // 2.3 根据skuIdsku库存信息
        CompletableFuture<Void> wareCompletableFuture = CompletableFuture.runAsync(() -> {

            ResponseVo<List<WareSkuEntity>> wareSkuEntityListResponseVo = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntityList = wareSkuEntityListResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntityList)) {
                boolean flag = wareSkuEntityList.stream()
                        .anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0);
                itemVo.setStore(flag);
            }
        }, threadPoolExecutor);

        // 2.4 根据spuId查询spu下所有sku的销售属性
        CompletableFuture<Void> allSkuAttrsCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<List<SaleAttrValueVo>> saleAttrValueVoListResponseVo = this.pmsClient.queryAllSkuAttrsBuSpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrValueVoList = saleAttrValueVoListResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrValueVoList);
        }, threadPoolExecutor);

        // 2.5 根据skuId查询当前sku的销售属性
        CompletableFuture<Void> currSkuAttrsCompletableFuture = CompletableFuture.runAsync(() -> {

            ResponseVo<List<SkuAttrValueEntity>> skuAttrValueEntityListResponseVo = this.pmsClient.querySaleAttrsBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntityList = skuAttrValueEntityListResponseVo.getData();
            if (!CollectionUtils.isEmpty(skuAttrValueEntityList)) {
                Map<Long, String> map = skuAttrValueEntityList
                        .stream()
                        .collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue));
                itemVo.setSaleAttr(map);
            }
        }, threadPoolExecutor);

        // 2.6根据spuId查询spu下所有sku和销售属性组合的映射关系
        CompletableFuture<Void> mappingCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<String> mappingResponseVo = this.pmsClient.querySaleAttrsMappingSkuId(skuEntity.getSpuId());
            String json = mappingResponseVo.getData();
            itemVo.setSkusJson(json);
        }, threadPoolExecutor);

        // 3、商品介绍
        // 3.1根据spuId查询商品描述信息
        CompletableFuture<Void> descCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null) {
                itemVo.setSpuImages(Arrays.asList(StringUtils.split(spuDescEntity.getDecript(), ",")));
            }
        }, threadPoolExecutor);

        // 3.2 根据cid、skuId、spuId查询组及组下的规格参数值
        CompletableFuture<Void> groupsCompletableFuture = skuEntityCompletableFuture.thenAcceptAsync(skuEntity -> {

            ResponseVo<List<GroupVo>> groupVoListResponseVo = this.pmsClient
                    .queryGroupAndAttrsByCategoryIdAndSpuIdAndSkuId(skuEntity.getCatagoryId(), skuId, skuEntity.getSpuId());
            List<GroupVo> groupVoList = groupVoListResponseVo.getData();
            itemVo.setGroups(groupVoList);
        }, threadPoolExecutor);

        CompletableFuture.allOf(catesCompletableFuture, brandCompletableFuture, spuCompletableFuture,
                imageCompletableFuture, skuSalesCompletableFuture, wareCompletableFuture, allSkuAttrsCompletableFuture,
                currSkuAttrsCompletableFuture, mappingCompletableFuture, descCompletableFuture, groupsCompletableFuture).join();

        return itemVo;
    }


}

class ThreadTest {
    public static void main(String[] args) throws IOException {

        /*CompletableFuture.runAsync( () -> {
            System.out.println("通过CompletableFuture的runAsync，初始化一个子任务");
        });*/

        /*CompletableFuture<String> completableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("通过CompletableFuture的supplyAsync，初始化一个子任务");
            return "";
        });*/


        /*CompletableFuture<String> completableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("通过CompletableFuture的supplyAsync，初始化一个子任务");

            int i = 1 / 0;

            return "completableFuture";
        }).whenCompleteAsync( (t, u) -> {
            System.out.println("t: " + t);
            System.out.println("u: " + u);
        }).exceptionally( (t) -> {
            System.out.println("t: " + t);

            return "";
        });*/


        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("通过CompletableFuture的supplyAsync，初始化一个子任务");

            //int i = 1 / 0;

            return "completableFuture1";
        });


        CompletableFuture<String> future1 = future.thenApplyAsync(t -> {
            System.out.println("========================thenApplyAsync1===========================");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t: " + t);
            return "hello thenApplyAsync";
        });

        CompletableFuture<Void> future2 = future.thenAcceptAsync(t -> {
            System.out.println("========================thenAcceptAsync2===========================");
            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t: " + t);
        });

        CompletableFuture<Void> future3 = future.thenRunAsync(() -> {
            System.out.println("========================thenRunAsync3===========================");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("这个任务是thenRun任务");
        });



        CompletableFuture.allOf(future1, future2, future3).join();

        System.out.println("this is main()");

        System.in.read();


        /*ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
        scheduledExecutorService.scheduleAtFixedRate( () -> {
            System.out.println("定时任务" + System.currentTimeMillis());
        }, 5, 10, TimeUnit.SECONDS);*/
    }
}