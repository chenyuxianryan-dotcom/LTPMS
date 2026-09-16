/** 路由表与导航守卫 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useUserStore } from '@/store/user';
import { ROLE } from '@/constants';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/pages/landing/LandingPage.vue'),
    meta: { title: '首页', public: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/login/LoginPage.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/pages/dashboard/DashboardPage.vue'),
        meta: { title: '概览', icon: 'Odometer' },
      },
      {
        path: 'laboratories',
        name: 'laboratories',
        component: () => import('@/pages/laboratory/LaboratoryPage.vue'),
        meta: { title: '实验室管理', icon: 'OfficeBuilding', roles: [ROLE.ADMIN] },
      },
      {
        path: 'semesters',
        name: 'semesters',
        component: () => import('@/pages/semester/SemesterPage.vue'),
        meta: { title: '学期管理', icon: 'Calendar', roles: [ROLE.ADMIN] },
      },
      {
        path: 'schedules',
        name: 'schedules',
        component: () => import('@/pages/schedule/SchedulePage.vue'),
        meta: { title: '课表管理', icon: 'Clock', roles: [ROLE.ADMIN] },
      },
      {
        path: 'schedules/teacher/:teacherId',
        name: 'schedule-teacher',
        component: () => import('@/pages/schedule/TeacherSchedulePage.vue'),
        meta: { title: '教师课表', roles: [ROLE.ADMIN] },
      },
      {
        path: 'teachers',
        name: 'teachers',
        component: () => import('@/pages/teacher/TeacherPage.vue'),
        meta: { title: '教师管理', icon: 'User', roles: [ROLE.ADMIN] },
      },
      {
        path: 'courses',
        name: 'courses',
        component: () => import('@/pages/course/CoursePage.vue'),
        meta: { title: '课程管理', icon: 'Notebook' },
      },
      {
        path: 'experiments',
        name: 'experiments',
        component: () => import('@/pages/experiment/ExperimentPage.vue'),
        meta: { title: '实验项目', icon: 'Flask', roles: [ROLE.TEACHER] },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/pages/report/ReportPage.vue'),
        meta: { title: '报表中心', icon: 'DataAnalysis' },
      },
      {
        path: 'nlq',
        name: 'nlq',
        component: () => import('@/pages/nlq/NlqPage.vue'),
        meta: { title: '智能查询', icon: 'ChatDotRound' },
      },
      {
        path: 'insight',
        name: 'insight',
        component: () => import('@/pages/insight/InsightPage.vue'),
        meta: { title: '数据智能', icon: 'TrendCharts' },
      },
      {
        path: 'dicts',
        name: 'dicts',
        component: () => import('@/pages/dict/DictPage.vue'),
        meta: { title: '字典管理', icon: 'Collection', roles: [ROLE.ADMIN] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const userStore = useUserStore();
  const meta = to.meta as { title?: string; public?: boolean; roles?: string[] };

  document.title = meta.title ? `${meta.title} · LTPMS` : 'LTPMS · 实验教学项目管理系统';

  if (meta.public) {
    // 已登录访问登录页/落地页 → 回首页
    if (userStore.isLoggedIn && (to.name === 'login' || to.name === 'landing')) {
      return '/dashboard';
    }
    return true;
  }

  if (!userStore.isLoggedIn) {
    // 未登录先到落地页，撕裂进登录后再回原目标
    return { path: '/', query: { redirect: to.fullPath } };
  }

  // 角色权限：未登录时先拉取用户信息再校验
  if (meta.roles && meta.roles.length > 0) {
    if (!userStore.user) {
      return userStore
        .fetchMe()
        .then(() => {
          if (meta.roles && !meta.roles.includes(userStore.role)) {
            return '/dashboard';
          }
          return true;
        })
        .catch(() => '/login');
    }
    if (!meta.roles.includes(userStore.role)) {
      return '/dashboard';
    }
  } else if (!userStore.user) {
    void userStore.fetchMe().catch(() => undefined);
  }

  return true;
});

export default router;
