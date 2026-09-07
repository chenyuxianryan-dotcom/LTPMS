package com.ltpms.config;

import com.ltpms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulingConfig {

    private final NotificationService notificationService;

    /**
     * 每年 8月15日 和 1月15日 向管理员发送学期提醒
     */
    @Scheduled(cron = "0 0 9 15 1,8 ?")
    public void semesterRemind() {
        log.info("Sending semester remind notifications");
        notificationService.createSemesterRemind();
    }
}
