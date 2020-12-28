package com.atguigu.gmall.index.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.aspect.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.lock.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private DistributedLock distributedLock;

    /**
     * 查询一级分类
     * @return
     */
    public List<CategoryEntity> queryLv1Categories() {
        ResponseVo<List<CategoryEntity>> categoriesResponse = this.gmallPmsClient.queryCategoriesByParentId(0L);
        return categoriesResponse.getData();
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    /**
     * 将缓存相关的代码抽离出来使当前业务方法更纯净--只包含业务逻辑，不必考虑缓存的实现。
     * 借助AOP思想完成缓存的逻辑代码。
     * @param parentId
     * @return
     */
    @GmallCache(prefix = KEY_PREFIX, timeout = 129600L, random = 14400, lock = "lock:cates:")
    public List<CategoryEntity> queryLv2CategoriesWithSubsByParentId(Long parentId) {

        ResponseVo<List<CategoryEntity>> listResponseVo =
                this.gmallPmsClient.queryLv2CategoriesWithSubsByParentId(parentId);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();

        return categoryEntities;

    }

    /**
     * 使用redissonClient--获取分布式锁，解决缓存击穿；存入null--解决穿透；expire随机值--解决雪崩
     * @param parentId
     * @return
     */
    public List<CategoryEntity> queryLv2CategoriesWithSubsByParentId2(Long parentId) {
        // 1、先查询缓存，若命中则直接返回结果
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + parentId);

        if (StringUtils.isNotBlank(json)) {
            // 命中缓存，直接返回结果
            return JSON.parseArray(json, CategoryEntity.class);
        }

        // 1.2 加分布式锁--在去MySQL查询之前加分布式锁，因为Redis不需要加锁--其并发能力高
        // --另外注意，这里的锁名含有parentId，是为了针对热点key来设置锁--
        // 只锁住热点key，同一时刻过来的其余parentId的请求不会被此热点key锁，继续争抢他们自己的锁即可。
        RLock lock = this.redissonClient.getLock("lock:" + parentId);
        lock.lock();

        try {
            // 2、若没有命中，则执行业务远程调用--获取数据，并将数据放入缓存

            // 2.1 在加了分布式锁之后，某个请求在查询了数据库后需将结果放进Redis，
            // 这样后续相同的parentId请求就可以去Redis查，而不是MySQL
            // --注意，此处拿到了锁，但又由于去缓存中查到了数据从而return，所以可能造成未放锁就return--死锁
            // --所以，需要将此语句放在try中确保最后能执行finally放锁操作。也可拿到try外，在return前写unlock操作。
            String json2 = redisTemplate.opsForValue().get(KEY_PREFIX + parentId);
            if (StringUtils.isNotBlank(json)) {
                return JSON.parseArray(json, CategoryEntity.class); // 命中缓存，直接返回结果
            }

            ResponseVo<List<CategoryEntity>> listResponseVo = this.gmallPmsClient.queryLv2CategoriesWithSubsByParentId(parentId);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            if (CollectionUtils.isEmpty(categoryEntities)) {
                // 为了防止缓存穿透，数据即使为null也缓存，为了防止缓存数据过多，缓存时间设置的极短
                this.redisTemplate.opsForValue().set(KEY_PREFIX + parentId, JSON.toJSONString(categoryEntities), 1, TimeUnit.MINUTES);

            } else {
                // 为了防止缓存雪崩，给缓存时间添加随机值
                this.redisTemplate.opsForValue().set(KEY_PREFIX + parentId, JSON.toJSONString(categoryEntities), 2160 + new Random().nextInt(900), TimeUnit.HOURS);
            }

            return categoryEntities;
        } finally {
            // 3、释放锁
            lock.unlock();
        }
    }

    /**
     * 不完成的分布式锁--没有实现可重入
     */
    public void testLock1() {
        // 尝试获取锁

        String uuid = UUID.randomUUID().toString();
        Boolean flag = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);

        // 1、获取锁失败，不断重试
        if (!flag) {
            try {
                Thread.sleep(50);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {

            // 2、获取锁成功，执行业务，最后释放锁

            String numJSON = this.redisTemplate.opsForValue().get("num");

            if ( StringUtils.isBlank(numJSON)) return;

            int num = Integer.parseInt(numJSON);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

            // 释放锁。为了防止误删，删除之前需要判断是不是自己的锁。但是还要保证判断和删除操作的原子性--借助LUA脚本
            /*if (StringUtils.equals(uuid, this.redisTemplate.opsForValue().get("lock"))) {
                this.redisTemplate.delete("lock");
            }*/

            // 使用LUA脚本
            String script = "if (redis.call('get', KEYS[1]) == ARGV[1]) then return redis.call('del', KEYS[1]) else return 0 end";
            // new DefaultRedisScript<>(script, Boolean.class)--需要指定返回值的class。若只有一个script参数会报错。
            this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList("lock"), uuid);
        }
    }


    /**
     * 手动实现可重入的分布式锁
     */
    public void testLock2() {
        String uuid = UUID.randomUUID().toString();

        // 1、加锁
        distributedLock.tryLock("lock", uuid, 30);

        // 2、获取锁成功，执行业务，最后释放锁
        String numJSON = this.redisTemplate.opsForValue().get("num");

        if ( StringUtils.isBlank(numJSON)) return;

        int num = Integer.parseInt(numJSON);
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

        // 通过看门狗子线程，重置线程的锁的过期时间
        try {
            TimeUnit.SECONDS.sleep(120);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        // 测试可重入特性
        this.testSub("lock", uuid);

        // 释放锁
        distributedLock.unLock("lock", uuid);
    }


    @Autowired
    private RedissonClient redissonClient;

    /**
     * 使用redisson实现分布式锁
     */
    public void testLock() {

        RLock lock = this.redissonClient.getLock("lock");

        // 1、加锁
        lock.lock(30, TimeUnit.SECONDS);

        try {
            // 2、获取锁成功，执行业务，最后释放锁
            String numJSON = this.redisTemplate.opsForValue().get("num");

            if ( StringUtils.isBlank(numJSON)) return;

            int num = Integer.parseInt(numJSON);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

        } finally {
            //lock.unlock();
        }
    }

    public void testSub(String lockName, String uuid) {
        // 加锁
        this.distributedLock.tryLock(lockName, uuid, 30);

        System.out.println("测试可重入的分布式锁");
        // 放锁
        this.distributedLock.unLock(lockName, uuid);
    }


    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);

        System.out.println("写操作");
    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10, TimeUnit.SECONDS);

        System.out.println("读操作");

    }

    public void latch() throws InterruptedException {
        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
        latch.trySetCount(6);

        latch.await();
    }

    public void countDown() {
        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");

        latch.countDown();
    }
}
