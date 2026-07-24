// ============================================================================
// ServiceNodeComponent.tsx — 内部服务编排入口节点
// 展示契约入参摘要（__contractInputs）+ 运行时上下文提示
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    NodeHeader,
    NodeWrapper,
    getNodeTheme,
    ResizeHandle,
    NODE_HEADER_WITH_ID_HEIGHT,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';

const SERVICE_ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M880 112H144c-17.7 0-32 14.3-32 32v736c0 17.7 14.3 32 32 32h736c17.7 0 32-14.3 32-32V144c0-17.7-14.3-32-32-32zM513.1 518.1l-192 161c-5.2 4.4-13.1.7-13.1-6.1v-62.7c0-2.3 1.1-4.5 2.9-5.9L465.3 512 310.9 389.6a7.4 7.4 0 0 1-2.9-5.9V321c0-6.8 7.9-10.5 13.1-6.1l192 160.9c3.9 3.3 3.9 9.1 0 12.3zM716 673c0 4.4-3.6 8-8 8H507c-4.4 0-8-3.6-8-8v-48c0-4.4 3.6-8 8-8h201c4.4 0 8 3.6 8 8v48z" />
    </svg>
);

const ROW_H = 20;
const ROW_GAP = 2;

export const SERVICE_LAYOUT = {
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    paddingTop: 10,
    rowHeight: ROW_H,
    rowGap: ROW_GAP,
    paddingBottom: 10,
    width: 260,
    rowCenterY: (index: number) => NODE_HEADER_WITH_ID_HEIGHT + 10 + index * (ROW_H + ROW_GAP) + ROW_H / 2,
    bodyHeight: (rowCount: number) => 10 + Math.max(rowCount, 2) * (ROW_H + ROW_GAP) + 10,
    get totalHeight() {
        return NODE_HEADER_WITH_ID_HEIGHT + this.bodyHeight(2);
    },
};

const ServiceBadge: React.FC = () => (
    <div
        style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            height: 22,
            padding: '0 8px',
            borderRadius: 11,
            border: '1px solid #91caff',
            background: '#ffffff',
            color: '#1677ff',
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.2,
            lineHeight: 1,
            pointerEvents: 'none',
            userSelect: 'none',
        }}
    >
        Service
    </div>
);

export const ServiceNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'blue');
    const { outlineCss, selected } = useNodeSelection(node, {
        defaultColor: themeObj.headerBorder,
        selectedColor: themeObj.primary,
        borderRadius: 10,
    });

    React.useEffect(() => {
        const onDataChange = () => setData({ ...node.getData() });
        node.on('change:data', onDataChange);
        return () => { node.off('change:data', onDataChange); };
    }, [node]);

    const nodeLabel = data?.__label || 'Service';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    const contractInputs: string[] = Array.isArray(data?.__contractInputs)
        ? data.__contractInputs
        : [];

    // 有契约则展示入参摘要（与「服务契约」Tab 同步）；否则展示运行时上下文提示
    const rows = contractInputs.length > 0
        ? contractInputs.map((name) => ({
            key: name,
            prefix: '$.service.input.',
            label: String(name).replace(/\*$/, ''),
            required: String(name).endsWith('*'),
            accent: true,
        }))
        : [
            { key: 'input', prefix: '$.service.', label: 'input', required: false, accent: false },
            { key: 'triggerTime', prefix: '$.service.', label: 'triggerTime', required: false, accent: false },
        ];

    const minH = SERVICE_LAYOUT.headerHeight + SERVICE_LAYOUT.bodyHeight(rows.length);

    React.useEffect(() => {
        const s = node.getSize();
        const w = Math.max(s.width || SERVICE_LAYOUT.width, SERVICE_LAYOUT.width);
        if (Math.abs(s.height - minH) > 1 || Math.abs(s.width - w) > 1) {
            node.resize(w, minH);
        }
        if (node.hasPort('out')) {
            node.setPortProp('out', 'args', {
                x: w,
                y: SERVICE_LAYOUT.rowCenterY(Math.max(rows.length - 1, 0)),
                dx: 0,
            });
        }
    }, [node, minH, rows.length]);

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={themeObj.primary}
            outlineCss={outlineCss}
            backgroundColor={themeObj.bodyBg}
            extraStyle={{ borderRadius: 10, flexDirection: 'row' }}
        >
            <div
                style={{
                    width: 12,
                    height: '100%',
                    background: themeObj.primary,
                    pointerEvents: 'auto',
                    borderRadius: '9px 0 0 9px',
                }}
            />

            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <NodeHeader
                    icon={SERVICE_ICON}
                    title={nodeLabel}
                    theme={themeObj}
                    height={SERVICE_LAYOUT.headerHeight}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                    extra={<ServiceBadge />}
                />

                <div
                    style={{
                        paddingTop: SERVICE_LAYOUT.paddingTop,
                        paddingBottom: SERVICE_LAYOUT.paddingBottom,
                        paddingLeft: 10,
                        paddingRight: 12,
                        display: 'flex',
                        flexDirection: 'column',
                        gap: SERVICE_LAYOUT.rowGap,
                        pointerEvents: 'auto',
                    }}
                >
                    {rows.map((row) => (
                        <div
                            key={row.key}
                            style={{
                                height: SERVICE_LAYOUT.rowHeight,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'flex-end',
                                gap: 6,
                            }}
                        >
                            <span
                                style={{
                                    fontSize: 10,
                                    color: '#8c8c8c',
                                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                }}
                            >
                                {row.prefix}
                            </span>
                            <span
                                style={{
                                    fontSize: 11,
                                    color: '#0958d9',
                                    fontWeight: 500,
                                }}
                                title={
                                    row.accent
                                        ? `契约入参 → $.service.input.${row.label}`
                                        : undefined
                                }
                            >
                                {row.label}
                                {row.required ? (
                                    <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>
                                ) : null}
                            </span>
                        </div>
                    ))}
                </div>
            </div>

            <ResizeHandle
                node={node}
                minWidth={SERVICE_LAYOUT.width}
                minHeight={minH}
                axes="x"
                color={themeObj.primary}
            />
        </NodeWrapper>
    );
};

export default ServiceNodeComponent;
