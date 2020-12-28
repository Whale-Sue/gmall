package com.atguigu.gmall.ums.service.impl;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;
import org.springframework.util.CollectionUtils;


@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public Boolean checkData(String data, Integer type) {
        // 1、根据type, 判断输入的是用户名、还是email、还是phone，组装QueryWrapper
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        switch (type) {
            case 1: queryWrapper.eq("username", data); break;
            case 2: queryWrapper.eq("phone", data); break;
            case 3: queryWrapper.eq("email", data); break;
            default: return null;
        }
        // 2、进行判断
        return this.count(queryWrapper) == 0;
    }

    @Override
    public void register(UserEntity userEntity, String code) {
        // TODO：1、取出Redis中存储的验证码，校验验证码

        // 2、生成salt
        String salt = StringUtils.substring(UUID.randomUUID().toString(), 0, 6);
        userEntity.setSalt(salt);

        // 3、对密码加盐加密
        String pwd = DigestUtils.md5Hex(userEntity.getPassword() + salt);
        userEntity.setPassword(pwd);
        // 设置默认字段
        userEntity.setLevelId(1l);
        userEntity.setSourceType(1);
        userEntity.setIntegration(1000);
        userEntity.setGrowth(1000);
        userEntity.setStatus(1);
        userEntity.setCreateTime(new Date());
        userEntity.setNickname(userEntity.getUsername());

        // 4、注册用户
        this.save(userEntity);

        // TODO：5、删除Redis中的短信验证码
    }

    @Override
    public UserEntity queryUser(String loginName, String password) {
        // 1、首先根据loginName，查询出UserEntity集合
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.or(userEntityQueryWrapper -> userEntityQueryWrapper.eq("username", loginName)
                                                                    .or().eq("email", loginName)
                                                                    .or().eq("phone", loginName));
        List<UserEntity> userEntityList = this.list(queryWrapper);

        // 2、判空
        if (CollectionUtils.isEmpty(userEntityList)) {
            //throw new RuntimeException("用户名输入不合法");   --应该由直接和页面交互的接口输出异常信息，而当前接口是被调用的
            return null;
        }

        // 3、获取UserEntity的salt，然后加盐加密；和UserEntity的密码进行比对
        for (UserEntity userEntity : userEntityList) {
            // 获取当前用户的salt
            String salt = userEntity.getSalt();
            // 根据输入的password + salt，进行加密
            String pwd = DigestUtils.md5Hex(password + salt);
            // 和当前用户的password进行比对
            if ( StringUtils.equals(pwd, userEntity.getPassword())) return userEntity;
        }

        return null;
    }

}