package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.util.concurrent.RateLimiter;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.config.NlqProperties;
import com.ltpms.dto.NlqRequest;
import com.ltpms.entity.QueryHistory;
import com.ltpms.mapper.QueryHistoryMapper;
import com.ltpms.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自然语言查询（NLQ）服务，负责用户请求限流、转发至 NL2SQL 模型服务、
 * 查询历史管理与数据库结构获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlqService {

    /** 附图单张大小上限（解码后字节数）：与前端提示一致 */
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final NlqProperties nlqProperties;
    private final QueryHistoryMapper queryHistoryMapper;
    private final ProjectAuthService projectAuthService;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<Long, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /**
     * 用户级限流（与 /query、/agent 同口径、同实现）：共享同一平滑桶，
     * 1 次 / (60/rateLimitPerMinute) 秒，无突发额度；超限抛 TOO_MANY_REQUESTS（前端 429 语义）。
     */
    private void acquireRateLimit(Long userId) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(nlqProperties.getRateLimitPerMinute() / 60.0));
        if (!limiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 处理自然语言查询请求：先进行用户级限流，再转发至 NL2SQL 模型服务，
     * 并将查询结果与 SQL 记录到历史。
     *
     * @param request 自然语言查询请求
     * @param userId  当前用户 ID
     * @param role    当前用户角色
     * @return 模型服务返回的查询结果映射（含 SQL、数据等）
     * @throws BusinessException 当触发限流或模型服务调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> query(NlqRequest request, Long userId, String role) {
        // 限流
        RateLimiter limiter = rateLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(nlqProperties.getRateLimitPerMinute() / 60.0));
        if (!limiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        // 转发到 NL2SQL 服务
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Role", role);

        Map<String, Object> body = Map.of("question", request.getQuestion());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        long start = System.currentTimeMillis();
        Map<String, Object> result;
        boolean success = false;
        String generatedSql = null;
        String resultSummary = null;

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/query", entity, Map.class);
            result = response.getBody();
            success = result != null && Boolean.TRUE.equals(result.get("success"));
            if (result != null) {
                generatedSql = (String) result.get("sql");
                Integer rowCount = (Integer) result.get("rowCount");
                resultSummary = rowCount != null ? "返回" + rowCount + "条记录" : null;
            }
        } catch (RestClientResponseException e) {
            log.error("NL2SQL service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } finally {
            saveHistory(userId, role, request.getQuestion(), generatedSql, resultSummary, success);
        }

        // 非数据问题（制度/流程/资料）：同请求内接力转 Agent 走知识库检索，答案与出处
        // 放进 consult 字段一起返回。刻意不再让前端另发一次请求——限流器是 1 次/6 秒
        // 的平滑桶（无突发额度），背靠背的第二次调用必然 429，用户只会看到「查询过于频繁」。
        if (result != null && "NLQ_UNSAFE".equals(result.get("error_code"))) {
            return consultFallback(request.getQuestion(), userId, role, result);
        }

        return result;
    }

    /**
     * 非数据问题的顾问接力：转发 Agent（knowledge_search 带出处作答），
     * 包成 {success=false, error_code=NLQ_UNSAFE, consult={answer, steps}}。
     *
     * @param question 原问题
     * @param userId   当前用户 ID
     * @param role     当前用户角色
     * @param queryResult NL2SQL 服务返回的原响应（取 error 文案）
     * @return 供前端渲染「顾问作答」卡片的结果体
     */
    private Map<String, Object> consultFallback(String question, Long userId, String role,
                                                Map<String, Object> queryResult) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", queryResult.get("error"));
        body.put("error_code", "NLQ_UNSAFE");
        body.put("consult", forwardAgent(question, userId, role, null));
        return body;
    }

    /**
     * 处理多轮 Agent 对话请求：限流后转发至 NL2SQL 服务的 /agent 端点，
     * 返回最终答案与工具调用轨迹。循环级错误（熔断/模型异常）统一映射为模型超时。
     *
     * @param request 自然语言查询请求（含可选的多轮历史）
     * @param userId  当前用户 ID
     * @param role    当前用户角色
     * @return Agent 服务返回的 {answer, steps, steps_used, error}
     * @throws BusinessException 当触发限流或 Agent 服务调用失败时抛出
     */
    public Map<String, Object> agent(NlqRequest request, Long userId, String role) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(nlqProperties.getRateLimitPerMinute() / 60.0));
        if (!limiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return forwardAgent(request.getQuestion(), userId, role, request);
    }

    /** Agent 转发本体（不含限流）：独立入口与 /query 的顾问接力共用 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> forwardAgent(String question, Long userId, String role, NlqRequest source) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Role", role);

        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        if (source != null && source.getHistory() != null) {
            body.put("history", source.getHistory());
        }
        if (source != null && Boolean.TRUE.equals(source.getSelfDossier())) {
            body.put("self_dossier", true);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/agent", entity, Map.class);
            Map<String, Object> result = response.getBody();
            if (result != null && result.get("error") != null) {
                log.warn("NL2SQL agent 循环级错误: {}", result.get("error"));
                throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
            }
            return result;
        } catch (RestClientResponseException e) {
            log.error("NL2SQL agent service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL agent service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL agent request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        }
    }

    /**
     * 课表照片识别：转发 base64 图片到 NL2SQL 服务的 /recognize-schedule 端点
     * （与智能查询附图同一视觉通道，模型与解析单点维护）。
     *
     * @param imageBase64 课表照片的 base64 编码（不含 data: 前缀）
     * @return 识别结果 {success, rows, error}
     * @throws BusinessException 当图片不合规或视觉服务调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> recognizeSchedulePhoto(String imageBase64) {
        String base64 = stripDataUrl(imageBase64);
        String imageMime = validateImage(base64);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());

        Map<String, Object> body = Map.of("image_base64", base64, "image_mime", imageMime);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/recognize-schedule", entity, Map.class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            log.error("NL2SQL recognize-schedule service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL recognize-schedule service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL recognize-schedule request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        }
    }

    /**
     * 附图查询：图片（+可选问题）转发到 NL2SQL 服务的 /image-query，由视觉通道做意图路由后
     * 分路作答（课表取数 / 资料顾问 / 通知转述）。图片本体仅内存中转，服务端不落盘。
     *
     * @param imageBase64 图片 base64（可带 data: 前缀）
     * @param question    用户附带的文字问题，可空
     * @param forceRoute  改道时指定的类型（schedule | document | notice），可空
     * @param userId      当前用户 ID
     * @param role        当前用户角色
     * @return 图片结果 {success, route, digest, answer, rows, notice, steps, suggestions, error}
     * @throws BusinessException 当触发限流、图片不合规或视觉服务调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> imageQuery(String imageBase64, String question, String forceRoute,
                                          Long userId, String role) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(nlqProperties.getRateLimitPerMinute() / 60.0));
        if (!limiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        String base64 = stripDataUrl(imageBase64);
        String imageMime = validateImage(base64);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Role", role);

        Map<String, Object> body = new HashMap<>();
        body.put("image_base64", base64);
        body.put("question", question == null ? "" : question);
        body.put("image_mime", imageMime);
        if (forceRoute != null && !forceRoute.isBlank()) {
            body.put("force_route", forceRoute);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, Object> result = null;
        boolean success = false;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/image-query", entity, Map.class);
            result = response.getBody();
            success = result != null && Boolean.TRUE.equals(result.get("success"));
            return result;
        } catch (RestClientResponseException e) {
            log.error("NL2SQL image-query service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL image-query service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL image-query request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } finally {
            // 附图查询同权记账：历史里只存文字与摘要，不存图片
            String historyQuestion = question == null || question.isBlank() ? "【图片】未附文字" : question;
            saveHistory(userId, role, historyQuestion, null, imageSummary(result), success);
        }
    }

    /** 附图结果的一句话摘要（查询历史列展示） */
    private static String imageSummary(Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
            return "图片：未识别出结果";
        }
        Object route = result.get("route");
        if ("schedule".equals(route)) {
            Object rows = result.get("rows");
            int count = rows instanceof List<?> list ? list.size() : 0;
            return "图片：课表 " + count + " 行";
        }
        if ("document".equals(route)) {
            return "图片：资料页问答";
        }
        if ("notice".equals(route)) {
            return "图片：通知转述";
        }
        return "图片：已识别";
    }

    /** 去掉 data:image/...;base64, 前缀（前端理论上已剥离，此处兜底） */
    private static String stripDataUrl(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少图片内容，请重新选择图片");
        }
        String value = imageBase64.trim();
        int comma = value.indexOf(',');
        return value.startsWith("data:") && comma > 0 ? value.substring(comma + 1) : value;
    }

    /**
     * 图片合规校验（所有附图入口唯一把关点）：base64 解码 → 大小上限 → 魔数白名单，
     * 返回真实 MIME 供视觉通道使用。扩展名不可信，一律以内容魔数为准。
     */
    private static String validateImage(String base64) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片数据无法解码，请重新选择图片");
        }
        if (raw.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容为空，请重新选择图片");
        }
        if (raw.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 10MB，请压缩后重试");
        }
        String mime = detectImageMime(raw);
        if (mime == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 jpg / png / webp 图片");
        }
        return mime;
    }

    /** 魔数识别：JPEG(FFD8FF) / PNG(89504E47) / WEBP(RIFF....WEBP) */
    private static String detectImageMime(byte[] raw) {
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xD8 && (raw[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (raw.length >= 8 && (raw[0] & 0xFF) == 0x89 && raw[1] == 'P' && raw[2] == 'N' && raw[3] == 'G') {
            return "image/png";
        }
        if (raw.length >= 12 && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F'
                && raw[8] == 'W' && raw[9] == 'E' && raw[10] == 'B' && raw[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /**
     * 实验台数字档案：转发到 NL2SQL 服务的 /archive 端点（AI 生成档案，数字来自真实统计）。
     *
     * @param entityType 实体类型（laboratory | experiment）
     * @param entityId   实体 ID
     * @param force      是否绕过 24h 缓存重新生成（前端「重新生成」入口）
     * @param userId     当前用户 ID（限流记账）
     * @param role       当前用户角色
     * @return 档案结果 {success, text, cached, cached_at, stats, error}
     * @throws BusinessException 当触发限流或档案服务调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> archive(String entityType, Long entityId, boolean force,
                                       Long userId, String role) {
        acquireRateLimit(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Role", role);

        Map<String, Object> body = new HashMap<>();
        body.put("entity_type", entityType);
        body.put("entity_id", entityId);
        body.put("force", force);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/archive", entity, Map.class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            log.error("NL2SQL archive service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL archive service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL archive request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        }
    }

    /**
     * 实验项目全项目报告：先经 ProjectAuthService 单路行级鉴权（ADMIN 放行 / TEACHER 须为归属），
     * 再限流转发到 NL2SQL 服务的 /report 端点（AI 生成六节报告，数字来自真实聚合 + 文件画像）。
     *
     * @param projectId 实验项目 ID
     * @param force     是否绕过 24h 缓存重新生成
     * @param userId    当前用户 ID
     * @param role      当前用户角色
     * @return 报告结果 {success, text, cached, generated_at, stats, error}
     * @throws BusinessException 当无权限、触发限流或报告服务调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> report(Long projectId, boolean force, Long userId, String role) {
        projectAuthService.requireReadable(projectId, userId, role);
        acquireRateLimit(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Role", role);

        Map<String, Object> body = new HashMap<>();
        body.put("project_id", projectId);
        body.put("force", force);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/report", entity, Map.class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            log.error("NL2SQL report service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL report service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL report request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        }
    }

    /**
     * 叙事报告/教学总结：转发到 NL2SQL 服务的 /narrative 端点。
     * 权限分层由 NL2SQL 服务按透传的用户身份处理（教师个人口径/管理员全校口径）。
     *
     * @param kind   报告类型（annual_report | teaching_summary）
     * @param userId 当前用户 ID
     * @param role   当前用户角色
     * @return 叙事结果 {success, text, numbers, scope, error}
     * @throws BusinessException 当叙事服务调用失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> narrative(String kind, Long userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Role", role);

        Map<String, Object> body = Map.of("kind", kind);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/narrative", entity, Map.class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            log.error("NL2SQL narrative service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("NL2SQL narrative service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("NL2SQL narrative request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        }
    }

    /**
     * 分页查询当前用户的自然语言查询历史。
     *
     * @param userId 当前用户 ID
     * @param page   页码，从 1 开始
     * @param size   每页大小
     * @return 分页后的查询历史列表
     */
    public PageResult<QueryHistory> history(Long userId, int page, int size) {
        Page<QueryHistory> result = queryHistoryMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<QueryHistory>()
                        .eq(QueryHistory::getUserId, userId)
                        .orderByDesc(QueryHistory::getCreatedAt));
        return PageResult.of(result);
    }

    /**
     * 删除指定查询历史，仅允许删除本人记录。
     *
     * @param id     查询历史 ID
     * @param userId 当前用户 ID
     * @throws BusinessException 当记录不存在或非本人记录时抛出
     */
    public void deleteHistory(Long id, Long userId) {
        QueryHistory history = queryHistoryMapper.selectById(id);
        if (history == null || !history.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        queryHistoryMapper.deleteById(id);
    }

    /**
     * 获取 NL2SQL 模型服务暴露的数据库结构（Schema）描述。
     *
     * @return 数据库结构描述文本
     */
    public String getSchema() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                nlqProperties.getServiceUrl() + "/schema",
                org.springframework.http.HttpMethod.GET,
                entity,
                String.class);
        return response.getBody();
    }

    private void saveHistory(Long userId, String role, String question, String sql, String summary, boolean success) {
        QueryHistory history = new QueryHistory();
        history.setUserId(userId);
        history.setRole(role);
        history.setQuestion(question);
        history.setGeneratedSql(sql);
        history.setResultSummary(summary);
        history.setSuccess(success ? 1 : 0);
        queryHistoryMapper.insert(history);
    }
}
