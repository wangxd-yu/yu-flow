import React from 'react';
import { Form, Input } from 'antd';
import { NodeMetadata } from '@antv/x6';
import type { ApiServiceCallStep, NodeData, Step } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';
import { safeJsonParse, safeJsonStringify } from '../../utils/json';

function ensureApiStep(step?: Partial<ApiServiceCallStep>): ApiServiceCallStep {
  return {
    id: step?.id || createId('api'),
    type: 'api',
    name: step?.name,
    serviceId: step?.serviceId ?? '',
    args: step?.args ?? {},
    output: step?.output ?? '',
    errorHandlers: step?.errorHandlers,
  };
}

export function createApiData(): NodeData {
  const step = ensureApiStep();
  return { kind: 'step', nodeType: 'api', step };
}

function ApiProperties(props: {
  step: ApiServiceCallStep;
  onChange: (next: NodeData) => void;
  setValidationError: (message: string | null) => void;
}) {
  const { step, onChange, setValidationError } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({
      name: step.name ?? '',
      serviceId: step.serviceId ?? '',
      argsObjectText: safeJsonStringify(step.args ?? {}),
      output: step.output ?? '',
    });
  }, [form, step]);

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={(_, values) => {
        const parsed = safeJsonParse(values.argsObjectText);
        if (parsed.ok && parsed.value && (typeof parsed.value !== 'object' || Array.isArray(parsed.value))) {
          setValidationError('args 必须是 JSON 对象');
          return;
        }
        setValidationError(parsed.ok ? null : parsed.message);
        const next: ApiServiceCallStep = ensureApiStep({
          ...step,
          name: values.name,
          serviceId: values.serviceId,
          output: values.output,
          args: parsed.ok ? (parsed.value ?? {}) : step.args,
        });
        onChange({ kind: 'step', nodeType: 'api', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="节点名称（可选）" />
      </Form.Item>
      <Form.Item label="serviceId" name="serviceId" rules={[{ required: true, message: '请输入 serviceId' }]}>
        <Input placeholder="例如：getUserDetail" />
      </Form.Item>
      <Form.Item label="args（JSON 对象）" name="argsObjectText">
        <Input.TextArea autoSize={{ minRows: 4, maxRows: 10 }} placeholder='例如：{"id":"#userId"}' />
      </Form.Item>
      <Form.Item label="output（写入变量名）" name="output">
        <Input placeholder="例如：detail" />
      </Form.Item>
    </Form>
  );
}

export const apiNodeDefinition: NodeDefinition<'api'> = {
  type: 'api',
  label: 'API 调用（api）',
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
          text: (step as any).name || `api #${step.id}`,
          fill: '#1f1f1f',
          fontSize: 12,
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as ApiServiceCallStep;
    return step.name || `api #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as ApiServiceCallStep;
    return <ApiProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};

