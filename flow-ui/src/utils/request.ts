import { history, RequestConfig } from '@umijs/max';
import { message, Modal } from 'antd';

// 处理未授权情况
function handleUnauthorized() {
  localStorage.removeItem('flow_token');
  //message.error('登录已过期，请重新登录');
  setTimeout(() => history.push('/login'), 1500);
}

// 创建请求配置
export const requestConfig: RequestConfig = {
  timeout: 10000,

  // 请求拦截器
  requestInterceptors: [
    (url, options) => {
      // 1. 处理请求路径
      let finalUrl = url;
      if (!url.startsWith('http') && !url.startsWith('https')) {
        if (process.env.NODE_ENV === 'production') {
          // 使用后端注入的真实 context-path
          const contextPath = (window as any).__CONTEXT_PATH__ || '';
          const targetUrl = url.startsWith('/') ? url : `/${url}`;
          
          // 如果 URL 已经被 amisEnv.ts 等特殊逻辑提前拼接了 contextPath，就不要重复拼接了
          if (contextPath && targetUrl.startsWith(`${contextPath}/`)) {
            finalUrl = targetUrl;
          } else {
            finalUrl = `${contextPath}${targetUrl}`;
          }
        }
      }

      // 2. 添加认证 Token 和凭据配置
      const token = localStorage.getItem('flow_token');
      const headers = {
        ...options.headers,
        "Flow-Authorization": token ? `${token}` : '',
      };

      // 3. 通过 headers 设置凭据
      /* if (options.credentials === 'include') {
        headers['credentials'] = 'include';
      } */

      // 4. 添加请求时间戳防止缓存
      if (options.method?.toUpperCase() === 'GET') {
        finalUrl +=
          (finalUrl.includes('?') ? '&' : '?') + `_t=${new Date().getTime()}`;
      }

      return {
        url: finalUrl,
        options: {
          ...options,
          headers,
          // 通过 fetch 原生选项设置凭据
          credentials: 'include' as RequestCredentials,
        },
      };
    },
  ],

  // 响应拦截器
  responseInterceptors: [
    [
      async (response) => {
        try {
          console.log("response", response)
          // 不再需要异步处理读取返回体内容，可直接在 data 中读出，部分字段可在 config 中找到
          const { data = {} as any, config } = response;

          // 处理未登录状态
          if (data.code === 401) {
            handleUnauthorized();
            throw new Error(data.msg || '请先登录');
          }

          // 处理权限不足
          if (data.code === 403) {
            throw new Error(data.msg || '权限不足');
          }

          if (data.code === 401003) {
            handleUnauthorized();
            throw new Error(data.msg || '请重新登录');
          }

          // 处理业务错误，兼容 amis 返回 ok、status 都有可能不存在的情况
          // ok = true 正常；status == 200（或 0） 正常；如果都不存在也视为正常不报错
          const isOkError = data.ok === false;
          const isStatusError = data.status !== undefined && data.status !== 200 && data.status !== 0;

          if (isOkError || isStatusError) {
            message.error(data.msg || '请求失败');
            throw new Error(data.msg || '请求失败');
          }

          // 返回处理后的数据
          console.log('data.data', data);
          return data;
        } catch (error: any) {
          // 这里抛出的错误，后续在业务代码中可以 catch 到
          return Promise.reject(error);
        }
      },
      (error: any) => {
        // 处理 HTTP 异常（如 500, 404 等非 2xx 状态码）
        console.error("请求发生错误:", error);
        
        // 如果后端有返回响应体（例如包含 {"ok": false, "code": 500, "msg": "..."}）
        if (error.response && error.response.data) {
          const data = error.response.data;
          
          if (data.code === 401 || data.code === 401003) {
            handleUnauthorized();
          }
          
          // 提取后端返回的 msg 或 message 字段
          const errorMsg = data.msg || data.message || `请求错误，状态码: ${error.response.status}`;
          message.error(errorMsg);
        } else {
          // 处理网络不通等其他异常
          message.error(error.message || '网络或服务器异常');
        }
        
        return Promise.reject(error);
      }
    ]
  ],
};