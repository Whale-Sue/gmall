package com.atguigu.gmall.index.lock;


import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DistributedLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Boolean tryLock(String lockName, String uuid, Integer expireTime) {
        // Lua脚本
        String script = "if (redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 ) then redis.call('hincrby', KEYS[1], ARGV[1], 1); redis.call('expire', KEYS[1], ARGV[2]); return 1; else return 0; end;";

        // 执行脚本尝试获取锁
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expireTime.toString());

        // 若获取锁失败，则重试
        if ( !flag) {
            try {
                Thread.sleep(50);
                tryLock(lockName, uuid, expireTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        this.reNewTime(lockName, uuid, expireTime);

        return true;
    }

    public void unLock(String lockName, String uuid) {

        // Lua脚本
        String script = "if (redis.call('hexists', KEYS[1], ARGV[1]) == 0)then return nil; elseif (redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0) then redis.call('del', KEYS[1]); return 1; else return 1 end;";

        // 执行脚本，释放锁
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid);

        if ( flag == null) {
            throw new RuntimeException("当前线程中没有锁，您正在尝试释放其他线程的锁，这是被禁止的！");
        }
    }

    private void reNewTime(String lockName, String uuid, Integer expireTime) {
        // Lua脚本
        String script = "if (redis.call('hexists', KEYS[1], ARGV[1]) == 1)then redis.call('expire', KEYS[1], ARGV[2]); return 1; else return 0; end;";

        // 创建看门狗子线程--自动续期
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    // 子线程睡眠一定时间，醒来后重置业务线程的锁的过期时间
                    Thread.sleep(expireTime * 2000 / 3); // 注意是以这种形式睡眠，毫秒为单位。若以秒为单位，则计算后的误差太大

                    this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expireTime.toString());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
}
