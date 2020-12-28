package com.atguigu.gmall.cart.interceptor;


import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;


@EnableConfigurationProperties(JwtProperties.class)
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 拦截器的前置方法。
     * 此处要获取userInfo，用于向handler中传递
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("this is pre");

        UserInfo userInfo = new UserInfo();

        // 1、设置userKey--不论是否登录，都要有userKey
        // 1.1 获取name为userKey的cookie
        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKey());
        // 1.2 若获取的cookie为空，则应生成一个userKey
        if (StringUtils.isBlank(userKey)) {
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.jwtProperties.getUserKey(), userKey, this.jwtProperties.getExpire());
        }

        // 1.3 为userInfo设置userKey
        userInfo.setUserKey(userKey);

        // 2、设置userId--在登录的情况下
        // 2.1 获取name为token的cookie--尝试从中获取userId
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        // 2.2 若token不为空，则获取并设置userId
        if ( StringUtils.isNotBlank(token)) {
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
            userInfo.setUserId(Long.valueOf(map.get("userId").toString()));
        }

        // 3、将userInfo放入THREAD_LOCAL中
        THREAD_LOCAL.set(userInfo);
        return true;
    }

    /**
     * 不能向后提供THREAD_LOCAL，否则会被修改；所以这里提供载荷的getter
     * @return
     */
    public static UserInfo getUserInfo() {
        return THREAD_LOCAL.get();
    }

    /**
     * 拦截器的完成方法。
     * 由于使用了Tomcat线程池，所以需要显式地删除ThreadLocal，否则会导致内存泄漏
     * afterCompletion是在DispatcherServlet向前端返回视图的时候起作用。
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        THREAD_LOCAL.remove();
    }
}
