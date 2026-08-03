/**
 * ControllerFormTabs
 * ─────────────────────────────────────────────────────────────────────────────
 * ControllerForm 的 Tab 内容路由分发组件。
 */
import React from 'react';
import type { FormInstance } from 'antd/es/form';
import {
  ASSET_FORM_FILL_CLASS,
  ASSET_FORM_SCROLL_CLASS,
} from '@/components/flow/ops';
import AssetRuntimePanel from '@/components/flow/AssetRuntimePanel';
import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';
import type { EngineMode } from './panels/ImplementationPanel';
import ImplementationPanel from './panels/ImplementationPanel';
import ReqSchemaPanel from './panels/ReqSchemaPanel';
import ResSchemaPanel from './panels/ResSchemaPanel';
import BasicInfoPanel from './panels/BasicInfoPanel';
import type { HostWrapBinding } from './panels/HostWrapConfig';

export type TabKey = 'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime';

export interface ControllerFormTabsProps {
  activeTab: TabKey;
  // implementation panel
  engineMode: EngineMode;
  onEngineModeChange: (mode: EngineMode) => void;
  interceptMode: 'REPLACE' | 'WRAP';
  hostBinding: HostWrapBinding;
  onHostBindingChange: (binding: HostWrapBinding) => void;
  onProbeNow: () => void;
  probing: boolean;
  dslContent: string;
  onDslContentChange: (v: string) => void;
  sqlContent: string;
  onSqlContentChange: (v: string) => void;
  jsonContent: string;
  onJsonContentChange: (v: string) => void;
  textContent: string;
  onTextContentChange: (v: string) => void;
  dbDatasource: string | undefined;
  onDbDatasourceChange: (v: string | undefined) => void;
  responseType: string | undefined;
  onResponseTypeChange: (v: string | undefined) => void;
  form: FormInstance;
  isEdit: boolean;
  onSave: () => void;
  onCancel: () => void;
  apiUrl: string;
  apiMethod: string;
  apiId?: string;
  apiName: string;
  defaultTriggerHeaders: Record<string, any>;
  defaultTriggerQueryParams: Record<string, any>;
  defaultTriggerBody: any;
  contractJson: string;
  // req-schema panel
  url: string;
  method: string;
  queryParams: SchemaNode[];
  onQueryParamsChange: (v: SchemaNode[]) => void;
  pathParams: SchemaNode[];
  onPathParamsChange: (v: SchemaNode[]) => void;
  headers: SchemaNode[];
  onHeadersChange: (v: SchemaNode[]) => void;
  bodyNodes: SchemaNode[];
  onBodyNodesChange: (v: SchemaNode[]) => void;
  bodyType: BodyType;
  onBodyTypeChange: (v: BodyType) => void;
  rawBody: string;
  onRawBodyChange: (v: string) => void;
  onCurlImport: () => void;
  // res-schema panel
  responseBody: SchemaNode[];
  onResponseBodyChange: (v: SchemaNode[]) => void;
  responseDesc: string;
  onResponseDescChange: (v: string) => void;
  statusCode: number;
  onStatusCodeChange: (v: number) => void;
  // basic-info / runtime
  valuesId?: string;
}

const ControllerFormTabs: React.FC<ControllerFormTabsProps> = (props) => {
  const {
    activeTab,
    engineMode,
    onEngineModeChange,
    interceptMode,
    hostBinding,
    onHostBindingChange,
    onProbeNow,
    probing,
    dslContent,
    onDslContentChange,
    sqlContent,
    onSqlContentChange,
    jsonContent,
    onJsonContentChange,
    textContent,
    onTextContentChange,
    dbDatasource,
    onDbDatasourceChange,
    responseType,
    onResponseTypeChange,
    form,
    isEdit,
    onSave,
    onCancel,
    apiUrl,
    apiMethod,
    apiId,
    apiName,
    defaultTriggerHeaders,
    defaultTriggerQueryParams,
    defaultTriggerBody,
    contractJson,
    url,
    method,
    queryParams,
    onQueryParamsChange,
    pathParams,
    onPathParamsChange,
    headers,
    onHeadersChange,
    bodyNodes,
    onBodyNodesChange,
    bodyType,
    onBodyTypeChange,
    rawBody,
    onRawBodyChange,
    onCurlImport,
    responseBody,
    onResponseBodyChange,
    responseDesc,
    onResponseDescChange,
    statusCode,
    onStatusCodeChange,
    valuesId,
  } = props;

  switch (activeTab) {
    case 'implementation':
      return (
        <div className={ASSET_FORM_FILL_CLASS}>
          <ImplementationPanel
            engineMode={engineMode}
            onEngineModeChange={onEngineModeChange}
            interceptMode={interceptMode}
            hostBinding={hostBinding}
            onHostBindingChange={onHostBindingChange}
            onProbeNow={onProbeNow}
            probing={probing}
            dslContent={dslContent}
            onDslContentChange={onDslContentChange}
            sqlContent={sqlContent}
            onSqlContentChange={onSqlContentChange}
            jsonContent={jsonContent}
            onJsonContentChange={onJsonContentChange}
            textContent={textContent}
            onTextContentChange={onTextContentChange}
            dbDatasource={dbDatasource}
            onDbDatasourceChange={onDbDatasourceChange}
            responseType={responseType}
            onResponseTypeChange={onResponseTypeChange}
            form={form}
            isEdit={isEdit}
            onSave={onSave}
            onCancel={onCancel}
            apiUrl={apiUrl}
            apiMethod={apiMethod}
            apiId={apiId}
            apiName={apiName}
            defaultTriggerHeaders={defaultTriggerHeaders}
            defaultTriggerQueryParams={defaultTriggerQueryParams}
            defaultTriggerBody={defaultTriggerBody}
            contractJson={contractJson}
          />
        </div>
      );
    case 'req-schema':
      return (
        <ReqSchemaPanel
          method={method}
          url={url}
          queryParams={queryParams}
          onQueryParamsChange={onQueryParamsChange}
          pathParams={pathParams}
          onPathParamsChange={onPathParamsChange}
          headers={headers}
          onHeadersChange={onHeadersChange}
          bodyNodes={bodyNodes}
          onBodyNodesChange={onBodyNodesChange}
          bodyType={bodyType}
          onBodyTypeChange={onBodyTypeChange}
          rawBody={rawBody}
          onRawBodyChange={onRawBodyChange}
          onCurlImport={onCurlImport}
        />
      );
    case 'res-schema':
      return (
        <ResSchemaPanel
          responseBody={responseBody}
          onResponseBodyChange={onResponseBodyChange}
          responseDesc={responseDesc}
          onResponseDescChange={onResponseDescChange}
          statusCode={statusCode}
          onStatusCodeChange={onStatusCodeChange}
        />
      );
    case 'basic-info': {
      const paramSuggestions = [
        ...queryParams.map((n) => ({ source: 'query', name: n.name })),
        ...pathParams.map((n) => ({ source: 'path', name: n.name })),
        ...headers.map((n) => ({ source: 'header', name: n.name })),
        ...bodyNodes.map((n) => ({ source: 'body', name: n.name })),
      ].filter((p) => !!p.name);
      return (
        <BasicInfoPanel
          form={form}
          paramSuggestions={paramSuggestions}
          interceptMode={interceptMode}
        />
      );
    }
    case 'runtime':
      return (
        <div className={ASSET_FORM_SCROLL_CLASS}>
          <AssetRuntimePanel assetType="API" assetId={valuesId} />
        </div>
      );
    default:
      return null;
  }
};

export default ControllerFormTabs;
