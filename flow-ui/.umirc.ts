import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  proxy: {
    '/flow-api': {
      target: 'http://127.0.0.1:11281/flow/flow-api/',
      changeOrigin: true,
      pathRewrite: { '^/flow-api': '' },
    },
    '/flow-amis': {
      target: 'http://127.0.0.1:11281/flow/',
      changeOrigin: true,
      pathRewrite: { '^/flow-amis': '' },
    },
  },
  // ============ 核心路由配置 ============
  // 生产环境使用绝对路径 /flow-ui/，确保 History 路由下静态资源引用正确
  publicPath: process.env.NODE_ENV === 'production' ? '/flow-ui/' : '/',
  // 启用运行时 publicPath，允许后端通过 window.publicPath 动态注入（适配 context-path）
  runtimePublicPath: {},
  // 应用的基础路由前缀，所有页面路由均在 /flow-ui/ 下
  base: '/flow-ui/',
  // 使用 Browser History 路由，使后端拦截器能获取真实页面路径
  history: { type: 'browser' },
  // 文件名带 hash 戳避免浏览器缓存问题
  hash: true,
  layout: {
    title: '@umijs/max',
    // 排除登录页，让登录页不显示布局
    exclude: ['/login'],
  },
  routes: [
    {
      path: '/login',
      component: './Login',
      layout: false, // 禁用登录页的布局
    },
    {
      path: '/home',
      component: './Home',
      //redirect: '/home',
      //wrappers: ['@/wrappers/auth'],
      name: '首页', // 添加名称，用于菜单显示
    },
    {
      name: '数据源管理',
      icon: 'CloudServerOutlined',
      path: '/flow/dataSource',
      component: './flow/dataSource',
    },
    {
      name: '数据模型',
      icon: 'DatabaseOutlined',
      path: '/data-model/list',
      component: './DataModel/List',
    },
    {
      name: '接口管理',
      icon: 'ApiOutlined',
      path: '/flow/controller',
      component: './flow/controller',
    },
    {
      name: '页面管理',
      path: '/page-manage/list',
      icon: 'LayoutOutlined',
      component: './PageManage/List',
    },
    {
      name: '全局参数',
      path: '/sys-macro/manage',
      icon: 'SettingOutlined',
      component: './SysMacroManage',
    },
    {
      name: '系统配置',
      path: '/sys-config/manage',
      icon: 'ControlOutlined',
      component: './SysConfig',
    },
    {
      name: '响应模板',
      path: '/response-template/manage',
      icon: 'FileTextOutlined',
      component: './ResponseTemplate',
    },
    {
      path: '/page-manage/designer/:id',
      component: './PageManage/Designer',
      layout: false,
    },
    {
      path: '/page-manage/preview/:id',
      component: './PageManage/Preview',
      layout: false,
    },
    {
      path: '/page-manage',
      redirect: '/page-manage/list',
    },
  ],
  npmClient: 'pnpm',
  esbuildMinifyIIFE: true,
});
