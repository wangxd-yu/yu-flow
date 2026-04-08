// ============================================================================
// node-registry/nodes/evaluate.tsx
// Evaluate (表达式) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Divider, Input, Select, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { EvaluateNodeComponent, EVALUATE_LAYOUT } from './EvaluateNodeComponent';
import CodeEditor from '../../../components/CodeEditor';

const { Text } = Typography;

const LANGUAGE_OPTIONS = [
    { value: 'Aviator', label: 'Aviator' },
    { value: 'SpEL', label: 'SpEL' },
    { value: 'JavaScript', label: 'JavaScript' },
];

// ── 属性面板编辑器 ──
function EvaluateEditor({ data, onChange }: PropertyEditorProps) {
    const lang = data.language || 'JavaScript';
    const editorLanguage = (lang === 'JavaScript' || lang === 'js') ? 'javascript' : 'text';

    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                表达式配置
            </Divider>
            <div style={{ marginBottom: 12 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>表达式语言</Text>
                <Select
                    size="small"
                    value={lang}
                    options={LANGUAGE_OPTIONS}
                    style={{ width: '100%', marginTop: 4 }}
                    onChange={(val) => onChange({ language: val })}
                />
            </div>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>表达式</Text>
                <div style={{ marginTop: 4 }}>
                    <CodeEditor
                        value={data.expression || ''}
                        onChange={(val) => onChange({ expression: val })}
                        language={editorLanguage}
                        height="120px"
                        maxHeight="300px"
                        fontSize={12}
                        lineNumbers={true}
                        theme="light"
                    />
                </div>
            </div>
        </div>
    );
}

// ── 注册配置 ──
export const evaluateNodeRegistration: NodeRegistration = {
    type: 'evaluate',
    label: '表达式 (Evaluate)',
    category: '逻辑节点',
    color: '#1677ff',
    tagColor: 'blue',
    hasInputs: true,

    shape: {
        shapeName: 'flow-evaluate',
        kind: 'react',
        component: EvaluateNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: EVALUATE_LAYOUT.width, y: EVALUATE_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out' }],
        data: { expression: '', language: 'JavaScript', inputs: {} },
        size: { width: EVALUATE_LAYOUT.width, height: EVALUATE_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'standard',
    },

    buildLabel: (data) =>
        data.expression ? `= ${String(data.expression).substring(0, 25)}` : 'Evaluate',

    PropertyEditor: EvaluateEditor,
};
