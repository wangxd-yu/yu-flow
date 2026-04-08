import React from 'react';
import { Button, Divider, Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { NodeMetadata } from '@antv/x6';
import type { NodeData, ReturnStep, Step, VarDef } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';

const { Text } = Typography;

function ensureReturnStep(step?: Partial<ReturnStep>): ReturnStep {
  return {
    id: step?.id || createId('return'),
    type: 'return',
    name: step?.name,
    mode: step?.mode ?? 'response',
    value: step?.value ?? '',
    response: step?.response ?? { statusCode: 200, headers: [], body: [] },
  };
}

export function createReturnData(): NodeData {
  const step = ensureReturnStep();
  return { kind: 'step', nodeType: 'return', step };
}

function ReturnProperties(props: {
  step: ReturnStep;
  onChange: (next: NodeData) => void;
  setValidationError: (message: string | null) => void;
}) {
  const { step, onChange, setValidationError } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({
      name: step.name ?? '',
      mode: step.mode ?? 'response',
      value: step.value ?? '',
      statusCode: step.response?.statusCode ?? 200,
      headers: step.response?.headers ?? [],
      body: step.response?.body ?? [],
    });
  }, [form, step]);

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={(_, values) => {
        setValidationError(null);
        const next: ReturnStep = ensureReturnStep({
          ...step,
          name: values.name,
          mode: values.mode,
          value: values.value,
          response: {
            statusCode: values.statusCode,
            headers: values.headers ?? [],
            body: values.body ?? [],
          },
        });
        onChange({ kind: 'step', nodeType: 'return', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="节点名称（可选）" />
      </Form.Item>

      <Form.Item label="类型" name="mode">
        <Select
          options={[
            { value: 'response', label: 'Response' },
            { value: 'output', label: 'Output（预留）', disabled: true },
          ]}
        />
      </Form.Item>

      <Form.Item noStyle shouldUpdate>
        {(f) => {
          const mode = f.getFieldValue('mode');
          if (mode !== 'response') {
            return (
              <Form.Item label="value" name="value" rules={[{ required: true, message: '请输入 value' }]}>
                <Input.TextArea autoSize={{ minRows: 4, maxRows: 10 }} placeholder="例如：#result" />
              </Form.Item>
            );
          }

          return (
            <>
              <Form.Item label="Status Code" name="statusCode">
                <InputNumber style={{ width: '100%' }} min={100} max={599} />
              </Form.Item>

              <Divider style={{ margin: '12px 0' }} />
              <Text strong>Headers</Text>
              <Form.List name="headers">
                {(fields, { add, remove }) => (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {fields.map((field) => (
                      <Space key={field.key} align="baseline">
                        <Form.Item noStyle name={[field.name, 'key']} initialValue={createId('var')}>
                          <Input style={{ display: 'none' }} />
                        </Form.Item>
                        <Form.Item
                          style={{ marginBottom: 0 }}
                          name={[field.name, 'name']}
                          rules={[{ required: true, message: 'name 必填' }]}
                        >
                          <Input placeholder="header 名称" style={{ width: 140 }} />
                        </Form.Item>
                        <Form.Item style={{ marginBottom: 0 }} name={[field.name, 'type']} initialValue={'string'}>
                          <Select
                            style={{ width: 110 }}
                            options={[
                              { value: 'string', label: 'string' },
                              { value: 'number', label: 'number' },
                              { value: 'boolean', label: 'boolean' },
                              { value: 'object', label: 'object' },
                              { value: 'array', label: 'array' },
                              { value: 'any', label: 'any' },
                            ]}
                          />
                        </Form.Item>
                        <Button size="small" onClick={() => remove(field.name)}>
                          删除
                        </Button>
                      </Space>
                    ))}
                    <Button
                      size="small"
                      onClick={() => add({ key: createId('var'), name: '', type: 'string' } satisfies VarDef)}
                    >
                      新增 Header
                    </Button>
                  </div>
                )}
              </Form.List>

              <Divider style={{ margin: '12px 0' }} />
              <Text strong>Body</Text>
              <Form.List name="body">
                {(fields, { add, remove }) => (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {fields.map((field) => (
                      <Space key={field.key} align="baseline">
                        <Form.Item noStyle name={[field.name, 'key']} initialValue={createId('var')}>
                          <Input style={{ display: 'none' }} />
                        </Form.Item>
                        <Form.Item
                          style={{ marginBottom: 0 }}
                          name={[field.name, 'name']}
                          rules={[{ required: true, message: 'name 必填' }]}
                        >
                          <Input placeholder="body 字段" style={{ width: 140 }} />
                        </Form.Item>
                        <Form.Item style={{ marginBottom: 0 }} name={[field.name, 'type']} initialValue={'string'}>
                          <Select
                            style={{ width: 110 }}
                            options={[
                              { value: 'string', label: 'string' },
                              { value: 'number', label: 'number' },
                              { value: 'boolean', label: 'boolean' },
                              { value: 'object', label: 'object' },
                              { value: 'array', label: 'array' },
                              { value: 'any', label: 'any' },
                            ]}
                          />
                        </Form.Item>
                        <Button size="small" onClick={() => remove(field.name)}>
                          删除
                        </Button>
                      </Space>
                    ))}
                    <Button size="small" onClick={() => add({ key: createId('var'), name: '', type: 'string' } satisfies VarDef)}>
                      新增 Body 字段
                    </Button>
                  </div>
                )}
              </Form.List>
            </>
          );
        }}
      </Form.Item>
    </Form>
  );
}

export const returnNodeDefinition: NodeDefinition<'return'> = {
  type: 'return',
  label: '返回（return）',
  category: '基础组件',
  buildNodeConfig: (data, position) => {
    const step = (data as any).step as Step;
    return {
      id: step.id,
      shape: 'rect',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      width: 280,
      height: 160,
      attrs: {
        body: { stroke: '#8c8c8c', fill: '#ffffff', rx: 10, ry: 10 },
        label: {
          text: (step as any).name || `Response`,
          fill: '#1f1f1f',
          fontSize: 12,
          textAnchor: 'start',
          refX: 12,
          refY: 12,
          textVerticalAnchor: 'top',
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as ReturnStep;
    return step.name || `return #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as ReturnStep;
    return <ReturnProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};

