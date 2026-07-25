/**
 * 纯前端 cURL 解析：粘贴常见 curl -X/-H/-d 命令 → method/path/契约草稿字段。
 * 不做后端调用；复杂 form 文件上传等标 warnings。
 */
import type { BodyType, SchemaNode, SchemaType } from '@/components/flow/ApiContractDesigner/types';

export interface CurlImportDraft {
  method: string;
  /** 业务 path（不含 origin），如 /api/orders */
  path: string;
  /** 完整 URL（若原命令含主机） */
  fullUrl?: string;
  query: SchemaNode[];
  headers: SchemaNode[];
  bodyType: BodyType;
  body: SchemaNode[];
  rawBody?: string;
  warnings: string[];
}

let _idSeq = 0;
const nid = (prefix: string) => `${prefix}_${Date.now()}_${++_idSeq}`;

function leaf(
  name: string,
  type: SchemaType,
  defaultValue?: string | number,
  required = false,
): SchemaNode {
  return {
    id: nid(name || 'f'),
    name,
    type,
    required,
    description: '',
    ...(defaultValue !== undefined && defaultValue !== '' ? { defaultValue } : {}),
  };
}

function inferNode(name: string, value: unknown, depth: number): SchemaNode {
  if (depth > 12) {
    return leaf(name, 'string');
  }
  if (value === null || value === undefined) {
    return leaf(name, 'null');
  }
  if (Array.isArray(value)) {
    const item = value.length > 0 ? value[0] : '';
    return {
      id: nid('arr'),
      name,
      type: 'array',
      required: false,
      description: '',
      children: [inferNode('items', item, depth + 1)],
    };
  }
  if (typeof value === 'object') {
    return {
      id: nid(name || 'obj'),
      name,
      type: 'object',
      required: false,
      description: '',
      children: Object.entries(value as Record<string, unknown>).map(([k, v]) =>
        inferNode(k, v, depth + 1),
      ),
    };
  }
  if (typeof value === 'number') {
    return leaf(name, Number.isInteger(value) ? 'integer' : 'number', value);
  }
  if (typeof value === 'boolean') {
    return leaf(name, 'boolean', String(value));
  }
  return leaf(name, 'string', String(value));
}

/** 示例 JSON → SchemaNode[]（根对象字段平铺；数组/嵌套保留结构） */
export function exampleJsonToSchemaNodes(value: unknown): SchemaNode[] {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value as Record<string, unknown>).map(([k, v]) => inferNode(k, v, 1));
  }
  if (Array.isArray(value)) {
    return [inferNode('root', value, 0)];
  }
  return [inferNode('value', value, 0)];
}

/** 去掉续行反斜杠，统一空白 */
function normalizeCurlText(raw: string): string {
  return raw
    .replace(/\r\n/g, '\n')
    .replace(/\\\n/g, ' ')
    .replace(/\\\r/g, ' ')
    .trim();
}

/**
 * 简易 shell tokenizer：支持单引号 / 双引号 / 无引号；忽略 $'\\n' 等复杂转义。
 */
export function tokenizeShell(input: string): string[] {
  const s = normalizeCurlText(input);
  const tokens: string[] = [];
  let i = 0;
  while (i < s.length) {
    while (i < s.length && /\s/.test(s[i])) i += 1;
    if (i >= s.length) break;
    const ch = s[i];
    if (ch === "'" || ch === '"') {
      const quote = ch;
      i += 1;
      let buf = '';
      while (i < s.length && s[i] !== quote) {
        if (quote === '"' && s[i] === '\\' && i + 1 < s.length) {
          buf += s[i + 1];
          i += 2;
          continue;
        }
        buf += s[i];
        i += 1;
      }
      if (i < s.length && s[i] === quote) i += 1;
      tokens.push(buf);
      continue;
    }
    let buf = '';
    while (i < s.length && !/\s/.test(s[i])) {
      buf += s[i];
      i += 1;
    }
    tokens.push(buf);
  }
  return tokens;
}

function parseHeader(raw: string): { name: string; value: string } | null {
  const idx = raw.indexOf(':');
  if (idx <= 0) return null;
  const name = raw.slice(0, idx).trim();
  const value = raw.slice(idx + 1).trim();
  if (!name) return null;
  return { name, value };
}

function headerValue(headers: Array<{ name: string; value: string }>, name: string): string | undefined {
  const hit = headers.find((h) => h.name.toLowerCase() === name.toLowerCase());
  return hit?.value;
}

function parseUrlParts(urlRaw: string): {
  path: string;
  fullUrl?: string;
  query: Array<{ name: string; value: string }>;
} {
  const trimmed = urlRaw.trim();
  if (!trimmed) {
    return { path: '/', query: [] };
  }
  try {
    const hasScheme = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(trimmed);
    const u = new URL(trimmed, hasScheme ? undefined : 'http://curl.local');
    const query: Array<{ name: string; value: string }> = [];
    u.searchParams.forEach((value, name) => {
      query.push({ name, value });
    });
    return {
      path: u.pathname || '/',
      fullUrl: hasScheme ? trimmed : undefined,
      query,
    };
  } catch {
    const qIdx = trimmed.indexOf('?');
    if (qIdx >= 0) {
      const path = trimmed.slice(0, qIdx) || '/';
      const query: Array<{ name: string; value: string }> = [];
      const sp = new URLSearchParams(trimmed.slice(qIdx + 1));
      sp.forEach((value, name) => query.push({ name, value }));
      return { path: path.startsWith('/') ? path : `/${path}`, query };
    }
    const path = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
    return { path, query: [] };
  }
}

function parseFormBody(body: string): SchemaNode[] {
  const nodes: SchemaNode[] = [];
  const sp = new URLSearchParams(body);
  sp.forEach((value, name) => {
    nodes.push(leaf(name, 'string', value));
  });
  if (nodes.length === 0 && body.includes('=')) {
    for (const part of body.split('&')) {
      if (!part) continue;
      const eq = part.indexOf('=');
      const name = decodeURIComponent(eq >= 0 ? part.slice(0, eq) : part);
      const value = eq >= 0 ? decodeURIComponent(part.slice(eq + 1)) : '';
      if (name) nodes.push(leaf(name, 'string', value));
    }
  }
  return nodes;
}

function takeArg(tokens: string[], i: number): { value: string; next: number } | null {
  if (i + 1 >= tokens.length) return null;
  return { value: tokens[i + 1], next: i + 2 };
}

/**
 * 解析 cURL 文本。失败时 error 非空；成功时返回 draft（可能带 warnings）。
 */
export function parseCurl(raw: string): { draft?: CurlImportDraft; error?: string } {
  const text = normalizeCurlText(raw || '');
  if (!text) {
    return { error: '请粘贴 cURL 命令' };
  }
  const tokens = tokenizeShell(text);
  if (tokens.length === 0) {
    return { error: '未能识别命令内容' };
  }

  const warnings: string[] = [];
  let method = '';
  let urlRaw = '';
  const headerPairs: Array<{ name: string; value: string }> = [];
  const dataParts: string[] = [];
  let forceGet = false;
  let formMode = false;

  let i = 0;
  // 允许前缀 sudo / 环境变量污染时跳过到 curl
  while (i < tokens.length && tokens[i].toLowerCase() !== 'curl') {
    i += 1;
  }
  if (i >= tokens.length) {
    return { error: '未找到 curl 命令（请以 curl 开头）' };
  }
  i += 1;

  while (i < tokens.length) {
    const t = tokens[i];

    if (t === '-X' || t === '--request') {
      const arg = takeArg(tokens, i);
      if (!arg) return { error: `${t} 缺少方法参数` };
      method = arg.value.toUpperCase();
      i = arg.next;
      continue;
    }
    if (t.startsWith('-X') && t.length > 2 && !t.startsWith('--')) {
      method = t.slice(2).toUpperCase();
      i += 1;
      continue;
    }
    if (t === '-H' || t === '--header') {
      const arg = takeArg(tokens, i);
      if (!arg) return { error: `${t} 缺少 Header 值` };
      const h = parseHeader(arg.value);
      if (h) headerPairs.push(h);
      else warnings.push(`无法解析 Header：${arg.value}`);
      i = arg.next;
      continue;
    }
    if (t === '-d' || t === '--data' || t === '--data-raw' || t === '--data-binary' || t === '--data-ascii') {
      const arg = takeArg(tokens, i);
      if (!arg) return { error: `${t} 缺少 Body` };
      dataParts.push(arg.value);
      i = arg.next;
      continue;
    }
    if (t === '--data-urlencode') {
      const arg = takeArg(tokens, i);
      if (!arg) return { error: `${t} 缺少参数` };
      dataParts.push(arg.value.includes('=') ? arg.value : `${arg.value}=`);
      i = arg.next;
      continue;
    }
    if (t === '-F' || t === '--form' || t === '--form-string') {
      formMode = true;
      const arg = takeArg(tokens, i);
      if (!arg) return { error: `${t} 缺少表单字段` };
      if (arg.value.includes('@')) {
        warnings.push(`已跳过文件字段（暂不支持 -F @file）：${arg.value.split('=')[0] || arg.value}`);
      } else {
        dataParts.push(arg.value.includes('=') ? arg.value : `${arg.value}=`);
      }
      i = arg.next;
      continue;
    }
    if (t === '-G' || t === '--get') {
      forceGet = true;
      i += 1;
      continue;
    }
    if (t === '-u' || t === '--user') {
      const arg = takeArg(tokens, i);
      if (arg) {
        warnings.push('已忽略 -u/--user（请手动配置鉴权或 Header）');
        i = arg.next;
      } else {
        i += 1;
      }
      continue;
    }
    if (t === '-A' || t === '--user-agent' || t === '-e' || t === '--referer'
      || t === '-b' || t === '--cookie' || t === '-c' || t === '--cookie-jar'
      || t === '-o' || t === '--output' || t === '-w' || t === '--write-out'
      || t === '--connect-timeout' || t === '--max-time' || t === '-m'
      || t === '--url') {
      const arg = takeArg(tokens, i);
      if (t === '--url' && arg) {
        urlRaw = arg.value;
        i = arg.next;
        continue;
      }
      if (t === '-A' || t === '--user-agent') {
        if (arg) {
          headerPairs.push({ name: 'User-Agent', value: arg.value });
          i = arg.next;
          continue;
        }
      }
      if (t === '-e' || t === '--referer') {
        if (arg) {
          headerPairs.push({ name: 'Referer', value: arg.value });
          i = arg.next;
          continue;
        }
      }
      if (t === '-b' || t === '--cookie') {
        if (arg) {
          headerPairs.push({ name: 'Cookie', value: arg.value });
          i = arg.next;
          continue;
        }
      }
      warnings.push(`已忽略不支持的选项：${t}`);
      i = arg ? arg.next : i + 1;
      continue;
    }
    if (t === '-k' || t === '--insecure' || t === '-s' || t === '--silent'
      || t === '-S' || t === '--show-error' || t === '-L' || t === '--location'
      || t === '-v' || t === '--verbose' || t === '-i' || t === '--include'
      || t === '-N' || t === '--no-buffer' || t === '--compressed'
      || t === '-#' || t === '--progress-bar') {
      i += 1;
      continue;
    }
    if (t.startsWith('-')) {
      warnings.push(`已忽略未知选项：${t}`);
      // 若下一项不像 flag/URL，跳过其参数
      if (i + 1 < tokens.length && !tokens[i + 1].startsWith('-') && !/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(tokens[i + 1]) && !tokens[i + 1].startsWith('/')) {
        i += 2;
      } else {
        i += 1;
      }
      continue;
    }
    if (!urlRaw) {
      urlRaw = t;
    } else {
      warnings.push(`多余参数已忽略：${t}`);
    }
    i += 1;
  }

  if (!urlRaw) {
    return { error: '未解析到请求 URL / 路径' };
  }

  const urlParts = parseUrlParts(urlRaw);
  const queryPairs = [...urlParts.query];

  let bodyText = dataParts.join('&');
  // 多个 -d 用 & 拼接是 curl 默认行为（非 raw JSON 场景）；若像 JSON 则取最后一段
  if (dataParts.length === 1) {
    bodyText = dataParts[0];
  } else if (dataParts.length > 1) {
    const looksJson = dataParts.some((p) => /^\s*[{\[]/.test(p));
    if (looksJson) {
      bodyText = dataParts[dataParts.length - 1];
      warnings.push('检测到多段 Body，已取最后一段用于 JSON 推断');
    } else {
      bodyText = dataParts.join('&');
    }
  }

  if (forceGet && bodyText) {
    const sp = new URLSearchParams(bodyText);
    sp.forEach((value, name) => queryPairs.push({ name, value }));
    bodyText = '';
  }

  if (!method) {
    method = bodyText || formMode ? 'POST' : 'GET';
  }
  if (forceGet) {
    method = 'GET';
  }

  const ct = (headerValue(headerPairs, 'Content-Type') || '').toLowerCase();
  let bodyType: BodyType = 'none';
  let body: SchemaNode[] = [];
  let rawBody: string | undefined;

  if (bodyText) {
    if (formMode || ct.includes('multipart/form-data')) {
      bodyType = 'form-data';
      body = parseFormBody(bodyText.includes('=') ? bodyText : '');
      // -F name=value 已按 & 拼过；再拆一次
      if (body.length === 0) {
        for (const part of dataParts) {
          if (part.includes('@')) continue;
          const eq = part.indexOf('=');
          const name = eq >= 0 ? part.slice(0, eq) : part;
          const value = eq >= 0 ? part.slice(eq + 1) : '';
          if (name) body.push(leaf(name, 'string', value));
        }
      }
    } else if (ct.includes('application/x-www-form-urlencoded')
      || (!ct && !/^\s*[{\[]/.test(bodyText) && bodyText.includes('=') && !bodyText.trim().startsWith('{'))) {
      bodyType = 'x-www-form-urlencoded';
      body = parseFormBody(bodyText);
    } else if (ct.includes('json') || /^\s*[{\[]/.test(bodyText)) {
      bodyType = 'json';
      try {
        const parsed = JSON.parse(bodyText);
        body = exampleJsonToSchemaNodes(parsed);
        rawBody = bodyText;
      } catch {
        bodyType = 'raw';
        rawBody = bodyText;
        warnings.push('Body 非合法 JSON，已按 raw 文本导入');
      }
    } else if (ct.includes('xml')) {
      bodyType = 'xml';
      rawBody = bodyText;
    } else {
      bodyType = 'raw';
      rawBody = bodyText;
    }
  }

  // 跳过会干扰契约的运行时 Header（可选保留 Authorization 等供文档）
  const skipHeader = new Set(['content-length', 'host', 'connection', 'accept-encoding']);
  const headers = headerPairs
    .filter((h) => !skipHeader.has(h.name.toLowerCase()))
    .map((h) => leaf(h.name, 'string', h.value, false));

  // Query 去重（同名保留首次 + 后续）
  const query = queryPairs.map((q) => leaf(q.name, 'string', q.value));

  if (method === 'GET' && bodyType !== 'none') {
    warnings.push('GET 请求通常不含 Body，已保留解析结果供你手动调整');
  }

  return {
    draft: {
      method,
      path: urlParts.path,
      fullUrl: urlParts.fullUrl,
      query,
      headers,
      bodyType,
      body,
      rawBody,
      warnings,
    },
  };
}
