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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

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

    private final NlqProperties nlqProperties;
    private final QueryHistoryMapper queryHistoryMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<Long, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

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
        } catch (Exception e) {
            log.error("NL2SQL request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } finally {
            saveHistory(userId, role, request.getQuestion(), generatedSql, resultSummary, success);
        }

        return result;
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
