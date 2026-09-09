<template>
  <el-dialog
    :model-value="modelValue"
    :title="`更换头像${user ? ` · ${user.realName}` : ''}`"
    width="480px"
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @closed="resetState"
  >
    <!-- 当前预览 -->
    <div class="avatar-picker__preview">
      <el-avatar :size="88" :src="previewUrl" class="avatar-picker__preview-avatar">
        {{ (user?.realName ?? 'U').slice(0, 1) }}
      </el-avatar>
      <div class="avatar-picker__preview-tip">选择系统内置头像，或上传本地照片（JPG / PNG / WebP，≤5MB）</div>
    </div>

    <!-- 内置头像九宫格 -->
    <div class="avatar-picker__grid">
      <div
        v-for="preset in AVATAR_PRESETS"
        :key="preset.id"
        class="avatar-picker__cell"
        :class="{ 'is-selected': selectedPreset === preset.id && !uploadedFile }"
        @click="selectPreset(preset.id)"
      >
        <el-avatar :size="56" :src="preset.url" />
        <el-icon v-if="selectedPreset === preset.id && !uploadedFile" class="avatar-picker__check"><CircleCheckFilled /></el-icon>
      </div>
    </div>

    <!-- 上传本地照片 -->
    <el-upload
      class="avatar-picker__upload"
      accept="image/jpeg,image/png,image/webp"
      :auto-upload="false"
      :show-file-list="false"
      :on-change="handleFileChange"
    >
      <el-button :icon="UploadFilled" :loading="uploading">
        {{ uploadedFile ? '重新选择本地照片' : '上传本地照片' }}
      </el-button>
    </el-upload>
    <div v-if="uploadedFile" class="avatar-picker__filename">
      <el-icon><Picture /></el-icon>
      {{ uploadedFile.name }}
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!selectedPreset && !uploadedFile" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { CircleCheckFilled, Picture, UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile } from 'element-plus';
import { AVATAR_PRESETS, resolveAvatarUrl } from '@/utils/avatar';
import { apiSetAvatar, apiUploadAvatar } from '@/api/user';

interface IPickerUser {
  id: number;
  realName: string;
  avatar?: string | null;
}

const props = defineProps<{
  modelValue: boolean;
  user: IPickerUser | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  /** 保存成功，携带最终头像标识（preset:avatar-XX 或上传 URL） */
  (e: 'saved', avatar: string): void;
}>();

const selectedPreset = ref<string | null>(null);
const uploadedFile = ref<File | null>(null);
const localPreview = ref('');
const saving = ref(false);
const uploading = ref(false);

/** 弹窗预览：本地照片 > 选中预设 > 当前头像 > 按用户 ID 的默认头像 */
const previewUrl = computed(() => {
  if (localPreview.value) return localPreview.value;
  if (selectedPreset.value && !uploadedFile.value) {
    return AVATAR_PRESETS.find((p) => p.id === selectedPreset.value)?.url;
  }
  if (props.user) return resolveAvatarUrl(props.user.id, props.user.avatar);
  return undefined;
});

watch(
  () => props.user,
  (u) => {
    // 打开时同步当前头像状态
    if (u) {
      selectedPreset.value = u.avatar && u.avatar.startsWith('preset:') ? u.avatar : null;
      uploadedFile.value = null;
      localPreview.value = '';
    }
  },
);

function selectPreset(id: string): void {
  selectedPreset.value = id;
  // 切回预设时丢弃未保存的本地上传
  if (uploadedFile.value) {
    uploadedFile.value = null;
    localPreview.value = '';
  }
}

function handleFileChange(file: UploadFile): void {
  const raw = file.raw;
  if (!raw) return;
  const allowed = ['image/jpeg', 'image/png', 'image/webp'];
  if (!allowed.includes(raw.type)) {
    ElMessage.error('仅支持 JPG / PNG / WebP 格式图片');
    return;
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB');
    return;
  }
  uploadedFile.value = raw;
  localPreview.value = URL.createObjectURL(raw);
}

async function handleSave(): Promise<void> {
  if (!props.user) return;
  saving.value = true;
  try {
    let avatar: string;
    if (uploadedFile.value) {
      const formData = new FormData();
      formData.append('file', uploadedFile.value);
      const result = await apiUploadAvatar(props.user.id, formData);
      avatar = result.avatar;
    } else if (selectedPreset.value) {
      const result = await apiSetAvatar(props.user.id, selectedPreset.value);
      avatar = result.avatar;
    } else {
      return;
    }
    ElMessage.success('头像已更新');
    emit('saved', avatar);
    emit('update:modelValue', false);
  } finally {
    saving.value = false;
  }
}

function resetState(): void {
  if (localPreview.value) URL.revokeObjectURL(localPreview.value);
  localPreview.value = '';
  uploadedFile.value = null;
  selectedPreset.value = null;
}
</script>

<style scoped lang="scss">
.avatar-picker {
  &__preview {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-4);
  }

  &__preview-avatar {
    background: var(--brand-100);
    color: var(--brand-800);
    font-size: 28px;
    box-shadow: 0 4px 16px rgb(0 0 0 / 12%);
  }

  &__preview-tip {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: var(--space-3);
    justify-items: center;
    margin-bottom: var(--space-4);
  }

  &__cell {
    position: relative;
    cursor: pointer;
    border-radius: var(--radius-full);
    border: 2px solid transparent;
    transition:
      transform 0.15s ease,
      border-color 0.15s ease;

    &:hover {
      transform: scale(1.06);
    }

    &.is-selected {
      border-color: var(--brand-600);
    }

    .el-avatar {
      display: block;
    }
  }

  &__check {
    position: absolute;
    right: -2px;
    bottom: -2px;
    color: var(--brand-600);
    background: #fff;
    border-radius: var(--radius-full);
    font-size: 16px;
  }

  &__upload {
    display: flex;
    justify-content: center;
  }

  &__filename {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--space-1);
    margin-top: var(--space-2);
    font-size: 12px;
    color: var(--text-secondary);
  }
}
</style>
