<template>
  <div class="login-page">
    <!-- 背景：复古电脑（blur 60% 档），撕裂破口后从这里露出 -->
    <div class="login-page__bg" aria-hidden="true">
      <img :src="bgComputer" alt="" />
    </div>
    <el-card class="login-page__card">
      <div class="login-page__brand">
        <div class="login-page__brand-title">LTPMS</div>
        <div class="login-page__brand-sub">基于 AI 大模型的实验教学项目管理系统</div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="handleLogin">
        <el-form-item label="工号" prop="username">
          <el-input v-model="form.username" placeholder="请输入工号" autocomplete="username" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-page__submit" :loading="loading" native-type="submit">
          登 录
        </el-button>
      </el-form>
    </el-card>

    <ChangePasswordDialog v-model="changePwdVisible" mandatory @update:model-value="handleChangePwdClosed" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { Lock, User } from '@element-plus/icons-vue';
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue';
import { useUserStore } from '@/store/user';
import { ERROR_CODE } from '@/constants';
import type { IBizError } from '@/utils/request';
import bgComputer from '@/assets/login-computer.jpg';

const router = useRouter();
const currentRoute = useRoute();
const userStore = useUserStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const changePwdVisible = ref(false);
const form = reactive({ username: '', password: '' });

const rules: FormRules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function handleLogin(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const user = await userStore.login(form.username, form.password);
    // 默认密码首次登录 → 强制改密
    if (user.isDefaultPwd) {
      changePwdVisible.value = true;
      return;
    }
    await redirectAfterLogin();
  } catch (error) {
    handleLoginError(error as IBizError);
  } finally {
    loading.value = false;
  }
}

function handleLoginError(error: IBizError): void {
  if (error.code === ERROR_CODE.ACCOUNT_LOCKED) {
    ElMessage.error(error.message);
  }
  // 其他错误码已由拦截器统一提示
}

async function handleChangePwdClosed(visible: boolean): Promise<void> {
  if (visible) return;
  if (userStore.needChangePassword) {
    // 强制改密未完成 → 退出会话
    userStore.logout();
    return;
  }
  await redirectAfterLogin();
}

async function redirectAfterLogin(): Promise<void> {
  const redirect = (currentRoute.query.redirect as string) || '/dashboard';
  // 等待下一个 tick，确保 store 中的 token/user 已完成响应式更新，
  // 避免路由守卫在 isLoggedIn 仍为旧值时把已登录用户拦截回落地页
  await nextTick();
  await router.push(redirect);
}
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
  background: #14162e;

  &__bg {
    position: absolute;
    inset: 0;
    z-index: 0;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transform: scale(1.06);
      filter: blur(6px) brightness(0.66) saturate(1.05);
    }

    // 深色轻遮罩，保证卡片对比度
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(
        180deg,
        rgb(10 12 32 / 46%) 0%,
        rgb(10 12 32 / 18%) 45%,
        rgb(10 12 32 / 52%) 100%
      );
    }
  }

  &__card {
    position: relative;
    z-index: 1;
    width: 400px;
    background: rgb(255 255 255 / 88%);
    border: 1px solid rgb(255 255 255 / 55%);
    border-radius: var(--radius-lg);
    box-shadow: 0 18px 48px rgb(0 0 0 / 35%);
    backdrop-filter: blur(14px);
  }

  &__brand {
    margin-bottom: var(--space-6);
    text-align: center;

    &-title {
      font-size: 32px;
      font-weight: 700;
      color: var(--brand-700);
      line-height: 1.25;
    }

    &-sub {
      margin-top: var(--space-2);
      font-size: 13px;
      color: var(--text-tertiary);
    }
  }

  &__submit {
    width: 100%;
    margin-top: var(--space-2);
  }
}
</style>
