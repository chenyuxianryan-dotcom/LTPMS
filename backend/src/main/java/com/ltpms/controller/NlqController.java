package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.dto.NlqRequest;
import com.ltpms.entity.QueryHistory;
import com.ltpms.service.NlqService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 自然语言查询（NLQ）接口，提供以自然语言提问查询数据、查询历史管理及数据库结构查看等功能。
 */
@RestController
@RequestMapping("/api/v1/nlq")
@RequiredArgsConstructor
public class NlqController {

    private final NlqService nlqService;

    /**
     * 以自然语言提问查询数据，返回 SQL 与查询结果。
     *
     * @param request 自然语言查询请求，包含用户问题
     * @return 查询结果映射（含生成的 SQL、数据等）
     */
    @PostMapping("/query")
    public Result<Map<String, Object>> query(@Valid @RequestBody NlqRequest request) {
        return Result.success(nlqService.query(request, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 分页查询当前用户的查询历史。
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 20
     * @return 分页后的查询历史列表
     */
    @GetMapping("/history")
    public Result<PageResult<QueryHistory>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(nlqService.history(getCurrentUserId(), page, size));
    }

    /**
     * 删除指定查询历史记录。
     *
     * @param id 查询历史 ID
     * @return 操作结果
     */
    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id) {
        nlqService.deleteHistory(id, getCurrentUserId());
        return Result.success();
    }

    /**
     * 获取数据库结构（Schema）描述，用于辅助自然语言查询（仅管理员）。
     *
     * @return 数据库结构描述文本
     */
    @GetMapping("/schema")
    @RequireRole("ADMIN")
    public Result<String> schema() {
        return Result.success(nlqService.getSchema());
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return null;
    }

    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            String authority = auth.getAuthorities().iterator().next().getAuthority();
            return authority.replace("ROLE_", "");
        }
        return null;
    }
}
