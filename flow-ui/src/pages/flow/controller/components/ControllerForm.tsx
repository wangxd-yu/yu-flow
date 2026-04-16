/**
 * ControllerForm.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * Yu Flow · API 动态编排平台 — 核心配置页面 (PageContainer 架构)
 *
 * 重构后的瘦编排器：所有 Tab 内容已拆分为独立的 Panel 组件，
 * 本文件仅负责：
 *   1. 顶级状态管理与生命周期（数据初始化 / 提交 Payload 组装）
 *   2. Header 区域（Method + URL + Name + 发布状态 + 保存/取消）
 *   3. Tab 路由（将状态通过 Props 下发到各 Panel）
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Drawer, message, Button, Form, Input, Select,
  Space, Tag, Dropdown, Tooltip, Popover
} from 'antd';
import type { MenuProps } from 'antd';
import { SaveOutlined, CloseOutlined, CopyOutlined } from '@ant-design/icons';
import { merge } from 'lodash';
import { PageContainer } from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { addAutoApiConfig, updateAutoApiConfig } from '../services/flowController';

// ── Panel 子组件 ──
import ImplementationPanel from './panels/ImplementationPanel';
import ReqSchemaPanel from './panels/ReqSchemaPanel';
import ResSchemaPanel from './panels/ResSchemaPanel';
import BasicInfoPanel from './panels/BasicInfoPanel';
import type { EngineMode } from './panels/ImplementationPanel';
import type { SchemaNode, BodyType } from './ApiContractDesigner/types';


// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

export type ControllerFormV2Props = {
  onCancel: () => void;
  onSubmit: (success: boolean) => void;
  modalVisible: boolean;
  values?: Partial<any>;
  isEdit: boolean;
  addonDebuggerComponent?: React.ComponentType<{
    dslContent?: string;
    apiUrl?: string;
    apiMethod?: string;
  }>;
};

/** Tab Key 类型 */
type TabKey = 'implementation' | 'req-schema' | 'res-schema' | 'basic-info';

/** HTTP Method → 主题色映射 */
const METHOD_COLORS: Record<string, string> = {
  GET: '#52c41a',
  POST: '#1677ff',
  PUT: '#faad14',
  DELETE: '#ff4d4f',
  PATCH: '#722ed1',
};

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

// ═══════════════════════════════════════════════════════════════════════════
//  主组件（瘦编排器）
// ═══════════════════════════════════════════════════════════════════════════

// ── 高内聚组件：API 业务路径输入框 ──
const ApiPathInput = React.forwardRef<any, {
  value?: string;
  onChange?: (val: string) => void;
  disabled?: boolean;
  status?: "" | "error" | "warning";
  className?: string;
  [key: string]: any;
}>(({ value, onChange, disabled, status, className, ...rest }, ref) => {
  const [prefix, setPrefix] = useState<string>('');

  useEffect(() => {
    // 从后端接口获取系统级前缀
    request('/flow-api/sys-configs/key/SYSTEM_PREFIX', { method: 'GET' })
      .then((res: any) => {
        const data = typeof res === 'string' ? res : res?.data;
        setPrefix(data ? data : '/');
      })
      .catch(() => {
        setPrefix('/');
      });
  }, []);

  const handleCopy = () => {
    // 处理完整 URL，合并时去除可能会出现的双斜杠
    let fullUrl = `${prefix}${value || ''}`;
    fullUrl = fullUrl.replace(/(?<!:)\/\/+/g, '/');

    if (navigator.clipboard) {
      navigator.clipboard.writeText(fullUrl).then(() => {
        message.success('完整 URL 已复制');
      }).catch(() => {
        message.error('复制失败，请重试');
      });
    } else {
      const input = document.createElement('input');
      input.value = fullUrl;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      message.success('完整 URL 已复制');
    }
  };

  return (
    <>
      <style>{`
        /* 全局正常状态（未发布）：前缀背景色置为白色，保持和主输入框一致 */
        .api-path-input-custom:not(.flow-api-path-disabled) .ant-input-group-addon {
          background-color: #ffffff !important;
        }

        /* 置灰状态（已发布）：覆盖外层 wrapper、input、前缀的背景色为灰色 */
        .flow-api-path-disabled, 
        .flow-api-path-disabled .ant-input-affix-wrapper,
        .flow-api-path-disabled input, 
        .flow-api-path-disabled .ant-input-group-addon {
          background-color: #f5f5f5 !important;
          color: rgba(0, 0, 0, 0.25) !important;
          cursor: pointer !important;
        }
        
        .flow-api-path-disabled:focus, 
        .flow-api-path-disabled:focus-within,
        .flow-api-path-disabled .ant-input-affix-wrapper:focus, 
        .flow-api-path-disabled .ant-input-affix-wrapper:focus-within {
           box-shadow: none !important;
           border-color: #d9d9d9 !important;
        }
      `}</style>
      <Input
        {...rest}
        ref={ref}
        className={`${className || ''} api-path-input-custom ${disabled ? 'flow-api-path-disabled' : ''}`.trim()}
        value={value}
        onChange={(e) => {
          if (!disabled) {
            onChange?.(e.target.value);
          }
        }}
        readOnly={disabled}
        onClick={(e) => {
          if (disabled) {
            message.info("当前接口已在线上运行，修改路径将导致现有调用方报错。若需修改，请先下线该接口。");
          }
          if (rest.onClick) {
            rest.onClick(e);
          }
        }}
        placeholder="请输入业务路径，例如: /user/info"
        addonBefore={prefix === '/' ? '/' : prefix}
        suffix={
          <Tooltip title="复制完整 URL">
            <CopyOutlined
              onClick={(e) => {
                e.stopPropagation();
                handleCopy();
              }}
              style={{ cursor: 'pointer', color: '#1677ff', transition: 'color 0.3s' }}
              onMouseEnter={(e) => (e.currentTarget.style.color = '#4096ff')}
              onMouseLeave={(e) => (e.currentTarget.style.color = '#1677ff')}
            />
          </Tooltip>
        }
        status={status}
        style={{ flex: 1, fontFamily: 'monospace', ...rest.style }}
      />
    </>
  );
});

const ControllerFormV2: React.FC<ControllerFormV2Props> = ({
  modalVisible, onCancel, onSubmit, values = {}, isEdit, addonDebuggerComponent: AddonDebuggerComponent
}) => {
  // ─── Tab 状态 ──────────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState<TabKey>('implementation');

  // ─── 表单实例 ──────────────────────────────────────────────────────
  const [form] = Form.useForm();

  // ─── 预处理 Values ───────────────────────────────────────────────────
  const processedValues = useMemo(() => {
    if (!values) return {};
    const processed = { ...values };
    if (processed.tags && typeof processed.tags === 'string') {
      processed.tags = processed.tags.split(',').map((t: string) => t.trim()).filter(Boolean);
    }
    if (processed.flowService) {
      processed.serviceType = processed.flowService.type;
      processed.config = processed.flowService.script;
      processed.datasource = processed.flowService.datasource;
      processed.responseType = processed.flowService.dbType || processed.responseType || processed.type;
      delete processed.flowService;
    }
    if (processed.type && !processed.responseType) {
      processed.responseType = processed.type;
    }
    return processed;
  }, [values]);

  // ─── 地址栏状态 ────────────────────────────────────────────────────
  const [method, setMethod] = useState<string>('GET');
  const [url, setUrl] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [submitAttempted, setSubmitAttempted] = useState<boolean>(false);
  const [urlConflictMsg, setUrlConflictMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!url || !method) {
      setUrlConflictMsg(null);
      return;
    }
    // 编辑状态下未修改则跳过查重
    if (isEdit && url === processedValues?.url && method === processedValues?.method) {
      setUrlConflictMsg(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await request<any>('/flow-api/api/check-exact', {
          method: 'GET',
          params: { method, url },
        });
        if (res?.data === true || res === true) {
          setUrlConflictMsg('接口路径已存在/冲突');
        } else {
          setUrlConflictMsg(null);
        }
      } catch (e: any) {
        setUrlConflictMsg('接口路径查重失败');
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [method, url, isEdit, processedValues]);

  // ─── 从 URL 自动提取 Path 参数 ─────────────────────────────
  useEffect(() => {
    if (!url) {
      setPathParams([]);
      return;
    }
    // 匹配 {paramName} 或 :paramName 两种风格
    const matches = Array.from(url.matchAll(/\{([^}]+)\}|:([A-Za-z_][A-Za-z0-9_]*)/g));
    const paramNames = matches.map((m) => m[1] || m[2]).filter(Boolean);

    if (paramNames.length === 0) {
      setPathParams([]);
      return;
    }

    setPathParams((prev) => {
      const existingMap = new Map(prev.map((n) => [n.name, n]));
      return paramNames.map((name) => {
        // 保留已有节点（用户可能修改了 type / description 等）
        if (existingMap.has(name)) return existingMap.get(name)!;
        return {
          id: `path_${name}_${Date.now()}`,
          name,
          type: 'string' as const,
          required: true,
          description: '',
        };
      });
    });
  }, [url]);

  // ─── 发布状态 ──────────────────────────────────────────────────────
  const [publishStatus, setPublishStatus] = useState<0 | 1>(0);

  // ─── 服务实现: 引擎模式 ───────────────────────────────────────────
  const [engineMode, setEngineMode] = useState<EngineMode>('FLOW');

  // ─── 4 个隔离的内容 State（状态绝对隔离） ────────────────────────
  const [dslContent, setDslContent] = useState<string>('');
  const [sqlContent, setSqlContent] = useState<string>('');
  const [jsonContent, setJsonContent] = useState<string>('');
  const [textContent, setTextContent] = useState<string>('');

  // ─── 请求参数契约 ─────────────────────────────────────────────────
  const [queryParams, setQueryParams] = useState<SchemaNode[]>([]);
  const [pathParams, setPathParams] = useState<SchemaNode[]>([]);
  const [headers, setHeaders] = useState<SchemaNode[]>([]);
  const [bodyNodes, setBodyNodes] = useState<SchemaNode[]>([]);
  const [bodyType, setBodyType] = useState<BodyType>('none');
  const [rawBody, setRawBody] = useState<string>('');

  // ─── 响应契约 ─────────────────────────────────────────────────────
  const [responseBody, setResponseBody] = useState<SchemaNode[]>([]);
  const [responseDesc, setResponseDesc] = useState<string>('成功');
  const [statusCode, setStatusCode] = useState<number>(200);



  // ─── DB 模式相关 ──────────────────────────────────────────────────
  const [dbDatasource, setDbDatasource] = useState<string | undefined>(undefined);
  const [responseType, setResponseType] = useState<string | undefined>(undefined);

  // ═══════════════════════════════════════════════════════════════════
  //  初始化：Drawer 打开时还原数据
  // ═══════════════════════════════════════════════════════════════════


  useEffect(() => {
    if (modalVisible) {
      form.resetFields();
      form.setFieldsValue({
        ...processedValues,
        isCustomSuccess: !!processedValues.customSuccessWrapper,
        isCustomPage: !!processedValues.customPageWrapper,
        isCustomFail: !!processedValues.customFailWrapper,
      });
      setMethod(processedValues.method || 'GET');
      setUrl(processedValues.url || '');
      setName(processedValues.name || '');
      setSubmitAttempted(false);
      setDslContent(processedValues.dslContent || processedValues.config || '');
      setSqlContent(processedValues.sqlContent || '');
      setJsonContent(processedValues.jsonContent || '');
      setTextContent(processedValues.textContent || '');
      setEngineMode(processedValues.serviceType || 'FLOW');
      setDbDatasource(processedValues.datasource);
      setResponseType(processedValues.responseType);
      setPublishStatus(processedValues.publishStatus ?? 0);

      setActiveTab('implementation');

      // 还原契约数据：从 contract 字段解析（持久化）
      let contract = null;
      if (processedValues.contract) {
        try {
          contract = JSON.parse(processedValues.contract);
        } catch { /* contract 解析失败则忽略 */ }
      }
      if (contract) {
        setQueryParams(contract.request?.query ?? []);
        setPathParams(contract.request?.pathParams ?? []);
        setHeaders(contract.request?.headers ?? []);
        setBodyNodes(contract.request?.body ?? []);
        setBodyType(contract.request?.bodyType ?? 'none');
        setRawBody(contract.request?.rawBody ?? '');
        setResponseBody(contract.responses?.['200']?.body ?? []);
        setResponseDesc(contract.responses?.['200']?.description ?? '成功');
        setStatusCode(contract.responses?.['200']?.statusCode ?? 200);
      } else {
        setQueryParams([]);
        setPathParams([]);
        setHeaders([]);
        setBodyNodes([]);
        setBodyType('none');
        setRawBody('');
        setResponseBody([]);
        setResponseDesc('成功');
        setStatusCode(200);
      }
    }
  }, [processedValues, form, modalVisible]);

  // ═══════════════════════════════════════════════════════════════════
  //  提交逻辑
  // ═══════════════════════════════════════════════════════════════════

  const handleSubmit = useCallback(async (externalScript?: any) => {
    setSubmitAttempted(true);
    if (!url?.trim() || !name?.trim()) {
      message.warning('请完善 API 路径和接口名称等必填项');
      return false;
    }
    if (urlConflictMsg) {
      message.warning('接口路径存在冲突，请修改后再保存');
      return false;
    }
    let hide = null;
    try {
      const formValues = await form.validateFields();
      const finalDsl = typeof externalScript === 'string' ? externalScript : dslContent;

      const contractSnapshot = {
        request: { query: queryParams, pathParams, headers, body: bodyNodes, bodyType, rawBody },
        responses: {
          [String(statusCode)]: { statusCode, description: responseDesc, body: responseBody },
        },
      };

      // Flow 校验
      if (engineMode === 'FLOW' && finalDsl) {
        try {
          const flowDef = JSON.parse(finalDsl);
          if (flowDef.nodes) {
            for (const node of flowDef.nodes) {
              if (node.type === 'database') {
                const sqlType = node.data?.sqlType;
                const sqlText = node.data?.sql || '';
                const cleanSql = sqlText.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--.*/g, '').trim().toUpperCase();
                let actualType: string | null = null;
                if (cleanSql.startsWith('INSERT')) actualType = 'INSERT';
                else if (cleanSql.startsWith('UPDATE')) actualType = 'UPDATE';
                else if (cleanSql.startsWith('DELETE')) actualType = 'DELETE';
                else if (cleanSql.startsWith('SELECT')) actualType = 'SELECT';
                if (actualType && sqlType !== actualType) {
                  message.error(`校验失败: 数据库节点 [${node.label || node.id}] SQL 类型不匹配`);
                  return false;
                }
              }
            }
          }
        } catch { /* parse error → not blocking */ }
      }

      const payload = {
        ...formValues,
        name,
        url,
        method,
        publishStatus,
        responseType,
        serviceType: engineMode,
        datasource: dbDatasource,
        dslContent: finalDsl,
        sqlContent,
        jsonContent,
        textContent,
        templateId: formValues.templateId,
        customSuccessWrapper: formValues.isCustomSuccess ? formValues.customSuccessWrapper : undefined,
        customPageWrapper: formValues.isCustomPage ? formValues.customPageWrapper : undefined,
        customFailWrapper: formValues.isCustomFail ? formValues.customFailWrapper : undefined,
        tags: formValues.tags && Array.isArray(formValues.tags) ? formValues.tags.join(',') : formValues.tags,
        // 将契约数据序列化为 JSON 字符串存入 contract 字段，后端用于入参校验
        contract: JSON.stringify(contractSnapshot),
      };

      hide = message.loading(isEdit ? '正在更新...' : '正在添加...');

      if (isEdit) {
        await updateAutoApiConfig(values.id!, merge({}, values, payload));
      } else {
        await addAutoApiConfig(payload);
      }

      hide();
      message.success(isEdit ? '更新成功' : '添加成功');
      onSubmit(true);
      return true;
    } catch (error: any) {
      hide?.();
      if (error?.errorFields) {
        message.warning('表单校验失败，请检查必填项');
      } else {
        message.error(isEdit ? '更新失败' : '添加失败');
      }
      onSubmit(false);
      return false;
    }
  }, [
    form, dslContent, sqlContent, jsonContent, textContent,
    name, url, method, publishStatus, isEdit, values,
    queryParams, pathParams, headers, bodyNodes, bodyType, rawBody,
    responseBody, responseDesc, statusCode, onSubmit,
    dbDatasource, engineMode, responseType,
    urlConflictMsg,
  ]);

  // ═══════════════════════════════════════════════════════════════════
  //  Header 区域配置
  // ═══════════════════════════════════════════════════════════════════

  const headerTitle = (
    <Space.Compact style={{ display: 'flex', width: '100%' }}>
      <Select
        value={method}
        onChange={setMethod}
        style={{ width: 116 }}
        popupMatchSelectWidth={false}
      >
        {METHOD_OPTIONS.map((m) => (
          <Select.Option key={m} value={m}>
            <span style={{ color: METHOD_COLORS[m], fontWeight: 700, fontFamily: 'monospace' }}>{m}</span>
          </Select.Option>
        ))}
      </Select>
      <Popover content={urlConflictMsg} open={!!urlConflictMsg} placement="bottomLeft" overlayInnerStyle={{ color: '#ff4d4f' }}>
        <ApiPathInput
          value={url}
          onChange={setUrl}
          disabled={publishStatus === 1}
          status={submitAttempted && !url?.trim() ? 'error' : (urlConflictMsg ? 'error' : undefined)}
        />
      </Popover>
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="接口名称"
        style={{ width: 180 }}
        status={submitAttempted && !name?.trim() ? 'error' : undefined}
      />
    </Space.Compact>
  );

  const publishMenuItems: MenuProps['items'] = [
    { key: '1', label: '已发布', onClick: () => setPublishStatus(1) },
    { key: '0', label: '未发布', onClick: () => setPublishStatus(0) },
  ];

  const headerExtra = (
    <Space size={12}>
      <Dropdown menu={{ items: publishMenuItems }} placement="bottomRight">
        <Tag
          color={publishStatus === 1 ? 'success' : 'default'}
          style={{ cursor: 'pointer', padding: '4px 12px', fontSize: 13 }}
        >
          {publishStatus === 1 ? '● 已发布' : '○ 未发布'}
        </Tag>
      </Dropdown>
      <Button icon={<CloseOutlined />} onClick={onCancel}>取消</Button>
      <Button type="primary" icon={<SaveOutlined />} onClick={() => handleSubmit()}>保存</Button>
    </Space>
  );

  // ═══════════════════════════════════════════════════════════════════
  //  Tab 内容路由 — 声明式分发到各独立 Panel
  // ═══════════════════════════════════════════════════════════════════

  const renderTabContent = () => {
    switch (activeTab) {
      case 'implementation':
        return (
          <ImplementationPanel
            engineMode={engineMode}
            onEngineModeChange={setEngineMode}
            dslContent={dslContent}
            onDslContentChange={setDslContent}
            sqlContent={sqlContent}
            onSqlContentChange={setSqlContent}
            jsonContent={jsonContent}
            onJsonContentChange={setJsonContent}
            textContent={textContent}
            onTextContentChange={setTextContent}
            dbDatasource={dbDatasource}
            onDbDatasourceChange={setDbDatasource}
            responseType={responseType}
            onResponseTypeChange={setResponseType}
            form={form}
            isEdit={isEdit}
            onSave={handleSubmit}
            onCancel={onCancel}
            addonDebugger={
              AddonDebuggerComponent && engineMode === 'FLOW' ? (
                <AddonDebuggerComponent
                  dslContent={dslContent}
                  apiUrl={url}
                  apiMethod={method}
                />
              ) : undefined
            }
          />
        );
      case 'req-schema':
        return (
          <ReqSchemaPanel
            method={method}
            url={url}
            queryParams={queryParams}
            onQueryParamsChange={setQueryParams}
            pathParams={pathParams}
            onPathParamsChange={setPathParams}
            headers={headers}
            onHeadersChange={setHeaders}
            bodyNodes={bodyNodes}
            onBodyNodesChange={setBodyNodes}
            bodyType={bodyType}
            onBodyTypeChange={setBodyType}
            rawBody={rawBody}
            onRawBodyChange={setRawBody}
          />
        );
      case 'res-schema':
        return (
          <ResSchemaPanel
            responseBody={responseBody}
            onResponseBodyChange={setResponseBody}
            responseDesc={responseDesc}
            onResponseDescChange={setResponseDesc}
            statusCode={statusCode}
            onStatusCodeChange={setStatusCode}
          />
        );
      case 'basic-info':
        return <BasicInfoPanel form={form} />;
      default:
        return null;
    }
  };

  // ═══════════════════════════════════════════════════════════════════
  //  渲染：主体 — Drawer 包裹 PageContainer
  // ═══════════════════════════════════════════════════════════════════

  return (
    <Drawer
      title={null}
      width="100%"
      open={modalVisible}
      onClose={onCancel}
      closable={false}
      styles={{
        body: { padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' },
      }}
      destroyOnClose
    >
      {/* 注入全局样式，强制 PageContainer 的标题栏撑满可用宽度，仅去除底部的冗余留白 */}
      <style>{`
        .controller-form-page-container .ant-page-header-heading-left,
        .controller-form-page-container .ant-page-header-heading-title {
          flex: 1;
          min-width: 0;
        }
        .controller-form-page-container .ant-pro-page-container-children-content {
          padding-bottom: 0 !important;
          margin-bottom: 0 !important;
        }
        .controller-form-page-container .ant-pro-grid-content {
          padding-bottom: 0 !important;
          margin-bottom: 0 !important;
        }
      `}</style>
      <PageContainer
        className="controller-form-page-container"
        header={{
          title: headerTitle,
          extra: headerExtra,
          style: { paddingBottom: 0 },
          breadcrumb: {},
        }}
        tabActiveKey={activeTab}
        onTabChange={(key) => setActiveTab(key as TabKey)}
        tabList={[
          { tab: '服务实现', key: 'implementation' },
          { tab: 'API 文档定义 · 请求', key: 'req-schema' },
          { tab: 'API 文档定义 · 响应', key: 'res-schema' },
          { tab: '基础信息', key: 'basic-info' },
        ]}
        style={{ height: '100%', overflow: 'auto' }}
      >
        {renderTabContent()}
      </PageContainer>
    </Drawer>
  );
};

export default ControllerFormV2;
