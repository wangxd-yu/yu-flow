import React from 'react';
import { Form, Input, Select } from 'antd';
import { NodeMetadata } from '@antv/x6';
import type { NodeData, ParallelStep, Step } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';
import { safeJsonParse, safeJsonStringify } from '../../utils/json';

function ensureParallelStep(step?: Partial<ParallelStep>): ParallelStep {
  return {
    id: step?.id || createId('parallel'),
    type: 'parallel',
    name: step?.name,
    errorMode: step?.errorMode ?? 'FAST_FAIL',
    tasks: step?.tasks ?? [],
  };
}

export function createParallelData(): NodeData {
  const step = ensureParallelStep();
  return { kind: 'step', nodeType: 'parallel', step };
}

function ParallelProperties(props: {
  step: ParallelStep;
  onChange: (next: NodeData) => void;
  setValidationError: (message: string | null) => void;
}) {
  const { step, onChange, setValidationError } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({
      name: step.name ?? '',
      errorMode: step.errorMode ?? 'FAST_FAIL',
      tasksText: safeJsonStringify(step.tasks ?? []),
    });
  }, [form, step]);

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={(_, values) => {
        const tasksParsed = safeJsonParse(values.tasksText);
        if (tasksParsed.ok && tasksParsed.value && !Array.isArray(tasksParsed.value)) {
          setValidationError('tasks 必须是 JSON 数组');
          return;
        }
        setValidationError(tasksParsed.ok ? null : tasksParsed.message);

        const next: ParallelStep = ensureParallelStep({
          ...step,
          name: values.name,
          errorMode: values.errorMode,
          tasks: tasksParsed.ok ? (tasksParsed.value ?? []) : step.tasks,
        });
        onChange({ kind: 'step', nodeType: 'parallel', step: next });
      }}
    >
      <Form.Item label="名称" name="name">
        <Input placeholder="节点名称（可选）" />
      </Form.Item>
      <Form.Item label="errorMode" name="errorMode">
        <Select
          options={[
            { value: 'FAST_FAIL', label: 'FAST_FAIL' },
            { value: 'CONTINUE', label: 'CONTINUE' },
          ]}
        />
      </Form.Item>
      <Form.Item label="tasks（JSON 数组，嵌套 steps）" name="tasksText">
        <Input.TextArea autoSize={{ minRows: 6, maxRows: 14 }} placeholder='例如：[{"id":"t1","type":"set","expression":"a=1"}]' />
      </Form.Item>
    </Form>
  );
}

export const parallelNodeDefinition: NodeDefinition<'parallel'> = {
  type: 'parallel',
  label: '并行（parallel）',
  category: '高级组件',
  buildNodeConfig: (data, position) => {
    const step = (data as any).step as Step;
    return {
      id: step.id,
      shape: 'rect',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      width: 260,
      height: 64,
      attrs: {
        body: {
          stroke: '#1677ff',
          fill: '#ffffff',
          rx: 8,
          ry: 8,
        },
        label: {
          text: (step as any).name || `parallel #${step.id}`,
          fill: '#1f1f1f',
          fontSize: 12,
        },
      },
      data,
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as ParallelStep;
    return step.name || `parallel #${step.id}`;
  },
  renderProperties: ({ data, onChange, setValidationError }) => {
    const step = (data as any).step as ParallelStep;
    return <ParallelProperties step={step} onChange={onChange} setValidationError={setValidationError} />;
  },
};

