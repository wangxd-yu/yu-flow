const fs = require('fs');
const path = require('path');

function walk(dir, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, acc);
    else if (e.name.endsWith('DO.java')) acc.push(p);
  }
  return acc;
}

const root = path.join(__dirname, '../src/main/java');
const pat1 =
  /@Column\(columnDefinition = "tinyint\(1\) default \d+"\)\r?\n(\s*private Boolean )/g;
const pat2 =
  /@Column\(name = "([^"]+)", columnDefinition = "tinyint\(1\) default \d+"\)/g;

for (const f of walk(root)) {
  const t = fs.readFileSync(f, 'utf8');
  const n = t
    .replace(pat1, '@Column(nullable = false)\n$1')
    .replace(pat2, '@Column(name = "$1", nullable = false)');
  if (n !== t) {
    fs.writeFileSync(f, n);
    console.log('updated', path.relative(root, f));
  }
}
