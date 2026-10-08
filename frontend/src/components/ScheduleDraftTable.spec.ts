import { describe, expect, it } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import type { IScheduleDraftRow } from '@/api/schedule';
import ScheduleDraftTable from './ScheduleDraftTable.vue';

function draftRow(overrides: Partial<IScheduleDraftRow> = {}): IScheduleDraftRow {
  return {
    courseCode: 'CS101',
    courseName: '数据结构',
    totalHours: 48,
    teacherNo: 'T900',
    labCode: '36-401',
    classNames: '计科2301班',
    studentCount: 30,
    rawScheduleText: '周一第1-2节(1-16周)',
    ...overrides,
  };
}

/** el-table 的列宽与行渲染在挂载后的一轮微任务里完成，测试需等它落位 */
async function mountTable(rows: IScheduleDraftRow[], readonly?: boolean) {
  const wrapper = mount(ScheduleDraftTable, {
    props: { rows, readonly },
    global: { plugins: [ElementPlus] },
  });
  await flushPromises();
  await new Promise((resolve) => setTimeout(resolve, 0));
  return wrapper;
}

describe('ScheduleDraftTable —— 课表识别草稿表', () => {
  it('八列列头齐备（与 Java 侧导入入参同名）', async () => {
    const wrapper = await mountTable([draftRow()]);
    const headers = wrapper.findAll('th').map((th) => th.text().trim());

    ['课程号', '课程名', '总学时', '教师工号', '实验室编号', '上课班级', '人数', '上课时间'].forEach((label) => {
      expect(headers).toContain(label);
    });
  });

  it('每行渲染八个可格编辑的输入框', async () => {
    const wrapper = await mountTable([draftRow(), draftRow({ courseCode: 'CS102' })]);

    expect(wrapper.findAll('tbody input')).toHaveLength(16);
  });

  it('输入框初始值回填识别结果', async () => {
    const wrapper = await mountTable([draftRow({ courseCode: 'NET302', labCode: '36-402' })]);
    const inputs = wrapper.findAll<HTMLInputElement>('tbody input');

    expect(inputs[0].element.value).toBe('NET302');
    expect(inputs[4].element.value).toBe('36-402');
  });

  it('编辑单元格直接写回行对象（写回草稿的数据来源）', async () => {
    const row = draftRow();
    const wrapper = await mountTable([row]);
    const inputs = wrapper.findAll('tbody input');

    await inputs[0].setValue('YZTEST01');
    await inputs[1].setValue('编译原理');

    expect(row.courseCode).toBe('YZTEST01');
    expect(row.courseName).toBe('编译原理');
  });

  it('数值列把字符串输入转成 number（v-model.number 契约）', async () => {
    const row = draftRow({ totalHours: null });
    const wrapper = await mountTable([row]);

    await wrapper.findAll('tbody input')[2].setValue('64');

    expect(row.totalHours).toBe(64);
    expect(typeof row.totalHours).toBe('number');
  });

  it('readonly 时全部格子禁用（ADMIN 只读口径）', async () => {
    const wrapper = await mountTable([draftRow()], true);
    const inputs = wrapper.findAll('tbody input');

    expect(inputs.length).toBeGreaterThan(0);
    inputs.forEach((input) => {
      expect(input.attributes('disabled')).toBeDefined();
    });
  });

  it('默认可编辑（教师草稿确认路径）', async () => {
    const wrapper = await mountTable([draftRow()]);

    wrapper.findAll('tbody input').forEach((input) => {
      expect(input.attributes('disabled')).toBeUndefined();
    });
  });

  it('空草稿行时不渲染数据行', async () => {
    const wrapper = await mountTable([]);

    expect(wrapper.findAll('tbody input')).toHaveLength(0);
  });
});
