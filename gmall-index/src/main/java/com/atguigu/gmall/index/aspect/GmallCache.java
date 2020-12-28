package com.atguigu.gmall.index.aspect;


import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {

    /**
     * 缓存key的前缀
     * 结构：模块名 + ":" + 实例名 + ":"
     * 举例：首页工程的三级分类缓存，index:cates:
     * @return
     */
    String prefix() default "gmall:cache:";

    /**
     * 缓存的过期时间，单位是min
     * @return
     */
    long timeout() default 5L;

    /**
     * 为了防止缓存雪崩，为缓存时间添加随机值
     * 这里指定的随机值范围是5min
     * @return
     */
    int random() default 5;

    /**
     * 为了缓存击穿，给缓存添加分布式锁
     * 这里指定分布式锁的前缀
     * 最终确定，分布式锁的名称为：lock: +方法参数
     * @return
     */
    String lock() default "lock:";

}
