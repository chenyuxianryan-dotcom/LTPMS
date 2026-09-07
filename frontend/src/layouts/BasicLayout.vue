<template>
  <el-container class="basic-layout">
    <!-- 左侧菜单 -->
    <el-aside :width="`${SIDEBAR_WIDTH}px`" class="basic-layout__aside">
      <div class="basic-layout__logo">
        <span class="basic-layout__logo-title">LTPMS</span>
        <span class="basic-layout__logo-sub">实验教学项目管理系统</span>
      </div>
      <el-menu router :default-active="activeMenu" class="basic-layout__menu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
      <div class="basic-layout__user">
        <el-avatar :size="32" class="basic-layout__user-avatar">{{ avatarChar }}</el-avatar>
        <div class="basic-layout__user-info">
          <div class="basic-layout__user-name">{{ userStore.user?.realName ?? '未登录' }}</div>
          <div class="basic-layout__user-role">{{ roleLabel }}</div>
        </div>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header :height="`${HEADER_HEIGHT}px`" class="basic-layout__header">
        <div class="basic-layout__page-title">{{ currentTitle }}</div>
        <div class="basic-layout__header-actions">
          <!-- 通知 -->
          <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadNotifications">
            <template #reference>
              <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
                <el-button text :icon="Bell" aria-label="站内通知" />
              </el-badge>
            </template>
            <div class="notification-panel">
              <div class="notification-panel__head">
                <span>通知</span>
                <el-link type="primary" :underline="false" @click="handleReadAll">全部已读</el-link>
              </div>
              <el-empty v-if="notifications.length === 0" description="暂无通知" :image-size="60" />
              <div v-else class="notification-panel__list">
                <div
                  v-for="item in notifications"
                  :key="item.id"
                  class="notification-panel__item"
                  :class="{ 'is-unread': item.isRead === 0 }"
                  @click="handleReadNotification(item)"
                >
                  <div class="notification-panel__item-title">{{ item.title }}</div>
                  <div class="notification-panel__item-content">{{ item.content }}</div>
                  <div class="notification-panel__item-time">{{ item.createdAt }}</div>
                </div>
              </div>
            </div>
          </el-popover>

          <!-- 用户操作 -->
          <el-dropdown trigger="click" @command="handleUserCommand">
            <span class="basic-layout__dropdown-trigger">
              <el-avatar :size="28">{{ avatarChar }}</el-avatar>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="change-password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容 -->
      <el-main class="basic-layout__main">
        <router-view />
      </el-main>
    </el-container>

    <ChangePasswordDialog v-model="changePwdVisible" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowDown, Bell, Box, Calendar, ChatDotRound, Clock, Collection, DataAnalysis, Notebook, Odometer, OfficeBuilding, User } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue';
import { apiGetUnreadCount, apiListNotifications, apiMarkAllNotificationsRead, apiMarkNotificationRead } from '@/api/notification';
import { useUserStore } from '@/store/user';
import { ROLE } from '@/constants';
import type { INotification } from '@/types/api';
import type { Component } from 'vue';

const SIDEBAR_WIDTH = 240;
const HEADER_HEIGHT = 56;
const UNREAD_POLL_INTERVAL = 60_000;

const router = useRouter();
const currentRoute = useRoute();
const userStore = useUserStore();

const changePwdVisible = ref(false);
const unreadCount = ref(0);
const notifications = ref<INotification[]>([]);
let unreadTimer: ReturnType<typeof setInterval> | null = null;

/** 主菜单（与路由表一致），按角色过滤 */
const MENU_ITEMS: { path: string; title: string; icon: Component; roles?: string[] }[] = [
  { path: '/dashboard', title: '概览', icon: Odometer },
  { path: '/laboratories', title: '实验室管理', icon: OfficeBuilding, roles: [ROLE.ADMIN] },
  { path: '/semesters', title: '学期管理', icon: Calendar, roles: [ROLE.ADMIN] },
  { path: '/schedules', title: '课表管理', icon: Clock, roles: [ROLE.ADMIN] },
  { path: '/teachers', title: '教师管理', icon: User, roles: [ROLE.ADMIN] },
  { path: '/courses', title: '课程管理', icon: Notebook },
  { path: '/experiments', title: '实验项目', icon: Box, roles: [ROLE.TEACHER] },
  { path: '/reports', title: '报表中心', icon: DataAnalysis },
  { path: '/nlq', title: '智能查询', icon: ChatDotRound },
  { path: '/dicts', title: '字典管理', icon: Collection, roles: [ROLE.ADMIN] },
];

const menuItems = computed(() => MENU_ITEMS.filter((item) => !item.roles || item.roles.includes(userStore.role)));

const activeMenu = computed(() => currentRoute.path);
const currentTitle = computed(() => (currentRoute.meta?.title as string) ?? '');
const avatarChar = computed(() => (userStore.user?.realName ?? 'U').slice(0, 1));
const roleLabel = computed(() => (userStore.isAdmin ? '管理员' : '教师'));

async function refreshUnread(): Promise<void> {
  try {
    unreadCount.value = await apiGetUnreadCount();
  } catch {
    // 轮询失败静默处理，不打扰用户
  }
}

async function loadNotifications(): Promise<void> {
  const result = await apiListNotifications({ page: 1, size: 20 });
  notifications.value = result.records;
}

async function handleReadNotification(item: INotification): Promise<void> {
  if (item.isRead === 0) {
    await apiMarkNotificationRead(item.id);
    item.isRead = 1;
    await refreshUnread();
  }
  if (item.refUrl) {
    await router.push(item.refUrl);
  }
}

async function handleReadAll(): Promise<void> {
  await apiMarkAllNotificationsRead();
  notifications.value = notifications.value.map((item) => ({ ...item, isRead: 1 }));
  unreadCount.value = 0;
}

function handleUserCommand(command: string): void {
  if (command === 'change-password') {
    changePwdVisible.value = true;
    return;
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout();
        void router.push('/login');
      })
      .catch(() => undefined);
  }
}

onMounted(() => {
  void userStore.fetchMe().catch(() => undefined);
  void refreshUnread();
  unreadTimer = setInterval(refreshUnread, UNREAD_POLL_INTERVAL);
});

onBeforeUnmount(() => {
  if (unreadTimer) {
    clearInterval(unreadTimer);
  }
});
</script>

<style scoped lang="scss">
.basic-layout {
  height: 100%;

  &__aside {
    display: flex;
    flex-direction: column;
    background: var(--bg-elevated);
    border-right: 1px solid var(--border-subtle);
  }

  &__logo {
    display: flex;
    flex-direction: column;
    padding: var(--space-5) var(--space-6);

    &-title {
      font-size: 20px;
      font-weight: 700;
      color: var(--brand-700);
      line-height: 1.35;
    }

    &-sub {
      margin-top: var(--space-1);
      font-size: 12px;
      color: var(--text-tertiary);
    }
  }

  &__menu {
    flex: 1;
    border-right: none;

    :deep(.el-menu-item.is-active) {
      border-left: 2px solid var(--brand-700);
      background-color: var(--brand-50);
      color: var(--brand-700);
      font-weight: 500;
    }
  }

  &__user {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-4) var(--space-6);
    border-top: 1px solid var(--border-subtle);

    &-avatar {
      background: var(--brand-100);
      color: var(--brand-800);
      font-weight: 600;
    }

    &-name {
      font-size: 14px;
      color: var(--text-primary);
    }

    &-role {
      font-size: 12px;
      color: var(--text-tertiary);
    }
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--bg-elevated);
    border-bottom: 1px solid var(--border-subtle);
  }

  &__page-title {
    font-size: 16px;
    font-weight: 500;
    color: var(--text-primary);
  }

  &__header-actions {
    display: flex;
    align-items: center;
    gap: var(--space-4);
  }

  &__dropdown-trigger {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    cursor: pointer;
  }

  &__main {
    background: var(--bg-canvas);
    padding: 0;
    overflow-y: auto;
  }
}

.notification-panel {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: var(--space-2);
    font-weight: 600;
  }

  &__list {
    max-height: 320px;
    overflow-y: auto;
  }

  &__item {
    padding: var(--space-3) 0;
    border-bottom: 1px solid var(--border-subtle);
    cursor: pointer;

    &:hover {
      background: var(--bg-subtle);
    }

    &.is-unread .notification-panel__item-title {
      font-weight: 600;

      &::before {
        content: '';
        display: inline-block;
        width: 6px;
        height: 6px;
        margin-right: 6px;
        border-radius: var(--radius-full);
        background: var(--brand-600);
        vertical-align: middle;
      }
    }

    &-title {
      font-size: 14px;
      color: var(--text-primary);
    }

    &-content {
      margin-top: var(--space-1);
      font-size: 13px;
      color: var(--text-secondary);
    }

    &-time {
      margin-top: var(--space-1);
      font-size: 12px;
      color: var(--text-quaternary);
    }
  }
}
</style>
