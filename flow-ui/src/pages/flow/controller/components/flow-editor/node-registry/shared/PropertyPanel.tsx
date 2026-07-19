// ============================================================================
// PropertyPanel — 右侧属性面板统一视觉与控件封装
// ============================================================================

import React from 'react';
import { Tooltip, Typography } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import CodeEditor, {
    mapExpressionLanguage,
    type CodeEditorLanguage,
} from '../../components/CodeEditor';

const { Text } = Typography;

/** 面板视觉 token（归拢风格） */
export const PROPERTY_THEME = {
    label: '#595959',
    muted: '#8c8c8c',
    text: '#262626',
    border: '#f0f0f0',
    cardBg: '#fff',
    pageBg: '#fafafa',
    hintBg: '#f7f8fa',
    hintBorder: '#eef0f3',
    hintText: '#6b7280',
    mono: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    radius: 8,
    controlHeight: 28,
    labelWidth: 72,
    gap: 8,
    sectionGap: 12,
};

/** 代码编辑器标准高度档位 */
export const CODE_HEIGHT = {
    sm: '88px',
    md: '140px',
    lg: '200px',
} as const;

const COLORS = PROPERTY_THEME;
const LABEL_WIDTH = PROPERTY_THEME.labelWidth;

export function PropertySection({
    title,
    tip,
    children,
    style,
}: {
    title: string;
    tip?: string;
    children: React.ReactNode;
    style?: React.CSSProperties;
}) {
    return (
        <div style={{ marginBottom: PROPERTY_THEME.sectionGap, ...style }}>
            <div
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    marginBottom: 8,
                    paddingBottom: 5,
                    borderBottom: `1px solid ${COLORS.border}`,
                }}
            >
                <Text strong style={{ fontSize: 12, color: COLORS.text, letterSpacing: 0.2 }}>
                    {title}
                </Text>
                {tip ? (
                    <Tooltip title={tip}>
                        <QuestionCircleOutlined style={{ color: COLORS.muted, fontSize: 11 }} />
                    </Tooltip>
                ) : null}
            </div>
            <div>{children}</div>
        </div>
    );
}

/**
 * 默认横向紧凑：左 label、右控件。
 * 大编辑器（代码框等）传 layout="vertical"。
 */
export function PropertyField({
    label,
    tip,
    children,
    extra,
    layout = 'horizontal',
    labelWidth = LABEL_WIDTH,
}: {
    label: string;
    tip?: string;
    children: React.ReactNode;
    /** 控件右侧附加（如单位 ms） */
    extra?: React.ReactNode;
    layout?: 'horizontal' | 'vertical';
    labelWidth?: number;
}) {
    const labelNode = (
        <div style={{ display: 'flex', alignItems: 'center', gap: 3, flexShrink: 0 }}>
            <Text style={{ fontSize: 12, color: COLORS.label, lineHeight: `${PROPERTY_THEME.controlHeight}px` }}>
                {label}
            </Text>
            {tip ? (
                <Tooltip title={tip}>
                    <QuestionCircleOutlined style={{ color: COLORS.muted, fontSize: 11 }} />
                </Tooltip>
            ) : null}
        </div>
    );

    if (layout === 'vertical') {
        return (
            <div style={{ marginBottom: 10 }}>
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        marginBottom: 4,
                    }}
                >
                    {labelNode}
                    {extra}
                </div>
                {children}
            </div>
        );
    }

    return (
        <div
            style={{
                display: 'flex',
                alignItems: 'center',
                gap: PROPERTY_THEME.gap,
                marginBottom: 8,
                minHeight: PROPERTY_THEME.controlHeight,
            }}
        >
            <div style={{ width: labelWidth, flexShrink: 0 }}>{labelNode}</div>
            <div style={{ flex: 1, minWidth: 0, display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
                {extra ? (
                    <span style={{ fontSize: 11, color: COLORS.muted, flexShrink: 0 }}>{extra}</span>
                ) : null}
            </div>
        </div>
    );
}

/** 一行并排两个紧凑字段（如 超时 + 重试） */
export function PropertyFieldRow({
    children,
    columns = 2,
}: {
    children: React.ReactNode;
    columns?: 2 | 3;
}) {
    return (
        <div
            style={{
                display: 'grid',
                gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                gap: '0 10px',
                marginBottom: 0,
            }}
        >
            {children}
        </div>
    );
}

export function PropertyHint({ children }: { children: React.ReactNode }) {
    return (
        <div
            style={{
                marginBottom: 8,
                padding: '6px 8px',
                background: COLORS.hintBg,
                border: `1px solid ${COLORS.hintBorder}`,
                borderRadius: 6,
                fontSize: 11,
                lineHeight: 1.5,
                color: COLORS.hintText,
            }}
        >
            {children}
        </div>
    );
}

export function PropertySwitchRow({
    label,
    tip,
    control,
}: {
    label: string;
    tip?: string;
    control: React.ReactNode;
}) {
    return (
        <div
            style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 10,
                marginBottom: 6,
                padding: '5px 8px',
                background: COLORS.hintBg,
                borderRadius: 6,
                border: `1px solid ${COLORS.hintBorder}`,
                minHeight: 30,
            }}
        >
            <div style={{ display: 'flex', alignItems: 'center', gap: 3, minWidth: 0 }}>
                <Text style={{ fontSize: 12, color: COLORS.label }}>{label}</Text>
                {tip ? (
                    <Tooltip title={tip}>
                        <QuestionCircleOutlined style={{ color: COLORS.muted, fontSize: 11, flexShrink: 0 }} />
                    </Tooltip>
                ) : null}
            </div>
            <div style={{ flexShrink: 0 }}>{control}</div>
        </div>
    );
}

/**
 * 属性面板代码区：统一 CodeMirror + 按语言格式化。
 * expressionLang：传 If/Evaluate/Switch 的 language 字符串时自动映射高亮。
 */
export function PropertyCodeField({
    label,
    tip,
    value,
    onChange,
    language = 'text',
    expressionLang,
    height = 'md',
    placeholder,
    lineNumbers = true,
}: {
    label: string;
    tip?: string;
    value: string;
    onChange: (val: string) => void;
    language?: CodeEditorLanguage;
    /** 若提供，优先用 mapExpressionLanguage 映射 */
    expressionLang?: string;
    height?: keyof typeof CODE_HEIGHT | string;
    placeholder?: string;
    lineNumbers?: boolean;
}) {
    const resolvedLang: CodeEditorLanguage = expressionLang
        ? mapExpressionLanguage(expressionLang)
        : language;
    const h = typeof height === 'string' && height in CODE_HEIGHT
        ? CODE_HEIGHT[height as keyof typeof CODE_HEIGHT]
        : height;

    return (
        <PropertyField label={label} tip={tip} layout="vertical">
            <CodeEditor
                value={value}
                onChange={onChange}
                language={resolvedLang}
                height={h}
                maxHeight="360px"
                fontSize={12}
                lineNumbers={lineNumbers}
                theme="light"
                showFormat
                placeholder={placeholder}
            />
        </PropertyField>
    );
}

/** 节点头卡片外壳（标题区） */
export function PropertyNodeCard({ children }: { children: React.ReactNode }) {
    return (
        <div
            style={{
                marginBottom: 14,
                padding: '12px 12px 10px',
                background: PROPERTY_THEME.cardBg,
                border: `1px solid ${PROPERTY_THEME.border}`,
                borderRadius: PROPERTY_THEME.radius,
            }}
        >
            {children}
        </div>
    );
}
