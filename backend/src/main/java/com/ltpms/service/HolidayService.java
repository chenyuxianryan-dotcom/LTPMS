package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.HolidayRequest;
import com.ltpms.entity.Holiday;
import com.ltpms.entity.Semester;
import com.ltpms.mapper.HolidayMapper;
import com.ltpms.mapper.SemesterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 节假日服务，提供按学期过滤的节假日查询与区间批量新增（逐日一行落库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    /** 单次区间新增的最大天数，防御性上限 */
    private static final int MAX_RANGE_DAYS = 366;

    private final HolidayMapper holidayMapper;
    private final SemesterMapper semesterMapper;

    /**
     * 查询节假日列表。指定学期时按 holiday_date ∈ [学期开始日期, 学期结束日期] 过滤。
     *
     * @param semesterId 学期 ID，可选；为空时返回全部
     * @return 按日期升序的节假日列表
     * @throws BusinessException 当指定学期不存在时抛出
     */
    public List<Holiday> list(Long semesterId) {
        LambdaQueryWrapper<Holiday> wrapper = new LambdaQueryWrapper<Holiday>()
                .orderByAsc(Holiday::getHolidayDate);
        if (semesterId != null) {
            Semester semester = semesterMapper.selectById(semesterId);
            if (semester == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "学期不存在");
            }
            wrapper.ge(Holiday::getHolidayDate, semester.getStartDate())
                    .le(Holiday::getHolidayDate, semester.getEndDate());
        }
        return holidayMapper.selectList(wrapper);
    }

    /**
     * 新增节假日，支持日期区间批量展开；区间内已存在的日期跳过。
     *
     * @param request 节假日信息
     * @return 实际新增的天数
     * @throws BusinessException 当日期不合法、区间过长或区间内日期全部已存在时抛出
     */
    @Transactional
    public int create(HolidayRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        long spanDays = request.getEndDate().toEpochDay() - request.getStartDate().toEpochDay() + 1;
        if (spanDays > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多添加 " + MAX_RANGE_DAYS + " 天");
        }

        Set<LocalDate> existing = new HashSet<>(holidayMapper.selectList(new LambdaQueryWrapper<Holiday>()
                        .ge(Holiday::getHolidayDate, request.getStartDate())
                        .le(Holiday::getHolidayDate, request.getEndDate()))
                .stream()
                .map(Holiday::getHolidayDate)
                .toList());

        int created = 0;
        for (LocalDate date = request.getStartDate(); !date.isAfter(request.getEndDate()); date = date.plusDays(1)) {
            if (existing.contains(date)) {
                continue;
            }
            Holiday holiday = new Holiday();
            holiday.setHolidayDate(date);
            holiday.setName(request.getName());
            holidayMapper.insert(holiday);
            created++;
        }
        if (created == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选区间内的日期已全部标记为节假日");
        }
        return created;
    }

    /**
     * 删除指定节假日。
     *
     * @param id 节假日 ID
     * @throws BusinessException 当记录不存在时抛出
     */
    @Transactional
    public void delete(Long id) {
        Holiday holiday = holidayMapper.selectById(id);
        if (holiday == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节假日不存在");
        }
        holidayMapper.deleteById(id);
    }
}
