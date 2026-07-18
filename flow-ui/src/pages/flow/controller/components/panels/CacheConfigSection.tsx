/**
 * CacheConfigSection.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「查询响应缓存」配置 — 用于 API 接口定义页面的 BasicInfoPanel
 * 启用开关已上移至卡片标题栏；本组件仅在开启时渲染，故此处均为已启用状态
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React from 'react';
import {
  AutoComplete, Button, Col, Form, InputNumber, Row, Select, Space, Switch, Tag, Typography,
} from 'antd';
import type { FormInstance } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';

const { Text } = Typography;

export interface CacheConfigSectionProps {
  form: FormInstance;
  /** 来自契约的参数名提示 */
  paramSuggestions?: Array<{ source: string; name: string }>;
}

const SOURCE_OPTIONS = [
  { label: 'Query', value: 'query' },
  { label: 'Body', value: 'body' },
  { label: 'Path', value: 'path' },
  { label: 'Header', value: 'header' },
];

const CacheConfigSection: React.FC<CacheConfigSectionProps> = ({ form, paramSuggestions = [] }) => {
  const keyParams = Form.useWatch('cacheKeyParams', form) || [];

  const optionsForSource = (source?: string) =>
    paramSuggestions
      .filter((p) => p?.name && (!source || p.source === source))
      .map((p) => ({ value: p.name, label: p.name }));

  const quickAdd = (source: string, name: string) => {
    const exists = (keyParams as Array<{ source?: string; name?: string }>).some(
      (p) => p?.source === source && p?.name === name,
    );
    if (exists) return;
    form.setFieldsValue({
      cacheKeyParams: [...keyParams, { source, name }],
    });
  };

  return (
    <div>
      <div
        style={{
          marginBottom: 12,
          padding: '6px 10px',
          background: '#e6f4ff',
          border: '1px solid #bae0ff',
          borderRadius: 6,
          fontSize: 12,
          color: '#4e5969',
          lineHeight: 1.5,
        }}
      >
        相同入参命中 Redis 缓存，跳过实际执行；仅缓存成功响应。Redis 异常时自动降级直连。
      </div>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="cacheTtlSeconds"
            label="缓存时长（秒）"
            rules={[{ required: true, message: '请输入 TTL' }]}
            initialValue={300}
            extra="默认 300 秒，最长 86400（1 天）"
          >
            <InputNumber
              min={1}
              max={86400}
              style={{ width: '100%' }}
              placeholder="默认 300"
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="cacheIncludePageable"
            label="分页参数计入 Key"
            valuePropName="checked"
            initialValue={true}
            extra="开启后 page / size / sort 参与区分缓存"
          >
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
        </Col>
      </Row>

      {paramSuggestions.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <Text type="secondary" style={{ fontSize: 12, marginRight: 8 }}>
            从请求契约快速添加 Key 参数：
          </Text>
          {paramSuggestions.map((p) => (
            <Tag
              key={`${p.source}-${p.name}`}
              style={{ cursor: 'pointer', marginBottom: 4 }}
              onClick={() => quickAdd(p.source, p.name)}
            >
              {p.source}.{p.name}
            </Tag>
          ))}
        </div>
      )}

      <Form.Item
        label="缓存 Key 参数（根据这些入参区分缓存）"
        extra="例如选择 query.userId：不同 userId 各自一份缓存；不配参数则全接口共用一份（仍受分页开关影响）"
        style={{ marginBottom: 0 }}
      >
        <Form.List name="cacheKeyParams">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => {
                const source = form.getFieldValue(['cacheKeyParams', field.name, 'source']);
                return (
                  <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item
                      {...field}
                      name={[field.name, 'source']}
                      rules={[{ required: true, message: '来源' }]}
                      style={{ marginBottom: 0, width: 120 }}
                    >
                      <Select options={SOURCE_OPTIONS} placeholder="来源" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'name']}
                      rules={[{ required: true, message: '参数名' }]}
                      style={{ marginBottom: 0, width: 220 }}
                    >
                      <AutoComplete
                        options={optionsForSource(source)}
                        placeholder="参数名，如 userId"
                        filterOption={(input, option) =>
                          String(option?.value ?? '')
                            .toLowerCase()
                            .includes(input.toLowerCase())
                        }
                      />
                    </Form.Item>
                    <MinusCircleOutlined
                      onClick={() => remove(field.name)}
                      style={{ color: '#ff4d4f', cursor: 'pointer' }}
                    />
                  </Space>
                );
              })}
              <Button
                type="dashed"
                onClick={() => add({ source: 'query' })}
                block
                icon={<PlusOutlined />}
              >
                添加缓存参数
              </Button>
            </>
          )}
        </Form.List>
      </Form.Item>
    </div>
  );
};

export default React.memo(CacheConfigSection);
