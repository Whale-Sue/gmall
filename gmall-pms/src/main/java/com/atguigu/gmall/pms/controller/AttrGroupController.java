package com.atguigu.gmall.pms.controller;

import java.util.List;

import com.atguigu.gmall.pms.vo.GroupVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.PageParamVo;

/**
 * 属性分组
 *
 * @author Whale_Su
 * @email 2033763785@qq.com
 * @date 2020-10-28 16:54:26
 */
@Api(tags = "属性分组 管理")
@RestController
@RequestMapping("pms/attrgroup")
public class AttrGroupController {

    @Autowired
    private AttrGroupService attrGroupService;

    /**
     * 根据categoryId，spuId，skuId，查询分组以及组内规格参数的值
     * @param categoryId
     * @param spuId
     * @param skuId
     * @return
     */
    @GetMapping(value = "/categoryId/spuId/skuId/{categoryId}")
    public ResponseVo<List<GroupVo>> queryGroupAndAttrsByCategoryIdAndSpuIdAndSkuId(@PathVariable(value = "categoryId") Long categoryId,
                                                                                    @RequestParam(value = "spuId") Long spuId,
                                                                                    @RequestParam(value = "skuId") Long skuId) {
        List<GroupVo> groupVoList = this.attrGroupService.
                                        queryGroupAndAttrsByCategoryIdAndSpuIdAndSkuId(categoryId, spuId, skuId);
        return ResponseVo.ok(groupVoList);
    }

    @GetMapping(value = "/withattrs/{categoryId}")
    public ResponseVo<List<AttrGroupEntity>> queryGroupsWithAttrsByCategoryId(
            @PathVariable(value = "categoryId")Long categoryId) {
        List<AttrGroupEntity> attrGroupEntities = this.attrGroupService.queryGroupsWithAttrsByCategoryId(categoryId);
        return ResponseVo.ok(attrGroupEntities);
    }

    @ApiOperation(value = "根据三级分类id进行查询")
    @GetMapping(value = "/category/{categoryId}")
    public ResponseVo<List<AttrGroupEntity>> queryByCategoryIdAndPage(@PathVariable(value = "categoryId")Long categoryId) {
        List<AttrGroupEntity> attrGroupEntities =
                this.attrGroupService.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", categoryId));
//        System.out.println("调用成功");
        return ResponseVo.ok(attrGroupEntities);
    }

    /**
     * 列表
     */
    @GetMapping
    @ApiOperation("分页查询")
    public ResponseVo<PageResultVo> queryAttrGroupByPage(PageParamVo paramVo){
        PageResultVo pageResultVo = attrGroupService.queryPage(paramVo);

        return ResponseVo.ok(pageResultVo);
    }


    /**
     * 信息
     */
    @GetMapping("{id}")
    @ApiOperation("详情查询")
    public ResponseVo<AttrGroupEntity> queryAttrGroupById(@PathVariable("id") Long id){
		AttrGroupEntity attrGroup = attrGroupService.getById(id);

        return ResponseVo.ok(attrGroup);
    }

    /**
     * 保存
     */
    @PostMapping
    @ApiOperation("保存")
    public ResponseVo<Object> save(@RequestBody AttrGroupEntity attrGroup){
		attrGroupService.save(attrGroup);

        return ResponseVo.ok();
    }

    /**
     * 修改
     */
    @PostMapping("/update")
    @ApiOperation("修改")
    public ResponseVo update(@RequestBody AttrGroupEntity attrGroup){
		attrGroupService.updateById(attrGroup);

        return ResponseVo.ok();
    }

    /**
     * 删除
     */
    @PostMapping("/delete")
    @ApiOperation("删除")
    public ResponseVo delete(@RequestBody List<Long> ids){
		attrGroupService.removeByIds(ids);

        return ResponseVo.ok();
    }

}
