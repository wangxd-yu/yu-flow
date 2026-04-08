import React from 'react';
import { Form, Input, Button, Space, Divider } from 'antd';
import { NodeMetadata } from '@antv/x6';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { NodeData, SwitchStep, Step } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';

function ensureSwitchStep(step?: Partial<SwitchStep>): SwitchStep {
  return {
    id: step?.id || createId('switch'),
    type: 'switch',
    name: step?.name,
    expression: step?.expression ?? '',
    cases: step?.cases ?? [],
    next: step?.next ?? {},
  };
}

export function createSwitchData(): NodeData {
  const step = ensureSwitchStep();
  return { kind: 'step', nodeType: 'switch', step };
}

function SwitchProperties(props: {
  step: SwitchStep;
  onChange: (next: NodeData) => void;
  setValidationError: (message: string | null) => void;
}) {
  const { step, onChange, setValidationError } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({
      name: step.name ?? '',
      expression: step.expression ?? '',
      cases: step.cases ?? [],
    });
  }, [form, step]);

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={(_, values) => {
        setValidationError(null);
        const next: SwitchStep = ensureSwitchStep({
          ...step,
          name: values.name,
          expression: values.expression,
          cases: values.cases,
        });
        onChange({ kind: 'step', nodeType: 'switch', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="Switch 节点名称" />
      </Form.Item>
      <Form.Item label="选择表达式" name="expression" rules={[{ required: true, message: '请输入表达式' }]}>
        <Input placeholder="例如：#userRole" />
      </Form.Item>

      <Divider orientation="left" style={{ fontSize: 12 }}>匹配分支 (Cases)</Divider>
      
      <Form.List name="cases">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                <Form.Item
                  {...field}
                  rules={[{ required: true, message: '值不能为空' }]}
                  noStyle
                >
                  <Input placeholder="匹配值" style={{ width: 200 }} />
                </Form.Item>
                <DeleteOutlined onClick={() => remove(field.name)} style={{ color: '#ff4d4f' }} />
              </Space>
            ))}
            <Form.Item>
              <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                添加 Case
              </Button>
            </Form.Item>
          </>
        )}
      </Form.List>
    </Form>
  );
}

export const switchNodeDefinition: NodeDefinition<'switch'> = {
  type: 'switch',
  label: '多路选择 (Switch)',
  category: '逻辑组件',
  buildNodeConfig: (data, position) => {
    const step = (data as any).step as SwitchStep;
    const casesCount = step.cases?.length || 0;
    // 根据 cases 数量动态调整高度
    const height = 80 + (casesCount * 25); 

    return {
      id: step.id,
      shape: 'rect',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      width: 200,
      height: Math.max(height, 80),
      attrs: {
        body: {
          stroke: '#722ed1',
          fill: '#ffffff',
          rx: 10,
          ry: 10,
          strokeWidth: 2,
        },
        label: {
          text: (step as any).name || `switch #${step.id}`,
          fill: '#1f1f1f',
          fontSize: 12,
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as SwitchStep;
    return step.name || `switch #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as SwitchStep;
    return <SwitchProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};
