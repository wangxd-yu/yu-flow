// ============================================================================
// DelayNodeComponent.tsx — 延迟 / 等待节点
// ============================================================================

import React from 'react';
import { InputNumber } from 'antd';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    getNodeTheme,
    NodeHeader,
    NodeWrapper,
    ResizeHandle,
    NodeOutFooter,
    NODE_HEADER_WITH_ID_HEIGHT,
    NODE_FOOTER_HEIGHT,
    NODE_FOOTER_PORT_OFFSET_Y,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';

export const DELAY_COLOR = '#0891b2';

export const DELAY_LAYOUT = {
    width: 180,
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    footerHeight: NODE_FOOTER_HEIGHT,
    get height() {
        return NODE_HEADER_WITH_ID_HEIGHT + 36 + NODE_FOOTER_HEIGHT;
    },
    get inPortY() {
        return NODE_HEADER_WITH_ID_HEIGHT + 18;
    },
    get outPortY() {
        return this.height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    },
};

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372zm30-622h-60v320l248 148 30-50.8-218-129.2V262z" />
    </svg>
);

export const DelayNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const data = (node.getData() as any) || {};
    const theme = getNodeTheme(data?.themeColor || 'cyan');
    const { selected, outlineCss } = useNodeSelection(node);
    const delayMs = typeof data.delayMs === 'number' ? data.delayMs : 1000;

    const setDelayMs = (val: number | null) => {
        node.setData({ ...node.getData(), delayMs: val ?? 0 });
    };

    React.useEffect(() => {
        const w = node.getSize().width || DELAY_LAYOUT.width;
        const ensure = (id: string, group: string, x: number, y: number) => {
            if (!node.hasPort(id)) {
                node.addPort({ id, group, args: { x, y, dx: 0 } });
            } else {
                node.setPortProp(id, 'group', group);
                node.setPortProp(id, 'args', { x, y, dx: 0 });
            }
        };
        ensure('in', 'absolute-in-solid', 0, DELAY_LAYOUT.inPortY);
        ensure('out', 'absolute-out-solid', w, DELAY_LAYOUT.outPortY);
    }, [node]);

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={theme.primary}
            outlineCss={outlineCss}
            backgroundColor={theme.bodyBg}
        >
            <NodeHeader
                icon={ICON}
                title={data.__label || 'Delay'}
                theme={theme}
                height={NODE_HEADER_WITH_ID_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />
            <div
                style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    padding: '0 12px',
                    gap: 8,
                    fontSize: 12,
                    color: '#64748b',
                }}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <span>等待</span>
                <InputNumber
                    size="small"
                    min={0}
                    max={3600000}
                    step={100}
                    value={delayMs}
                    style={{ width: 90 }}
                    onChange={setDelayMs}
                />
                <span>ms</span>
            </div>
            <NodeOutFooter label="out" color={theme.primary} borderColor={theme.headerBorder} />
            <ResizeHandle node={node} axes="x" minWidth={160} minHeight={DELAY_LAYOUT.height} />
        </NodeWrapper>
    );
};
