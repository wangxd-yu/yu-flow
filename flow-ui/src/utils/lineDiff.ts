export type DiffLine = { type: 'same' | 'add' | 'del'; text: string };

/** 超过该规模（行数乘积）不做逐行对比，避免大文本卡住页面 */
const MAX_CELLS = 4_000_000;

/** JSON 文本格式化后再比较，否则整段 DSL 只有一行，差异无从定位 */
export function prettyIfJson(text?: string | null): string {
  if (!text) return '';
  const t = text.trim();
  if (!(t.startsWith('{') || t.startsWith('['))) return text;
  try {
    return JSON.stringify(JSON.parse(t), null, 2);
  } catch {
    return text;
  }
}

/**
 * 基于最长公共子序列的逐行差异。规模过大时返回 null，由调用方退回左右并排展示。
 */
export function lineDiff(before: string, after: string): DiffLine[] | null {
  const a = before.split('\n');
  const b = after.split('\n');
  if (a.length * b.length > MAX_CELLS) return null;
  const n = a.length;
  const m = b.length;
  const lcs: Uint32Array[] = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ type: 'same', text: a[i] });
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      out.push({ type: 'del', text: a[i++] });
    } else {
      out.push({ type: 'add', text: b[j++] });
    }
  }
  while (i < n) out.push({ type: 'del', text: a[i++] });
  while (j < m) out.push({ type: 'add', text: b[j++] });
  return out;
}
