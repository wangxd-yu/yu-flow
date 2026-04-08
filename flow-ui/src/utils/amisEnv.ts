import { request as umiRequest, history } from '@umijs/max';
import { message } from 'antd';

/**
 * 创建系统公共的 Amis 环境（Env）
 * 在内部分别实现了 fetcher、部分 UI 通知桥接和路有跳转
 */
export const createAmisEnv = () => ({
  fetcher: ({ url, method, data, responseType, config, headers }: any) => {
    const normalizedMethod = (method || 'get').toLowerCase();

    // 统一在 fetcher 接管 /flow-amis 前缀，防止被框架底层的配置或绕过影响
    let finalUrl = url;
    if (finalUrl.startsWith('/flow-amis')) {
      if (process.env.NODE_ENV === 'production') {
        const contextPath = (window as any).__CONTEXT_PATH__ || '';
        finalUrl = finalUrl.replace('/flow-amis', '');
        finalUrl = `${contextPath}${finalUrl.startsWith('/') ? finalUrl : `/${finalUrl}`}`;
      }
      // 开发环境时保留 /flow-amis 交给 webpack proxy
    }

    return umiRequest(finalUrl, {
      method: normalizedMethod,
      // GET 请求使用 params，其余使用 data(body)
      ...(normalizedMethod === 'get' ? { params: data } : { data }),
      headers,
      responseType: responseType as any,
      getResponse: true, // 防止 umi 请求自动返回 res.data
      ...config,
    })
      .then((raw: any) => {
        // 根据全局拦截器当前是直接返回 response 还是 data，做一层兼容提取
        const res = (raw && raw.code === undefined && raw.data && raw.data.code !== undefined) ? raw.data : raw;

        console.log("Amis 响应拦截:", finalUrl, res);

        // 如果已经是标准 Amis 格式：{ status: 0, data: {...} }，直接返回
        if (res && res.status !== undefined && res.data !== undefined) {
           return { status: 200, data: res } as any;
        }

        // Adapter 转换层：处理我们后端的标准包裹 { code: 200, msg: '', data: ... }
        if (res && res.code !== undefined) {
          return {
            status: 200, // HTTP 层面强制给 200，防止 Amis 内部中断机制
            data: {
              status: res.code === 200 ? 0 : res.code,
              msg: res.msg || '',
              // 对于下拉框这种期望 items 的，这里需要直接抛出后端包裹在 data 里的数组或者分页结构
              data: res.data,
            },
          } as any;
        }

        // 走到这里，说明 res 没有 code 也没有 status，比如 MyBatis Plus 的 IPage 分页结果（没有套 R 响应体）
        // 那就把整个 res 当做 data 包裹起来给 Amis
        return { 
          status: 200, 
          data: {
             status: 0, 
             msg: '', 
             data: res
          } 
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
      case 'success': message.success(msg); break;
      case 'error': message.error(msg); break;
      case 'warning': message.warning(msg); break;
      default: message.info(msg);
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
    if (to.startsWith('http://') || to.startsWith('https://')) {
      window.open(to, '_blank');
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
    
    let finalTo = to;
    // 如果 Amis 传来的是纯参数 （比如 "?page=1"），把它和当前的路径结合
    if (finalTo.startsWith('?')) {
      finalTo = window.location.pathname + finalTo;
    }

    const contextPath = (window as any).__CONTEXT_PATH__ || '';
    const basename = `${contextPath}/flow-ui`;
    
    // 如果包含了 basename 则剥离它，因为 UmiJS 发起跳转时还会再拼接一次
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
