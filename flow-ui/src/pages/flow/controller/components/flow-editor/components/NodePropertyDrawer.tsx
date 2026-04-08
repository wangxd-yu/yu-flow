// ============================================================================
// NodePropertyDrawer.tsx
// V3.2 配置面板 —— 通过 node-registry 动态渲染表单，无需 switch-case
// ============================================================================

import React, { useCallback, useMemo, useState } from 'react';
import {
    Alert,
    AutoComplete,
    Button,
    Collapse,
    Divider,
    Input,
    Space,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import {
    PlusOutlined,
    DeleteOutlined,
    InfoCircleOutlined,
    QuestionCircleOutlined,
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
import { queryDataSourceList } from '@/pages/flow/dataSource/services/dataSource';

const { Text, Paragraph } = Typography;

// ── JSONPath 自动补全建议 ────────────────────────────────────────
const JSONPATH_SUGGESTIONS = [
    { value: '$.start.args.', label: '$.start.args.<param>' },
    { value: '$.', label: '$.<nodeId>.result' },
    { value: "$['", label: "$['<nodeId>']['<field>']" },
];

// ── Props ────────────────────────────────────────────────────────
type NodePropertyDrawerProps = {
    node: Node | null;
    onDataChange: (node: Node, data: Record<string, any>) => void;
    globalForm?: FormInstance;
    isEdit?: boolean;
};

// ============================================================================
// 主组件
// ============================================================================

export default function NodePropertyDrawer({
    node,
    onDataChange,
    globalForm,
    isEdit,
}: NodePropertyDrawerProps) {
    if (!node) {
        return (
            <div style={{ height: '100%', overflowY: 'auto' }}>
                <GlobalConfigEditor
                    form={globalForm}
                    isEdit={isEdit}
                />
            </div>
        );
    }

    const rawData = node.getData() as Record<string, any>;
    const nodeType = rawData?.__dslType as DslNodeType | undefined;

    if (!nodeType) {
        return (
            <div style={{ padding: 16 }}>
                <Alert type="warning" message="无法识别的节点类型" />
            </div>
        );
    }

    // 从注册表获取属性编辑器组件
    const PropertyEditor = getPropertyEditor(nodeType);

    return (
        <div style={{ padding: 16 }}>
            {/* ── 节点标题 ── */}
            <div style={{ marginBottom: 12 }}>
                <Tag color={getNodeTagColor(nodeType)}>{nodeType.toUpperCase()}</Tag>
                <Text strong style={{ marginLeft: 4 }}>
                    {node.id}
                </Text>
            </div>

            <Divider style={{ margin: '8px 0 16px 0' }} />

            {/* ── Inputs 配置区（通用 JSONPath 映射） ── */}
            {hasInputsField(nodeType) && (
                <InputsEditor
                    inputs={(rawData.inputs as InputsMap) || {}}
                    onChange={(inputs) => onDataChange(node, { inputs })}
                />
            )}

            {/* ── 业务配置区（从注册表动态获取编辑器） ── */}
            {PropertyEditor ? (
                <PropertyEditor
                    node={node}
                    data={rawData}
                    onChange={(changes: Record<string, any>) => onDataChange(node, changes)}
                />
            ) : (
                <Paragraph type="secondary" style={{ marginTop: 12 }}>
                    该节点类型（{nodeType}）暂无专用配置项
                </Paragraph>
            )}
        </div>
    );
}

// ============================================================================
// Inputs 配置器 (Key-Value + JSONPath) —— 通用，所有节点共用
// ============================================================================

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
        <Collapse
            ghost
            defaultActiveKey={['inputs']}
            items={[
                {
                    key: 'inputs',
                    label: (
                        <Space>
                            <Text strong style={{ fontSize: 13 }}>
                                数据映射 (Inputs)
                            </Text>
                            <Tooltip title="使用 JSONPath 映射上游节点数据到当前节点变量">
                                <QuestionCircleOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                            </Tooltip>
                        </Space>
                    ),
                    children: (
                        <div>
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
                                        style={{ width: 90, fontFamily: 'monospace' }}
                                        onBlur={(e) => handleKeyChange(entry.key, e.target.value)}
                                    />
                                    <span style={{ color: '#8c8c8c' }}>←</span>
                                    <AutoComplete
                                        size="small"
                                        options={JSONPATH_SUGGESTIONS}
                                        defaultValue={entry.extractPath}
                                        placeholder="$.nodeId.field"
                                        style={{ flex: 1, fontFamily: 'monospace' }}
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
                                style={{ marginTop: 4 }}
                            >
                                添加输入映射
                            </Button>

                            <Paragraph
                                type="secondary"
                                style={{ fontSize: 11, lineHeight: 1.4, marginTop: 8 }}
                            >
                                <InfoCircleOutlined /> 格式说明：
                                <br />• <code>$.start.args.name</code> — 引用 start 节点的参数
                                <br />• <code>$.nodeId.result</code> — 引用某节点的执行结果
                                <br />• <code>{'$[\'nodeId\'][\'field\']'}</code> — 括号路径语法
                            </Paragraph>
                        </div>
                    ),
                },
            ]}
        />
    );
}

// ============================================================================
// 全局配置编辑器 (表单无提交按钮，自动保存)
// ============================================================================

export function GlobalConfigEditor({
    form,
    isEdit,
}: {
    form?: FormInstance;
    isEdit?: boolean;
}) {
    return (
        <div style={{ padding: 16 }}>
            <div style={{ marginBottom: 16 }}>
                <Text strong style={{ fontSize: 16 }}>API 全局配置</Text>
            </div>
            <ProForm
                form={form}
                submitter={false}
                layout="horizontal"
                labelCol={{ span: 6 }}
                wrapperCol={{ span: 18 }}
            >
                {/* 确保在“逻辑编排”界面的全局表单中，能够正确收集到 type 为 FLOW 以及带有默认值 */}
                <ProFormText name={['flowService', 'type']} hidden initialValue="FLOW" />
                <Collapse
                    defaultActiveKey={['basic', 'config']}
                    ghost
                    items={[
                        {
                            key: 'basic',
                            label: <Text strong>基础设置</Text>,
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
                            label: <Text strong>基本信息</Text>,
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
                            label: <Text strong>返回值</Text>,
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
        </div>
    );
}
