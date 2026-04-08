// 运行时配置
import { requestConfig } from '@/utils/request';
import { history } from 'umi'; // 从 umi 导入 history

import { extensionRegistry } from '@/utils/extensionRegistry';

// ============ ContextPath 动态适配（仿 Swagger UI） ============
// 后端 FlowUiController 在渲染 index.html 时会注入:
//   <script>window.__CONTEXT_PATH__='/flow';</script>
//   <script>window.publicPath='/flow/flow-ui/';</script>
// 此处读取 __CONTEXT_PATH__ 动态调整路由 basename，使前端路由感知宿主系统的 context-path
declare global {
  interface Window {
    __CONTEXT_PATH__?: string;
  }
}

/**
 * 运行时修改渲染配置 —— 动态设置路由 basename
 *
 * 当宿主系统配置了 context-path（如 /flow）时，
 * 将 basename 从编译时的 '/flow-ui/' 调整为 '/flow/flow-ui/'，
 * 确保 history.push('/login') 正确跳转到 /flow/flow-ui/login
 */
/**
 * 对于 UmiJS 4，必须在 modifyContextOpts 中修改 basename 才能被 createHistory 采纳！
 * modifyClientRenderOpts 只能改渲染选项，来不及影响路由实例的创建。
 */
export function modifyContextOpts(memo: any) {
  const contextPath = window.__CONTEXT_PATH__ || '';
  if (contextPath) {
    memo.basename = contextPath + '/flow-ui/';
  }
  return memo;
}

export function modifyClientRenderOpts(memo: any) {
  const contextPath = window.__CONTEXT_PATH__ || '';
  if (contextPath) {
    memo.basename = contextPath + '/flow-ui/';
  }
  return memo;
}

// 全局初始化数据配置，用于 Layout 用户信息和权限初始化
// 更多信息见文档：https://umijs.org/docs/api/runtime-config#getinitialstate
export async function getInitialState(): Promise<{ name: string }> {
  return { name: '@umijs/max' };
}

export const layout = () => {
  return {
    logo: 'https://img.alicdn.com/tfs/TB1YHEpwUT1gK0jSZFhXXaAtVXa-28-27.svg',
    menu: {
      locale: false,
    },
  };
};

export const request = {
  ...requestConfig,
  // 可以覆盖或添加其他配置
};

// src/app.tsx
// src/app.tsx
export function onRouteChange({ location }: { location: Location }) {
  // 白名单路径（无需登录）
  const whiteList = ['/login'];
  console.log("location.pathname", location.pathname)
  // 检查当前路径是否在白名单中
  const isWhitePath = whiteList.some(path =>
    location.pathname === path || location.pathname.endsWith(path)
  );

  // 获取 token
  const token = localStorage.getItem('flow_token');

  // 如果不在白名单且没有 token，跳转到登录页
  if (!isWhitePath && !token) {
    // 进一步检查，防止 history.push 导致的递归调用
    if (location.pathname.endsWith('/login')) {
      return;
    }
    history.push('/login'); // 无刷新跳转，保持路由状态一致
  }
}