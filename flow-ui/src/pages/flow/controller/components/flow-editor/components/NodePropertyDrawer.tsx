// ============================================================================
// NodePropertyDrawer.tsx
// 右侧配置面板 —— 统一视觉 + node-registry 动态编辑器
// ============================================================================

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert,
    AutoComplete,
    Button,
    Collapse,
    Input,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import {
    PlusOutlined,
    DeleteOutlined,
} from '@ant-design/icons';
import {
    ProForm,
    ProFormText,
    ProFormSelect,
    ProFormDigit,
    ProFormTextArea,
} from '@ant-design/pro-components';
import type { Node } from '@antv/x6';
import type { FormInstance } from 'antd';
import type { DslNodeType, InputsMap, InputMapping } from '../types';
import {
    getNodeTagColor,
    hasInputsField,
    getPropertyEditor,
} from '../node-registry';
import {
    PropertyField,
    PropertyHint,
    PropertySection,
} from '../node-registry/shared/PropertyPanel';

const { Text, Paragraph } = Typography;

const JSONPATH_SUGGESTIONS = [
    { value: '$.', label: '$.<nodeId>.out' },
    { value: '$.cfg.out.', label: '$.cfg.out.<field>' },
    { value: '$', label: '$（相对上游）' },
];

type NodePropertyDrawerProps = {
    node: Node | null;
    onDataChange: (node: Node, data: Record<string, any>) => void;
    globalForm?: FormInstance;
    isEdit?: boolean;
    isBreakpoint?: boolean;
    onToggleBreakpoint?: (nodeId: string) => void;
};

export default function NodePropertyDrawer({
    node,
    onDataChange,
    globalForm,
    isEdit,
    isBreakpoint,
    onToggleBreakpoint,
}: NodePropertyDrawerProps) {
    const [, setDataTick] = useState(0);

    useEffect(() => {
        if (!node) return;
        const onData = () => setDataTick((t) => t + 1);
        node.on('change:data', onData);
        return () => {
            node.off('change:data', onData);
        };
    }, [node]);

    if (!node) {
        return (
            <div style={{ height: '100%', overflowY: 'auto', background: '#fafafa' }}>
                <GlobalConfigEditor form={globalForm} isEdit={isEdit} />
            </div>
        );
    }

    const rawData = node.getData() as Record<string, any>;
    const nodeType = rawData?.__dslType as DslNodeType | undefined;
    const nodeLabel = rawData?.__label || nodeType || '节点';

    if (!nodeType) {
        return (
            <div style={{ padding: 16 }}>
                <Alert type="warning" message="无法识别的节点类型" />
            </div>
        );
    }

    const PropertyEditor = getPropertyEditor(nodeType);

    return (
        <div
            style={{
                height: '100%',
                overflowY: 'auto',
                background: '#fafafa',
                padding: '12px 14px 24px',
            }}
        >
            <div
                style={{
                    marginBottom: 14,
                    padding: '12px 12px 10px',
                    background: '#fff',
                    border: '1px solid #f0f0f0',
                    borderRadius: 8,
                }}
            >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                    <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                            <Tag color={getNodeTagColor(nodeType)} style={{ margin: 0 }}>
                                {nodeType}
                            </Tag>
                        </div>
                        <Text strong style={{ fontSize: 14, color: '#262626' }} ellipsis>
                            {nodeLabel}
                        </Text>
                        <div style={{ marginTop: 2 }}>
                            <Text
                                style={{
                                    fontSize: 11,
                                    color: '#8c8c8c',
                                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                }}
                            >
                                {node.id}
                            </Text>
                        </div>
                    </div>
                    {onToggleBreakpoint && (
                        <Tooltip title={isBreakpoint ? '取消断点' : '设置断点'}>
                            <Button
                                type={isBreakpoint ? 'primary' : 'default'}
                                danger={isBreakpoint}
                                shape="circle"
                                size="small"
                                icon={
                                    <span
                                        style={{
                                            display: 'inline-block',
                                            width: 12,
                                            height: 12,
                                            borderRadius: '50%',
                                            background: isBreakpoint ? '#fff' : '#ff4d4f',
                                        }}
                                    />
                                }
                                onClick={() => onToggleBreakpoint(node.id)}
                            />
                        </Tooltip>
                    )}
                </div>
            </div>

            {hasInputsField(nodeType) && (
                <InputsEditor
                    inputs={(rawData.inputs as InputsMap) || {}}
                    onChange={(inputs) => onDataChange(node, { inputs })}
                />
            )}

            {PropertyEditor ? (
                <PropertyEditor
                    node={node}
                    data={rawData}
                    onChange={(changes: Record<string, any>) => onDataChange(node, changes)}
                />
            ) : (
                <PropertyHint>该节点类型暂无额外配置项，可在画布节点内直接编辑。</PropertyHint>
            )}
        </div>
    );
}

export function InputsEditor({
    inputs,
    onChange,
}: {
    inputs: InputsMap;
    onChange: (inputs: InputsMap) => void;
}) {
    const entries = useMemo(() => {
        return Object.entries(inputs).map(([key, value]) => ({
            key,
            extractPath:
                typeof value === 'string'
                    ? value
                    : (value as InputMapping)?.extractPath || '',
        }));
    }, [inputs]);

    const handleAdd = useCallback(() => {
        const newKey = `var_${Date.now().toString(36).slice(-4)}`;
        onChange({ ...inputs, [newKey]: { extractPath: '' } });
    }, [inputs, onChange]);

    const handleRemove = useCallback(
        (key: string) => {
            const next = { ...inputs };
            delete next[key];
            onChange(next);
        },
        [inputs, onChange],
    );

    const handleKeyChange = useCallback(
        (oldKey: string, newKey: string) => {
            if (oldKey === newKey || !newKey.trim()) return;
            const next: InputsMap = {};
            Object.entries(inputs).forEach(([k, v]) => {
                next[k === oldKey ? newKey : k] = v;
            });
            onChange(next);
        },
        [inputs, onChange],
    );

    const handlePathChange = useCallback(
        (key: string, extractPath: string) => {
            onChange({ ...inputs, [key]: { extractPath } });
        },
        [inputs, onChange],
    );

    return (
        <PropertySection
            title="数据映射"
            tip="将上游数据映射到当前节点变量；画布连线也会写入 inputs"
        >
            {entries.length === 0 && (
                <PropertyHint>暂无映射。可添加变量，或在画布上连线到节点入口。</PropertyHint>
            )}
            {entries.map((entry) => (
                <div
                    key={entry.key}
                    style={{
                        display: 'flex',
                        gap: 6,
                        marginBottom: 8,
                        alignItems: 'center',
                    }}
                >
                    <Input
                        size="small"
                        placeholder="变量名"
                        defaultValue={entry.key}
                        style={{
                            width: 96,
                            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                        }}
                        onBlur={(e) => handleKeyChange(entry.key, e.target.value)}
                    />
                    <span style={{ color: '#bfbfbf', fontSize: 12 }}>←</span>
                    <AutoComplete
                        size="small"
                        options={JSONPATH_SUGGESTIONS}
                        value={entry.extractPath}
                        placeholder="$ 或 $.field"
                        style={{
                            flex: 1,
                            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                        }}
                        onChange={(val) => handlePathChange(entry.key, val)}
                        filterOption={(input, option) =>
                            (option?.value?.toString() || '').includes(input)
                        }
                    />
                    <Button
                        type="text"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        onClick={() => handleRemove(entry.key)}
                    />
                </div>
            ))}
            <Button
                type="dashed"
                size="small"
                block
                icon={<PlusOutlined />}
                onClick={handleAdd}
                style={{ marginTop: 2 }}
            >
                添加映射
            </Button>
        </PropertySection>
    );
}

export function GlobalConfigEditor({
    form,
    isEdit,
}: {
    form?: FormInstance;
    isEdit?: boolean;
}) {
    return (
        <div style={{ padding: '12px 14px 24px' }}>
            <PropertySection title="API 全局配置">
                <ProForm
                    form={form}
                    submitter={false}
                    layout="vertical"
                    style={{ marginTop: 4 }}
                >
                    <ProFormText name={['flowService', 'type']} hidden initialValue="FLOW" />
                    <Collapse
                        defaultActiveKey={['basic']}
                        ghost
                        style={{ background: 'transparent' }}
                        items={[
                            {
                                key: 'basic',
                                label: <Text strong style={{ fontSize: 12 }}>基础设置</Text>,
                                children: (
                                    <>
                                        <ProFormText name="name" label="名称" placeholder="请输入名称" rules={[{ required: true }]} />
                                        <ProFormSelect
                                            name="method"
                                            label="方法"
                                            valueEnum={{ GET: 'GET', POST: 'POST', PUT: 'PUT', DELETE: 'DELETE' }}
                                            rules={[{ required: true }]}
                                        />
                                        <ProFormText name="url" label="URL" placeholder="请输入URL" rules={[{ required: true }]} disabled={isEdit} />
                                    </>
                                ),
                            },
                            {
                                key: 'info',
                                label: <Text strong style={{ fontSize: 12 }}>基本信息</Text>,
                                children: (
                                    <>
                                        <ProFormText name="module" label="模块" placeholder="请输入模块" />
                                        <ProFormText name="version" label="版本" placeholder="请输入版本" />
                                        <ProFormSelect
                                            name="publishStatus"
                                            label="发布状态"
                                            options={[{ value: 0, label: '未发布' }, { value: 1, label: '已发布' }]}
                                            rules={[{ required: true }]}
                                        />
                                        <ProFormDigit name="level" label="优先级" min={1} max={10} fieldProps={{ style: { width: '100%' } }} />
                                        <ProFormSelect
                                            name="tags"
                                            label="标签"
                                            mode="tags"
                                            placeholder="最多5个标签"
                                            fieldProps={{ maxTagCount: 5, tokenSeparators: [','] }}
                                        />
                                        <ProFormText name="info" label="描述" placeholder="请输入描述" />
                                    </>
                                ),
                            },
                            {
                                key: 'response',
                                label: <Text strong style={{ fontSize: 12 }}>返回值</Text>,
                                children: (
                                    <>
                                        <ProFormTextArea name="wrapSuccess" label="成功返回包装" fieldProps={{ rows: 3 }} />
                                        <ProFormTextArea name="wrapError" label="失败返回包装" fieldProps={{ rows: 3 }} />
                                    </>
                                ),
                            },
                        ]}
                    />
                </ProForm>
            </PropertySection>
            <Paragraph type="secondary" style={{ fontSize: 11, margin: 0 }}>
                选中画布节点后，可在此编辑该节点的映射与策略配置。
            </Paragraph>
        </div>
    );
}
