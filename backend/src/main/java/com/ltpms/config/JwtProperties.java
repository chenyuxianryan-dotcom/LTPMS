package com.ltpms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ltpms.jwt")
/**
 * JWT 配置属性。
 */
public class JwtProperties {

    private String secret;
    private Long expiration;
}
