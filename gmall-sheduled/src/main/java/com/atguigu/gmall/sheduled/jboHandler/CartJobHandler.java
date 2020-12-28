package com.atguigu.gmall.sheduled.jboHandler;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.sheduled.mapper.CartMapper;
import com.atguigu.gmall.sheduled.pojo.Cart;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@Slf4j
public class CartJobHandler {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String EXCEPTION_KEY = "cart:exception";

    private static final String KEY_PREFIX = "cart:info:";

    @Autowired
    private CartMapper cartMapper;

    @XxlJob("cartJobHandler")
    public ReturnT<String> handler(String param) {
        BoundSetOperations<String, String> setOps = this.redisTemplate.boundSetOps(EXCEPTION_KEY);
        String userId = setOps.pop();

        // 当userId不为空，则持续同步
        while (StringUtils.isNotBlank(userId)) {
            // 1、根据userId，将MySQL中该条数据删除
            this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userId));

            // 2、获取Redis中该userId的数据
            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX);
            List<Object> cartJsonList = hashOps.values();
            if ( !CollectionUtils.isEmpty(cartJsonList)) {
                cartJsonList.forEach( cartJson -> {
                    Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);

                    // 3、将Redis中的该用户数据写入MySQL中
                    this.cartMapper.insert(cart);
                });
            }
            // 取下一个userId
            userId = setOps.pop();
        }

        return ReturnT.SUCCESS;
    }
}
