// ============================================================================
// DynamicVariableList.tsx — 通用动态变量列表 UI 组件
// 配合 useNodeVariables Hook 使用，负责渲染可排序的变量行
// 放置于 shared/ 目录，供所有节点组件复用
//
// 本文件导出三个复用单元：
//   - DraggableRowList  : 通用「可拖拽排序行 + 底部新增行 + 行内删除」骨架（泛型）
//   - RowDeleteButton   : 行内删除按钮
//   - DynamicVariableList: 变量列表（name + extractPath），基于 DraggableRowList
// Record 等自定义节点可直接复用 DraggableRowList，仅通过 renderRow 注入各自的值编辑器。
// ============================================================================

import React from 'react';
import { Typography, Input } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { NodeVariable } from './useNodeVariables';
import { VariableValueEditor } from './VariableValueEditor';
import type { ValueKind } from './VariableValueEditor';

const { Text } = Typography;

/** 行内输入高度（对齐 Record 的 ValueShell，Postman 向，紧凑） */
const FIELD_H = 26;

const DRAG_ICON = (
    <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor"><path d="M384 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m384-512a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z" /></svg>
);

export interface DragState {
    index: number;
    startY: number;
    currentY: number;
}

/**
 * 行内删除按钮（通用）：垃圾桶图标 + hover 淡入 + 轻底色。
 * 预留固定宽度，显隐用 opacity，避免行内其它控件在 hover 时抖动。
 * 供 DynamicVariableList / Record 等所有「可删除行」复用。
 */
export const RowDeleteButton: React.FC<{
    visible: boolean;
    onDelete: (e: React.MouseEvent) => void;
    title?: string;
}> = ({ visible, onDelete, title = '删除' }) => (
    <div
        title={title}
        onClick={(e) => {
            e.stopPropagation();
            onDelete(e);
        }}
        style={{
            width: 22,
            height: 22,
            flexShrink: 0,
            borderRadius: 6,
            color: '#ff4d4f',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 13,
            opacity: visible ? 1 : 0,
            pointerEvents: visible ? 'auto' : 'none',
            transition: 'opacity 0.15s, background 0.15s',
        }}
        onMouseEnter={(e) => {
            if (visible) e.currentTarget.style.background = '#fff1f0';
        }}
        onMouseLeave={(e) => {
            e.currentTarget.style.background = 'transparent';
        }}
    >
        <DeleteOutlined />
    </div>
);

// ============================================================================
// DraggableRowList — 通用「可拖拽排序 + 新增 + 删除」列表骨架（泛型）
// 只负责行外壳（拖拽把手 / hover 底色 / 位移动画 / 删除按钮 / 占位新增行），
// 行内具体内容（输入框、值编辑器等）由使用方通过 renderRow 注入。
// 返回 Fragment（不含外层容器），由使用方自行控制外层尺寸 / padding。
// ============================================================================
export interface DraggableRowListProps<T> {
    items: T[];
    rowHeight: number;
    getRowKey: (item: T, index: number) => string;
    dragState: DragState | null;
    hoverRowIndex: number | null;
    onHoverChange: (index: number | null) => void;
    onDragStart: (index: number) => (e: React.MouseEvent) => void;
    /** 渲染「拖拽把手」之后、「删除按钮」之前的行内容 */
    renderRow: (item: T, index: number, state: { isDragging: boolean; isHovering: boolean }) => React.ReactNode;
    /** 是否为底部新增占位行（不渲染把手/删除，整行交给 renderPlaceholder） */
    isPlaceholder?: (item: T, index: number) => boolean;
    renderPlaceholder?: (item: T, index: number) => React.ReactNode;
    /** 该行是否可删除（默认 true） */
    canRemove?: (item: T, index: number) => boolean;
    onRemove?: (index: number) => void;
    removeTitle?: string;
    /** 拖拽中把手/高亮色，默认蓝 */
    accentColor?: string;
    /** 行内元素间距，默认 6 */
    rowGap?: number;
    dragBg?: string;
    hoverBg?: string;
}

export function DraggableRowList<T>(props: DraggableRowListProps<T>) {
    const {
        items,
        rowHeight,
        getRowKey,
        dragState,
        hoverRowIndex,
        onHoverChange,
        onDragStart,
        renderRow,
        isPlaceholder,
        renderPlaceholder,
        canRemove,
        onRemove,
        removeTitle,
        accentColor = '#1677ff',
        rowGap = 6,
        dragBg = '#f0f1f3',
        hoverBg = '#e2e8f0',
    } = props;

    return (
        <>
            {items.map((item, idx) => {
                const placeholder = isPlaceholder?.(item, idx) ?? false;
                if (placeholder) {
                    return (
                        <React.Fragment key={getRowKey(item, idx)}>
                            {renderPlaceholder?.(item, idx)}
                        </React.Fragment>
                    );
                }

                const isDragging = dragState?.index === idx;
                const isHovering = hoverRowIndex === idx;
                const removable = canRemove?.(item, idx) ?? true;
                const showDelete = !!onRemove && isHovering && !dragState && removable;

                let transform = 'translateY(0)';
                let zIndex = 1;
                if (isDragging && dragState) {
                    transform = `translateY(${dragState.currentY - dragState.startY}px)`;
                    zIndex = 100;
                }

                return (
                    <div
                        key={getRowKey(item, idx)}
                        onMouseEnter={() => !dragState && onHoverChange(idx)}
                        onMouseLeave={() => !dragState && onHoverChange(null)}
                        style={{
                            height: rowHeight,
                            display: 'flex',
                            alignItems: 'center',
                            // 左右 padding 恒定：hover 不改行宽，只由右侧值框自行让位
                            padding: '0 4px 0 2px',
                            gap: rowGap,
                            transform,
                            zIndex,
                            position: 'relative',
                            backgroundColor: isDragging ? dragBg : isHovering ? hoverBg : 'transparent',
                            transition: isDragging ? 'none' : 'background-color 0.15s',
                            // 顶到节点左右边时不做圆角，避免阴影像「浮在中间」
                            borderRadius: 0,
                        }}
                    >
                        <div
                            onMouseDown={onDragStart(idx)}
                            style={{
                                color: isDragging
                                    ? accentColor
                                    : isHovering
                                        ? '#475569'
                                        : '#64748b',
                                display: 'flex',
                                cursor: isDragging ? 'grabbing' : 'grab',
                                padding: '4px 2px',
                                flexShrink: 0,
                                opacity: isDragging || isHovering ? 1 : 0.85,
                                transition: 'opacity 0.15s, color 0.15s',
                            }}
                        >
                            {DRAG_ICON}
                        </div>
                        {renderRow(item, idx, { isDragging, isHovering })}
                        {onRemove && (
                            <div
                                style={{
                                    position: 'absolute',
                                    right: 4,
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    display: 'flex',
                                }}
                            >
                                <RowDeleteButton
                                    visible={showDelete}
                                    onDelete={() => onRemove(idx)}
                                    title={removeTitle}
                                />
                            </div>
                        )}
                    </div>
                );
            })}
        </>
    );
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
    /** 切换取值类型（path=wire，其余=literal）。缺省时不显示类型下拉能力对应逻辑 */
    onSetVarKind?: (id: string, kind: ValueKind) => void;
    /** 底部「+」行文案，默认「输入变量」 */
    addLabel?: string;
    /** 变量名输入框 placeholder */
    namePlaceholder?: string;
    /** 路径输入框 placeholder */
    pathPlaceholder?: string;
    /** 隐藏底部新增行（如服务契约入参已固定） */
    hideAdd?: boolean;
    /** 隐藏删除（契约锁定时） */
    hideRemove?: boolean;
    /** 外层容器 padding，默认 '4px 0'（Record 等自控高度的节点可传 '0'） */
    containerPadding?: string;
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
        onSetVarKind,
        addLabel = '输入变量',
        namePlaceholder,
        pathPlaceholder = '$ 或 $.字段',
        hideAdd = false,
        hideRemove = false,
        containerPadding = '4px 0',
    } = props;

    return (
        <div style={{ padding: containerPadding, pointerEvents: 'auto', position: 'relative', flexShrink: 0 }}>
            <DraggableRowList<NodeVariable>
                items={variables}
                rowHeight={rowHeight}
                getRowKey={(v) => v.id}
                dragState={dragState}
                hoverRowIndex={hoverRowIndex}
                onHoverChange={onHoverChange}
                onDragStart={onDragStart}
                onRemove={onRemoveVar}
                rowGap={4}
                canRemove={(v) => !hideRemove && !v.fromContract}
                // 未锁定时末行空名占位为「+」；锁定契约时所有行都是真实入参
                isPlaceholder={(_, idx) => !hideAdd && idx === variables.length - 1}
                renderPlaceholder={() => (
                    <div
                        onClick={(e) => { e.stopPropagation(); onAddVar(e); }}
                        className="yf-var-add"
                        style={{
                            height: rowHeight,
                            display: 'inline-flex',
                            alignItems: 'center',
                            padding: '0 8px',
                            marginLeft: 2,
                            gap: 6,
                            cursor: 'pointer',
                            color: '#1677ff',
                            borderRadius: 6,
                        }}
                    >
                        <PlusOutlined style={{ fontSize: 12 }} />
                        <Text style={{ fontSize: 12, color: '#1677ff' }}>{addLabel}</Text>
                    </div>
                )}
                renderRow={(v, _idx, state) => (
                    <>
                        {v.paramSource ? (
                            <span
                                title={v.required ? `${v.paramSource} · 必填` : v.paramSource}
                                style={{
                                    flexShrink: 0,
                                    fontSize: 10,
                                    lineHeight: '18px',
                                    padding: '0 5px',
                                    borderRadius: 3,
                                    color: v.required ? '#d46b08' : '#595959',
                                    background: v.required ? '#fff7e6' : '#f5f5f5',
                                    border: `1px solid ${v.required ? '#ffd591' : '#e8e8e8'}`,
                                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                                }}
                            >
                                {v.paramSource}
                            </span>
                        ) : null}
                        <Input
                            size="small"
                            variant="borderless"
                            value={v.name}
                            placeholder={namePlaceholder}
                            disabled={!!v.fromContract}
                            onChange={(e) => onUpdateVar(v.id, { name: e.target.value })}
                            onMouseDown={(e) => e.stopPropagation()}
                            style={{
                                // 固定宽：hover 出删除时不跟着缩
                                flex: '0 0 72px',
                                width: 72,
                                fontSize: 12,
                                height: FIELD_H,
                                padding: '0 8px',
                                // 默认无框，hover 行时才显示输入框底色/边框
                                background: state.isHovering ? (v.fromContract ? '#fafafa' : '#f4f4f5') : 'transparent',
                                border: `1px solid ${state.isHovering ? '#e4e4e7' : 'transparent'}`,
                                borderRadius: 6,
                                transition: 'background 0.15s, border-color 0.15s',
                                color: v.fromContract ? '#262626' : undefined,
                            }}
                        />
                        {/* 值编辑器：仅右侧值框在 hover 时 margin 让位，key/把手不动 */}
                        <div
                            style={{
                                flex: 1,
                                minWidth: 0,
                                display: 'flex',
                                marginRight:
                                    state.isHovering && !state.isDragging && !hideRemove && !v.fromContract
                                        ? 22
                                        : 0,
                                transition: 'margin-right 0.15s',
                            }}
                        >
                            <VariableValueEditor
                                kind={v.source === 'literal' ? (v.valueType || 'string') : 'path'}
                                value={v.source === 'literal' ? (v.value ?? '') : v.extractPath}
                                height={FIELD_H}
                                typeSelectable={!!onSetVarKind}
                                pathPlaceholder={pathPlaceholder}
                                onChangeKind={(k) => onSetVarKind?.(v.id, k)}
                                onChangeValue={(val) =>
                                    v.source === 'literal'
                                        ? onUpdateVar(v.id, { value: val })
                                        : onUpdateVar(v.id, { extractPath: val })
                                }
                            />
                        </div>
                    </>
                )}
            />
        </div>
    );
}
