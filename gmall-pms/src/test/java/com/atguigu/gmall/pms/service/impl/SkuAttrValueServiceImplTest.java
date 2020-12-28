package com.atguigu.gmall.pms.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkuAttrValueServiceImplTest {

    @Autowired
    private SkuAttrValueServiceImpl skuAttrValueService;

    @Test
    void querySaleAttrsMappingSkuId() {
        System.out.println(this.skuAttrValueService.querySaleAttrsMappingSkuId(29L));
    }

    @Test
    void testMapping() {
        System.out.println(this.skuAttrValueService.testMapping(29L));
    }
}