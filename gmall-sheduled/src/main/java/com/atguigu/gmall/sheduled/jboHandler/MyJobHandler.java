package com.atguigu.gmall.sheduled.jboHandler;


import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MyJobHandler {

    @XxlJob(value = "myJobHandler")
    public ReturnT<String> handler(String param) {
        XxlJobLogger.log("向调度中心输出日志");
        log.info("这是一个xxl-job的定时任务，接收到调度中心的参数：{}", param);

        return ReturnT.SUCCESS;
    }
}
