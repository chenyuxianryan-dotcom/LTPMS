package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.DictionaryRequest;
import com.ltpms.entity.Dictionary;
import com.ltpms.service.DictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典接口，提供字典项按类型查询以及字典的增删改（仅管理员）等功能。
 */
@RestController
@RequestMapping("/api/v1/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /**
     * 按字典类型查询字典项列表。
     *
     * @param dictType 字典类型编码
     * @return 该类型下的字典项列表
     */
    @GetMapping("/type/{dictType}")
    public Result<List<Dictionary>> listByType(@PathVariable String dictType) {
        return Result.success(dictService.listByType(dictType));
    }

    /**
     * 新增字典项（仅管理员）。
     *
     * @param request 字典项信息
     * @return 操作结果
     */
    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> create(@Valid @RequestBody DictionaryRequest request) {
        dictService.create(request);
        return Result.success();
    }

    /**
     * 更新指定字典项（仅管理员）。
     *
     * @param id      字典项 ID
     * @param request 字典项更新信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DictionaryRequest request) {
        dictService.update(id, request);
        return Result.success();
    }

    /**
     * 删除指定字典项（仅管理员）。
     *
     * @param id 字典项 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return Result.success();
    }
}
