package com.ltpms.aspect;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限切面
 */
@Aspect
@Component
public class RoleAspect {

    @Before("@annotation(com.ltpms.annotation.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole requireRole = method.getAnnotation(RequireRole.class);

        Set<String> allowedRoles = Arrays.stream(requireRole.value())
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toSet());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Set<String> userRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean hasRole = userRoles.stream().anyMatch(allowedRoles::contains);
        if (!hasRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
