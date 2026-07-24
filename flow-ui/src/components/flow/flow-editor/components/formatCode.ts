// ============================================================================
// formatCode — 按语言格式化编辑器内容（SQL / JSON / JS / Java·Groovy / Python）
// ============================================================================

import { format as formatSql } from 'sql-formatter';

export type FormatLanguage = 'json' | 'sql' | 'javascript' | 'python' | 'java' | 'text';

/**
 * 按语言格式化源码。失败时抛错，由调用方提示。
 */
export function formatCode(source: string, language: FormatLanguage = 'text'): string {
    const code = source ?? '';
    if (!code.trim()) return code;

    switch (language) {
        case 'sql':
            return formatSql(code, {
                language: 'sql',
                tabWidth: 2,
                // 保留 ${var} 占位，避免被拆坏
                paramTypes: {
                    custom: [{ regex: String.raw`\$\{[^}]+\}` }],
                },
            });
        case 'json':
            return JSON.stringify(JSON.parse(code), null, 2);
        case 'javascript':
        case 'java':
            return formatBraceIndent(code, 2);
        case 'python':
            return formatPythonLight(code);
        case 'text':
        default:
            return formatBraceIndent(code, 2);
    }
}

/** 花括号 / 方括号 / 圆括号 缩进（JS / Groovy / Aviator 多行够用） */
function formatBraceIndent(code: string, tabWidth: number): string {
    const indentUnit = ' '.repeat(tabWidth);
    const lines = code.replace(/\r\n/g, '\n').split('\n');
    let depth = 0;
    const out: string[] = [];

    for (const raw of lines) {
        const trimmed = raw.trim();
        if (!trimmed) {
            out.push('');
            continue;
        }

        // 以闭合括号开头时先减缩进
        if (/^[}\]\)]/.test(trimmed)) {
            depth = Math.max(0, depth - 1);
        }

        out.push(indentUnit.repeat(depth) + trimmed);

        const opens = (trimmed.match(/[{\[(]/g) || []).length;
        const closes = (trimmed.match(/[}\])]/g) || []).length;
        depth = Math.max(0, depth + opens - closes);
    }

    return out.join('\n').replace(/\n{3,}/g, '\n\n');
}

/** 轻量 Python：按行尾冒号增减缩进 */
function formatPythonLight(code: string): string {
    const indentUnit = '    ';
    const lines = code.replace(/\r\n/g, '\n').split('\n');
    let depth = 0;
    const out: string[] = [];

    for (const raw of lines) {
        const trimmed = raw.trim();
        if (!trimmed) {
            out.push('');
            continue;
        }

        if (/^(elif|else|except|finally)\b/.test(trimmed)) {
            depth = Math.max(0, depth - 1);
        }

        out.push(indentUnit.repeat(depth) + trimmed);

        if (trimmed.endsWith(':') && !trimmed.startsWith('#')) {
            depth += 1;
        }
    }

    return out.join('\n').replace(/\n{3,}/g, '\n\n');
}
