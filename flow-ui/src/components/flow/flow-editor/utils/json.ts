type ParseResult =
  | { ok: true; value: any }
  | {
      ok: false;
      message: string;
    };

export function safeJsonParse(text?: string): ParseResult {
  const raw = (text ?? '').trim();
  if (!raw) return { ok: true, value: undefined };
  try {
    return { ok: true, value: JSON.parse(raw) };
  } catch (e: any) {
    return { ok: false, message: e?.message || 'JSON 解析失败' };
  }
}

export function safeJsonStringify(value: any, space = 2) {
  try {
    return JSON.stringify(value, null, space);
  } catch {
    return '';
  }
}

