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

// ============ 登录白名单（无需登录即可访问的路径） ============
const LOGIN_WHITE_LIST = ['/login'];

/**
 * 判断当前路径是否在白名单中
 */
function isWhiteListed(pathname: string): boolean {
  return LOGIN_WHITE_LIST.some(
    (path) => pathname === path || pathname.endsWith(path),
  );
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

/**
 * render —— 在整个应用渲染之前执行的钩子
 *
 * 安全修复：在应用渲染前检查登录态，未登录且不在白名单的直接跳转登录页。
 * 这样可以确保未登录用户看不到任何页面内容（包括侧边栏菜单/目录结构），
 * 从根本上杜绝目录遍历漏洞。
 */
export function render(oldRender: () => void) {
  const token = localStorage.getItem('flow_token');
  const pathname = window.location.pathname;

  // 需要去掉可能的 base 前缀来获取路由路径
  const base = '/flow-ui/';
  const contextPath = window.__CONTEXT_PATH__ || '';
  const fullBase = contextPath ? contextPath + base : base;

  // 从完整路径中提取路由路径
  let routePath = pathname;
  if (pathname.startsWith(fullBase)) {
    routePath = pathname.substring(fullBase.length - 1); // 保留开头的 /
  } else if (pathname.startsWith(base)) {
    routePath = pathname.substring(base.length - 1);
  }

  if (!token && !isWhiteListed(routePath)) {
    // 未登录且非白名单页面 → 直接重定向到登录页，不渲染任何应用内容
    history.push('/login');
  }

  // 继续正常渲染
  oldRender();
}

// 全局初始化数据配置，用于 Layout 用户信息和权限初始化
// 更多信息见文档：https://umijs.org/docs/api/runtime-config#getinitialstate
export async function getInitialState(): Promise<{
  name: string;
  isLogin: boolean;
}> {
  const token = localStorage.getItem('flow_token');
  return {
    name: '@umijs/max',
    isLogin: !!token,
  };
}

export const layout = () => {
  // 在 layout 渲染时再次检查登录态，确保未登录用户看不到菜单
  const token = localStorage.getItem('flow_token');

  return {
    logo: 'https://img.alicdn.com/tfs/TB1YHEpwUT1gK0jSZFhXXaAtVXa-28-27.svg',
    menu: {
      locale: false,
    },
    // 未登录时不渲染菜单，防止目录遍历
    menuRender: token ? undefined : false,
    // 未登录时不渲染页头
    headerRender: token ? undefined : false,
  };
};

export const request = {
  ...requestConfig,
  // 可以覆盖或添加其他配置
};

// src/app.tsx
export function onRouteChange({ location }: { location: Location }) {
  // 获取 token
  const token = localStorage.getItem('flow_token');

  // 检查当前路径是否在白名单中
  const isWhitePath = isWhiteListed(location.pathname);

  // 如果不在白名单且没有 token，跳转到登录页
  if (!isWhitePath && !token) {
    // 进一步检查，防止 history.push 导致的递归调用
    if (location.pathname.endsWith('/login')) {
      return;
    }
    history.push('/login'); // 无刷新跳转，保持路由状态一致
  }
}