package com.ltpms.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 工具类，用于从 HTTP 请求中解析客户端真实 IP（兼容常见代理请求头）。
 */
public class IpUtil {

    private IpUtil() {
    }

    /**
     * 获取客户端 IP 地址，依次尝试 X-Forwarded-For 等代理请求头，最后回退到远程地址，
     * 并对多值地址取第一个。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
