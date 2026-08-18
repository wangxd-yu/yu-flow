/**
 * 由 sql-{mysql|pg}/flow_*.sql 生成 00_drop_all_flow_tables.sql
 * 用法（在 flow-api 目录）: node scripts/gen-drop-all-flow-tables.js
 */
const fs = require('fs');
const path = require('path');

function gen(dir, dialect) {
  const tables = fs
    .readdirSync(dir)
    .filter((f) => f.startsWith('flow_') && f.endsWith('.sql') && !f.includes('_init'))
    .map((f) => f.replace(/\.sql$/, ''))
    .sort()
    .reverse(); // 逆序降低偶发依赖顺序问题；PG 另用 CASCADE

  // 已改名、Flyway 故意保留的旧表；仅清空重装时删，现网迁移不 DROP
  const legacyDrops = ['flow_datasource'];

  const header =
    '-- Yu Flow 全量删表（验证初始化前清空用）\n' +
    '-- 由 scripts/gen-drop-all-flow-tables.js 生成，勿手工维护表清单\n' +
    '-- 用法：本文件 → 00_all_flow_tables.sql → 00_system_init.sql\n' +
    '-- 警告：删除全部 flow_* 业务表及数据，不可恢复\n\n';

  const dropLine = (t) =>
    dialect === 'pg' ? `DROP TABLE IF EXISTS ${t} CASCADE;` : `DROP TABLE IF EXISTS \`${t}\`;`;
  const body = tables.concat(legacyDrops).map(dropLine).join('\n') + '\n';

  const out = path.join(dir, '00_drop_all_flow_tables.sql');
  fs.writeFileSync(out, header + body, 'utf8');
  console.log('wrote', out, 'tables', tables.length);
}

const root = path.resolve(__dirname, '..');
gen(path.join(root, 'sql-mysql'), 'mysql');
gen(path.join(root, 'sql-pg'), 'pg');
