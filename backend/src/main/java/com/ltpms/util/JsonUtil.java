package com.ltpms.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具类，基于 Jackson 提供序列化与反序列化方法，并对异常做降级处理。
 */
@Slf4j
public class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    /**
     * 将对象序列化为 JSON 字符串，失败时返回空对象字符串 "{}"。
     *
     * @param obj 待序列化对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON serialize error", e);
            return "{}";
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的对象，失败时返回 null。
     *
     * @param json  待反序列化 JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化后的对象；失败时返回 null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON deserialize error", e);
            return null;
        }
    }
}
