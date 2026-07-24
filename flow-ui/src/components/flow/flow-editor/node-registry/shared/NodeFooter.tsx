// ============================================================================
// NodeFooter.tsx — 节点底部统一封装
//
// 两类底部：
//   1. 单出口 Result Footer（计算 / 数据库 / 模板 / API 等需要输出结果）
//      → 使用 NODE_FOOTER_HEIGHT + NodeResultFooter / NodeOutFooter
//   2. 无底部（开始 Request / 结束 Response / Schedule 等）
//      → NODE_FOOTER_NONE，不渲染 Footer，出口端口自行定位
//
// 多出口（If / HTTP / Switch / ForEach）仍用各自的 exit 行高，
// 极简模式走 CompactExitLabels；不要混用本文件的单出口高度。
// ============================================================================

import React from 'react';
import {
    NodeOutFooter,
    NODE_FOOTER_HEIGHT,
    NODE_FOOTER_PORT_OFFSET_Y,
    NODE_FOOTER_SAFE_RIGHT,
} from './useNodeSelection';
import {
    COMPACT_FOOTER_HEIGHT,
    CompactOutFooter,
    compactSingleOutPortY,
} from './CompactNodeChrome';

/** 单出口 Footer 标准高度（card 模式）— 所有「需要底部」的节点必须用这个 */
export { NODE_FOOTER_HEIGHT, NODE_FOOTER_PORT_OFFSET_Y, NODE_FOOTER_SAFE_RIGHT };

/** 极简单出口 Footer 高度 */
export { COMPACT_FOOTER_HEIGHT };

/**
 * 无底部：开始 / 结束 / 触发类节点。
 * height=0 表示不渲染 Footer 条；出口端口由节点自己算（通常贴侧边中部）。
 */
export const NODE_FOOTER_NONE = 0;

/** 当前模式下的单出口 Footer 高度 */
export function singleOutFooterHeight(isCompact: boolean): number {
    return isCompact ? COMPACT_FOOTER_HEIGHT : NODE_FOOTER_HEIGHT;
}

/**
 * 单出口端口 Y（相对节点顶边）。
 * card:  footer 顶 + NODE_FOOTER_PORT_OFFSET_Y
 * compact: footer 垂直中心
 */
export function singleOutPortY(nodeHeight: number, isCompact = false): number {
    if (isCompact) return compactSingleOutPortY(nodeHeight);
    return nodeHeight - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
}

export type NodeResultFooterProps = {
    /** 出口文案，默认 Result */
    label?: string;
    /** 是否极简模式 */
    isCompact?: boolean;
    color?: string;
    borderColor?: string;
    /** 仅 card 模式可用：文案左侧附加内容 */
    children?: React.ReactNode;
};

/**
 * 单出口底部条（统一入口）。
 * 需要输出结果的节点：card → NodeOutFooter，compact → CompactOutFooter。
 * 高度固定，禁止各节点再传自定义 height。
 */
export const NodeResultFooter: React.FC<NodeResultFooterProps> = ({
    label = 'Result',
    isCompact = false,
    color,
    borderColor,
    children,
}) => {
    if (isCompact) {
        return <CompactOutFooter label={label} color={color} borderColor={borderColor} />;
    }
    return (
        <NodeOutFooter label={label} color={color} borderColor={borderColor} height={NODE_FOOTER_HEIGHT}>
            {children}
        </NodeOutFooter>
    );
};

export { NodeOutFooter, CompactOutFooter };
