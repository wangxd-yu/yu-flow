import React from 'react';
import { Form, Input } from 'antd';
import { NodeMetadata } from '@antv/x6';
import type { NodeData, ServiceCallStep, Step } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';
import { safeJsonParse, safeJsonStringify } from '../../utils/json';

function ensureCallStep(step?: Partial<ServiceCallStep>): ServiceCallStep {
  return {
    id: step?.id || createId('call'),
    type: 'call',
    name: step?.name, // Add name
    service: step?.service ?? '',
    method: step?.method ?? '',
    args: step?.args ?? [],
    output: step?.output ?? '',
    errorHandlers: step?.errorHandlers,
  };
}

export function createCallData(): NodeData {
  const step = ensureCallStep();
  return { kind: 'step', nodeType: 'call', step };
}

function CallProperties(props: {
  step: ServiceCallStep;
  onChange: (next: NodeData) => void;
  setValidationError: (message: string | null) => void;
}) {
  const { step, onChange, setValidationError } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({
      name: step.name ?? '',
      service: step.service ?? '',
      method: step.method ?? '',
      argsText: safeJsonStringify(step.args ?? []),
      output: step.output ?? '',
    });
  }, [form, step]);

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={(_, values) => {
        const parsed = safeJsonParse(values.argsText);
        if (parsed.ok && parsed.value && !Array.isArray(parsed.value)) {
          setValidationError('args 必须是 JSON 数组');
          return;
        }
        setValidationError(parsed.ok ? null : parsed.message);
        const next: ServiceCallStep = ensureCallStep({
          ...step,
          name: values.name,
          service: values.service,
          method: values.method,
          output: values.output,
          args: parsed.ok ? (parsed.value ?? []) : step.args,
        });
        onChange({ kind: 'step', nodeType: 'call', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="节点名称（可选）" />
      </Form.Item>
      <Form.Item label="service" name="service" rules={[{ required: true, message: '请输入 service' }]}>
        <Input placeholder="例如：userService" />
      </Form.Item>
      <Form.Item label="method" name="method" rules={[{ required: true, message: '请输入 method' }]}>
        <Input placeholder="例如：queryUser" />
      </Form.Item>
      <Form.Item label="args（JSON 数组）" name="argsText">
        <Input.TextArea autoSize={{ minRows: 4, maxRows: 10 }} placeholder='例如：["#userId"]' />
      </Form.Item>
      <Form.Item label="output（写入变量名）" name="output">
        <Input placeholder="例如：user" />
      </Form.Item>
    </Form>
  );
}

export const callNodeDefinition: NodeDefinition<'call'> = {
  type: 'call',
  label: '服务调用（call）',
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
          text: step.name || `call #${step.id}`,
          fill: '#1f1f1f',
          fontSize: 12,
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as ServiceCallStep;
    return step.name || `call #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as ServiceCallStep;
    return <CallProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};

