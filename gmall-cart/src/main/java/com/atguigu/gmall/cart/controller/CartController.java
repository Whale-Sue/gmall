package com.atguigu.gmall.cart.controller;


import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 根据userId，查询用户已经勾选的购物车--用于生成订单
     * 此处的userId，只能传入，拦截器获取不到--
     * 因为拦截器只能从request的cookie中获取userId，远程调用中没有request，所以只能显式传参
     * @param userId
     * @return
     */
    @GetMapping(value = "/check/{userId}")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCarts(@PathVariable(value = "userId") Long userId) {
        List<Cart> cartList = this.cartService.queryCheckedCarts(userId);
        return ResponseVo.ok(cartList);
    }

    /**
     * 新增购物车
     * 新增之后要重定向到新增成功页面
     * @param cart 包含有skuId、count两个参数
     * @return
     */
    @GetMapping
    public String addCart(Cart cart) {
        this.cartService.addCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }

    /**
     * 新增购物车成功页面，本质上是根据用户的登录信息和skuId，对购物车进行查询
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping(value = "/addCart.html")
    public String queryCart(@RequestParam(value = "skuId")Long skuId, Model model) {
        Cart cart = this.cartService.queryCart(skuId);
        model.addAttribute("cart", cart);
        return "addCart";
    }

    /**
     * 查询所有购物车
     * @return
     */
    @GetMapping(value = "/cart.html")
    public String queryAllCarts() {
        List<Cart> cartList = this.cartService.queryAllCarts();
        return "cart";
    }

    /**
     * 更新某个商品的数量
     * @param cart
     * @return
     */
    @PostMapping(value = "/updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart) {
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    /**
     * 删除某个购物车
     * @param skuId
     * @return
     */
    @PostMapping(value = "/deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam(value = "skuId") Long skuId) {
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }

    /*@GetMapping("test")
    public String test(HttpServletRequest request){
        System.out.println("这是一个Handler方法。。。。。。。。。。。。。" + LoginInterceptor.getUserInfo());
        return "hello interceptors";
    }*/


    @ResponseBody
    @GetMapping("/test")
    public String test(HttpServletRequest request) throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();

        System.out.println("controller中的test方法，开始调用executor1、executor2");
        /*Future<String> future1 = this.cartService.executor1();
        Future<String> future2 = this.cartService.executor2();
        System.out.println("future1的执行结果：" + future1.get());
        System.out.println("future2的执行结果：" + future2.get());
        System.out.println("controller.test方法结束执行！！！" + (System.currentTimeMillis() - start));*/

        /*ListenableFuture<String> fu1 = this.cartService.executor1();
        ListenableFuture<String> fu2 = this.cartService.executor2();
        fu1.addCallback(t -> System.out.println("异步成功：" + t), ex -> System.out.println("异步失败：" + ex));
        fu2.addCallback(t -> System.out.println("异步成功：" + t), ex -> System.out.println("异步失败：" + ex));*/

        this.cartService.executor2();
        System.out.println("controller.test方法结束执行！！！" + (System.currentTimeMillis() - start));

        return "hello cart!";

    }
}
