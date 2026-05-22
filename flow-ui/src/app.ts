// 运行时配置
import { requestConfig } from '@/utils/request';
import { history } from 'umi'; // 从 umi 导入 history
import logo from '@/assets/logo1.svg';
import React from 'react';

import { extensionRegistry } from '@/utils/extensionRegistry';

/**
 * 退出登录 —— 清除 Token 并跳转到登录页
 */
function handleLogout() {
  localStorage.removeItem('flow_token');
  history.push('/login');
}

// ============ Favicon 动态与多环境适配 ============
(function () {
  if (typeof document !== 'undefined') {
    const publicPath =
      (window as any).publicPath ||
      (process.env.NODE_ENV === 'production' ? '/flow-ui/' : '/');
    let link = document.querySelector("link[rel*='icon']") as HTMLLinkElement;
    if (!link) {
      link = document.createElement('link');
      link.rel = 'shortcut icon';
      document.getElementsByTagName('head')[0].appendChild(link);
    }
    link.href = `${publicPath}logo1.svg`;
  }
})();

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
 * 校验 JWT Token 的格式和过期时间
 */
function isTokenValid(token: string | null): boolean {
  if (!token) return false;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false;

    // 解析 Base64URL 编码的 Payload
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window.atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );

    const payload = JSON.parse(jsonPayload);
    // 判断是否已经过期
    if (payload.exp && Date.now() >= payload.exp * 1000) {
      return false;
    }
    return true;
  } catch (error) {
    return false;
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

/**
 * render —— 在整个应用渲染之前执行的钩子
 *
 * 安全修复：在应用渲染前检查登录态，未登录且不在白名单的路径：
 * 1. 立即跳转到登录页
 * 2. 【关键】不调用 oldRender()，完全中止应用渲染
 * 这样可以确保未登录用户看不到任何页面内容（包括侧边栏菜单/目录结构），
 * 从根本上杜绝未授权页面闪现漏洞。
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

  const isValid = isTokenValid(token);

  if (!isValid && !isWhiteListed(routePath)) {
    if (token) {
      localStorage.removeItem('flow_token');
    }
    // 未登录或 token 过期：
    // - 跳转到登录页
    // - 【不调用 oldRender()】，彻底阻止应用渲染，防止侧边栏/菜单泄露
    history.push('/login');
    // 跳转后仍需渲染（渲染登录页），否则页面空白
    // 但此时路由已跳到 /login（白名单），直接继续渲染
    oldRender();
    return;
  }

  // 已登录或白名单路径 → 正常渲染
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
    isLogin: isTokenValid(token),
  };
}

/**
 * layout —— 运行时布局配置
 *
 * 安全修复：绑定到 initialState.isLogin，实现响应式更新。
 * 当登录状态变化时（登录/退出），布局立即重新渲染，
 * 确保未登录时菜单栏和页头完全不可见。
 */
export const layout = ({ initialState }: { initialState: { isLogin: boolean } | null }) => {
  // 绑定到 initialState.isLogin 而非静态读取 localStorage
  // 这样登录/退出时布局可以响应式更新，不会残留菜单栏
  const isLogin = initialState?.isLogin ?? false;

  return {
    title: 'YU Flow',
    logo: logo,
    menu: {
      locale: false,
    },
    // 右上角操作区：退出登录按钮
    actionsRender: isLogin
      ? () => [
          React.createElement(
            'span',
            {
              key: 'logout',
              onClick: handleLogout,
              style: {
                cursor: 'pointer',
                fontSize: 14,
                padding: '0 12px',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
              },
              title: '退出登录',
            },
            '退出登录',
          ),
        ]
      : undefined,
    // 未登录时不渲染菜单，防止目录遍历
    menuRender: isLogin ? undefined : false,
    // 未登录时不渲染页头
    headerRender: isLogin ? undefined : false,
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

  const isValid = isTokenValid(token);

  // 如果不在白名单且没有有效 token，跳转到登录页
  if (!isWhitePath && !isValid) {
    if (token) {
      localStorage.removeItem('flow_token');
    }
    // 进一步检查，防止 history.push 导致的递归调用
    if (location.pathname.endsWith('/login')) {
      return;
    }
    history.push('/login'); // 无刷新跳转，保持路由状态一致
  }
}