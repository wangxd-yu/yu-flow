// ============================================================================
// PropertyPanel — 右侧属性面板统一视觉组件（默认紧凑横排）
// ============================================================================

import React from 'react';
import { Tooltip, Typography } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

const { Text } = Typography;

const COLORS = {
    label: '#595959',
    muted: '#8c8c8c',
    border: '#f0f0f0',
    hintBg: '#f7f8fa',
    hintBorder: '#eef0f3',
    hintText: '#6b7280',
};

const LABEL_WIDTH = 72;

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
        <div style={{ marginBottom: 12, ...style }}>
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
                <Text strong style={{ fontSize: 12, color: '#262626', letterSpacing: 0.2 }}>
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
            <Text style={{ fontSize: 12, color: COLORS.label, lineHeight: '22px' }}>{label}</Text>
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
                gap: 8,
                marginBottom: 8,
                minHeight: 28,
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
export function PropertyFieldRow({ children }: { children: React.ReactNode }) {
    return (
        <div
            style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
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
