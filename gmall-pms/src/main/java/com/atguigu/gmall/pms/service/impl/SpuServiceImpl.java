package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;

import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCategoryIdAndPage(Long categoryId, PageParamVo pageParamVo) {
        QueryWrapper<SpuEntity> queryWrapper = new QueryWrapper<>();

        // 1、若 categoryId ！= 0,则要组装categoryId；
        if ( categoryId != 0) queryWrapper.eq("category_id", categoryId);

        // 2、若 pageParamVo.getKey() 不为空，则继续组装key()。categoryId && key；key内部：id == key || name like key
        String key = pageParamVo.getKey();
        if (StringUtils.isNotBlank(key)) {
            queryWrapper.and( wrapper -> {
                wrapper.eq("id", key).or().like("name", key);
            });
        }

        // 3、进行查询
        IPage<SpuEntity> page = this.page(pageParamVo.getPage(), queryWrapper);
        return new PageResultVo(page);
    }

    @Autowired
    private SpuDescMapper spuDescMapper;

    @Autowired
    private SpuAttrValueService spuAttrValueService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuAttrValueService skuAttrValueService;

    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Autowired
    private SpuDescService spuDescService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public void bigSave(SpuVo spuVo) {
        // 1.保存Spu相关信息
        // 1.1. 保存spu表
        Long spuId = saveSpu(spuVo);

        // 1.2. 保存spu_desc表
        this.spuDescService.saveSpuDesc(spuVo, spuId);

        // 1.3. 保存Spu_attr_value表
        saveBaseAttr(spuVo, spuId);

        // 2.保存sku相关信息--不能再使用批量保存的方式，保存sku相关信息
        // --因为除了sku表之外，还有sku图片、attr等表也需要保存，所以遍历的方式更好一些。
        saveSku(spuVo, spuId);

        this.rabbitTemplate.convertAndSend("PMS_SPU_EXCHANGE", "item.insert", spuId);

        //int i = 1 / 0;
    }

    private void saveSku(SpuVo spuVo, Long spuId) {
        List<SkuVo> skuVos = spuVo.getSkus();
        if ( CollectionUtils.isEmpty(skuVos)) return;  // 若集合为空，则直接返回。

        skuVos.forEach(skuVo -> {
            // 2.1. 保存sku表
            skuVo.setSpuId(spuId);

            skuVo.setBrandId(spuVo.getBrandId());
            skuVo.setCatagoryId(spuVo.getCategoryId());

            List<String> images = skuVo.getImages();
            if ( !CollectionUtils.isEmpty(images)) {
                skuVo.setDefaultImage(StringUtils.isNotBlank(skuVo.getDefaultImage()) ? skuVo.getDefaultImage() : images.get(0));
            }

            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            // 2.2. 保存sku图片表
            if ( !CollectionUtils.isEmpty(images)) {
                List<SkuImagesEntity> skuImagesEntities = images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setUrl(image);
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(image, skuVo.getDefaultImage()) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList());

                skuImagesService.saveBatch(skuImagesEntities);
            }

            // 2.3. 保存sku_attr_value表
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if ( !CollectionUtils.isEmpty(saleAttrs)) {
                saleAttrs.forEach(skuAttrValueEntity -> skuAttrValueEntity.setSkuId(skuId) );
                this.skuAttrValueService.saveBatch(saleAttrs);
            }

            // 3.保存sku的营销信息
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuVo.getId());
            gmallSmsClient.saveSales(skuSaleVo);
        });
    }

    private void saveBaseAttr(SpuVo spuVo, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spuVo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {  // 在集合不空的前提下进行保存
            // 使用stream()的map()完成集合的转换。然后使用批量保存方法。
            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream().map(spuAttrValueVO -> {

                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVO, spuAttrValueEntity);

                spuAttrValueEntity.setSpuId(spuId);

                return spuAttrValueEntity;
            }).collect(Collectors.toList());

            boolean b = this.spuAttrValueService.saveBatch(spuAttrValueEntities);
            // System.out.println("b = " + b);

        }
    }

    private Long saveSpu(SpuVo spuVo) {
        spuVo.setPublishStatus(1); // 默认是已上架
        spuVo.setCreateTime(new Date());
        spuVo.setUpdateTime(spuVo.getCreateTime()); // 新增时，更新时间和创建时间一致
        this.save(spuVo);
        return spuVo.getId();
    }


    /*public static void main(String[] args) {
        List<User> users = Arrays.asList(
                new User("小黑", 1, true),
                new User("小红", 1, false),
                new User("小花", 1, true),
                new User("大黑", 1, false)
        );


        //**********filter()***********
        //List<User> userList = users.stream().filter(user -> user.isSex()).collect(Collectors.toList());
        //List<User> userList = users.stream().filter(User::isSex).collect(Collectors.toList());
        //System.out.println("userList = " + userList);

        //**********map()**************
        *//*List<Person> personList = users.stream().map(user -> {
            Person person = new Person();
            person.setAge(user.getAge());
            person.setUsername(user.getUsername());
            return person;
        }).collect(Collectors.toList());
        System.out.println("personList = " + personList);*//*

        //************reduce()*********
        // 首先通过map()拿到年龄集合，然后通reduce()进行计算
        *//*System.out.println(users.stream().map(User::getAge).reduce((a, b) -> {
            return a + b;
        }).get());*//*
    }*/
}

/*
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class User{
    String username;
    int age;
    boolean sex;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class Person {
    String username;
    int age;
}*/
