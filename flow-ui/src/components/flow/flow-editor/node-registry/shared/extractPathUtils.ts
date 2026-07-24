// ============================================================================
// extractPath 简写：UI 保留 $ / $.field，保存时由 FlowParser 展开为 $.source.port[.field]
// ============================================================================

/**
 * 将绝对路径压回相对简写（仅当路径以「当前入边源」为前缀时）。
 * - `$.cfg.out` → `$`
 * - `$.cfg.out.clientCode` → `$.clientCode`
 * - 其它绝对路径（手写引用非连线源）原样返回
 */
export function relativizeExtractPath(
    path: string | undefined | null,
    sourceNodeId: string,
    sourcePort: string = 'out',
): string {
    const p = (path ?? '').trim();
    if (!p || !sourceNodeId) return p;
    const port = sourcePort || 'out';
    const prefix = `$.${sourceNodeId}.${port}`;
    if (p === prefix) return '$';
    if (p.startsWith(`${prefix}.`)) {
        return `$.${p.slice(prefix.length + 1)}`;
    }
    return p;
}

/** 是否为相对简写（空 / $ / $.field…，且第二段不是「像节点 ID」的完整绝对路径无法在此判定） */
export function isRelativeExtractPath(path: string | undefined | null): boolean {
    const p = (path ?? '').trim();
    return !p || p === '$' || (p.startsWith('$.') && p.split('.').length === 2);
}
