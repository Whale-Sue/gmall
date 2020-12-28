package com.atguigu.gmall.sheduled;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.atguigu.gmall.sheduled.mapper")
public class GmallSheduledApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmallSheduledApplication.class, args);
    }

}
