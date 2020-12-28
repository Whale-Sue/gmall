package com.atguigu.gmall.index.controller;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping(value = {"/", "/index"})
    public String toIndex(Model model) {
        // 一级分类的集合
        List<CategoryEntity> categoryEntities = this.indexService.queryLv1Categories();

        // TODO：查询广告

        model.addAttribute("categories", categoryEntities);
        return "index";
    }





    /**
     * 根据一级分类Id，查找二级、三级分类
     * @param parentId
     * @return
     */
    @GetMapping(value = "/index/cates/{parentId}")
    @ResponseBody
    public ResponseVo<List<CategoryEntity>> queryLv2CategoriesWithSubsByParentId(
            @PathVariable(value = "parentId") Long parentId
    ) {


        List<CategoryEntity> categoryEntities = this.indexService.queryLv2CategoriesWithSubsByParentId(parentId);
        return ResponseVo.ok(categoryEntities);
    }


    /**
     * 锁测试
     * @return
     */
    @GetMapping("index/test/lock")
    @ResponseBody
    public ResponseVo testLock(){
        this.indexService.testLock();
        return ResponseVo.ok();
    }

    @GetMapping("index/test/write")
    @ResponseBody
    public ResponseVo testWirte(){
        this.indexService.testWrite();
        return ResponseVo.ok("写入成功");
    }

    @GetMapping("index/test/read")
    @ResponseBody
    public ResponseVo testRead(){
        this.indexService.testRead();
        return ResponseVo.ok("读取成功");
    }


    @GetMapping("index/test/latch")
    @ResponseBody
    public ResponseVo testLatch() throws InterruptedException {
        this.indexService.latch();
        return ResponseVo.ok("成功锁门啦。。。");
    }

    @GetMapping("index/test/countDown")
    @ResponseBody
    public ResponseVo testCountDown(){
        this.indexService.countDown();
        return ResponseVo.ok("出来一位啦。。。");
    }
}
