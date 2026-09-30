package com.ltpms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// secret 必须是合法 Base64：配置占位默认值 "please-change-me-in-production" 会让
// JwtUtil.init 的 Base64 解码抛错，导致未设 JWT_SECRET 时上下文装载失败
@SpringBootTest(properties = "ltpms.jwt.secret=bHRwbXMtanVuaXQtdGVzdC1vbmx5LXNlY3JldC0zMmJ5dGVzIQ==")
class LtpmsApplicationTests {

    @Test
    void contextLoads() {
    }
}
