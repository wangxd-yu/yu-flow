// ============================================================================
// DynamicVariableList.tsx — 通用动态变量列表 UI 组件
// 配合 useNodeVariables Hook 使用，负责渲染可排序的变量行
// 放置于 shared/ 目录，供所有节点组件复用
// ============================================================================

import React from 'react';
import { Space, Typography, Input } from 'antd';
import type { NodeVariable } from './useNodeVariables';

const { Text } = Typography;

const ICONS = {
    drag: (<svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor"><path d="M384 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m384-512a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z" /></svg>),
    path: (<svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 824c-207.3 0-376-168.7-376-376s168.7-376 376-376 376 168.7 376 376-168.7 376-376 376z m166-401H547V321c0-19.3-15.7-35-35-35s-35 15.7-35 35v166H311c-19.3 0-35 15.7-35 35s15.7 35 35 35h166v166c0 19.3 15.7 35 35 35s35-15.7 35-35V522h131c19.3 0 35-15.7 35-35s-15.7-35-35-35z" /></svg>),
};

export interface DragState {
    index: number;
    startY: number;
    currentY: number;
}

export interface DynamicVariableListProps {
    variables: NodeVariable[];
    rowHeight: number;
    dragState: DragState | null;
    hoverRowIndex: number | null;
    onHoverChange: (index: number | null) => void;
    onDragStart: (index: number) => (e: React.MouseEvent) => void;
    onAddVar: (e?: React.MouseEvent) => void;
    onUpdateVar: (id: string, patch: Partial<NodeVariable>) => void;
    onRemoveVar: (index: number) => void;
}

export function DynamicVariableList(props: DynamicVariableListProps) {
    const {
        variables,
        rowHeight,
        dragState,
        hoverRowIndex,
        onHoverChange,
        onDragStart,
        onAddVar,
        onUpdateVar,
        onRemoveVar,
    } = props;

    return (
        <div style={{ padding: '4px 0', pointerEvents: 'auto', position: 'relative', flexShrink: 0 }}>
            {variables.map((v, idx) => {
                const isPlaceholder = idx === variables.length - 1;
                const isDragging = dragState?.index === idx;
                const isHovering = hoverRowIndex === idx;

                let transform = 'translateY(0)';
                let zIndex = 1;

                if (isDragging && dragState) {
                    transform = `translateY(${dragState.currentY - dragState.startY}px)`;
                    zIndex = 100;
                }

                if (isPlaceholder) {
                    return (
                        <div
                            key={v.id}
                            onClick={(e) => { e.stopPropagation(); onAddVar(e); }}
                            style={{
                                height: rowHeight,
                                display: 'flex',
                                alignItems: 'center',
                                padding: '0 4px 0 16px',
                                gap: 6,
                                cursor: 'pointer',
                            }}
                        >
                            <Space size={8}>
                                <Text style={{ fontSize: 12, color: '#8c8c8c' }}>输入变量</Text>
                                <div style={{ fontSize: 18, color: '#1677ff', display: 'flex', alignItems: 'center', width: 20, height: 20, justifyContent: 'center', lineHeight: 1 }}>
                                    +
                                </div>
                            </Space>
                        </div>
                    );
                }

                return (
                    <div
                        key={v.id}
                        onMouseEnter={() => !dragState && onHoverChange(idx)}
                        onMouseLeave={() => !dragState && onHoverChange(null)}
                        style={{
                            height: rowHeight,
                            display: 'flex',
                            alignItems: 'center',
                            padding: '0 4px 0 16px',
                            gap: 6,
                            transform,
                            zIndex,
                            position: 'relative',
                            backgroundColor: isDragging ? '#e6f4ff' : isHovering ? '#f5f5f5' : 'transparent',
                            transition: isDragging ? 'none' : 'background-color 0.2s',
                            borderRadius: 4,
                        }}
                    >
                        <div
                            onMouseDown={onDragStart(idx)}
                            style={{
                                color: isDragging ? '#1677ff' : '#bfbfbf',
                                display: 'flex',
                                cursor: isDragging ? 'grabbing' : 'grab',
                                padding: '4px',
                                transition: 'color 0.2s',
                            }}
                        >
                            {ICONS.drag}
                        </div>
                        <Input
                            size="small"
                            variant="filled"
                            value={v.name}
                            onChange={(e) => onUpdateVar(v.id, { name: e.target.value })}
                            onMouseDown={(e) => e.stopPropagation()}
                            style={{ flex: 1, fontSize: 12, height: 32, backgroundColor: '#f5f5f5', border: 'none' }}
                        />
                        <Input
                            size="small"
                            prefix={<div style={{ color: '#bfbfbf', display: 'flex' }}>{ICONS.path}</div>}
                            placeholder="$"
                            value={v.extractPath}
                            onChange={(e) => onUpdateVar(v.id, { extractPath: e.target.value })}
                            onMouseDown={(e) => e.stopPropagation()}
                            style={{ flex: 1.5, fontSize: 12, height: 32 }}
                        />
                        {isHovering && !dragState && (
                            <div
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onRemoveVar(idx);
                                }}
                                style={{ color: '#ff4d4f', cursor: 'pointer', padding: '0 4px', fontSize: 14, display: 'flex' }}
                            >
                                ✕
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
}
