package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private CartAsyncService cartAsyncService;

    public void addCart(Cart cart) {
        // 1、获取登录信息。若userId不空，则以userId为key插入Redis、MySQL；否则以userKey插入
        String userId = getUserId();

        System.out.println("addCart中，userId = " + userId);
        System.out.println("addCart中，skuId = " + cart.getSkuId());

        // 2、通过userId，获取内层的Map结构
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // 3、判断当前的购物车中，是否包含当前的商品
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        if ( hashOps.hasKey(skuId)) {       // 3.1 若包含，则更新该商品的数量
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);

            cart.setCount(cart.getCount().add(count));

            // 写入Redis、MySQL

            //this.cartMapper.update(cart, new QueryWrapper<Cart>().eq("user_id", userId).eq("sku_id", skuId));
            cartAsyncService.updateCart(userId, cart);
        } else {                            // 3.2 不包含，则向Redis、MySQL中插入该商品
            cart.setUserId(userId);

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) return;
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // 查询库存信息
            ResponseVo<List<WareSkuEntity>> wareSkusResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntityList = wareSkusResponseVo.getData();
            if ( !CollectionUtils.isEmpty(wareSkuEntityList)) {
                cart.setStore( wareSkuEntityList.stream().anyMatch( wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 销售属性
            ResponseVo<List<SkuAttrValueEntity>> skuAttrValueEntityListResponseVo = this.pmsClient.querySaleAttrsBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntityList = skuAttrValueEntityListResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntityList));

            // 营销信息
            ResponseVo<List<ItemSaleVo>> itemSaleVoListResponeVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVoList = itemSaleVoListResponeVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVoList));

            // 选中
            cart.setCheck(true);

            //this.cartMapper.insert(cart);
            cartAsyncService.insertCart(userId, cart);

            // 添加价格缓存
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuEntity.getPrice().toString());
        }
        // 放入redis
        hashOps.put(userId, JSON.toJSONString(cart));
    }

    public Cart queryCart(Long skuId) {
        /*String userId = this.getUserId();

        System.out.println("queryCart中，userId = " + userId);
        System.out.println("queryCart中，skuId = " + skuId);

        // 获取内存的Map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        if ( hashOps.hasKey(skuId.toString())) {
            System.out.println("???????????????????????????????????????????????????");
            String cartJson = hashOps.get(skuId.toString()).toString();
            Cart cart = JSON.parseObject(cartJson, Cart.class);
            return cart;
        }

        throw  new CartException("此用户不存在此购物车记录");*/

        String userId = this.getUserId();

        // 获取内存的map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        System.out.println("queryCart中，skuId = " + skuId + ".");

        Set<Object> keys = hashOps.keys();
        keys.forEach(key -> System.out.println("key = " + key));

        if (hashOps.hasKey(skuId.toString())){
            System.out.println("返回");
            String cartJson = hashOps.get(skuId.toString()).toString();
            return JSON.parseObject(cartJson, Cart.class);
        }

        throw new CartException("此用户不存在这条购物车记录！");
    }

    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        /*String userId = userInfo.getUserId().toString();
        if (userId == null) {
            userId = userInfo.getUserKey();
        }
        return userId;*/

        if ( userInfo.getUserId() == null) {
            return userInfo.getUserKey();
        }
        return  userInfo.getUserId().toString();
    }

    @Async
    public String executor1() {
        try {
            System.out.println("executor1方法开始执行");
            TimeUnit.SECONDS.sleep(4);
            System.out.println("executor1方法结束执行。。。");
            int i = 1 / 0;
            return "executor1"; // 正常响应
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Async
    public String executor2() {
        try {
            System.out.println("executor2方法开始执行");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("executor2方法结束执行。。。");
            int i = 1 / 0; // 制造异常
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Cart> queryAllCarts() {
        // 1、获取userKey
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();

        // 2、根据userKey，查询未登录状态下的购物车
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userKey);  // 根据userKey查询该条记录--双层Map
        List<Object> unLoginCartJsons = unLoginHashOps.values();        // 获取内层Map
        List<Cart> unLoginCartList = null;     // 未登录状态购物车
        if ( !CollectionUtils.isEmpty(unLoginCartJsons)) {
            unLoginCartList = unLoginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                // 设置实时价格
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        // 3、获取userId，若userId为空则直接返回未登录状态下的购物车
        Long userId = userInfo.getUserId();
        if ( userId == null) return unLoginCartList;

        // 4、获取登录状态下的购物车
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // 5、将未登录状态的购物车，合并到已登录状态的购物车
        // 只有在未登录购物车不为空的情况下才进行合并
        if ( !CollectionUtils.isEmpty(unLoginCartList)) {
            unLoginCartList.forEach( unLoginCart -> {
                BigDecimal unLoginCartCount = unLoginCart.getCount();   // 未登录状态下的该商品数量
                String skuId = unLoginCart.getSkuId().toString();
                // 5.1 若登录状态的购物车已经包含该cart，则更新cart数量；
                if ( loginHashOps.hasKey(skuId)) {
                    String cartJson = loginHashOps.get(skuId).toString();
                    Cart cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(unLoginCartCount));

                    // 更新Redis、异步更新MySQL
                    this.cartAsyncService.updateCart(userId.toString(), cart);
                    loginHashOps.put(skuId, cart);

                } else {    // 5.2 若不包含该记录，则新增
                    unLoginCart.setUserId(userId.toString());
                    // 更新Redis、异步更新MySQL
                    this.cartAsyncService.insertCart(userId.toString(), unLoginCart);
                    loginHashOps.put(skuId, unLoginCart);
                }
            });

            // 6、删除未登录状态的购物车--放在if中是因为若未登录的购物车为空，则没有必要进行删除
            this.redisTemplate.delete(KEY_PREFIX + userKey);
            this.cartAsyncService.deleteCart(userKey);
        }

        // 7、查询登录状态购物车，并将其返回
        List<Object> loginCartJsons = loginHashOps.values();
        if ( !CollectionUtils.isEmpty(loginCartJsons)) {
            List<Cart> cartList = loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
            return cartList;
        }

        return null;
    }

    public void updateNum(Cart cart) {


        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> cartHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if ( cartHashOps.hasKey(cart.getSkuId().toString())) {
            String cartJson = cartHashOps.get(cart.getSkuId().toString()).toString();
            BigDecimal count = cart.getCount();     // 页面中修改后的数量
            Cart oldCart = JSON.parseObject(cartJson, Cart.class);
            oldCart.setCount(count);

            cartHashOps.put(cart.getSkuId().toString(), JSON.toJSONString(oldCart));
            this.cartAsyncService.updateCart(userId, oldCart);
            return;
        }
        throw new CartException("该用户的购物车不包含该条记录!!!!");
    }

    public void deleteCart(Long skuId) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> cartHashOps = this.redisTemplate.boundHashOps(userId);

        if ( cartHashOps.hasKey(skuId.toString())) {

            // 删除Redis中的该购物车
            this.redisTemplate.delete(skuId.toString());
            // 删除MySQL中的该购物车
            this.cartAsyncService.deleteCartByUserIdAndSkuId(userId, skuId);
            return;
        }
        throw new CartException("该用户的购物车不包含该条记录。");
    }


    public List<Cart> queryCheckedCarts(Long userId) {
        BoundHashOperations<String, Object, Object> boundHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        List<Object> cartJsonList = boundHashOps.values();
        if ( CollectionUtils.isEmpty(cartJsonList)) {
            throw new CartException("您没有购物车记录");
        }
        List<Cart> cartList = cartJsonList.stream()
                                .map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class))
                                .filter(Cart::getCheck)
                                .collect(Collectors.toList());
        return cartList;
    }
}
