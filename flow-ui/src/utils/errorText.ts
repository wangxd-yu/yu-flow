/**
 * 统一请求错误文案工具。
 *
 * 背景：全局拦截器（utils/request.ts）已对业务错误 toast 过一次，
 * 页面 catch 后再 message.error 兜底文案会出现「双重弹窗」。
 * 约定：拦截器弹过的错误带 uiHandled 标记；页面统一用 showActionError 兜底，
 * 只在拦截器未处理时提示，且自动跳过 DEMO_RESTRICTED（由拦截器 Modal 呈现）。
 */
import { message } from 'antd';

/** 已知机器码 → 用户可读文案（后端 msg 为纯错误码时兜底翻译） */
const CODE_TEXT: Record<string, string> = {
  SCRIPT_LANGUAGE_DISABLED: '该脚本语言未在白名单内，请联系管理员开启',
  IGNORE_SSL_DISABLED: '当前环境已禁用跳过证书校验（ignoreSsl）',
  OPEN_RATE_LIMITED: '调用过于频繁，请稍后再试',
  OPEN_AUTH_DENIED: '平台已停用或未授权该接口',
};

function extractRawMessage(error: unknown): string {
  if (!error) return '';
  if (typeof error === 'string') return error;
  const e = error as any;
  return (
    e?.response?.data?.msg ||
    e?.response?.data?.message ||
    e?.message ||
    ''
  );
}

/** 给拦截器用：标记该错误已向用户提示过 */
export function markUiHandled<T>(error: T): T {
  if (error && typeof error === 'object') {
    (error as any).uiHandled = true;
  }
  return error;
}

export function isUiHandled(error: unknown): boolean {
  return !!(error as any)?.uiHandled;
}

export function isDemoRestricted(error: unknown): boolean {
  return extractRawMessage(error).includes('DEMO_RESTRICTED');
}

/**
 * 错误 → 用户可读文案。
 * 优先级：网络类归一文案 > 已知错误码翻译 > 后端原始 msg > fallback。
 */
export function getErrorText(error: unknown, fallback = '操作失败，请重试'): string {
  const raw = extractRawMessage(error).trim();
  if (!raw) return fallback;
  if (/timeout|timed?\s*out/i.test(raw)) return '请求超时，请稍后重试';
  if (/failed to fetch|network\s*error/i.test(raw)) return '网络连接异常，请检查网络后重试';
  if (CODE_TEXT[raw]) return CODE_TEXT[raw];
  return raw;
}

/**
 * 页面操作兜底提示：拦截器已弹过 / DEMO_RESTRICTED（Modal 呈现）时静默，
 * 否则用统一文案 toast 一次。
 */
export function showActionError(error: unknown, fallback = '操作失败，请重试'): void {
  if (isUiHandled(error) || isDemoRestricted(error)) return;
  message.error(getErrorText(error, fallback));
}
