package com.ltpms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ltpms.nlq")
/**
 * NL2SQL 服务配置属性。
 */
public class NlqProperties {

    private String serviceUrl;
    private String internalToken;
    private int rateLimitPerMinute = 10;
}
