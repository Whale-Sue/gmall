package com.atguigu.gmall.gateway.filter;


import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties jwtProperties;

    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        //return Arrays.asList("key", "value");

        return Arrays.asList("paths");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Data
    public static class PathConfig {
        /*private  String key;
        private  String value;*/

        private List<String> paths;
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                /*System.out.println("this is local filter....................");

                //System.out.println("局部过滤器获取的配置信息是--key= " + config.getKey() + ", value= " + config.getValue());
                System.out.println("使用List。。。。" + config.getPaths());
                return chain.filter(exchange);*/

                System.out.println("局部过滤器.................0");
                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();

                // 1、判断当前请求路径是否在拦截名单中--若不在名单中则直接放行
                String currPath = request.getURI().getPath();       // 当前请求的路径
                List<String> paths = config.getPaths();             // 拦截名单
                if ( !paths.stream().anyMatch( path -> currPath.startsWith(path))) {    // 都不匹配
                    return chain.filter(exchange);
                }

                // 2、在名单中则拦截：
                // 2.1 获取请求中的token信息；异步则在请求头中；同步则在cookie中
                String token = request.getHeaders().getFirst("token");      // 异步
                if (StringUtils.isBlank(token)) {// 若没有从请求头中获取到，则尝试从cookie中获取--同步
                    MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                    System.out.println("局部过滤器.................2.1");
                    if ( !CollectionUtils.isEmpty(cookies) && cookies.containsKey(jwtProperties.getCookieName())) {
                        HttpCookie cookie = cookies.getFirst(jwtProperties.getCookieName());
                        token = cookie.getValue();
                    }
                }

                // 2.2 判断得到的token是否为空，为空则重定向到登录页面
                if ( StringUtils.isBlank(token)) {
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    System.out.println("局部过滤器.................2.2");
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());

                    return response.setComplete();      // 拦截后续业务逻辑
                }

                try {
                    // 2.3 解析token信息，若有异常则重定向到登录页面
                    Map<String, Object> map = JwtUtils.getInfoFromToken(token, jwtProperties.getPublicKey());
                    System.out.println("局部过滤器.................2.3");
                    // 2.4 获取载荷中的ip，与当前请求的ip进行比较，若不一致则说明被盗用，重定向到登录页面
                    String ip = map.get("ip").toString();
                    String ipAddressAtGateway = IpUtils.getIpAddressAtGateway(request);
                    if ( !StringUtils.equals(ip, ipAddressAtGateway)) {
                        System.out.println("局部过滤器.................2.4");

                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                        return response.setComplete(); // 拦截后续业务逻辑
                    }
                    System.out.println("局部过滤器.................2.4");
                    // 2.5 将解析后的用户信息传递给后续服务..
                    request.mutate().header("userId", map.get("userId").toString()).build();
                    exchange.mutate().request(request).build();
                    System.out.println("局部过滤器.................2.5");
                    // 2.6 放行
                    return chain.filter(exchange);

                } catch (Exception e) {
                    e.printStackTrace();
                    // 解析出现异常，重定向到登录页面
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete(); // 拦截后续业务逻辑
                }

            }
        };
    }


}
