/** 目录/系统路径前缀拼接与拆分（模型 B：输入框只存相对段） */

import { request } from '@umijs/max';

export function normalizePathPrefix(raw?: string | null): string {
  if (raw == null) return '';
  let v = String(raw).trim();
  if (!v) return '';
  if (!v.startsWith('/')) v = `/${v}`;
  while (v.length > 1 && v.endsWith('/')) v = v.slice(0, -1);
  return v;
}

/** 拼接多段前缀/相对路径，去掉多余斜杠 */
export function joinPathSegments(...parts: Array<string | null | undefined>): string {
  const segs: string[] = [];
  for (const p of parts) {
    const n = normalizePathPrefix(p);
    if (n) segs.push(n);
  }
  if (!segs.length) return '';
  return segs.join('').replace(/\/{2,}/g, '/');
}

/**
 * 沿目录父链读取各级 pathPrefix，按根→叶叠加。
 * 不依赖后端 effective-path-prefix（避免旧包仍是「就近覆盖」）。
 */
export async function fetchStackedDirectoryPathPrefix(
  directoryId?: string | null,
): Promise<string> {
  if (!directoryId) return '';
  const leafToRoot: string[] = [];
  let cur: string | undefined = String(directoryId);
  for (let i = 0; i < 32 && cur; i++) {
    const res: any = await request(`/flow-api/directories/${cur}`, { method: 'GET' });
    const data = res?.data ?? res;
    const seg = normalizePathPrefix(data?.pathPrefix);
    if (seg) leafToRoot.push(seg);
    const parentId = data?.parentId;
    cur = parentId != null && String(parentId).trim() !== ''
      ? String(parentId)
      : undefined;
  }
  leafToRoot.reverse();
  return joinPathSegments(...leafToRoot);
}

/**
 * 若 fullPath 以 prefix 开头，返回去掉前缀后的相对路径（保证以 / 开头或空）。
 * 不匹配则返回原 fullPath（兼容历史完整 path）。
 */
export function stripPathPrefix(fullPath?: string | null, prefix?: string | null): string {
  const full = normalizePathPrefix(fullPath);
  const pre = normalizePathPrefix(prefix);
  if (!full) return '';
  if (!pre) return full;
  if (full === pre) return '/';
  if (full.startsWith(`${pre}/`)) {
    return full.slice(pre.length) || '/';
  }
  return full;
}

export function ensureRelativePath(raw?: string | null): string {
  const v = (raw ?? '').trim();
  if (!v) return '';
  return v.startsWith('/') ? v : `/${v}`;
}

/** 按 / 分段的最长公共前缀；无公共段返回空串 */
export function longestCommonPathPrefix(urls: Array<string | null | undefined>): string {
  const partsList: string[][] = [];
  for (const u of urls) {
    const n = normalizePathPrefix(u);
    if (!n || n === '/') return '';
    const parts = n.replace(/^\//, '').split('/').filter(Boolean);
    if (!parts.length) return '';
    partsList.push(parts);
  }
  if (!partsList.length) return '';
  const first = partsList[0];
  let common = first.length;
  for (let i = 1; i < partsList.length; i++) {
    const cur = partsList[i];
    let j = 0;
    const m = Math.min(common, cur.length);
    while (j < m && first[j] === cur[j]) j++;
    common = j;
    if (common === 0) return '';
  }
  return `/${first.slice(0, common).join('/')}`;
}

/** 旧前缀剥离后拼新前缀；旧前缀不匹配则返回 null */
export function rewritePathWithPrefix(
  fullPath: string | null | undefined,
  oldPrefix: string | null | undefined,
  newPrefix: string | null | undefined,
): string | null {
  const from = normalizePathPrefix(fullPath);
  const oldP = normalizePathPrefix(oldPrefix);
  const newP = normalizePathPrefix(newPrefix);
  if (!from || !newP) return null;
  let relative = from;
  if (oldP) {
    if (from === oldP) relative = '/';
    else if (from.startsWith(`${oldP}/`)) relative = from.slice(oldP.length) || '/';
    else return null;
  }
  return joinPathSegments(newP, relative === '/' ? '' : relative) || newP;
}
