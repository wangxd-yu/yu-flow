/**
 * ApiDataViewTemplateTab
 * ─────────────────────────────────────────────────────────────────────────────
 * ApiDataViewDrawer「导出模板」Tab：模板上传、占位符 key 配置。
 */
import React from 'react';
import {
  Alert, Button, Descriptions, Form, Input, InputNumber, Popconfirm, Radio, Space, Table, Tag, Typography, Upload,
  message,
} from 'antd';
import type { FormInstance } from 'antd/es/form';
import {
  CloudUploadOutlined, DeleteOutlined, DownloadOutlined, SaveOutlined,
} from '@ant-design/icons';
import {
  downloadApiExcelTemplateFile,
  downloadApiExcelTemplateSample,
} from '@/services/flow/flowController';
import type { ViewExportColumn, ApiExcelTemplateMeta } from '@/services/flow/flowController';
import { formatBytes } from './apiDataViewUtils';

export interface ApiDataViewTemplateTabProps {
  apiId: string;
  cfgForm: FormInstance;
  templateMeta: ApiExcelTemplateMeta | null;
  columns: ViewExportColumn[];
  setColumns: React.Dispatch<React.SetStateAction<ViewExportColumn[]>>;
  markDirty: () => void;
  uploading: boolean;
  saving: boolean;
  handleUploadTemplate: (file: File) => void;
  handleDeleteTemplate: () => void;
  resetTemplateKeys: () => void;
  saveColumnConfig: (override?: any, quiet?: boolean) => void;
}

const ApiDataViewTemplateTab: React.FC<ApiDataViewTemplateTabProps> = ({
  apiId,
  cfgForm,
  templateMeta,
  columns,
  setColumns,
  markDirty,
  uploading,
  saving,
  handleUploadTemplate,
  handleDeleteTemplate,
  resetTemplateKeys,
  saveColumnConfig,
}) => {
  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Alert
        type="info"
        showIcon
        message="公司标准表头模板（上传桌面 Excel 做好的 .xlsx）"
        description={
          <ol style={{ margin: '8px 0 0', paddingLeft: 18 }}>
            <li>下载示例模板，按公司样式改表头 / 合并单元格</li>
            <li>
              列表占位符写成 <Typography.Text code>{'{.fieldName}'}</Typography.Text>
              ，单值可用 <Typography.Text code>{'{exportTime}'}</Typography.Text> /{' '}
              <Typography.Text code>{'{apiName}'}</Typography.Text>
            </li>
            <li>上传后保存配置并发布；缺失或占位符不对时自动回退动态表头</li>
          </ol>
        }
      />
      <Form form={cfgForm} layout="inline" onValuesChange={() => markDirty()}>
        <Form.Item name="exportMode" label="导出模式">
          <Radio.Group>
            <Radio.Button value="DYNAMIC">动态表头</Radio.Button>
            <Radio.Button value="TEMPLATE" disabled={!templateMeta?.present}>
              模板填充
            </Radio.Button>
          </Radio.Group>
        </Form.Item>
        <Form.Item
          name="templateSheetNo"
          label="模板 Sheet 下标"
          tooltip="多 Sheet 时填写从 0 开始的序号；P0 仅填充一个列表区"
        >
          <InputNumber min={0} max={20} style={{ width: 80 }} />
        </Form.Item>
      </Form>

      <Upload.Dragger
        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        showUploadList={false}
        disabled={uploading}
        beforeUpload={(file) => {
          handleUploadTemplate(file as File);
          return false;
        }}
        style={{ padding: '8px 0' }}
      >
        <p className="ant-upload-drag-icon" style={{ marginBottom: 8 }}>
          <CloudUploadOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽上传公司 .xlsx 模板</p>
        <p className="ant-upload-hint">仅 xlsx，禁止 xlsm；最大 2MB；上传即覆盖</p>
      </Upload.Dragger>

      <Space wrap>
        <Button
          icon={<DownloadOutlined />}
          onClick={() =>
            downloadApiExcelTemplateSample(apiId).catch((e: any) => message.error(e.message))
          }
        >
          下载示例模板
        </Button>
        <Button
          icon={<DownloadOutlined />}
          disabled={!templateMeta?.present}
          onClick={() =>
            downloadApiExcelTemplateFile(apiId).catch((e: any) => message.error(e.message))
          }
        >
          下载已上传模板
        </Button>
        <Popconfirm
          title="删除导出模板？"
          description="删除后导出将回退为动态表头"
          okText="删除"
          okButtonProps={{ danger: true }}
          onConfirm={handleDeleteTemplate}
        >
          <Button danger icon={<DeleteOutlined />} disabled={!templateMeta?.present}>
            删除模板
          </Button>
        </Popconfirm>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={saving}
          onClick={() => saveColumnConfig()}
        >
          保存导出配置到草稿
        </Button>
      </Space>

      {templateMeta?.present ? (
        <>
          <Descriptions size="small" bordered column={2}>
            <Descriptions.Item label="文件名" span={2}>
              <Typography.Text ellipsis={{ tooltip: templateMeta.fileName }}>
                {templateMeta.fileName}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="大小">
              {formatBytes(templateMeta.fileSize)}
            </Descriptions.Item>
            <Descriptions.Item label="更新时间">
              {templateMeta.updateTime || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="列表占位符">
              {templateMeta.hasListPlaceholder === false ? (
                <Tag color="warning">未检测到</Tag>
              ) : (
                <Tag color="success">已检测</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color="processing">已绑定本接口</Tag>
            </Descriptions.Item>
          </Descriptions>
          {templateMeta.warning && (
            <Alert type="warning" showIcon message={templateMeta.warning} />
          )}
        </>
      ) : (
        <Alert type="warning" showIcon message="尚未上传模板，导出将使用动态表头" />
      )}

      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Text type="secondary">
          字段 → 模板占位符 key（对应 {'{.'}key{'}'}，建议字母数字下划线）
        </Typography.Text>
        <Button size="small" onClick={resetTemplateKeys}>
          重置 key 为字段名
        </Button>
      </Space>
      <Table
        size="small"
        rowKey="field"
        pagination={false}
        dataSource={columns.filter((c) => c.exportable !== false)}
        columns={[
          { title: '数据字段', dataIndex: 'field', width: 160 },
          { title: '中文表头', dataIndex: 'header', width: 140, ellipsis: true },
          {
            title: '模板 key',
            dataIndex: 'templateKey',
            render: (v, row) => {
              const fullIdx = columns.findIndex((c) => c.field === row.field);
              const key = (v || row.field || '').trim();
              const invalid = key && !/^[A-Za-z_][A-Za-z0-9_]*$/.test(key);
              return (
                <Input
                  status={invalid ? 'warning' : undefined}
                  value={v || row.field}
                  addonBefore="{."
                  addonAfter="}"
                  onChange={(e) => {
                    if (fullIdx < 0) return;
                    const next = [...columns];
                    next[fullIdx] = { ...row, templateKey: e.target.value };
                    setColumns(next);
                    markDirty();
                  }}
                />
              );
            },
          },
        ]}
      />
    </Space>
  );
};

export default ApiDataViewTemplateTab;
