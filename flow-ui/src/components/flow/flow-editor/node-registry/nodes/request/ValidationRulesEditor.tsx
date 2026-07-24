// ============================================================================
// ValidationRulesEditor.tsx — Request 参数校验规则构建器
// 对齐后端 ValidationRule：required / type / pattern / min / max / message
// ============================================================================

import React from 'react';
import { Button, Checkbox, Input, InputNumber, Select, Space } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { PropertyHint } from '../../shared/PropertyPanel';

export type ValidationRuleUI = {
    required?: boolean;
    type?: string;
    pattern?: string;
    min?: number | null;
    max?: number | null;
    message?: string;
};

const TYPE_OPTIONS = [
    { value: '', label: '无（仅必填）' },
    { value: 'phone', label: '手机号' },
    { value: 'email', label: '邮箱' },
    { value: 'regex', label: '正则' },
    { value: 'range', label: '数值范围' },
    { value: 'length', label: '字符串长度' },
];

type RuleRow = {
    key: string;
    field: string;
    rule: ValidationRuleUI;
};

function mapToRows(validations: Record<string, ValidationRuleUI> | undefined): RuleRow[] {
    if (!validations || typeof validations !== 'object') return [];
    return Object.entries(validations).map(([field, rule], idx) => ({
        key: `r-${idx}-${field}`,
        field,
        rule: { ...(rule || {}) },
    }));
}

function rowsToMap(rows: RuleRow[]): Record<string, ValidationRuleUI> {
    const out: Record<string, ValidationRuleUI> = {};
    for (const row of rows) {
        const name = row.field.trim();
        if (!name) continue;
        const rule: ValidationRuleUI = {};
        if (row.rule.required) rule.required = true;
        if (row.rule.type) rule.type = row.rule.type;
        if (row.rule.pattern) rule.pattern = row.rule.pattern;
        if (row.rule.min != null && row.rule.min !== ('' as any)) rule.min = row.rule.min;
        if (row.rule.max != null && row.rule.max !== ('' as any)) rule.max = row.rule.max;
        if (row.rule.message) rule.message = row.rule.message;
        out[name] = rule;
    }
    return out;
}

export type ValidationRulesEditorProps = {
    value?: Record<string, ValidationRuleUI>;
    onChange: (validations: Record<string, ValidationRuleUI>) => void;
};

export default function ValidationRulesEditor({ value, onChange }: ValidationRulesEditorProps) {
    const [rows, setRows] = React.useState<RuleRow[]>(() => mapToRows(value));

    // 外部变更（如撤销）时同步
    React.useEffect(() => {
        setRows(mapToRows(value));
    }, [value]);

    const commit = (next: RuleRow[]) => {
        setRows(next);
        onChange(rowsToMap(next));
    };

    const updateRow = (key: string, patch: { field?: string; rule?: Partial<ValidationRuleUI> }) => {
        commit(
            rows.map((r) => {
                if (r.key !== key) return r;
                return {
                    ...r,
                    field: patch.field !== undefined ? patch.field : r.field,
                    rule: patch.rule ? { ...r.rule, ...patch.rule } : r.rule,
                };
            }),
        );
    };

    const addRow = () => {
        commit([
            ...rows,
            {
                key: `r-${Date.now()}`,
                field: '',
                rule: { required: true },
            },
        ]);
    };

    const removeRow = (key: string) => {
        commit(rows.filter((r) => r.key !== key));
    };

    return (
        <div>
            <PropertyHint>
                校验合并后的 params + body 字段。类型：phone / email / regex / range / length。
                下游读取：<code>$.request.params.xxx</code>、<code>$.request.body.xxx</code>。
            </PropertyHint>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {rows.map((row) => {
                    const t = row.rule.type || '';
                    const showPattern = t === 'regex';
                    const showMinMax = t === 'range' || t === 'length';
                    return (
                        <div
                            key={row.key}
                            style={{
                                padding: 10,
                                border: '1px solid #f0f0f0',
                                borderRadius: 6,
                                background: '#fafafa',
                            }}
                        >
                            <Space wrap size={8} style={{ width: '100%' }}>
                                <Input
                                    size="small"
                                    placeholder="字段名"
                                    value={row.field}
                                    style={{ width: 120 }}
                                    onChange={(e) => updateRow(row.key, { field: e.target.value })}
                                />
                                <Checkbox
                                    checked={!!row.rule.required}
                                    onChange={(e) => updateRow(row.key, { rule: { required: e.target.checked } })}
                                >
                                    必填
                                </Checkbox>
                                <Select
                                    size="small"
                                    value={t}
                                    options={TYPE_OPTIONS}
                                    style={{ width: 120 }}
                                    getPopupContainer={() => document.body}
                                    onChange={(val) =>
                                        updateRow(row.key, {
                                            rule: {
                                                type: val || undefined,
                                                ...(val !== 'regex' ? { pattern: undefined } : {}),
                                                ...(val !== 'range' && val !== 'length'
                                                    ? { min: undefined, max: undefined }
                                                    : {}),
                                            },
                                        })
                                    }
                                />
                                <Button
                                    size="small"
                                    type="text"
                                    danger
                                    icon={<DeleteOutlined />}
                                    onClick={() => removeRow(row.key)}
                                />
                            </Space>

                            {showPattern && (
                                <Input
                                    size="small"
                                    placeholder="正则 pattern，如 ^\\d{6}$"
                                    value={row.rule.pattern || ''}
                                    style={{ marginTop: 8, fontFamily: 'monospace' }}
                                    onChange={(e) => updateRow(row.key, { rule: { pattern: e.target.value } })}
                                />
                            )}

                            {showMinMax && (
                                <Space size={8} style={{ marginTop: 8 }}>
                                    <InputNumber
                                        size="small"
                                        placeholder="min"
                                        value={row.rule.min ?? undefined}
                                        style={{ width: 100 }}
                                        onChange={(val) => updateRow(row.key, { rule: { min: val } })}
                                    />
                                    <InputNumber
                                        size="small"
                                        placeholder="max"
                                        value={row.rule.max ?? undefined}
                                        style={{ width: 100 }}
                                        onChange={(val) => updateRow(row.key, { rule: { max: val } })}
                                    />
                                </Space>
                            )}

                            <Input
                                size="small"
                                placeholder="失败提示（可选）"
                                value={row.rule.message || ''}
                                style={{ marginTop: 8 }}
                                onChange={(e) => updateRow(row.key, { rule: { message: e.target.value } })}
                            />
                        </div>
                    );
                })}
            </div>

            <Button
                size="small"
                type="dashed"
                icon={<PlusOutlined />}
                style={{ width: '100%', marginTop: 10 }}
                onClick={addRow}
            >
                添加校验规则
            </Button>
        </div>
    );
}
