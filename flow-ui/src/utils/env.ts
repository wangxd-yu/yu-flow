/**
 * 获取 Amis 接口请求的前缀
 * 为保证保存的 JSON Schema 具有完全的环境无关和可移植性，
 * 我们始终返回伪造的统一样板前缀 '/flow-amis'。
 * 
 * 1. 本地开发（development）：UmiJS 的 proxy 看到 /flow-amis 会把它替换为空，并转发给后端真实业务接口。
 * 2. 生产环境（production）：在 src/utils/request.ts 的底层拦截器里，如果识别到 URL 是以 /flow-amis 开头，
 *    会动态将其剥离，然后换成正确的 window.__CONTEXT_PATH__。
 */
export const getAmisApiPrefix = (): string => {
  return '/flow-amis';
};
