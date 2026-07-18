/** 超过该字符数关闭自动换行，减轻大 JSON 渲染压力 */
export const LOG_WORDWRAP_LIMIT = 200_000;
/** 超过该字符数跳过 pretty-print */
export const LOG_PRETTY_LIMIT = 500_000;

/** 格式化耗时 */
export const formatDuration = (ms?: number | null) => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
};

/**
 * 耗时三档色：
 * - <200ms 绿
 * - 200ms~1s 橙
 * - ≥1s 红
 */
export const getDurationColor = (ms?: number | null) => {
  if (ms == null) return '#8c8c8c';
  if (ms >= 1000) return '#ff4d4f';
  if (ms >= 200) return '#faad14';
  return '#52c41a';
};

/** 安全解析 JSON */
export const safeParse = (str?: string): any => {
  if (!str) return null;
  try {
    return JSON.parse(str);
  } catch {
    return str;
  }
};

/** 尽量美化 JSON；失败或过大则原样返回 */
export const formatMaybeJson = (raw?: string): string => {
  if (!raw) return '';
  if (raw.length > LOG_PRETTY_LIMIT) return raw;
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
};

/** 对象/字符串 → 美化文本 */
export const prettyJson = (obj: any): string => {
  if (obj === undefined || obj === null) return '';
  if (typeof obj === 'string') {
    return formatMaybeJson(obj);
  }
  try {
    const text = JSON.stringify(obj, null, 2);
    return text.length > LOG_PRETTY_LIMIT ? JSON.stringify(obj) : text;
  } catch {
    return String(obj);
  }
};
