<template>
  <div style="max-width: 640px; margin: 0 auto; padding: 24px;">
    <el-card header="通知偏好设置">
      <div
        v-for="item in prefItems"
        :key="item.type"
        style="display: flex; justify-content: space-between; align-items: center; padding: 16px 0; border-bottom: 1px solid var(--el-border-color-lighter);"
      >
        <div>
          <div style="font-size: 15px; font-weight: 500; margin-bottom: 4px;">{{ item.label }}</div>
          <div style="font-size: 13px; color: var(--el-text-color-secondary);">{{ item.desc }}</div>
        </div>
        <el-switch
          :model-value="prefs[item.type] ?? true"
          @change="(val: boolean) => handleToggle(item.type, val)"
        />
      </div>
      <div v-if="loading" style="text-align: center; padding: 32px 0;">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Loading } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { apiGetNotificationPreferences, apiUpdateNotificationPreferences } from '@/api/notification';
import { NOTIFICATION_PREF_ITEMS } from '@/constants';

const prefItems = NOTIFICATION_PREF_ITEMS;
const prefs = ref<Record<string, boolean>>({});
const loading = ref(true);

onMounted(async () => {
  try {
    prefs.value = await apiGetNotificationPreferences();
  } catch {
    ElMessage.error('加载通知偏好失败');
  } finally {
    loading.value = false;
  }
});

async function handleToggle(type: string, val: boolean) {
  prefs.value[type] = val;
  try {
    await apiUpdateNotificationPreferences(prefs.value);
    ElMessage.success(val ? '已开启' : '已关闭');
  } catch {
    // 回滚
    prefs.value[type] = !val;
    ElMessage.error('保存失败');
  }
}
</script>
