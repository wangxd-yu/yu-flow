import React from 'react';
import { Alert, Button, ColorPicker, Divider, Form, Input, Tabs, Typography } from 'antd';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { ProForm, ProFormText, ProFormSelect, ProFormDigit, ProFormTextArea } from '@ant-design/pro-components';
import type { Graph, Node } from '@antv/x6';
import { safeJsonParse, safeJsonStringify } from '../utils/json';
import type { NodeData, NodeType, Step } from '../types';
import { getNodeDefinition } from '../registry';
import { queryDataSourceList } from '@/pages/flow/dataSource/services/dataSource';

const { Text } = Typography;

function getNodeData(node: Node | null): NodeData | null {
  const data = node?.getData() as any;
  if (!data || typeof data !== 'object') return null;
  return data as NodeData;
}

export type PropertiesPanelProps = {
  graph: Graph | null;
  selectedNode: Node | null;
  onDeleteSelected: () => void;
  onApplyData: (nextData: NodeData) => void;
  // 全局配置相关
  globalConfig: any;
  onGlobalConfigChange: (values: any) => void;
  isEdit: boolean;
};

function StepJsonPanel(props: {
  node: Node;
  data: Extract<NodeData, { kind: 'step' }>;
  validationError: string | null;
  setValidationError: (msg: string | null) => void;
  onApplyData: (nextData: NodeData) => void;
}) {
  const { node, data, validationError, setValidationError, onApplyData } = props;
  const [form] = Form.useForm();

  React.useEffect(() => {
    form.setFieldsValue({ rawJson: safeJsonStringify(data.step) });
  }, [data, form]);

  const applyRawJson = React.useCallback(async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const parsed = safeJsonParse(values.rawJson);
    if (!parsed.ok) {
      setValidationError(parsed.message);
      return;
    }
    const next = parsed.value as Step;
    if (!next?.id || !next?.type) {
      setValidationError('Step JSON 必须包含 id 和 type');
      return;
    }
    if (next.id !== node.id) {
      setValidationError('不支持在此处修改 step.id（需保持画布 ID 与 step.id 一致）');
      return;
    }
    if (next.type !== data.nodeType) {
      setValidationError('不支持在此处修改 step.type');
      return;
    }
    setValidationError(null);
    onApplyData({ kind: 'step', nodeType: data.nodeType, step: next });
  }, [data.nodeType, form, node.id, onApplyData, setValidationError]);

  return (
    <div>
      {validationError ? (
        <div style={{ marginBottom: 10 }}>
          <Alert type="warning" showIcon message="配置校验提示" description={validationError} />
        </div>
      ) : null}
      <Form form={form} layout="vertical">
        <Form.Item
          label="Step JSON"
          name="rawJson"
          rules={[
            {
              validator: (_, v) => {
                const parsed = safeJsonParse(v);
                if (parsed.ok) return Promise.resolve();
                return Promise.reject(new Error(parsed.message));
              },
            },
          ]}
        >
          <Input.TextArea autoSize={{ minRows: 18, maxRows: 28 }} />
        </Form.Item>
      </Form>
      <Button size="small" onClick={applyRawJson}>
        应用 JSON
      </Button>
    </div>
  );
}

export default function PropertiesPanel(props: PropertiesPanelProps) {
  const { graph, selectedNode, onDeleteSelected, onApplyData, globalConfig, onGlobalConfigChange, isEdit } = props;
  const data = getNodeData(selectedNode);
  const nodeType = (data?.nodeType as NodeType) || null;
  const def = nodeType ? getNodeDefinition(nodeType) : null;
  const accentColor = (globalConfig as any)?.uiConfig?.accentColor || '#ff7a45';

  const [validationError, setValidationError] = React.useState<string | null>(null);
  const [expanded, setExpanded] = React.useState(true);

  const debugStartEnabled = React.useMemo(() => {
    if (typeof window === 'undefined') return false;
    const combined = `${window.location?.search || ''}&${window.location?.hash || ''}`;
    return combined.includes('debugStart=1') || (window as any).__SDS_FLOW_DEBUG_START__ === true;
  }, []);

  React.useEffect(() => {
    setValidationError(null);
  }, [data]);

  // 全局配置面板渲染
  const renderGlobalConfig = () => (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 12 }}>
        <Text type="secondary">主题色</Text>
        <ColorPicker
          value={accentColor}
          showText
          format="hex"
          onChange={(_, hex) => onGlobalConfigChange({ uiConfig: { accentColor: hex } })}
        />
      </div>
      <ProForm
        initialValues={globalConfig}
        submitter={false}
        onValuesChange={(_, allValues) => onGlobalConfigChange(allValues)}
        layout="vertical"
        grid={true}
        rowProps={{ gutter: [16, 0] }}
      >
        <ProFormText name="name" label="名称" placeholder="请输入名称" rules={[{ required: true }]} colProps={{ span: 24 }} />
        <ProFormText name="url" label="URL" placeholder="请输入URL" rules={[{ required: true }]} disabled={isEdit} colProps={{ span: 24 }} />
        <ProFormSelect
          name="method"
          label="方法"
          valueEnum={{ GET: 'GET', POST: 'POST', PUT: 'PUT', DELETE: 'DELETE' }}
          rules={[{ required: true }]}
          colProps={{ span: 24 }}
        />
        <Tabs
          style={{ width: '100%', marginTop: 12 }}
          items={[
            {
              key: 'basic',
              label: '基本信息',
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <ProFormText name="module" label="模块" />
                  <ProFormText name="info" label="描述" />
                  <ProFormText name="version" label="版本" />
                  <ProFormSelect
                    name="publishStatus"
                    label="发布状态"
                    options={[{ value: 0, label: '未发布' }, { value: 1, label: '已发布' }]}
                    rules={[{ required: true }]}
                  />
                  <ProFormDigit name="level" label="优先级" min={1} max={10} />
                  <ProFormSelect
                    name="tags"
                    label="标签"
                    mode="tags"
                    placeholder="最多5个标签"
                    fieldProps={{ maxTagCount: 5, tokenSeparators: [','] }}
                  />
                </div>
              ),
            },
            {
              key: 'config',
              label: '配置',
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <ProFormSelect
                    name={['flowService', 'type']}
                    label="类型"
                    valueEnum={{ DB: '数据库', JSON: 'JSON', STRING: '字符串', FLOW: '逻辑编排' }}
                    rules={[{ required: true }]}
                  />
                  <ProForm.Item noStyle shouldUpdate>
                    {(form) => {
                      const type = form.getFieldValue(['flowService', 'type']);
                      return type === 'DB' ? (
                        <>
                          <ProFormSelect
                            name={['flowService', 'datasource']}
                            label="数据源"
                            rules={[{ required: true }]}
                            request={async () =>
                              (await queryDataSourceList()).map((item) => ({ label: item.name, value: item.code }))
                            }
                          />
                          <ProFormSelect
                            name={['flowService', 'dbType']}
                            label="DB类型"
                            valueEnum={{ PAGE: '分页', LIST: '列表', OBJECT: '对象', UPDATE: '更新' }}
                            rules={[{ required: true }]}
                          />
                        </>
                      ) : null;
                    }}
                  </ProForm.Item>
                </div>
              ),
            },
            {
              key: 'response',
              label: '返回值',
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <ProFormTextArea name="wrapSuccess" label="成功返回包装" fieldProps={{ autoSize: { minRows: 4 } }} />
                  <ProFormTextArea name="wrapError" label="失败返回包装" fieldProps={{ autoSize: { minRows: 4 } }} />
                </div>
              ),
            },
          ]}
        />
      </ProForm>
    </div>
  );

  return (
    <div
      style={{
        position: 'relative',
        height: '100%',
        width: expanded ? 380 : 0,
        transition: 'width 0.3s cubic-bezier(0.23, 1, 0.32, 1)',
        flexShrink: 0,
        zIndex: 10
      }}
    >
      {/* 展开/收起按钮 - 悬浮在左侧 */}
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          position: 'absolute',
          left: -18, // 突出在面板外侧
          top: '50%',
          transform: 'translateY(-50%)', // 垂直居中
          width: 18,
          height: 80, // 更长一点
          backgroundColor: '#fff',
          border: '1px solid #e5e6eb',
          borderRight: 'none',
          borderTopLeftRadius: 8,
          borderBottomLeftRadius: 8,
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#86909c',
          boxShadow: '-4px 0 8px rgba(0,0,0,0.08)', // 更明显的阴影
          zIndex: 1000,
        }}
      >
        {expanded ? <RightOutlined style={{ fontSize: 12 }} /> : <LeftOutlined style={{ fontSize: 12 }} />}
      </div>

      {/* 内容容器 - 负责裁切内容 */}
      <div style={{ width: '100%', height: '100%', overflow: 'hidden', backgroundColor: '#ffffff', position: 'relative' }}>
        {/* 固定宽度的内容区，确保收起时内容不换行 */}
        <div style={{ width: 380, height: '100%', borderLeft: '1px solid #e5e6eb', padding: 12, overflow: 'auto', position: 'absolute', right: 0 }}>
          <Text strong>{selectedNode ? '节点属性' : '全局属性'}</Text>
          <Divider style={{ margin: '12px 0' }} />

          {debugStartEnabled && (data as any)?.kind === 'ui' && (data as any)?.nodeType === 'input' ? (
            <div style={{ marginBottom: 10 }}>
              <Alert
                type="info"
                showIcon
                message="Start Debug 已开启"
                description={
                  <div style={{ wordBreak: 'break-all' }}>
                    <div>debugStart=1 生效中</div>
                    <div>{String((window as any).__SDS_FLOW_DEBUG_START_INPUT_TSX__ || '')}</div>
                  </div>
                }
              />
            </div>
          ) : null}

          {!selectedNode ? (
            renderGlobalConfig()
          ) : (
            <div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                <Button danger size="small" onClick={onDeleteSelected}>
                  删除节点
                </Button>
              </div>

              {!data || !def ? (
                <Text type="secondary">无法识别的节点数据</Text>
              ) : (
                <Tabs
                  items={[
                    {
                      key: 'form',
                      label: '表单',
                      children: (
                        <div>
                          {def.renderProperties ? def.renderProperties({ data, onChange: onApplyData, setValidationError }) : null}
                        </div>
                      ),
                    },
                    ...(data.kind === 'step'
                      ? [
                        {
                          key: 'json',
                          label: 'JSON',
                          children: (
                            <StepJsonPanel
                              node={selectedNode}
                              data={data}
                              validationError={validationError}
                              setValidationError={setValidationError}
                              onApplyData={onApplyData}
                            />
                          ),
                        },
                      ]
                      : []),
                  ]}
                />
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
