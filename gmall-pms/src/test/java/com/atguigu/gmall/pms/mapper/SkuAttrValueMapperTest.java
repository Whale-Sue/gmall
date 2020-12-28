package com.atguigu.gmall.pms.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkuAttrValueMapperTest {

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Test
    void querySaleAttrsMappingSkuId() {
        System.out.println(skuAttrValueMapper);
        System.out.println(this.skuAttrValueMapper.querySaleAttrsMappingSkuId(29L));
    }

    @Test
    void testMapping() {
        System.out.println(this.skuAttrValueMapper.testMapping(29L));
    }
}