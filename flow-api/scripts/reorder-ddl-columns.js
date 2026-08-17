/**
 * 统一 sql-mysql / sql-pg 单表 CREATE TABLE 列顺序：
 *   id → code(若有) → 业务列 → deleted → create_by → create_time → update_by → update_time
 * PG 的 COMMENT ON COLUMN 顺序同步调整。
 *
 * 用法（在 flow-api 目录）: node scripts/reorder-ddl-columns.js
 */
const fs = require('fs');
const path = require('path');

const TRAIL_ORDER = [
  'deleted',
  'create_by',
  'create_time',
  'update_by',
  'update_time',
];

function stripTicks(name) {
  return String(name || '')
    .replace(/^`|`$/g, '')
    .replace(/^"|"$/g, '');
}

function colNameOf(def) {
  // 注意：反引号名后不能用 \b（` 与空白均为非单词字符，会导致 MySQL 列名解析失败）
  const m = def.trim().match(/^(`[^`]+`|"[^"]+"|[a-zA-Z_][a-zA-Z0-9_]*)(?:\s|$)/);
  return m ? stripTicks(m[1]) : null;
}

function isConstraintLine(def) {
  const t = def.trim().toUpperCase();
  return (
    t.startsWith('PRIMARY KEY') ||
    t.startsWith('UNIQUE KEY') ||
    t.startsWith('UNIQUE ') ||
    t.startsWith('KEY ') ||
    t.startsWith('INDEX ') ||
    t.startsWith('CONSTRAINT ') ||
    t.startsWith('CHECK ') ||
    t.startsWith('FOREIGN KEY')
  );
}

/** 拆分 CREATE TABLE (...) 内顶层逗号分隔项（忽略括号/引号内逗号） */
function splitTopLevel(body) {
  const parts = [];
  let cur = '';
  let depth = 0;
  let quote = null;
  for (let i = 0; i < body.length; i++) {
    const ch = body[i];
    const prev = i > 0 ? body[i - 1] : '';
    if (quote) {
      cur += ch;
      if ((ch === quote && prev !== '\\') || (quote === '`' && ch === '`')) {
        if (quote === '`' && body[i + 1] === '`') {
          cur += body[++i];
          continue;
        }
        quote = null;
      }
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') {
      quote = ch;
      cur += ch;
      continue;
    }
    if (ch === '(') {
      depth++;
      cur += ch;
      continue;
    }
    if (ch === ')') {
      depth--;
      cur += ch;
      continue;
    }
    if (ch === ',' && depth === 0) {
      parts.push(cur.trim());
      cur = '';
      continue;
    }
    cur += ch;
  }
  if (cur.trim()) parts.push(cur.trim());
  return parts;
}

function bucketOf(name) {
  const n = stripTicks(name);
  if (n === 'id') return 0;
  if (n === 'code') return 1;
  const trail = TRAIL_ORDER.indexOf(n);
  if (trail >= 0) return 100 + trail; // deleted … update_time
  return 2; // 业务列
}

function reorderParts(parts) {
  const cols = [];
  const constraints = [];
  for (const p of parts) {
    if (isConstraintLine(p)) constraints.push(p);
    else cols.push(p);
  }
  // stable：同桶保持原相对顺序；尾部审计列按 TRAIL_ORDER
  const decorated = cols.map((p, i) => ({ p, i, name: colNameOf(p) || `_${i}` }));
  decorated.sort((a, b) => {
    const ba = bucketOf(a.name);
    const bb = bucketOf(b.name);
    if (ba !== bb) return ba - bb;
    return a.i - b.i;
  });
  return [...decorated.map((d) => d.p), ...constraints];
}

function detectIndent(body) {
  const m = body.match(/\n([ \t]+)\S/);
  return m ? m[1] : '  ';
}

function reorderCreateTable(sql) {
  // 兼容 PG `);\n` 与 MySQL `) ENGINE=InnoDB ...;`
  const re =
    /(CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"]?[\w.]+[`"]?\s*\()([\s\S]*?)(\)\s*(?:ENGINE\b[\s\S]*?)?;)/i;
  const m = sql.match(re);
  if (!m) return { sql, changed: false, names: [] };

  const open = m[1];
  const body = m[2];
  const close = m[3];
  const parts = splitTopLevel(body);
  const beforeNames = parts.filter((p) => !isConstraintLine(p)).map(colNameOf);
  const reordered = reorderParts(parts);
  const afterNames = reordered.filter((p) => !isConstraintLine(p)).map(colNameOf);
  const indent = detectIndent(body);
  const newBody =
    '\n' + reordered.map((p) => indent + p.replace(/^\s+/, '')).join(',\n') + '\n';
  // close 统一为 ")...;"；去掉历史匹配里多余前导空白/空行
  const closeNorm = close.replace(/^\s*/, '');
  const next = sql.slice(0, m.index) + open + newBody + closeNorm + sql.slice(m.index + m[0].length);
  const orderSame =
    beforeNames.length === afterNames.length &&
    beforeNames.every((n, i) => n === afterNames[i]);
  const changed = !orderSame || next !== sql;
  return { sql: next, changed, names: afterNames };
}

/** 按列名顺序重排 COMMENT ON COLUMN（PG） */
function reorderComments(sql, colOrder) {
  if (!colOrder.length) return sql;
  const commentRe =
    /COMMENT\s+ON\s+COLUMN\s+([\w.]+)\.("[^"]+"|[a-zA-Z_][\w]*)\s+IS\s+(?:'[^']*'|[^;])+;/gi;
  const comments = [];
  let m;
  while ((m = commentRe.exec(sql)) !== null) {
    comments.push({
      full: m[0].trim(),
      table: m[1],
      col: stripTicks(m[2]),
      index: m.index,
      end: m.index + m[0].length,
    });
  }
  if (comments.length < 2) return sql;

  const byTable = new Map();
  for (const c of comments) {
    if (!byTable.has(c.table)) byTable.set(c.table, []);
    byTable.get(c.table).push(c);
  }

  // 从后往前替换，避免 index 漂移
  const tables = [...byTable.keys()].reverse();
  let out = sql;
  for (const table of tables) {
    const list = byTable.get(table);
    if (list.length < 2) continue;
    list.sort((a, b) => a.index - b.index);
    const start = list[0].index;
    const end = list[list.length - 1].end;
    const orderIndex = new Map(colOrder.map((n, i) => [n, i]));
    const sorted = [...list].sort((a, b) => {
      const ia = orderIndex.has(a.col) ? orderIndex.get(a.col) : 9999;
      const ib = orderIndex.has(b.col) ? orderIndex.get(b.col) : 9999;
      if (ia !== ib) return ia - ib;
      return a.index - b.index;
    });
    const newBlock = sorted.map((c) => c.full).join('\n');
    out = out.slice(0, start) + newBlock + out.slice(end);
  }
  return out;
}

function processFile(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  const { sql: afterCreate, changed, names } = reorderCreateTable(raw);
  let next = afterCreate;
  if (filePath.includes(`${path.sep}sql-pg${path.sep}`) || filePath.includes('/sql-pg/')) {
    next = reorderComments(next, names);
  }
  if (next !== raw) {
    fs.writeFileSync(filePath, next.endsWith('\n') ? next : next + '\n', 'utf8');
    return { file: path.basename(filePath), changed: true, names };
  }
  return { file: path.basename(filePath), changed: false, names };
}

function processDir(dir) {
  const files = fs
    .readdirSync(dir)
    .filter((f) => f.startsWith('flow_') && f.endsWith('.sql') && !f.includes('_init'))
    .sort();
  const results = [];
  for (const f of files) {
    results.push(processFile(path.join(dir, f)));
  }
  return results;
}

const root = path.resolve(__dirname, '..');
const mysql = processDir(path.join(root, 'sql-mysql'));
const pg = processDir(path.join(root, 'sql-pg'));
const changed = [...mysql, ...pg].filter((r) => r.changed);
console.log('mysql changed', mysql.filter((r) => r.changed).map((r) => r.file).join(', ') || '(none)');
console.log('pg changed', pg.filter((r) => r.changed).map((r) => r.file).join(', ') || '(none)');
console.log('total changed', changed.length);
