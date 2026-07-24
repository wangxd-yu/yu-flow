/**
 * Amis Schema 静态消毒：降低存储型 XSS / 危险自定义组件风险。
 * 无法替代权限控制；设计者仍视为高权限角色。
 */

const STRIP_KEYS = new Set(['script', 'jsEngine', 'jsFunction', 'srcdoc']);

function stripDangerousHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*(['"]).*?\1/gi, '')
    .replace(/\son\w+\s*=\s*[^\s>]+/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/data:text\/html/gi, '');
}

function sanitizeTpl(tpl: string): string {
  return stripDangerousHtml(tpl)
    .replace(/\$\{[\s\S]*?\}/g, (m) => {
      // 保留简单变量插值，去掉明显脚本片段
      if (/javascript:|<\/?\s*script/i.test(m)) {
        return '';
      }
      return m;
    });
}

function isDangerousIframeSrc(src: string): boolean {
  const s = src.trim();
  if (!s) return true;
  if (/^(javascript|data|vbscript):/i.test(s)) return true;
  if (s.startsWith('//')) return true; // protocol-relative → 外域风险
  return false;
}

function sanitizeNode(node: any): any {
  if (node == null) return node;
  if (Array.isArray(node)) {
    return node.map(sanitizeNode);
  }
  if (typeof node !== 'object') {
    return node;
  }

  const type = typeof node.type === 'string' ? node.type.toLowerCase() : '';
  if (type === 'custom') {
    return { type: 'tpl', tpl: '[已拦截不安全组件: custom]' };
  }

  const out: Record<string, any> = {};
  for (const [key, value] of Object.entries(node)) {
    if (STRIP_KEYS.has(key)) {
      continue;
    }
    if (key === 'actionType' && typeof value === 'string' && value.toLowerCase() === 'custom') {
      out[key] = 'toast';
      out.msgType = 'warning';
      out.msg = '已拦截自定义脚本动作';
      continue;
    }
    if ((key === 'html' || key === 'richText') && typeof value === 'string') {
      out[key] = stripDangerousHtml(value);
      continue;
    }
    if (key === 'tpl' && typeof value === 'string') {
      out[key] = sanitizeTpl(value);
      continue;
    }
    if (type === 'iframe' && key === 'src' && typeof value === 'string') {
      if (isDangerousIframeSrc(value)) {
        continue;
      }
      const s = value.trim();
      if (/^https?:\/\//i.test(s)) {
        try {
          const u = new URL(s);
          if (typeof window !== 'undefined' && u.origin !== window.location.origin) {
            out[key] = 'about:blank';
            continue;
          }
        } catch {
          continue;
        }
      }
    }
    out[key] = sanitizeNode(value);
  }

  if (type === 'html' && typeof out.html === 'string') {
    out.html = stripDangerousHtml(out.html);
  }
  if (type === 'tpl' && typeof out.tpl === 'string') {
    out.tpl = sanitizeTpl(out.tpl);
  }
  if (type === 'iframe' && out.srcdoc != null) {
    delete out.srcdoc;
  }

  return out;
}

export function sanitizeAmisSchema(schema: any): any {
  try {
    return sanitizeNode(schema);
  } catch {
    return { type: 'tpl', tpl: '[Schema 消毒失败]' };
  }
}
