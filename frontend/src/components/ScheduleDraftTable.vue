<template>
  <el-table :data="rows" border size="small" max-height="360" class="draft-table">
    <el-table-column label="课程号" width="110">
      <template #default="{ row }"><el-input v-model="row.courseCode" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="课程名" min-width="150">
      <template #default="{ row }"><el-input v-model="row.courseName" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="总学时" width="90">
      <template #default="{ row }"><el-input v-model.number="row.totalHours" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="教师工号" width="110">
      <template #default="{ row }"><el-input v-model="row.teacherNo" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="实验室编号" width="120">
      <template #default="{ row }"><el-input v-model="row.labCode" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="上课班级" min-width="140">
      <template #default="{ row }"><el-input v-model="row.classNames" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="人数" width="80">
      <template #default="{ row }"><el-input v-model.number="row.studentCount" size="small" :disabled="readonly" /></template>
    </el-table-column>
    <el-table-column label="上课时间" min-width="150">
      <template #default="{ row }"><el-input v-model="row.rawScheduleText" size="small" :disabled="readonly" /></template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { IScheduleDraftRow } from '@/api/schedule';

/**
 * 课表识别草稿表（课表页拍照弹窗与智能查询附图草稿卡共用）：
 * 逐格可改，改动直接写回行对象；校验与归属规则在服务端，前端只负责让人改。
 */
defineProps<{
  rows: IScheduleDraftRow[];
  readonly?: boolean;
}>();
</script>

<style scoped lang="scss">
.draft-table {
  width: 100%;
}
</style>
