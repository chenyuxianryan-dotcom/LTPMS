package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.ChangePasswordRequest;
import com.ltpms.dto.LoginRequest;
import com.ltpms.dto.LoginResponse;
import com.ltpms.entity.User;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务，处理用户登录、密码修改及当前用户信息获取等业务逻辑，
 * 包含登录失败锁定与账号禁用校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 30;

    /**
     * 用户登录认证，校验账号状态、锁定情况与密码，成功后返回令牌与用户信息。
     *
     * @param request 登录请求，包含用户名与密码
     * @return 登录响应，包含 JWT 令牌与用户信息
     * @throws BusinessException 当账号不存在、已禁用、已锁定或密码错误时抛出
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }

        // 检查账号锁定
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long remainSeconds = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).getSeconds();
            long remainMinutes = remainSeconds / 60 + 1;
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请" + remainMinutes + "分钟后再试");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleLoginFailure(user);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 登录成功：清空失败次数和锁定时间
        clearLock(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                user.getIsDefaultPwd(),
                user.getAvatar()
        );

        return new LoginResponse(token, userInfo);
    }

    private void handleLoginFailure(User user) {
        int failed = user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
        failed++;

        User update = new User();
        update.setId(user.getId());
        update.setFailedAttempts(failed);

        if (failed >= MAX_FAILED_ATTEMPTS) {
            update.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            log.warn("User {} locked due to too many failed attempts", user.getUsername());
        }

        userMapper.updateById(update);
    }

    private void clearLock(User user) {
        User update = new User();
        update.setId(user.getId());
        update.setFailedAttempts(0);
        update.setLockedUntil(null);
        userMapper.updateById(update);
    }

    /**
     * 修改指定用户的登录密码。
     *
     * @param userId  当前登录用户 ID
     * @param request 修改密码请求，包含原密码与新密码
     * @throws BusinessException 当用户不存在或原密码错误时抛出
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_WRONG);
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        update.setIsDefaultPwd(0);
        userMapper.updateById(update);
    }

    /**
     * 获取指定用户的基本信息。
     *
     * @param userId 用户 ID
     * @return 用户信息
     * @throws BusinessException 当用户不存在时抛出
     */
    public LoginResponse.UserInfo getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                user.getIsDefaultPwd(),
                user.getAvatar()
        );
    }
}
