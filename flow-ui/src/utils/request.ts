import { history, RequestConfig } from '@umijs/max';
import { message, Modal } from 'antd';
import { clearAuthHint, csrfHeaders, hostAuthHeaders } from '@/utils/session';
import { markUiHandled } from '@/utils/errorText';

function handleUnauthorized() {
  clearAuthHint();
  history.push('/login');
}

/** 后端每个响应都带 X-Request-Id，报错时附在提示后面，方便按 ID 查日志 */
function requestIdOf(headers: any): string | undefined {
  if (!headers) return undefined;
  const id = typeof headers.get === 'function' ? headers.get('x-request-id') : headers['x-request-id'];
  return id ? String(id) : undefined;
}

function withRequestId(msg: string, headers: any): string {
  const id = requestIdOf(headers);
  return id ? `${msg}（请求 ID：${id}）` : msg;
}

export const requestConfig: RequestConfig = {
  timeout: 10000,

  requestInterceptors: [
    (url, options) => {
      let finalUrl = url;
      if (!url.startsWith('http') && !url.startsWith('https')) {
        if (process.env.NODE_ENV === 'production') {
          const contextPath = (window as any).__CONTEXT_PATH__ || '';
          const targetUrl = url.startsWith('/') ? url : `/${url}`;

          if (contextPath && targetUrl.startsWith(`${contextPath}/`)) {
            finalUrl = targetUrl;
          } else {
            finalUrl = `${contextPath}${targetUrl}`;
          }
        }
      }

      const isExternalAbsolute =
        /^https?:\/\//i.test(finalUrl) &&
        (() => {
          try {
            return new URL(finalUrl).origin !== window.location.origin;
          } catch {
            return true;
          }
        })();

      const headers: Record<string, any> = {
        ...options.headers,
      };

      // Cookie 会话：同源请求附加 CSRF；嵌入时再附宿主 OPCENTER Authorization
      if (!isExternalAbsolute) {
        Object.assign(headers, csrfHeaders(), hostAuthHeaders());
      }

      const method = (options.method || 'GET').toUpperCase();
      if (
        ['POST', 'PUT', 'PATCH'].includes(method) &&
        options.data !== undefined &&
        options.data !== null &&
        !(options.data instanceof FormData)
      ) {
        const ct = String(headers['Content-Type'] || headers['content-type'] || '');
        if (!ct || ct.startsWith('application/json')) {
          headers['Content-Type'] = 'application/json';
          delete headers['content-type'];
        }
      }

      if (options.method?.toUpperCase() === 'GET') {
        finalUrl +=
          (finalUrl.includes('?') ? '&' : '?') + `_t=${new Date().getTime()}`;
      }

      return {
        url: finalUrl,
        options: {
          ...options,
          headers,
          credentials: 'include' as RequestCredentials,
        },
      };
    },
  ],

  responseInterceptors: [
    [
      async (response) => {
        try {
          if (process.env.NODE_ENV === 'development') {
            console.log('response', response);
          }
          const { data = {} as any } = response;

          if (
            data.code === 401 ||
            data.code === 401002 ||
            data.code === 401003 ||
            data.code === 401004
          ) {
            handleUnauthorized();
            // 已跳转登录页；标记避免页面重复弹错
            throw markUiHandled(new Error(data.msg || '请先登录'));
          }

          if (data.code === 403) {
            throw new Error(data.msg || '权限不足');
          }

          const isOkError = data.ok === false;
          const isStatusError =
            data.status !== undefined && data.status !== 200 && data.status !== 0;

          if (isOkError || isStatusError) {
            const errorMsg = data.msg || '请求失败';
            if (errorMsg.includes('DEMO_RESTRICTED')) {
              const cleanMsg = errorMsg.replace(/.*\[DEMO_RESTRICTED\]/, '').trim();
              Modal.warning({
                title: '演示环境安全限制',
                content: cleanMsg,
                okText: '我知道了',
                centered: true,
                maskClosable: true,
              });
            } else {
              message.error(withRequestId(errorMsg, response.headers));
            }
            // 已向用户提示；标记后页面 catch 不再重复弹
            const handled: any = markUiHandled(new Error(errorMsg));
            handled.requestId = requestIdOf(response.headers);
            throw handled;
          }

          if (process.env.NODE_ENV === 'development') {
            console.log('data.data', data);
          }
          return data;
        } catch (error: any) {
          return Promise.reject(error);
        }
      },
      (error: any) => {
        console.error('请求发生错误:', error);

        if (error.response && error.response.data) {
          const data = error.response.data;

          if (data.code === 401 || data.code === 401003) {
            handleUnauthorized();
          }

          const errorMsg =
            data.msg || data.message || `请求错误，状态码: ${error.response.status}`;
          if (errorMsg.includes('DEMO_RESTRICTED')) {
            const cleanMsg = errorMsg.replace(/.*\[DEMO_RESTRICTED\]/, '').trim();
            Modal.warning({
              title: '演示环境安全限制',
              content: cleanMsg,
              okText: '我知道了',
              centered: true,
              maskClosable: true,
            });
          } else {
            message.error(withRequestId(errorMsg, error.response.headers));
          }
          error.requestId = requestIdOf(error.response.headers);
        } else {
          message.error(error.message || '网络或服务器异常');
        }

        return Promise.reject(markUiHandled(error));
      },
    ],
  ],
};
