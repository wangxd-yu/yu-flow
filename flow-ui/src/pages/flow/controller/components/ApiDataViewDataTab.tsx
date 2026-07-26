/**
 * ApiDataViewDataTab
 * ─────────────────────────────────────────────────────────────────────────────
 * ApiDataViewDrawer「数据」Tab：请求参数表单 + 预览结果表格/对象描述。
 */
import React from 'react';
import { Alert, Descriptions, Form, Input, Table } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { ParamField } from './apiDataViewUtils';
import type { ViewExportColumn, ApiDataPreviewResult } from '@/services/flow/flowController';

export interface ApiDataViewDataTabProps {
  paramFields: ParamField[];
  paramForm: FormInstance;
  result: ApiDataPreviewResult | null;
  loading: boolean;
  page: number;
  pageSize: number;
  setPage: (p: number) => void;
  setPageSize: (s: number) => void;
  runPreview: (nextPage: number, nextSize: number) => void;
  isObject: boolean;
  columns: ViewExportColumn[];
  tableColumns: any[];
}

const ApiDataViewDataTab: React.FC<ApiDataViewDataTabProps> = ({
  paramFields,
  paramForm,
  result,
  loading,
  page,
  pageSize,
  setPage,
  setPageSize,
  runPreview,
  isObject,
  columns,
  tableColumns,
}) => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, overflow: 'hidden' }}>
      <div style={{ flexShrink: 0, marginBottom: 12 }}>
        {paramFields.length > 0 ? (
          <Form form={paramForm} layout="inline" style={{ rowGap: 8 }}>
            {paramFields.map((f) => (
              <Form.Item
                key={`${f.section}__${f.name}`}
                name={`${f.section}__${f.name}`}
                label={`${f.title || f.name}(${f.section})`}
              >
                <Input allowClear style={{ width: 160 }} />
              </Form.Item>
            ))}
          </Form>
        ) : (
          <Alert type="info" showIcon message="未配置请求契约参数，将按空参查询" />
        )}
      </div>

      {isObject ? (
        <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
          <Descriptions bordered size="small" column={1}>
            {Object.entries(result?.object || result?.rows?.[0] || {}).map(([k, v]) => {
              const col = (result?.columns || columns).find((c) => c.field === k);
              return (
                <Descriptions.Item key={k} label={col?.header || k}>
                  {v === null || v === undefined ? '-' : String(v)}
                </Descriptions.Item>
              );
            })}
          </Descriptions>
        </div>
      ) : (
        <Table
          size="small"
          rowKey={(_, i) => String(i)}
          loading={loading}
          columns={tableColumns}
          dataSource={result?.rows || []}
          scroll={{ x: true, y: 'calc(100vh - 280px)' }}
          pagination={{
            current: (result?.page ?? page) + 1,
            pageSize: result?.size ?? pageSize,
            total: result?.total ?? 0,
            showSizeChanger: true,
            onChange: (p, ps) => {
              const nextPage = p - 1;
              const nextSize = ps || 20;
              setPage(nextPage);
              setPageSize(nextSize);
              runPreview(nextPage, nextSize);
            },
          }}
        />
      )}
    </div>
  );
};

export default ApiDataViewDataTab;
