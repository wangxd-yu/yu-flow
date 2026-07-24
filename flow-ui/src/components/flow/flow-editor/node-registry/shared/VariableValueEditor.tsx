// ============================================================================
// VariableValueEditor.tsx — 通用「值编辑器」（Postman 向）
// 统一 Record 与 DynamicVariableList 的取值编辑：
//   - 左侧类型条为下拉：Path / String / Number / Bool / Null
//   - kind==='path' 表示走连线（wire），其余为字面量（literal，无端口）
//   - 选中字面量类型后，调用方负责「自动去掉连线/端口」（参照 Postman）
//   - 选中 Path 后，调用方把该行恢复为 wire（重新长出端口，可连接）
// 供 Record / evaluate / database / if / service 等所有变量行复用。
// ============================================================================

import React from 'react';
import { Dropdown, Input, InputNumber, Select } from 'antd';

export type ValueKind = 'path' | 'string' | 'number' | 'boolean' | 'null';
export type LiteralType = Exclude<ValueKind, 'path'>;

/** 左侧类型条宽度 */
const TYPE_ADDON_WIDTH = 30;
/** 默认行内输入高度（紧凑） */
const DEFAULT_FIELD_H = 26;

const PATH_ICON = (
    <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor"><path d="M512 128a384 384 0 1 0 0.1 768.1A384 384 0 0 0 512 128zm0 704a320 320 0 1 1 0-640 320 320 0 0 1 0 640zm0-480a160 160 0 1 0 0.1 320.1A160 160 0 0 0 512 352z" /></svg>
);

const TYPE_BADGE_LABEL: Record<LiteralType, string> = {
    string: 'Aa',
    number: '123',
    boolean: '0/1',
    null: '∅',
};

export const TYPE_MENU_ITEMS: { key: ValueKind; label: string }[] = [
    { key: 'path', label: 'Path' },
    { key: 'string', label: 'String' },
    { key: 'number', label: 'Number' },
    { key: 'boolean', label: 'Bool' },
    { key: 'null', label: 'Null' },
];

/** 类型图标：path 用小圆环，其余用等宽字母角标 */
export const typeGlyph = (kind: ValueKind, color = '#8b8f98') => {
    if (kind === 'path') {
        return (
            <span style={{ color, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                {PATH_ICON}
            </span>
        );
    }
    return (
        <span
            style={{
                color,
                fontSize: 11,
                fontWeight: 600,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                letterSpacing: kind === 'number' || kind === 'boolean' ? -0.5 : 0,
                lineHeight: 1,
            }}
        >
            {TYPE_BADGE_LABEL[kind]}
        </span>
    );
};

/** 切换到某字面量类型时的默认值 */
export const defaultLiteralValue = (t: LiteralType): string => {
    if (t === 'boolean') return 'true';
    if (t === 'number') return '0';
    return ''; // string / null
};

export interface VariableValueEditorProps {
    /** 当前类型；'path' 表示 wire（走连线） */
    kind: ValueKind;
    /** 当前文本值（path 为 extractPath；literal 为字面量文本） */
    value: string;
    /** 是否已有连线（用于 path 占位文案） */
    connected?: boolean;
    /** 是否可切换类型（false 时左侧类型条为静态展示，保持旧行为） */
    typeSelectable?: boolean;
    height?: number;
    pathPlaceholder?: string;
    /** 类型切换（path=wire，其余=literal），调用方据此增删端口/连线 */
    onChangeKind: (kind: ValueKind) => void;
    onChangeValue: (value: string) => void;
}

/** Postman 向：左侧类型下拉条 + 右侧无边框输入，整体一个 ValueShell */
export const VariableValueEditor: React.FC<VariableValueEditorProps> = ({
    kind,
    value,
    connected,
    typeSelectable = true,
    height = DEFAULT_FIELD_H,
    pathPlaceholder,
    onChangeKind,
    onChangeValue,
}) => {
    const muted = kind === 'null';

    const typeMenu = {
        items: TYPE_MENU_ITEMS.map((t) => ({
            key: t.key,
            label: (
                <span style={{ fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    {typeGlyph(t.key)}
                    <span>{t.label}</span>
                </span>
            ),
        })),
        onClick: ({ key }: { key: string }) => onChangeKind(key as ValueKind),
    };

    const innerInputStyle: React.CSSProperties = {
        flex: 1,
        width: '100%',
        minWidth: 0,
        fontSize: 12,
        padding: 0,
        background: 'transparent',
    };

    let content: React.ReactNode;
    if (kind === 'path') {
        content = (
            <Input
                size="small"
                variant="borderless"
                placeholder={pathPlaceholder ?? (connected ? 'Enter path...' : '$.node.out 或连线')}
                value={value === '$' ? '' : value}
                onChange={(e) => onChangeValue(e.target.value)}
                onMouseDown={(e) => e.stopPropagation()}
                style={innerInputStyle}
            />
        );
    } else if (kind === 'null') {
        content = (
            <span style={{ color: '#8c8c8c', fontFamily: 'monospace', fontSize: 12 }}>null</span>
        );
    } else if (kind === 'boolean') {
        content = (
            <Select
                size="small"
                variant="borderless"
                value={value === 'false' ? 'false' : 'true'}
                options={[
                    { value: 'true', label: 'true' },
                    { value: 'false', label: 'false' },
                ]}
                onChange={(v) => onChangeValue(v)}
                onMouseDown={(e) => e.stopPropagation()}
                style={{ flex: 1, width: '100%', fontSize: 12 }}
            />
        );
    } else if (kind === 'number') {
        content = (
            <InputNumber
                size="small"
                controls={false}
                variant="borderless"
                value={value === '' ? undefined : Number(value)}
                onChange={(v) => onChangeValue(v == null ? '' : String(v))}
                onMouseDown={(e) => e.stopPropagation()}
                style={innerInputStyle}
                placeholder="0"
            />
        );
    } else {
        content = (
            <Input
                size="small"
                variant="borderless"
                placeholder="Enter text..."
                value={value}
                onChange={(e) => onChangeValue(e.target.value)}
                onMouseDown={(e) => e.stopPropagation()}
                style={innerInputStyle}
            />
        );
    }

    return (
        <div
            style={{
                flex: 1,
                minWidth: 0,
                height,
                display: 'flex',
                alignItems: 'stretch',
                background: muted ? '#fafafa' : '#fff',
                border: '1px solid #e4e4e7',
                borderRadius: 6,
                overflow: 'hidden',
                boxSizing: 'border-box',
            }}
        >
            {typeSelectable ? (
                <Dropdown menu={typeMenu} trigger={['click']} placement="bottomLeft">
                    <div
                        title="选择类型"
                        onClick={(e) => e.stopPropagation()}
                        onMouseDown={(e) => e.stopPropagation()}
                        style={{
                            width: TYPE_ADDON_WIDTH,
                            flexShrink: 0,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: '#f4f4f5',
                            borderRight: '1px solid #ebebeb',
                            cursor: 'pointer',
                        }}
                    >
                        {typeGlyph(kind)}
                    </div>
                </Dropdown>
            ) : (
                <div
                    style={{
                        width: TYPE_ADDON_WIDTH,
                        flexShrink: 0,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: '#f4f4f5',
                        borderRight: '1px solid #ebebeb',
                    }}
                >
                    {typeGlyph(kind)}
                </div>
            )}
            <div
                style={{
                    flex: 1,
                    minWidth: 0,
                    display: 'flex',
                    alignItems: 'center',
                    padding: '0 8px',
                }}
            >
                {content}
            </div>
        </div>
    );
};
