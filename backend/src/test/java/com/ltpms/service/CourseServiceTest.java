package com.ltpms.service;

import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.CourseEditRequest;
import com.ltpms.entity.Course;
import com.ltpms.mapper.CourseMapper;
import com.ltpms.mapper.ExperimentMapper;
import com.ltpms.mapper.SemesterMapper;
import com.ltpms.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService —— 课程聚合行编辑")
class CourseServiceTest {

    @Mock
    CourseMapper courseMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    SemesterMapper semesterMapper;
    @Mock
    ExperimentMapper experimentMapper;
    @Mock
    RevisionService revisionService;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    CourseService courseService;

    private static final Long COURSE_ID = 1L;
    private static final Long TEACHER_ID = 10L;

    private Course existingCourse() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setCourseName("数据结构");
        course.setTeacherId(TEACHER_ID);
        course.setTotalHours(new BigDecimal("48"));
        return course;
    }

    @Test
    @DisplayName("课程不存在 → 抛 404，不触碰修订与通知")
    void update_courseNotFound_throws404() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);
        CourseEditRequest request = new CourseEditRequest();
        request.setCourseName("新名字");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.update(COURSE_ID, request, TEACHER_ID, "TEACHER"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
        verifyNoInteractions(revisionService, notificationService);
    }

    @Test
    @DisplayName("教师修改他人课程 → 403，不落库")
    void update_teacherEditsOthersCourse_forbidden() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(existingCourse());
        CourseEditRequest request = new CourseEditRequest();
        request.setCourseName("偷偷改掉");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.update(COURSE_ID, request, 999L, "TEACHER"));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
        verifyNoInteractions(revisionService, notificationService);
    }

    @Test
    @DisplayName("课程名改为空白 → 400 参数错误")
    void update_blankName_badRequest() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(existingCourse());
        CourseEditRequest request = new CourseEditRequest();
        request.setCourseName("   ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.update(COURSE_ID, request, TEACHER_ID, "TEACHER"));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("总学时 <= 0 → 400 参数错误")
    void update_nonPositiveHours_badRequest() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(existingCourse());
        CourseEditRequest request = new CourseEditRequest();
        request.setTotalHours(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.update(COURSE_ID, request, TEACHER_ID, "TEACHER"));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("提交内容与现值相同 → 不落库、不留痕（幂等）")
    void update_noActualChange_skips() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(existingCourse());
        CourseEditRequest request = new CourseEditRequest();
        request.setCourseName("数据结构");
        request.setTotalHours(new BigDecimal("48.00"));

        courseService.update(COURSE_ID, request, TEACHER_ID, "TEACHER");

        verify(courseMapper, never()).updateById(any(Course.class));
        verifyNoInteractions(revisionService, notificationService);
    }

    @Test
    @DisplayName("教师改本人课程 → 落库并记录字段级修订，不发通知")
    void update_teacherEditsOwn_recordsRevision() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(existingCourse());
        CourseEditRequest request = new CourseEditRequest();
        request.setMajor("软件工程");

        courseService.update(COURSE_ID, request, TEACHER_ID, "TEACHER");

        ArgumentCaptor<Course> updateCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(updateCaptor.capture());
        assertEquals("软件工程", updateCaptor.getValue().getMajor());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String[]>> changesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(revisionService).record(
                eq(RevisionService.TARGET_COURSE), eq(COURSE_ID),
                changesCaptor.capture(), eq("TEACHER"), eq(TEACHER_ID));
        assertArrayEquals(new String[]{null, "软件工程"}, changesCaptor.getValue().get("major"));
        verify(notificationService, never()).createScheduleRevised(any(), any());
    }

    @Test
    @DisplayName("管理员改学时 → 落库、留痕，并通知授课教师")
    void update_adminEdits_notifiesTeacher() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(existingCourse());
        CourseEditRequest request = new CourseEditRequest();
        request.setTotalHours(new BigDecimal("64"));

        courseService.update(COURSE_ID, request, 1L, "ADMIN");

        verify(courseMapper).updateById(any(Course.class));
        verify(revisionService).record(eq(RevisionService.TARGET_COURSE), eq(COURSE_ID),
                any(), eq("ADMIN"), eq(1L));
        verify(notificationService).createScheduleRevised(eq(TEACHER_ID), contains("总学时"));
    }
}
