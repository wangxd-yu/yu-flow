/* eslint-disable no-console */
/**
 * ESLint 存量错误棘轮：按规则统计 error 数，与 lint-baseline.json 比较，任何规则只许减少、不许增加。
 *
 *   pnpm lint            检查（CI 使用）
 *   pnpm lint:baseline   清理存量后更新基线（数字下降时运行并提交）
 */
const fs = require('fs');
const path = require('path');
const { ESLint } = require('eslint');

const ROOT = path.join(__dirname, '..');
const BASELINE_FILE = path.join(ROOT, 'lint-baseline.json');
const PATTERNS = ['src/**/*.{ts,tsx}'];

function countByRule(results) {
  const byRule = {};
  const filesByRule = {};
  for (const result of results) {
    for (const msg of result.messages) {
      if (msg.severity !== 2) continue;
      const rule = msg.ruleId || 'parse-error';
      byRule[rule] = (byRule[rule] || 0) + 1;
      (filesByRule[rule] = filesByRule[rule] || new Set()).add(
        `${path.relative(ROOT, result.filePath)}:${msg.line}`,
      );
    }
  }
  return { byRule, filesByRule };
}

(async () => {
  const eslint = new ESLint({ cwd: ROOT });
  const results = await eslint.lintFiles(PATTERNS);
  const { byRule, filesByRule } = countByRule(results);
  const total = Object.values(byRule).reduce((a, b) => a + b, 0);

  if (process.argv.includes('--update')) {
    const sorted = Object.fromEntries(Object.entries(byRule).sort(([a], [b]) => a.localeCompare(b)));
    fs.writeFileSync(BASELINE_FILE, `${JSON.stringify({ total, rules: sorted }, null, 2)}\n`);
    console.log(`已更新 lint 基线：${total} 个错误`);
    return;
  }

  const baseline = fs.existsSync(BASELINE_FILE)
    ? JSON.parse(fs.readFileSync(BASELINE_FILE, 'utf8'))
    : { total: 0, rules: {} };
  const grown = Object.entries(byRule).filter(([rule, n]) => n > (baseline.rules[rule] || 0));

  console.log(`ESLint 错误：${total}（基线 ${baseline.total}）`);
  if (grown.length > 0) {
    console.error('\n以下规则的错误数超过基线，请修复新增问题：');
    for (const [rule, n] of grown) {
      console.error(`\n  ${rule}: ${n}（基线 ${baseline.rules[rule] || 0}）`);
      [...filesByRule[rule]].slice(0, 30).forEach((loc) => console.error(`    ${loc}`));
    }
    process.exit(1);
  }
  if (total < baseline.total) {
    console.log('错误数比基线少，运行 pnpm lint:baseline 更新基线并提交，防止回退。');
  }
})().catch((e) => {
  console.error(e);
  process.exit(2);
});
