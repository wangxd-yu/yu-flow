import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Yu Flow",
  description: "新一代全栈嵌入式低代码引擎",
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: '/logo.svg', // 占位logo，后续可替换为实际项目logo
    nav: [
      { text: '首页', link: '/' },
      { text: '入门指南', link: '/guide/introduction' },
      { text: '操作手册', link: '/manual/data-source' },
      { text: '高阶扩展', link: '/advanced/backend-extension' },
      { text: 'API 参考', link: '/api/' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: '入门指南',
          items: [
            { text: '引擎介绍', link: '/guide/introduction' },
            { text: '快速开始', link: '/guide/quick-start' },
            { text: '核心概念', link: '/guide/concepts' }
          ]
        }
      ],
      '/manual/': [
        {
          text: '操作手册',
          items: [
            { text: '动态数据源', link: '/manual/data-source' },
            { text: '数据模型', link: '/manual/data-model' },
            { text: '动态 API', link: '/manual/dynamic-api' },
            { text: '可视化页面', link: '/manual/visual-page' },
            { text: '全局参数', link: '/manual/global-params' }
          ]
        }
      ],
      '/advanced/': [
        {
          text: '进阶与二次开发',
          items: [
            { text: 'Flow DSL 协议规范', link: '/advanced/flow-dsl' },
            { text: '后端功能扩展', link: '/advanced/backend-extension' },
            { text: '前端组件扩展', link: '/advanced/frontend-extension' },
            { text: '系统深度集成', link: '/advanced/embed-integration' },
            { text: '权限与安全', link: '/advanced/security' }
          ]
        }
      ],
      '/api/': [
        {
          text: '总览',
          items: [
            { text: '编排画布与核心概念', link: '/api/' }
          ]
        },
        {
          text: '节点参考手册',
          items: [
            { text: 'Request 请求入口', link: '/api/nodes/request' },
            { text: 'Database 数据库', link: '/api/nodes/database' },
            { text: 'If 条件判断', link: '/api/nodes/if' },
            { text: 'Evaluate 表达式计算', link: '/api/nodes/evaluate' },
            { text: 'Response HTTP 响应', link: '/api/nodes/response' },
            { text: 'SystemMethod 系统方法', link: '/api/nodes/system-method' },
            { text: 'SystemVar 系统变量', link: '/api/nodes/system-var' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/your-repo/yu-flow' }
    ],

    search: {
      provider: 'local'
    },

    docFooter: {
      prev: '上一页',
      next: '下一页'
    },

    outline: {
      label: '页面导航',
      level: [2, 3]
    },

    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2026-present Yu Flow Team'
    }
  }
})
