<template>
  <el-container class="basic-layout">
    <!-- 左侧菜单 -->
    <el-aside :width="`${asideWidth}px`" class="basic-layout__aside">
      <!-- 收起/展开开关（悬浮在侧栏右边缘的圆形按钮） -->
      <button
        class="basic-layout__collapse-btn"
        :class="{ 'is-collapsed': collapsed }"
        type="button"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        :title="collapsed ? '展开侧栏' : '收起侧栏'"
        @click="toggleSidebar"
      >
        <el-icon :size="14"><CaretLeft /></el-icon>
      </button>

      <div class="basic-layout__logo" :class="{ 'is-collapsed': collapsed }">
        <span v-if="!collapsed" class="basic-layout__logo-title">LTPMS</span>
        <span v-if="!collapsed" class="basic-layout__logo-sub">实验教学项目管理系统</span>
        <span v-if="collapsed" class="basic-layout__logo-mark" title="LTPMS">L</span>
      </div>

      <el-menu
        router
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        class="basic-layout__menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="basic-layout__user" :class="{ 'is-collapsed': collapsed }">
        <el-tooltip content="点击更换头像" placement="right" :disabled="!collapsed">
          <el-avatar
            :size="collapsed ? 36 : 32"
            :src="myAvatarUrl"
            class="basic-layout__user-avatar"
            @click="openMyAvatarPicker"
          >
            {{ avatarChar }}
          </el-avatar>
        </el-tooltip>
        <div v-if="!collapsed" class="basic-layout__user-info">
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
              <el-avatar :size="28" :src="myAvatarUrl">{{ avatarChar }}</el-avatar>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="change-avatar">
                  <el-icon><Avatar /></el-icon>更换头像
                </el-dropdown-item>
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

    <AvatarPickerDialog v-model="avatarPickerVisible" :user="pickerUser" @saved="onAvatarSaved" />
    <ChangePasswordDialog v-model="changePwdVisible" />
    <NlqWidget />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowDown, Avatar, Bell, Box, Calendar, CaretLeft, ChatDotRound, Clock, Collection, DataAnalysis, Notebook, Odometer, OfficeBuilding, User } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import AvatarPickerDialog from '@/components/AvatarPickerDialog.vue';
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue';
import NlqWidget from '@/components/NlqWidget.vue';
import { apiGetUnreadCount, apiListNotifications, apiMarkAllNotificationsRead, apiMarkNotificationRead } from '@/api/notification';
import { resolveAvatarUrl } from '@/utils/avatar';
import { useUserStore } from '@/store/user';
import { ROLE } from '@/constants';
import type { INotification } from '@/types/api';
import type { Component } from 'vue';

const SIDEBAR_WIDTH = 240;
const SIDEBAR_COLLAPSED_WIDTH = 68;
const HEADER_HEIGHT = 56;
const UNREAD_POLL_INTERVAL = 60_000;
const SIDEBAR_STATE_KEY = 'ltpms_sidebar_collapsed';

const router = useRouter();
const currentRoute = useRoute();
const userStore = useUserStore();

const changePwdVisible = ref(false);
const avatarPickerVisible = ref(false);
const unreadCount = ref(0);
const notifications = ref<INotification[]>([]);
let unreadTimer: ReturnType<typeof setInterval> | null = null;

/** 侧栏收起状态（localStorage 持久化，跨会话记忆） */
const collapsed = ref(sessionStorage.getItem(SIDEBAR_STATE_KEY) === '1');

watch(collapsed, (value) => {
  if (value) {
    sessionStorage.setItem(SIDEBAR_STATE_KEY, '1');
  } else {
    sessionStorage.removeItem(SIDEBAR_STATE_KEY);
  }
});

const asideWidth = computed(() => (collapsed.value ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH));

function toggleSidebar(): void {
  collapsed.value = !collapsed.value;
}

/** 主菜单（与路由表一致），按角色过滤；智能查询置顶为第一项 */
const MENU_ITEMS: { path: string; title: string; icon: Component; roles?: string[] }[] = [
  { path: '/nlq', title: '智能查询', icon: ChatDotRound },
  { path: '/dashboard', title: '概览', icon: Odometer },
  { path: '/laboratories', title: '实验室管理', icon: OfficeBuilding, roles: [ROLE.ADMIN] },
  { path: '/semesters', title: '学期管理', icon: Calendar, roles: [ROLE.ADMIN] },
  { path: '/schedules', title: '课表管理', icon: Clock, roles: [ROLE.ADMIN] },
  { path: '/teachers', title: '教师管理', icon: User, roles: [ROLE.ADMIN] },
  { path: '/courses', title: '课程管理', icon: Notebook },
  { path: '/experiments', title: '实验项目', icon: Box, roles: [ROLE.TEACHER] },
  { path: '/reports', title: '报表中心', icon: DataAnalysis },
  { path: '/dicts', title: '字典管理', icon: Collection, roles: [ROLE.ADMIN] },
];

const menuItems = computed(() => MENU_ITEMS.filter((item) => !item.roles || item.roles.includes(userStore.role)));

const activeMenu = computed(() => currentRoute.path);
const currentTitle = computed(() => (currentRoute.meta?.title as string) ?? '');
const avatarChar = computed(() => (userStore.user?.realName ?? 'U').slice(0, 1));
const roleLabel = computed(() => (userStore.isAdmin ? '管理员' : '教师'));
/** 当前登录用户的头像 URL（preset / 上传文件 / 按用户 ID 的默认分配） */
const myAvatarUrl = computed(() =>
  userStore.user ? resolveAvatarUrl(userStore.user.id, userStore.user.avatar) : '',
);

/** 头像选择器的目标用户（自己或教师管理页指定的教师） */
const pickerUser = computed(() =>
  userStore.user ? { id: userStore.user.id, realName: userStore.user.realName, avatar: userStore.user.avatar } : null,
);

function openMyAvatarPicker(): void {
  if (pickerUser.value) {
    avatarPickerVisible.value = true;
  }
}

function onAvatarSaved(avatar: string): void {
  if (userStore.user) {
    userStore.user.avatar = avatar;
  }
}

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
  if (command === 'change-avatar') {
    openMyAvatarPicker();
    return;
  }
  if (command === 'change-password') {
    changePwdVisible.value = true;
    return;
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout();
        void router.push('/');
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
    position: relative;
    background: var(--bg-elevated);
    border-right: 1px solid var(--border-subtle);
    transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
    overflow: visible;
  }

  // 悬浮在侧栏右边缘的圆形开关按钮
  &__collapse-btn {
    position: absolute;
    top: 66px;
    right: -13px;
    z-index: 20;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    padding: 0;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-full);
    background: var(--bg-elevated);
    color: var(--text-secondary);
    cursor: pointer;
    box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
    transition:
      background 0.2s ease,
      color 0.2s ease,
      transform 0.2s ease,
      box-shadow 0.2s ease;

    .el-icon {
      transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
    }

    &:hover {
      background: var(--brand-600);
      border-color: var(--brand-600);
      color: #fff;
      box-shadow: 0 3px 12px rgb(0 0 0 / 18%);
      transform: scale(1.1);
    }

    &:active {
      transform: scale(0.96);
    }

    &.is-collapsed .el-icon {
      transform: rotate(180deg);
    }
  }

  &__logo {
    display: flex;
    flex-direction: column;
    padding: var(--space-5) var(--space-6);
    overflow: hidden;
    white-space: nowrap;

    &.is-collapsed {
      align-items: center;
      padding: var(--space-5) 0;
    }

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

    // 收起态：圆形徽标
    &-mark {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: var(--radius-md);
      background: linear-gradient(135deg, var(--brand-600), var(--brand-800));
      color: #fff;
      font-size: 18px;
      font-weight: 700;
      letter-spacing: 0;
    }
  }

  &__menu {
    flex: 1;
    border-right: none;
    overflow-x: hidden;

    &:not(.el-menu--collapse) {
      width: 100%;
    }

    &.el-menu--collapse {
      width: 100%;

      :deep(.el-menu-item) {
        padding: 0 calc(50% - 10px);
      }
    }

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
    white-space: nowrap;

    &.is-collapsed {
      justify-content: center;
      padding: var(--space-4) 0;
    }

    &-avatar {
      background: var(--brand-100);
      color: var(--brand-800);
      font-weight: 600;
      cursor: pointer;
      transition:
        box-shadow 0.2s ease,
        transform 0.2s ease;

      &:hover {
        box-shadow: 0 0 0 3px var(--brand-100);
        transform: scale(1.05);
      }
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
