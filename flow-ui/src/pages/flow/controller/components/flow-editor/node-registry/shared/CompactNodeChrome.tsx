// ============================================================================
// CompactNodeChrome — 极简模式统一视觉：宽度、单出口 Footer、多出口标签条、端口 Y
// ============================================================================

import React from 'react';
import { NODE_FOOTER_SAFE_RIGHT } from './useNodeSelection';

/** 极简模式统一内容宽度（中间节点） */
export const COMPACT_NODE_WIDTH = 220;
/** Request 等左侧色带宽度；compact 总宽 = COMPACT_NODE_WIDTH + 此值 */
export const COMPACT_ACCENT_WIDTH = 12;
export const COMPACT_HEADER_HEIGHT = 52;
/** 极简单出口条高度 */
export const COMPACT_FOOTER_HEIGHT = 32;
/** 极简多出口每行高度 */
export const COMPACT_EXIT_ROW = 22;

/** 进入极简前缓存的卡片宽度（transient，不入 DSL） */
export const CARD_WIDTH_DATA_KEY = '__cardWidth';

/** Switch：cases + Default */
export function switchCompactFooterHeight(caseCount: number): number {
    const n = Math.max(1, caseCount) + 1;
    return Math.max(COMPACT_FOOTER_HEIGHT, n * COMPACT_EXIT_ROW + 4);
}

/** If：THEN / ELSE */
export const IF_COMPACT_FOOTER_HEIGHT = COMPACT_EXIT_ROW * 2 + 4;

/** HttpRequest：success / fail */
export const HTTP_COMPACT_FOOTER_HEIGHT = COMPACT_EXIT_ROW * 2 + 4;

/** 多出口 footer 高度（exitCount 行） */
export function multiExitCompactFooterHeight(exitCount: number): number {
    const n = Math.max(1, exitCount);
    return Math.max(COMPACT_FOOTER_HEIGHT, n * COMPACT_EXIT_ROW + 4);
}

/** 单出口端口 Y（footer 垂直居中） */
export function compactSingleOutPortY(nodeHeight: number, footerHeight = COMPACT_FOOTER_HEIGHT): number {
    return nodeHeight - footerHeight / 2;
}

/** 多出口：相对 footer 顶的第 index 行中线 */
export function compactExitPortY(footerTop: number, index: number): number {
    return footerTop + 2 + index * COMPACT_EXIT_ROW + COMPACT_EXIT_ROW / 2;
}

const LABEL_STYLE: React.CSSProperties = {
    fontSize: 11,
    color: '#595959',
    lineHeight: 1,
    userSelect: 'none',
};

export type CompactOutFooterProps = {
    /** 默认 Result；Delay 可传 out */
    label?: string;
    color?: string;
    height?: number;
    borderColor?: string;
    style?: React.CSSProperties;
};

/**
 * 极简单出口 Footer：与 card 的 NodeOutFooter 同源安全区，禁止 absolute right。
 */
export function CompactOutFooter({
    label = 'Result',
    color = '#595959',
    height = COMPACT_FOOTER_HEIGHT,
    borderColor = '#f0f0f0',
    style,
}: CompactOutFooterProps) {
    return (
        <div
            style={{
                height,
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                paddingLeft: 12,
                paddingRight: NODE_FOOTER_SAFE_RIGHT,
                borderTop: `1px solid ${borderColor}`,
                boxSizing: 'border-box',
                pointerEvents: 'none',
                ...style,
            }}
        >
            <span style={{ ...LABEL_STYLE, color }}>{label}</span>
        </div>
    );
}

export type CompactExitLabelsProps = {
    exits: { id: string; label: string; color?: string }[];
    height: number;
};

/**
 * 极简多出口标签条：右对齐 + NODE_FOOTER_SAFE_RIGHT，与 CompactOutFooter 对齐。
 */
export function CompactExitLabels({ exits, height }: CompactExitLabelsProps) {
    const rowH = exits.length > 0 ? (height - 4) / exits.length : COMPACT_EXIT_ROW;
    return (
        <div
            style={{
                height,
                flexShrink: 0,
                borderTop: '1px solid #f0f0f0',
                boxSizing: 'border-box',
                paddingTop: 2,
                paddingBottom: 2,
                paddingLeft: 12,
                paddingRight: NODE_FOOTER_SAFE_RIGHT,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                pointerEvents: 'none',
            }}
        >
            {exits.map((e) => (
                <div
                    key={e.id}
                    style={{
                        height: rowH,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-end',
                        ...LABEL_STYLE,
                        color: e.color || '#595959',
                    }}
                >
                    {e.label}
                </div>
            ))}
        </div>
    );
}
