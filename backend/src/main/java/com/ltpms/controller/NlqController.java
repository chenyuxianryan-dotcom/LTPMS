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
     * 多轮 Agent 对话入口：以自然语言多轮交互查询数据，返回最终答案与工具调用轨迹。
     *
     * @param request 自然语言查询请求，含用户问题与可选的多轮历史
     * @return Agent 结果映射（含 answer、steps 等）
     */
    @PostMapping("/agent")
    public Result<Map<String, Object>> agent(@Valid @RequestBody NlqRequest request) {
        return Result.success(nlqService.agent(request, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 课表照片识别：base64 图片 → 结构化课表行草稿（与智能查询附图同一视觉通道）。
     *
     * @param body 请求体，含 image_base64 字段
     * @return 识别结果 {success, rows, error}
     */
    @PostMapping("/recognize-schedule")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<String, Object>> recognizeSchedule(@RequestBody Map<String, String> body) {
        String imageBase64 = body.get("image_base64");
        return Result.success(nlqService.recognizeSchedulePhoto(imageBase64));
    }

    /**
     * 智能查询附图：图片（+可选文字问题）→ 意图路由后分路作答（课表取数 / 资料顾问 / 通知转述）。
     * 全角色可用（学生只读提问；写回不经此入口——草稿确认走课表导入，教师专属）。
     *
     * @param body 请求体，含 image_base64、可选 question 与 force_route（改道类型）
     * @return 图片结果 {success, route, digest, answer, rows, notice, steps, suggestions, error}
     */
    @PostMapping("/image-query")
    public Result<Map<String, Object>> imageQuery(@RequestBody Map<String, Object> body) {
        String imageBase64 = body.get("image_base64") == null ? null : String.valueOf(body.get("image_base64"));
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        String forceRoute = body.get("force_route") == null ? null : String.valueOf(body.get("force_route"));
        return Result.success(nlqService.imageQuery(
                imageBase64, question, forceRoute, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 实验台数字档案：AI 生成实验室/实验项目的「一句话简介 + 历史 + 使用统计」。
     *
     * @param body 请求体，含 entity_type（laboratory | experiment）与 entity_id
     * @return 档案结果 {success, text, cached, stats, error}
     */
    @PostMapping("/archive")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<String, Object>> archive(@RequestBody Map<String, Object> body) {
        String entityType = String.valueOf(body.get("entity_type"));
        Long entityId = body.get("entity_id") == null ? null : Long.valueOf(String.valueOf(body.get("entity_id")));
        return Result.success(nlqService.archive(entityType, entityId));
    }

    /**
     * 叙事报告/教学总结：报表数字真实聚合，AI 只做语言包装。
     * 教师强制个人口径的教学总结；管理员可为全校年度叙事报告或教学总结。
     *
     * @param body 请求体，含 kind（annual_report | teaching_summary）
     * @return 叙事结果 {success, text, numbers, scope, error}
     */
    @PostMapping("/narrative")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<String, Object>> narrative(@RequestBody Map<String, String> body) {
        return Result.success(nlqService.narrative(
                body.getOrDefault("kind", "teaching_summary"),
                getCurrentUserId(), getCurrentRole()));
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
