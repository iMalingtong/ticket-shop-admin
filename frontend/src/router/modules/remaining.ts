const Layout = () => import("@/layout/index.vue");

export default [
  {
    path: "/account",
    name: "Account",
    component: () => import("@/views/auth/account.vue"),
    meta: { title: "当前管理员", showLink: false }
  },
  {
    path: "/first-password",
    name: "FirstPassword",
    component: () => import("@/views/auth/password.vue"),
    meta: { title: "首次改密", showLink: false }
  },
  {
    path: "/password",
    name: "OwnPassword",
    component: () => import("@/views/auth/password.vue"),
    meta: { title: "修改密码", showLink: false }
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login/index.vue"),
    meta: {
      title: "登录",
      showLink: false
    }
  },
  {
    path: "/network-error",
    name: "NetworkError",
    component: () => import("@/views/error/network.vue"),
    meta: { title: "连接异常", showLink: false }
  },
  // 全屏403（无权访问）页面
  {
    path: "/access-denied",
    name: "AccessDenied",
    component: () => import("@/views/error/403.vue"),
    meta: {
      title: "403",
      showLink: false
    }
  },
  // 全屏500（服务器出错）页面
  {
    path: "/server-error",
    name: "ServerError",
    component: () => import("@/views/error/500.vue"),
    meta: {
      title: "500",
      showLink: false
    }
  },
  {
    path: "/redirect",
    component: Layout,
    meta: {
      title: "加载中...",
      showLink: false
    },
    children: [
      {
        path: "/redirect/:path(.*)",
        name: "Redirect",
        component: () => import("@/layout/redirect.vue")
      }
    ]
  }
] satisfies Array<RouteConfigsTable>;
