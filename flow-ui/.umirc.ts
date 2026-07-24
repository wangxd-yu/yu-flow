import { defineConfig } from '@umijs/max';
import path from 'path';

const linkifyItReal = path.join(
  __dirname,
  'node_modules/linkify-it/build/index.cjs.js',
);

export default defineConfig({
  antd: {},
  favicons: [process.env.NODE_ENV === 'production' ? '/flow-ui/logo1.svg' : '/logo1.svg'],
  access: {},
  model: {},
  initialState: {},
  request: {},
  // Amis/安全 overrides 兼容：TinyMCE7 无 template；linkify-it5+ 无 default export
  // linkify-it$ 精确匹配，真实包经 @yu-flow/linkify-it-real 绝对路径加载
  alias: {
    'tinymce/plugins/template': path.join(__dirname, 'src/shims/tinymce-plugin-template.js'),
    'linkify-it$': path.join(__dirname, 'src/shims/linkify-it-compat.cjs'),
    '@yu-flow/linkify-it-real': linkifyItReal,
  },
  plugins: [path.join(__dirname, 'plugin.security-headers.ts')],
  proxy: {
    '/flow-api': {
      target: 'http://127.0.0.1:11281/flow/flow-api/',
      changeOrigin: true,
      pathRewrite: { '^/flow-api': '' },
      // Umi bundler-utils 会注入 x-real-url，开发态剥离以免泄露上游
      onProxyRes(proxyRes: any) {
        if (proxyRes?.headers) {
          delete proxyRes.headers['x-real-url'];
          delete proxyRes.headers['X-Real-Url'];
        }
      },
    },
    '/flow-amis': {
      target: 'http://127.0.0.1:11281/flow/',
      changeOrigin: true,
      pathRewrite: { '^/flow-amis': '' },
      onProxyRes(proxyRes: any) {
        if (proxyRes?.headers) {
          delete proxyRes.headers['x-real-url'];
          delete proxyRes.headers['X-Real-Url'];
        }
      },
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
      layout: false,
    },
    {
      path: '/home',
      component: './Home',
      name: '首页',
      icon: 'HomeOutlined',
      access: 'canHome',
    },

    // ── 流程资产：接口 / 任务 / 服务 / 页面 ──
    // 注意：分组节点不要写 path；子路由是绝对路径（如 /flow/api），父级挂 /asset 会触发 RR6 报错白屏
    {
      name: '流程资产',
      icon: 'AppstoreOutlined',
      key: 'menu-asset',
      access: 'canAssetGroup',
      routes: [
        {
          name: '接口管理',
          icon: 'ApiOutlined',
          path: '/flow/api',
          component: './flow/controller',
          access: 'canApi',
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
          access: 'canTask',
        },
        {
          name: '服务管理',
          icon: 'ClusterOutlined',
          path: '/flow/service',
          component: './flow/service',
          access: 'canService',
        },
        {
          name: '页面管理',
          icon: 'LayoutOutlined',
          path: '/page-manage/list',
          component: './PageManage/List',
          access: 'canPage',
        },
      ],
    },

    // ── 运行观测：运行 / 开放 / 日志 ──
    {
      name: '运行观测',
      icon: 'DashboardOutlined',
      key: 'menu-ops',
      access: 'canOpsGroup',
      routes: [
        {
          name: '运行中心',
          icon: 'ThunderboltOutlined',
          path: '/flow/runtime',
          component: './flow/runtime',
          access: 'canRuntime',
        },
        {
          name: '告警规则',
          icon: 'AlertOutlined',
          path: '/flow/alert/rules',
          component: './flow/alert/rules',
          access: 'canAlert',
        },
        {
          name: '告警历史',
          icon: 'HistoryOutlined',
          path: '/flow/alert/history',
          component: './flow/alert/history',
          access: 'canAlert',
        },
        {
          name: '开放平台',
          icon: 'KeyOutlined',
          path: '/flow/open-platform',
          component: './flow/openPlatform',
          access: 'canOpen',
        },
        {
          name: '日志中心',
          icon: 'FileSearchOutlined',
          path: '/log',
          access: 'canLog',
          routes: [
            {
              name: '接口日志',
              path: '/log/execution',
              component: './Log/ExecutionLog',
              access: 'canLog',
            },
            {
              name: '任务日志',
              path: '/log/task',
              component: './Log/TaskLog',
              access: 'canLog',
            },
            {
              name: '服务日志',
              path: '/log/service',
              component: './Log/ServiceLog',
              access: 'canLog',
            },
            {
              name: '三方日志',
              path: '/log/third',
              component: './Log/ThirdLog',
              access: 'canLog',
            },
            {
              name: '登录日志',
              path: '/log/login',
              component: './Log/LoginLog',
              access: 'canLog',
            },
            {
              name: '变更审计',
              path: '/log/audit',
              component: './Log/AuditLog',
              access: 'canLog',
            },
          ],
        },
      ],
    },

    // ── 基础设施：数据源 / 模型 ──
    {
      name: '基础设施',
      icon: 'CloudServerOutlined',
      key: 'menu-infra',
      access: 'canInfraGroup',
      routes: [
        {
          name: '数据源',
          icon: 'CloudOutlined',
          path: '/flow/dataSource',
          component: './flow/dataSource',
          access: 'canDs',
        },
        {
          name: '数据模型',
          icon: 'DatabaseOutlined',
          path: '/data-model/list',
          component: './DataModel/List',
          access: 'canModel',
        },
      ],
    },

    // ── 平台设置：模板 / 参数 / 配置 / 用户 / 文档 ──
    {
      name: '平台设置',
      icon: 'SettingOutlined',
      key: 'menu-platform',
      access: 'canPlatformGroup',
      routes: [
        {
          name: '响应模板',
          icon: 'FileTextOutlined',
          path: '/response-template/manage',
          component: './ResponseTemplate',
          access: 'canTemplate',
        },
        {
          name: '全局参数',
          icon: 'CodeOutlined',
          path: '/sys-macro/manage',
          component: './SysMacroManage',
          access: 'canMacro',
        },
        {
          name: '系统配置',
          icon: 'ControlOutlined',
          path: '/sys-config/manage',
          component: './SysConfig',
          access: 'canConfig',
        },
        {
          name: '用户管理',
          icon: 'TeamOutlined',
          path: '/sys-user/manage',
          component: './SysUser',
          access: 'canUser',
        },
        {
          name: '角色管理',
          icon: 'SafetyCertificateOutlined',
          path: '/sys-role/manage',
          component: './SysRole',
          access: 'canRole',
        },
        {
          name: '接口文档',
          icon: 'BookOutlined',
          path: '/api-docs',
          component: './ApiDocs',
          access: 'canDocs',
        },
      ],
    },

    // ── 兼容跳转 / 无菜单页 ──
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
      access: 'canPage',
    },
    {
      path: '/page-manage/preview/:id',
      component: './PageManage/Preview',
      layout: false,
      access: 'canPage',
    },
    {
      path: '/page-manage',
      redirect: '/page-manage/list',
    },
  ],
  npmClient: 'pnpm',
  esbuildMinifyIIFE: true,
});
