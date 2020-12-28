package com.atguigu.gmall.index.aspect;



import com.alibaba.fastjson.JSON;
import com.google.common.hash.BloomFilter;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    // 前置通知
    @Before( value = "execution(* com.atguigu.gmall.index.service.*.*(..))")
    public void before(JoinPoint joinPoint) {
        joinPoint.getArgs();
    }

    // 后置通知
    @After(value = "execution(* com.atguigu.gmall.index.service.*.*(..))")
    public void after(JoinPoint joinPoint) {
        joinPoint.getArgs();
    }


    // 后置返回通知
    @AfterReturning( value = "execution(* com.atguigu.gmall.index.service.*.*(..))", returning = "result")
    public void afterReturning( JoinPoint joinPoint, Object result) {
        joinPoint.getArgs();
    }

    // 异常通知
    @AfterThrowing( value = "execution(* com.atguigu.gmall.index.service.*.*(..))", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Throwable ex) {
        joinPoint.getArgs();
    }


    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RBloomFilter<String> bloomFilter;

    /**
     * 环绕通知
     * 获取目标方法参数：joinPoint.getArgs()
     * 获取目标方法签名：MethodSignature signature = (MethodSignature)joinPoint.getSignature()
     * 获取目标类：joinPoint.getTarget().getClass();
     * @return
     * @throws Throwable
     */
    @Around( value = "@annotation(GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        /*System.out.println("环绕前置");
        Object result = joinPoint.proceed(joinPoint.getArgs()); // 切入点
        System.out.println("环绕后置");
        return result;*/

        // 1、先查询缓存，缓存命中则直接获取并反序列化返回
        // 1.1 由于去Redis中查询缓存的时候需要使用key，因此先拼接key
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();     // 获取方法签名
        Method method = signature.getMethod();  // 获取切入点方法的反射对象
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);     // 获取切入点方法上的注解对象
        String prefix = gmallCache.prefix();        // 获取注解中的prefix属性
        List<Object> args = Arrays.asList(joinPoint.getArgs()); // 切入点的形参列表

        String key = prefix + args;     // 组装缓存key

        // 通过布隆过滤器判断数据库中是否有想要的数据
        boolean flag = this.bloomFilter.contains(key);
        if ( !flag) return null;        // 布隆过滤器中没有需要的key，说明MySQL数据库中没有目标数据

        // 1.2 在拿到key后，查询Redis。若命中缓存则直接返回
        String json = this.redisTemplate.opsForValue().get(key);
        Class<?> returnType = method.getReturnType();
        if (StringUtils.isNotBlank(json)) return JSON.parseObject(json, returnType);

        // 2、缓存中没有，则需查询数据库
        // 2.1 为防止缓存击穿，故添加分布式锁
        String lock = gmallCache.lock();    // 注解中的锁的前缀
        RLock fairLock = redissonClient.getFairLock(lock + args);
        fairLock.lock();

        try {
            // 2.2 拿到锁后，再去缓存中查一次(有可能在加锁过程中其他请求已经将结果放入缓存)--若命中则反序列化并返回之
            String json2 = this.redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json2)) return JSON.parseObject(json2, returnType);

            // 2.3 执行目标方法，获取数据库中的数据
            Object result = joinPoint.proceed(args.toArray());

            // 2.4 将查询结果放入Redis--
            // 为防止缓存穿透，不论是否为null都要放入，为null则将过期时间设置得短一些
            if ( result == null) {
                redisTemplate.opsForValue().set(key, null, 1, TimeUnit.MINUTES);
            } else {
                // 为防止缓存雪崩，要给过期时间设置随机值
                long expire = gmallCache.timeout() + new Random().nextInt(gmallCache.random());
                this.redisTemplate.opsForValue().set(key, JSON.toJSONString(result), expire, TimeUnit.MINUTES);
            }
            return result;
        } finally {
            // 2.5 释放分布式锁
            fairLock.unlock();
        }
    }
}
