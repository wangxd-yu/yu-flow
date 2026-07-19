// ============================================================================
// switchCases.ts — Switch 分支数据规范化
// cases: [{ id, name, value }]
// 端口：case_<id>（稳定，改名/改匹配值不换口）
// ============================================================================

import { createId } from '../../../utils/id';

export type SwitchCaseItem = {
    id: string;
    /** 画布可编辑展示名 */
    name: string;
    /** 与表达式结果匹配的值 */
    value: string;
};

export function casePortId(id: string): string {
    return `case_${id}`;
}

/** 仅接受对象项；跳过 string 等非对象 */
export function normalizeCases(raw: unknown): SwitchCaseItem[] {
    if (!Array.isArray(raw)) return [];
    return raw
        .filter((item): item is Record<string, unknown> => !!item && typeof item === 'object')
        .map((obj, i) => ({
            id: typeof obj.id === 'string' && obj.id ? obj.id : createId('c'),
            name: typeof obj.name === 'string' && obj.name.trim()
                ? obj.name
                : `Case ${i + 1}`,
            value: typeof obj.value === 'string'
                ? obj.value
                : typeof obj.match === 'string'
                    ? obj.match
                    : '',
        }));
}

export function createEmptyCase(index: number): SwitchCaseItem {
    return {
        id: createId('c'),
        name: `Case ${index + 1}`,
        value: '',
    };
}
