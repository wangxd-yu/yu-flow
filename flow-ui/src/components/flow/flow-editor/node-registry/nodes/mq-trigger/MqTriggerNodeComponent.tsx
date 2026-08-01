// ============================================================================
// MqTriggerNodeComponent.tsx — MQ 消息触发入口节点
//
// 作为 MQ 任务流程的入口节点，仅有一个右侧 out 端口。
// 视觉风格对齐 Schedule：浅色 Header + 左侧色带 + 右侧对齐信息行。
// 连接 / topic / 消费组在「MQ 任务」资产上配置，此节点只做画布锚点。
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

// 消息队列图标（消息气泡）
const MQ_TRIGGER_ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M464 512a48 48 0 1 0 96 0 48 48 0 1 0-96 0zm200 0a48 48 0 1 0 96 0 48 48 0 1 0-96 0zm-400 0a48 48 0 1 0 96 0 48 48 0 1 0-96 0zm661.2-173.6c-22.6-53.7-55-101.9-96.3-143.3-41.3-41.3-89.5-73.8-143.3-96.3C630.6 75.7 571.6 64 512 64h-2c-60.6.3-119.3 12.3-174.5 35.9-53.3 22.8-101.1 55.2-142 96.5-40.9 41.3-73 89.3-95.2 142.8-23 55.4-34.6 114.3-34.3 174.9.3 69.4 16.9 138.3 47.9 199.9v152c0 25.4 20.6 46 46 46h152.1c61.6 31 130.5 47.6 199.9 47.9h2.1c59.9 0 118-11.6 172.7-34.3 53.5-22.2 101.6-54.2 142.8-95.2 41.3-40.9 73.8-88.7 96.5-142 23.6-55.2 35.6-113.9 35.9-174.5.3-60.9-11.5-120-34.8-175.6zm-151.1 438C704 845.8 611 884 512 884h-1.7c-60.3-.3-120.2-15.3-173.1-43.5l-8.4-4.5H188v-140.8l-4.5-8.4C155.3 633.9 140.3 574 140 513.7c-.4-99.7 37.7-193.3 107.6-263.8 69.8-70.5 163.1-109.5 262.8-109.9h1.7c50 0 98.5 9.7 144.2 28.9 44.6 18.7 84.6 45.6 119 80 34.3 34.3 61.3 74.4 80 119 19.4 46.2 29.1 95.2 28.9 145.8-.6 99.6-39.7 192.9-110.1 262.7z" />
    </svg>
);

// ── 布局常量（与端口定位共享） ──
export const MQ_TRIGGER_LAYOUT = {
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    paddingTop: 10,
    rowHeight: 20,
    rowGap: 2,
    paddingBottom: 10,
    width: 240,
    rowCenterY: (index: number) => NODE_HEADER_WITH_ID_HEIGHT + 10 + index * (20 + 2) + 10,
    get totalHeight() {
        return NODE_HEADER_WITH_ID_HEIGHT + 10 + 3 * 22 + 10;
    },
};

/** Header 右侧 MQ 徽标（只读展示，风格对齐 Schedule 的 Cron 徽章） */
const MqBadge: React.FC = () => (
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
            color: '#0958d9',
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.2,
            lineHeight: 1,
            pointerEvents: 'none',
            userSelect: 'none',
        }}
    >
        {MQ_TRIGGER_ICON}
        MQ
    </div>
);

// ── MqTrigger 节点卡片 ──
export const MqTriggerNodeComponent = ({ node }: { node: Node }) => {
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

    const nodeLabel = data?.__label || 'MQ Trigger';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    // 右侧对齐，与 Schedule 的信息行一致；端口对齐第二行
    const rows = [
        { key: 'message', label: 'message' },
        { key: 'topic', label: 'topic' },
        { key: 'messageId', label: 'messageId' },
    ];

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={themeObj.primary}
            outlineCss={outlineCss}
            backgroundColor={themeObj.bodyBg}
            extraStyle={{ borderRadius: 10, flexDirection: 'row' }}
        >
            {/* 左侧色带（入口节点专属） */}
            <div
                style={{
                    width: 12,
                    height: '100%',
                    background: themeObj.primary,
                    pointerEvents: 'auto',
                    borderRadius: '9px 0 0 9px',
                }}
            />

            {/* 内容区 */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <NodeHeader
                    icon={MQ_TRIGGER_ICON}
                    title={nodeLabel}
                    theme={themeObj}
                    height={MQ_TRIGGER_LAYOUT.headerHeight}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                    extra={<MqBadge />}
                />

                <div
                    style={{
                        paddingTop: MQ_TRIGGER_LAYOUT.paddingTop,
                        paddingBottom: MQ_TRIGGER_LAYOUT.paddingBottom,
                        paddingLeft: 10,
                        paddingRight: 12,
                        display: 'flex',
                        flexDirection: 'column',
                        gap: MQ_TRIGGER_LAYOUT.rowGap,
                        pointerEvents: 'auto',
                    }}
                >
                    {rows.map((row) => (
                        <div
                            key={row.key}
                            style={{
                                height: MQ_TRIGGER_LAYOUT.rowHeight,
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
                                $.mq.
                            </span>
                            <span
                                style={{
                                    fontSize: 11,
                                    color: '#0958d9',
                                    fontWeight: 500,
                                }}
                            >
                                {row.label}
                            </span>
                        </div>
                    ))}
                </div>
            </div>

            <ResizeHandle
                node={node}
                minWidth={MQ_TRIGGER_LAYOUT.width}
                minHeight={MQ_TRIGGER_LAYOUT.totalHeight}
                axes="x"
                color={themeObj.primary}
            />
        </NodeWrapper>
    );
};

export default MqTriggerNodeComponent;
