package com.atguigu.gmall.pms.api;


import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GmallPmsApi {

    /**
     * 根据categoryId，spuId，skuId，查询分组及组内所有attr及对应的值
     * @param categoryId
     * @param spuId
     * @param skuId
     * @return
     */
    @GetMapping(value = "pms/attrgroup/categoryId/spuId/skuId/{categoryId}")
    public ResponseVo<List<GroupVo>> queryGroupAndAttrsByCategoryIdAndSpuIdAndSkuId(@PathVariable(value = "categoryId") Long categoryId,
                                                                                    @RequestParam(value = "spuId") Long spuId,
                                                                                    @RequestParam(value = "skuId") Long skuId);

    /**
     * 根据spuId，查询销售属性与skuId的映射关系
     * @param spuId
     * @return
     */
    @GetMapping(value = "pms/skuattrvalue/sku/mapping/{spuId}")
    public ResponseVo<String> querySaleAttrsMappingSkuId(@PathVariable(value = "spuId")Long spuId);

    /**
     * 根据spuId，查询其对应的所有的sku的销售属性
     * @param spuId
     * @return
     */
    @GetMapping(value = "pms/skuattrvalue/spu/{spuId}")
    public ResponseVo<List<SaleAttrValueVo>> queryAllSkuAttrsBuSpuId(@PathVariable(value = "spuId") Long spuId);

    /**
     * 根据spuId查询spuDescEntity
     * @param spuId
     * @return
     */
    @GetMapping("pms/spudesc/{spuId}")
    public ResponseVo<SpuDescEntity> querySpuDescById(@PathVariable("spuId") Long spuId);

    /**
     * 根据skuId查询sku属性
     * @param skuId
     * @return
     */
    @GetMapping(value = "pms/skuattrvalue/sale/attr/{skuId}")
    public ResponseVo<List<SkuAttrValueEntity>> querySaleAttrsBySkuId(@PathVariable(value = "skuId")Long skuId);

    /**
     * 根据skuId查询sku图片的信息
     * @param skuId
     * @return
     */
    @GetMapping(value = "pms/skuimages/sku/{skuId}")
    public ResponseVo<List<SkuImagesEntity>> queryImagesBySkuId(@PathVariable(value = "skuId")Long skuId);

    /**
     * 根据三级分类Id,查询二级分类Id、一级分类Id
     * @param categoryId
     * @return
     */
    @GetMapping(value = "pms/category/all/{categoryId}")
    public ResponseVo<List<CategoryEntity>> queryAllLvCategoriesByCategoryId3(
            @PathVariable(value = "categoryId") Long categoryId);

    /**
     * 根据skuId查询sku信息
     * @param id
     * @return
     */
    @GetMapping("pms/sku/{id}")
    public ResponseVo<SkuEntity> querySkuById(@PathVariable("id") Long id);


    /**
     * 获取spu信息
     * @param pageParamVo
     * @return
     */
    @PostMapping(value = "pms/spu/json")
    public ResponseVo<List<SpuEntity>> querySpuByPageJson(@RequestBody PageParamVo pageParamVo);

    /**
     * 根据spuID查询sku信息
     * @param spuId
     * @return
     */
    @GetMapping(value = "pms/sku/spu/{spuId}")
    public ResponseVo<List<SkuEntity>> querySkusBySpuId(@PathVariable(value = "spuId") long spuId);

    /**
     * 根据brandId查询品牌
     * @param id
     * @return
     */
    @GetMapping("pms/brand/{id}")
    public ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

    /**
     * 根据categoryId查询分类
     * @param id
     * @return
     */
    @GetMapping("pms/category/{id}")
    public ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);


    /**
     * 根据skuId和categoryId，查询sku属性
     * @param skuId
     * @param categoryId
     * @return
     */
    @GetMapping(value = "pms/skuattrvalue/search/attr/{skuId}")
    public ResponseVo<List<SkuAttrValueEntity>> querySearchSkuAttrValueBySkuIdAndCategoryId(
            @PathVariable(value = "skuId") Long skuId,
            @RequestParam Long categoryId
    );

    /**
     * 根据spuId和categoryId，查询spu属性
     * @param spuId
     * @param categoryId
     * @return
     */
    @GetMapping(value = "pms/spuattrvalue/search/attr/{spuId}")
    public ResponseVo<List<SpuAttrValueEntity>> querySearchSpuAttrValueBySpuIdAndCategoryId(
            @PathVariable(value = "spuId") Long spuId,
            @RequestParam Long categoryId
    );


    @GetMapping("pms/spu/{id}")
    public ResponseVo<SpuEntity> querySpuById(@PathVariable("id") Long id);


    /**
     * 查询一级分类
     * @param parentId
     * @return
     */
    @GetMapping(value = "pms/category/parent/{parentId}")
    public ResponseVo<List<CategoryEntity>> queryCategoriesByParentId(
            @PathVariable(value = "parentId") Long parentId
    );

    /**
     * 通过一级分类Id，查找二级、三级分类
     * @param parentId
     * @return
     */
    @GetMapping(value = "pms/category/parent/sub/{parentId}")
    public ResponseVo<List<CategoryEntity>> queryLv2CategoriesWithSubsByParentId(
            @PathVariable(value = "parentId")Long parentId
    );
}
