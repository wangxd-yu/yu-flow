import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  favicons: [process.env.NODE_ENV === 'production' ? '/flow-ui/logo1.svg' : '/logo1.svg'],
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
    title: 'YU Flow',
    // 排除登录页，让登录页不显示布局
    exclude: ['/login'],
  },
  routes: [
    {
      path: '/',
      redirect: '/home',
    },
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
      path: '/flow/api',
      component: './flow/controller',
    },
    {
      path: '/flow/controller',
      redirect: '/flow/api',
    },
    {
      name: '任务管理',
      icon: 'ClockCircleOutlined',
      path: '/flow/task',
      component: './flow/task',
    },
    {
      name: '服务编排',
      icon: 'ClusterOutlined',
      path: '/flow/service',
      component: './flow/service',
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
      name: '日志',
      icon: 'FileSearchOutlined',
      path: '/log',
      routes: [
        {
          name: '登录日志',
          path: '/log/login',
          component: './Log/LoginLog',
        },
        {
          name: '接口日志',
          path: '/log/execution',
          component: './Log/ExecutionLog',
        },
        {
          name: '任务日志',
          path: '/log/task',
          component: './Log/TaskLog',
        },
        {
          name: '服务日志',
          path: '/log/service',
          component: './Log/ServiceLog',
        },
        {
          name: '三方日志',
          path: '/log/third',
          component: './Log/ThirdLog',
        },
      ],
    },
    {
      name: 'API 文档',
      path: '/api-docs',
      icon: 'FileTextOutlined',
      component: './ApiDocs',
    },
    {
      path: '/flow/task-log',
      redirect: '/log/task',
    },
    {
      path: '/log/login-log',
      redirect: '/log/login',
    },
    {
      path: '/log/execution-log',
      redirect: '/log/execution',
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
