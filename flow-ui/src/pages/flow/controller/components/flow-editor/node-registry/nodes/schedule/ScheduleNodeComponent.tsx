// ============================================================================
// ScheduleNodeComponent.tsx — Schedule 定时调度节点
//
// 作为任务管理流程的入口节点，仅有一个右侧 out 端口。
// 视觉风格对齐 Request：浅色 Header + 色带 + 右侧对齐信息行。
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    NodeHeader,
    NodeWrapper,
    getNodeTheme,
} from '../../shared/useNodeSelection';

// 时钟图标
const SCHEDULE_ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372zm32-580v244.7l172.2 102.3c12.7 7.5 15.6 24.2 6.5 35.6-6.4 8-16.8 11.2-26.5 8.2l-5.5-2.4-192-114c-8.8-5.2-14.2-14.8-14.2-25.2V304c0-15.5 12.5-28 28-28s28 12.5 28 28z" />
    </svg>
);

// ── 布局常量（与端口定位共享） ──
export const SCHEDULE_LAYOUT = {
    headerHeight: 40,
    paddingTop: 10,
    rowHeight: 20,
    rowGap: 2,
    paddingBottom: 10,
    width: 240,
    rowCenterY: (index: number) => 40 + 10 + index * (20 + 2) + 10,
    // Header + 2 行上下文变量
    get totalHeight() {
        return 40 + 10 + 2 * 22 + 10;
    },
};

/** Header 右侧 Cron 徽标（只读展示，风格对齐 Request 的 Method 徽章） */
const CronBadge: React.FC = () => (
    <div
        style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            height: 22,
            padding: '0 8px',
            borderRadius: 11,
            border: '1px solid #d3adf7',
            background: '#ffffff',
            color: '#722ed1',
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.2,
            lineHeight: 1,
            pointerEvents: 'none',
            userSelect: 'none',
        }}
    >
        <svg viewBox="0 0 1024 1024" width="11" height="11" fill="currentColor">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z" />
            <path d="M544 304c0-15.5-12.5-28-28-28s-28 12.5-28 28v244.7c0 10.4 5.4 20 14.2 25.2l192 114c13.5 8 31.1 3.6 39.1-9.9 5.7-9.5 3.9-21.8-4.4-29.1L544 532.4V304z" />
        </svg>
        Cron
    </div>
);

// ── Schedule 节点卡片 ──
export const ScheduleNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'purple');
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

    const nodeLabel = data?.__label || 'Schedule';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    // 右侧对齐，与 Request 的 Headers/Params 行一致；端口对齐第二行
    const rows = [
        { key: 'taskName', label: 'taskName' },
        { key: 'triggerTime', label: 'triggerTime' },
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
            {/* 左侧色带 */}
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
                    icon={SCHEDULE_ICON}
                    title={nodeLabel}
                    theme={themeObj}
                    height={SCHEDULE_LAYOUT.headerHeight}
                    onTitleChange={handleTitleChange}
                    extra={<CronBadge />}
                />

                <div
                    style={{
                        paddingTop: SCHEDULE_LAYOUT.paddingTop,
                        paddingBottom: SCHEDULE_LAYOUT.paddingBottom,
                        paddingLeft: 10,
                        paddingRight: 12,
                        display: 'flex',
                        flexDirection: 'column',
                        gap: SCHEDULE_LAYOUT.rowGap,
                        pointerEvents: 'auto',
                    }}
                >
                    {rows.map((row) => (
                        <div
                            key={row.key}
                            style={{
                                height: SCHEDULE_LAYOUT.rowHeight,
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
                                $.schedule.
                            </span>
                            <span
                                style={{
                                    fontSize: 11,
                                    color: '#531dab',
                                    fontWeight: 500,
                                }}
                            >
                                {row.label}
                            </span>
                        </div>
                    ))}
                </div>
            </div>
        </NodeWrapper>
    );
};

export default ScheduleNodeComponent;
