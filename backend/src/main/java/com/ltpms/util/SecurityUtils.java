package com.ltpms.util;

import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文读取（JWT 过滤器写入的 SecurityContext）。
 */
public final class SecurityUtils {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_STUDENT = "STUDENT";

    private SecurityUtils() {
    }

    /** 当前登录用户 ID；未认证抛 401 */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /** 当前登录用户角色（去掉 ROLE_ 前缀）；未认证抛 401 */
    public static String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga.getAuthority().startsWith("ROLE_")) {
                return ga.getAuthority().substring("ROLE_".length());
            }
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
