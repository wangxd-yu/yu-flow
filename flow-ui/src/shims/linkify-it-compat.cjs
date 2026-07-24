/**
 * linkify-it@5+ 改为命名导出，markdown-it@14 仍 `import LinkifyIt from 'linkify-it'`。
 * 真实入口经 umirc alias `@yu-flow/linkify-it-real` 指向磁盘绝对路径，避开 exports 子路径限制与别名循环。
 */
const mod = require('@yu-flow/linkify-it-real');
const LinkifyIt = mod.LinkifyIt || mod;

module.exports = LinkifyIt;
module.exports.default = LinkifyIt;
module.exports.LinkifyIt = LinkifyIt;
if (mod.REBuilder) module.exports.REBuilder = mod.REBuilder;
if (mod.linkifyit) module.exports.linkifyit = mod.linkifyit;
