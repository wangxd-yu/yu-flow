import React from 'react';
import { Form, Input } from 'antd';
import { NodeMetadata } from '@antv/x6';
import type { NodeData, SetVarStep, Step } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';

function ensureSetStep(step?: Partial<SetVarStep>): SetVarStep {
  return {
    id: step?.id || createId('set'),
    type: 'set',
    name: step?.name,
    expression: step?.expression ?? '',
  };
}

export function createSetData(): NodeData {
  const step = ensureSetStep();
  return { kind: 'step', nodeType: 'set', step };
}

function SetProperties(props: {
  step: SetVarStep;
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
        const next: SetVarStep = ensureSetStep({ ...step, name: values.name, expression: values.expression });
        onChange({ kind: 'step', nodeType: 'set', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="节点名称（可选）" />
      </Form.Item>
      <Form.Item label="expression" name="expression" rules={[{ required: true, message: '请输入 expression' }]}>
        <Input.TextArea autoSize={{ minRows: 4, maxRows: 10 }} placeholder="例如：a=#FP.id" />
      </Form.Item>
    </Form>
  );
}

export const setNodeDefinition: NodeDefinition<'set'> = {
  type: 'set',
  label: '赋值（set）',
  category: '基础组件',
  buildNodeConfig: (data, position) => {
    const step = (data as any).step as Step;
    return {
      id: step.id,
      shape: 'rect',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      width: 240,
      height: 56,
      attrs: {
        body: {
          stroke: '#1677ff',
          fill: '#ffffff',
          rx: 8,
          ry: 8,
        },
        label: {
          text: (step as any).name || `set #${step.id}`,
          fill: '#1f1f1f',
          fontSize: 12,
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as SetVarStep;
    return step.name || `set #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as SetVarStep;
    return <SetProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};

