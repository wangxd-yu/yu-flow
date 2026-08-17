/**
 * 由 sql-{mysql|pg}/flow_*.sql 汇总生成 00_all_flow_tables.sql
 * 用法（在 flow-api 目录）: node scripts/rebuild-00-all-flow-tables.js
 */
const fs = require('fs');
const path = require('path');

function rebuild(dir, dialect) {
  const files = fs
    .readdirSync(dir)
    .filter((f) => f.startsWith('flow_') && f.endsWith('.sql') && !f.includes('_init'))
    .sort((a, b) => a.localeCompare(b));
  const header =
    dialect === 'mysql'
      ? `-- Yu Flow MySQL 全量建表（由 sql-mysql/flow_*.sql 汇总生成，勿手工穿插重复表）\n-- 生成时间: ${new Date().toISOString()}\n-- 用法: 先执行本文件，再执行 00_system_init.sql\n\n`
      : `-- Yu Flow PostgreSQL / 瀚高 全量建表（由 sql-pg/flow_*.sql 汇总生成，勿手工穿插重复表）\n-- 生成时间: ${new Date().toISOString()}\n-- 用法: 空库执行本文件 → 再执行 00_system_init.sql\n-- Boolean 映射列必须用 boolean，勿写成 smallint\n--\n-- 重要：CREATE TABLE IF NOT EXISTS 不会升级已存在的旧表。\n-- 缺列（如 sort_order/cache_config/wall_config）时：DROP flow_* 重跑，或执行 sql/20260812_pg_align_missing_columns.sql\n\n`;
  const body = files
    .map((f) => `-- >>> ${f}\n${fs.readFileSync(path.join(dir, f), 'utf8').trimEnd()}\n`)
    .join('\n');
  const out = path.join(dir, '00_all_flow_tables.sql');
  fs.writeFileSync(out, header + body + '\n', 'utf8');
  console.log('wrote', out, 'tables', files.length);
}

const root = path.resolve(__dirname, '..');
rebuild(path.join(root, 'sql-mysql'), 'mysql');
rebuild(path.join(root, 'sql-pg'), 'pg');
