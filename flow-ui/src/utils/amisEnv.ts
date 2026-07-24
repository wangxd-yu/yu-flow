import { request as umiRequest, history } from '@umijs/max';
import { message } from 'antd';

const isDev = process.env.NODE_ENV === 'development';

function isAbsoluteHttpUrl(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

function pathAllowed(pathname: string): boolean {
  return pathname.startsWith('/flow-api') || pathname.startsWith('/flow-amis');
}

function isAllowedAmisApiUrl(url: string): boolean {
  if (!url || typeof url !== 'string') return false;
  const u = url.trim();
  if (!u || u.startsWith('//')) return false;
  // 仅允许显式 /flow-api、/flow-amis 前缀（含 contextPath 下的绝对同源 URL）
  if (u.startsWith('/flow-api') || u.startsWith('/flow-amis')) return true;
  if (!isAbsoluteHttpUrl(u)) return false;
  try {
    const parsed = new URL(u, window.location.origin);
    if (parsed.origin !== window.location.origin) return false;
    const contextPath = (typeof window !== 'undefined' && (window as any).__CONTEXT_PATH__) || '';
    let path = parsed.pathname;
    if (contextPath && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length) || '/';
      if (!path.startsWith('/')) path = '/' + path;
    }
    return pathAllowed(path);
  } catch {
    return false;
  }
}

/**
 * 创建系统公共的 Amis 环境（Env）
 * 在内部分别实现了 fetcher、部分 UI 通知桥接和路由跳转
 */
export const createAmisEnv = () => ({
  fetcher: ({ url, method, data, responseType, config, headers }: any) => {
    const normalizedMethod = (method || 'get').toLowerCase();

    let finalUrl = url;
    if (typeof finalUrl === 'string' && finalUrl.startsWith('/flow-amis')) {
      if (process.env.NODE_ENV === 'production') {
        const contextPath = (window as any).__CONTEXT_PATH__ || '';
        finalUrl = finalUrl.replace('/flow-amis', '');
        finalUrl = `${contextPath}${finalUrl.startsWith('/') ? finalUrl : `/${finalUrl}`}`;
      }
    }

    if (!isAllowedAmisApiUrl(String(finalUrl))) {
      return Promise.resolve({
        status: 403,
        data: {
          status: 1,
          msg: 'Amis 请求地址不在允许范围（仅同源 /flow-api、/flow-amis）',
          data: null,
        },
      } as any);
    }

    return umiRequest(finalUrl, {
      method: normalizedMethod,
      ...(normalizedMethod === 'get' ? { params: data } : { data }),
      headers,
      responseType: responseType as any,
      getResponse: true,
      ...config,
    })
      .then((raw: any) => {
        const res =
          raw && raw.code === undefined && raw.data && raw.data.code !== undefined
            ? raw.data
            : raw;

        if (isDev) {
          console.log('Amis 响应拦截:', finalUrl, res);
        }

        if (res && res.status !== undefined && res.data !== undefined) {
          return { status: 200, data: res } as any;
        }

        if (res && res.code !== undefined) {
          return {
            status: 200,
            data: {
              status: res.code === 200 ? 0 : res.code,
              msg: res.msg || '',
              data: res.data,
            },
          } as any;
        }

        return {
          status: 200,
          data: {
            status: 0,
            msg: '',
            data: res,
          },
        } as any;
      })
      .catch((err: any) => {
        return {
          status: 500,
          data: {
            status: 1,
            msg: err?.message || '请求异常',
            data: null,
          },
        } as any;
      });
  },

  isCancel: () => false,

  notify: (type: 'success' | 'error' | 'info' | 'warning', msg: string) => {
    switch (type) {
      case 'success':
        message.success(msg);
        break;
      case 'error':
        message.error(msg);
        break;
      case 'warning':
        message.warning(msg);
        break;
      default:
        message.info(msg);
    }
  },

  alert: (content: string) => {
    message.info(content);
  },

  confirm: (content: string) => {
    return Promise.resolve(window.confirm(content));
  },

  jumpTo: (to: string) => {
    if (to === 'goBack') return history.back();
    if (/^(javascript|data):/i.test(to)) {
      message.warning('已拦截不安全跳转');
      return;
    }
    if (to.startsWith('http://') || to.startsWith('https://') || to.startsWith('//')) {
      try {
        const parsed = new URL(to.startsWith('//') ? `${window.location.protocol}${to}` : to);
        if (parsed.origin !== window.location.origin) {
          message.warning('已拦截外域跳转，仅允许站内路径');
          return;
        }
        const contextPath = (window as any).__CONTEXT_PATH__ || '';
        const basename = `${contextPath}/flow-ui`;
        let path = parsed.pathname + parsed.search + parsed.hash;
        if (path.startsWith(basename)) {
          path = path.substring(basename.length) || '/';
          if (!path.startsWith('/')) path = '/' + path;
        }
        history.push(path);
      } catch {
        message.warning('非法跳转地址');
      }
      return;
    }

    let finalTo = to;
    if (finalTo.startsWith('?')) {
      finalTo = window.location.pathname + finalTo;
    }

    const contextPath = (window as any).__CONTEXT_PATH__ || '';
    const basename = `${contextPath}/flow-ui`;

    if (finalTo.startsWith(basename)) {
      finalTo = finalTo.substring(basename.length);
      if (!finalTo.startsWith('/')) finalTo = '/' + finalTo;
    }
    history.push(finalTo);
  },

  updateLocation: (to: string, replace?: boolean) => {
    if (to === 'goBack') return history.back();
    if (/^(javascript|data):/i.test(to)) {
      message.warning('已拦截不安全跳转');
      return;
    }
    if (to.startsWith('http://') || to.startsWith('https://') || to.startsWith('//')) {
      try {
        const parsed = new URL(to.startsWith('//') ? `${window.location.protocol}${to}` : to);
        if (parsed.origin !== window.location.origin) {
          message.warning('已拦截外域跳转，仅允许站内路径');
          return;
        }
        const contextPath = (window as any).__CONTEXT_PATH__ || '';
        const basename = `${contextPath}/flow-ui`;
        let path = parsed.pathname + parsed.search + parsed.hash;
        if (path.startsWith(basename)) {
          path = path.substring(basename.length) || '/';
          if (!path.startsWith('/')) path = '/' + path;
        }
        if (replace) history.replace(path);
        else history.push(path);
      } catch {
        message.warning('非法跳转地址');
      }
      return;
    }

    let finalTo = to;
    if (finalTo.startsWith('?')) {
      finalTo = window.location.pathname + finalTo;
    }

    const contextPath = (window as any).__CONTEXT_PATH__ || '';
    const basename = `${contextPath}/flow-ui`;

    if (finalTo.startsWith(basename)) {
      finalTo = finalTo.substring(basename.length);
      if (!finalTo.startsWith('/')) finalTo = '/' + finalTo;
    }

    if (replace) {
      history.replace(finalTo);
    } else {
      history.push(finalTo);
    }
  },

  theme: 'cxd',
});
