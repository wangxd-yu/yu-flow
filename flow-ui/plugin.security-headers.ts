import type { IApi } from '@umijs/max';

/**
 * 开发服务器为 SPA HTML 注入基础安全响应头。
 * CORS 白名单见 pnpm patch：@umijs/bundler-webpack。
 */
export default (api: IApi) => {
  api.addBeforeMiddlewares(() => {
    return (req, res, next) => {
      res.setHeader('X-Frame-Options', 'DENY');
      res.setHeader('X-Content-Type-Options', 'nosniff');
      res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
      res.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');
      const accept = String(req.headers.accept || '');
      if (accept.includes('text/html') || String(req.url || '').startsWith('/flow-ui')) {
        res.setHeader(
          'Content-Security-Policy',
          [
            "default-src 'self'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "object-src 'none'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "style-src 'self' 'unsafe-inline'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
            "connect-src 'self'",
            "worker-src 'self' blob:",
          ].join('; '),
        );
      }
      next();
    };
  });
};
