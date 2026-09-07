package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.DictionaryRequest;
import com.ltpms.entity.Dictionary;
import com.ltpms.mapper.DictionaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据字典服务，提供字典项查询、新增、更新与删除等能力。
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictionaryMapper dictionaryMapper;

    /**
     * 按字典类型查询已启用的字典项列表。
     *
     * @param dictType 字典类型编码
     * @return 该类型下的字典项列表
     */
    public List<Dictionary> listByType(String dictType) {
        return dictionaryMapper.selectList(
                new LambdaQueryWrapper<Dictionary>()
                        .eq(Dictionary::getDictType, dictType)
                        .eq(Dictionary::getStatus, 1)
                        .orderByAsc(Dictionary::getSortOrder));
    }

    /**
     * 新增字典项。
     *
     * @param request 字典项信息
     */
    @Transactional
    public void create(DictionaryRequest request) {
        Dictionary dict = new Dictionary();
        dict.setDictType(request.getDictType());
        dict.setDictLabel(request.getDictLabel());
        dict.setDictValue(request.getDictValue());
        dict.setSortOrder(request.getSortOrder());
        dict.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        dictionaryMapper.insert(dict);
    }

    /**
     * 更新指定字典项。
     *
     * @param id      字典项 ID
     * @param request 字典项更新信息
     * @throws BusinessException 当字典项不存在时抛出
     */
    @Transactional
    public void update(Long id, DictionaryRequest request) {
        Dictionary dict = dictionaryMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        dict.setDictType(request.getDictType());
        dict.setDictLabel(request.getDictLabel());
        dict.setDictValue(request.getDictValue());
        dict.setSortOrder(request.getSortOrder());
        if (request.getStatus() != null) {
            dict.setStatus(request.getStatus());
        }
        dictionaryMapper.updateById(dict);
    }

    /**
     * 删除指定字典项。
     *
     * @param id 字典项 ID
     */
    @Transactional
    public void delete(Long id) {
        dictionaryMapper.deleteById(id);
    }
}
