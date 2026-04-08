import React, { useState, useEffect, useCallback } from 'react';
import { Tabs, Empty, Form, Input, Select, Radio, Typography } from 'antd';
import {
  InfoCircleOutlined, SendOutlined, CheckCircleOutlined,
} from '@ant-design/icons';
import type { ApiContract, BaseInfo, BodyType, SchemaNode } from './types';
import SchemaTreeTable from './SchemaTreeTable';

// ─── Props ──────────────────────────────────────────────────

export interface ApiContractDesignerProps {
  /** 初始值（编辑场景传入已有契约数据） */
  initialValue?: ApiContract;
  /** 契约变更回调 */
  onChange?: (value: ApiContract) => void;
}

// ─── 默认空契约 ─────────────────────────────────────────────

const createEmptyContract = (): ApiContract => ({
  baseInfo: {
    path: '',
    method: 'GET',
    summary: '',
    tags: [],
  },
  request: {
    headers: [],
    query: [],
    body: [],
    bodyType: 'none',
    rawBody: '',
  },
  responses: {
    '200': {
      statusCode: 200,
      description: '成功',
      body: [],
    },
  },
});

// ─── 常量选项 ────────────────────────────────────────────────

const METHOD_OPTIONS = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
];

const BODY_TYPE_OPTIONS: { label: string; value: BodyType }[] = [
  { label: 'none', value: 'none' },
  { label: 'form-data', value: 'form-data' },
  { label: 'x-www-form-urlencoded', value: 'x-www-form-urlencoded' },
  { label: 'json', value: 'json' },
  { label: 'xml', value: 'xml' },
  { label: 'raw', value: 'raw' },
];

// ─── 组件 ───────────────────────────────────────────────────

const ApiContractDesigner: React.FC<ApiContractDesignerProps> = ({
  initialValue,
  onChange,
}) => {
  const [contract, setContract] = useState<ApiContract>(
    initialValue ?? createEmptyContract(),
  );

  // 当外部 initialValue 变化时同步内部状态
  useEffect(() => {
    if (initialValue) {
      setContract(initialValue);
    }
  }, [initialValue]);

  // 通用状态更新 & 回调
  const updateContract = useCallback(
    (next: ApiContract) => {
      setContract(next);
      onChange?.(next);
    },
    [onChange],
  );

  // ─── BaseInfo 表单变更 ───────────────────────────────────

  const handleBaseInfoChange = useCallback(
    (field: keyof BaseInfo, value: any) => {
      updateContract({
        ...contract,
        baseInfo: { ...contract.baseInfo, [field]: value },
      });
    },
    [contract, updateContract],
  );

  // ─── Request 子模块变更 ──────────────────────────────────

  const handleRequestFieldChange = useCallback(
    (field: string, value: any) => {
      updateContract({
        ...contract,
        request: { ...contract.request, [field]: value },
      });
    },
    [contract, updateContract],
  );

  // ─── 渲染：基础信息 Tab ─────────────────────────────────

  const renderBaseInfo = () => (
    <Form layout="vertical" style={{ maxWidth: 720 }}>
      <Form.Item label="接口名称 (summary)" required>
        <Input
          placeholder="如：获取用户列表"
          value={contract.baseInfo.summary}
          onChange={(e) => handleBaseInfoChange('summary', e.target.value)}
        />
      </Form.Item>
      <Form.Item label="路径 (path)" required>
        <Input
          addonBefore="/"
          placeholder="api/v1/users"
          value={contract.baseInfo.path}
          onChange={(e) => handleBaseInfoChange('path', e.target.value)}
        />
      </Form.Item>
      <Form.Item label="请求方式 (method)" required>
        <Select
          value={contract.baseInfo.method}
          options={METHOD_OPTIONS}
          onChange={(v) => handleBaseInfoChange('method', v)}
          style={{ width: 200 }}
        />
      </Form.Item>
      <Form.Item label="标签分组 (tags)">
        <Select
          mode="tags"
          value={contract.baseInfo.tags}
          placeholder="输入后回车添加标签"
          onChange={(v) => handleBaseInfoChange('tags', v)}
          tokenSeparators={[',']}
          style={{ width: '100%' }}
        />
      </Form.Item>
    </Form>
  );

  // ─── 渲染：Body 面板内容（按 bodyType 切换） ─────────────

  const renderBodyContent = () => {
    const { bodyType } = contract.request;

    switch (bodyType) {
      case 'none':
        return (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="该请求没有 Body"
            style={{ padding: '40px 0' }}
          />
        );

      case 'json':
        return (
          <SchemaTreeTable
            value={contract.request.body}
            onChange={(nodes) => handleRequestFieldChange('body', nodes)}
          />
        );

      case 'form-data':
      case 'x-www-form-urlencoded':
        return (
          <SchemaTreeTable
            flat
            value={contract.request.body}
            onChange={(nodes) => handleRequestFieldChange('body', nodes)}
          />
        );

      case 'raw':
      case 'xml':
        return (
          <Input.TextArea
            value={contract.request.rawBody ?? ''}
            onChange={(e) => handleRequestFieldChange('rawBody', e.target.value)}
            placeholder={bodyType === 'xml' ? '请输入 XML 内容...' : '请输入原始内容...'}
            autoSize={{ minRows: 8, maxRows: 20 }}
            style={{ fontFamily: 'monospace', fontSize: 13 }}
          />
        );

      default:
        return null;
    }
  };

  // ─── 渲染：请求参数 Tab（二级 Tabs） ─────────────────────

  const renderRequest = () => {
    const requestTabItems = [
      {
        key: 'params',
        label: 'Params',
        children: (
          <SchemaTreeTable
            flat
            value={contract.request.query}
            onChange={(nodes) => handleRequestFieldChange('query', nodes)}
          />
        ),
      },
      {
        key: 'body',
        label: 'Body',
        children: (
          <div>
            <div style={{ marginBottom: 12 }}>
              <Radio.Group
                value={contract.request.bodyType}
                onChange={(e) => handleRequestFieldChange('bodyType', e.target.value)}
                optionType="button"
                buttonStyle="solid"
                size="small"
              >
                {BODY_TYPE_OPTIONS.map((opt) => (
                  <Radio.Button key={opt.value} value={opt.value}>
                    {opt.label}
                  </Radio.Button>
                ))}
              </Radio.Group>
            </div>
            {renderBodyContent()}
          </div>
        ),
      },
      {
        key: 'headers',
        label: 'Headers',
        children: (
          <SchemaTreeTable
            flat
            value={contract.request.headers}
            onChange={(nodes) => handleRequestFieldChange('headers', nodes)}
          />
        ),
      },
    ];

    return (
      <Tabs
        defaultActiveKey="params"
        items={requestTabItems}
        size="small"
      />
    );
  };

  // ─── 一级 Tab 配置 ────────────────────────────────────────

  const tabItems = [
    {
      key: 'baseInfo',
      label: (
        <span>
          <InfoCircleOutlined style={{ marginRight: 6 }} />
          基础信息
        </span>
      ),
      children: renderBaseInfo(),
    },
    {
      key: 'request',
      label: (
        <span>
          <SendOutlined style={{ marginRight: 6 }} />
          请求参数
        </span>
      ),
      children: renderRequest(),
    },
    {
      key: 'responses',
      label: (
        <span>
          <CheckCircleOutlined style={{ marginRight: 6 }} />
          响应结果
        </span>
      ),
      children: (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="响应结果面板（待实现）"
        />
      ),
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Tabs
        defaultActiveKey="baseInfo"
        items={tabItems}
        type="card"
        style={{ minHeight: 300 }}
      />
    </div>
  );
};

export default ApiContractDesigner;
