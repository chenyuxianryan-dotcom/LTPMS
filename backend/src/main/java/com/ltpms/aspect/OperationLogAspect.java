package com.ltpms.aspect;

import com.ltpms.annotation.OperationLog;
import com.ltpms.entity.OperationLogEntity;
import com.ltpms.mapper.OperationLogMapper;
import com.ltpms.util.IpUtil;
import com.ltpms.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作审计日志切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    @AfterReturning("@annotation(com.ltpms.annotation.OperationLog)")
    public void logOperation(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            OperationLog annotation = method.getAnnotation(OperationLog.class);

            Long userId = getCurrentUserId();
            if (userId == null) {
                return;
            }

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String ip = "";
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ip = IpUtil.getIpAddress(request);
            }

            OperationLogEntity logRecord = new OperationLogEntity();
            logRecord.setUserId(userId);
            logRecord.setAction(annotation.action());
            logRecord.setTargetType(annotation.targetType());
            logRecord.setDetail(JsonUtil.toJson(joinPoint.getArgs()));
            logRecord.setIp(ip);
            operationLogMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("Failed to record operation log", e);
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long) {
            return (Long) authentication.getDetails();
        }
        return null;
    }
}
