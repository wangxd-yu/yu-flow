import React from 'react';
import { Form, Input } from 'antd';
import { NodeMetadata } from '@antv/x6';
import type { NodeData, IfStep, Step } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';

function ensureIfStep(step?: Partial<IfStep>): IfStep {
  return {
    id: step?.id || createId('if'),
    type: 'if',
    name: step?.name,
    expression: step?.expression ?? '',
    next: step?.next ?? {},
  };
}

export function createIfData(): NodeData {
  const step = ensureIfStep();
  return { kind: 'step', nodeType: 'if', step };
}

function IfProperties(props: {
  step: IfStep;
  onChange: (next: NodeData) => void;
  setValidationError: (message: string | null) => void;
}) {
  const { step, onChange, setValidationError } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({ name: step.name ?? '', expression: step.expression ?? '' });
  }, [form, step]);

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={(_, values) => {
        setValidationError(null);
        const next: IfStep = ensureIfStep({ ...step, name: values.name, expression: values.expression });
        onChange({ kind: 'step', nodeType: 'if', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="If 节点名称" />
      </Form.Item>
      <Form.Item label="条件表达式" name="expression" rules={[{ required: true, message: '请输入条件表达式' }]}>
        <Input.TextArea autoSize={{ minRows: 2 }} placeholder="例如：#age > 18" />
      </Form.Item>
    </Form>
  );
}

export const ifNodeDefinition: NodeDefinition<'if'> = {
  type: 'if',
  label: '条件判断 (If)',
  category: '逻辑组件',
  buildNodeConfig: (data, position) => {
    const step = (data as any).step as Step;
    return {
      id: step.id,
      shape: 'rect',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      width: 180,
      height: 70,
      attrs: {
        body: {
          stroke: '#ff4d4f',
          fill: '#ffffff',
          rx: 10,
          ry: 10,
          strokeWidth: 2,
        },
        label: {
          text: (step as any).name || `if #${step.id}`,
          fill: '#1f1f1f',
          fontSize: 12,
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as IfStep;
    return step.name || `if #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as IfStep;
    return <IfProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};
