package com.ltpms.service;

import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.config.NlqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库向量化客户端：转发到 nlq-service 的 /embed（DashScope text-embedding-v4）。
 * 密钥只存在于 nlq-service，Java 侧不持有；返回值为 float32 小端序字节串，直存 MEDIUMBLOB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    /** DashScope 单请求文本数上限 */
    private static final int BATCH_SIZE = 10;

    private final NlqProperties nlqProperties;
    private final RestTemplate restTemplate = buildRestTemplate();

    /** 批量向量化（自动按 10 条拆批），返回与 texts 等长、等维的字节串 */
    @SuppressWarnings("unchecked")
    public List<byte[]> embedDocuments(List<String> texts) {
        List<byte[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    @SuppressWarnings("unchecked")
    private List<byte[]> embedBatch(List<String> batch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", nlqProperties.getInternalToken());

        Map<String, Object> body = Map.of("texts", batch, "text_type", "document");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlqProperties.getServiceUrl() + "/embed", entity, Map.class);
            Map<String, Object> payload = response.getBody();
            if (payload == null || !Boolean.TRUE.equals(payload.get("success"))) {
                log.error("Embedding service returned failure: {}",
                        payload == null ? "empty body" : payload.get("error"));
                throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
            }
            int dim = payload.get("dim") instanceof Number number ? number.intValue() : 0;
            List<List<Number>> vectors = (List<List<Number>>) payload.get("vectors");
            if (dim <= 0 || vectors == null || vectors.size() != batch.size()) {
                log.error("Embedding response malformed: dim={}, vectors={}",
                        dim, vectors == null ? null : vectors.size());
                throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
            }
            return vectors.stream().map(vector -> toFloat32Bytes(vector, dim)).toList();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("Embedding service error: {}", e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        } catch (ResourceAccessException e) {
            log.error("Embedding service unreachable: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NLQ_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Embedding request failed", e);
            throw new BusinessException(ErrorCode.NLQ_MODEL_TIMEOUT);
        }
    }

    private static byte[] toFloat32Bytes(List<Number> vector, int dim) {
        ByteBuffer buffer = ByteBuffer.allocate(dim * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dim; i++) {
            buffer.putFloat(i < vector.size() ? vector.get(i).floatValue() : 0f);
        }
        return buffer.array();
    }

    /** 连接与读超时显式设定：默认无超时会在外部服务卡死时挂住上传线程 */
    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }
}
