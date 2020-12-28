package com.atguigu.gmall.ums.api;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface GmallUmsApi {

    /**
     * 根据userId，查询用户详情--用户积分
     * @param id
     * @return
     */
    @GetMapping("ums/user/{id}")
    public ResponseVo<UserEntity> queryUserById(@PathVariable("id") Long id);

    /**
     * 根据userId，查询收货地址列表
     * @param userId
     * @return
     */
    @GetMapping(value = "ums/useraddress/user/{userId}")
    public ResponseVo<List<UserAddressEntity>> queryAddressByUserId(
            @RequestParam(value = "userId") Long userId);

    @GetMapping(value = "ums/user/query")
    public ResponseVo<UserEntity> queryUser(@RequestParam(value = "loginName")String loginName,
                                            @RequestParam(value = "password")String password);
}
