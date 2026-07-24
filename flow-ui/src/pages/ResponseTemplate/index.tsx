/**
 * ResponseTemplate/index.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * API 响应模板管理 — ProTable + ModalForm CRUD 页面
 *
 * 提供响应模板的列表查询、新增、编辑、删除及设置全局默认操作。
 * 模板包含三种包装体：普通成功、分页成功、失败返回，均基于 JSONPath 语法。
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormSwitch,
} from '@ant-design/pro-components';
import { Button, message, Tag, Popconfirm, Space, Typography, Tooltip, Row, Col, Tabs } from 'antd';
import { PlusOutlined, StarOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
import '@/styles/fullHeightTable.css';

const { Text } = Typography;

// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

export interface ResponseTemplateItem {
  id: string;
  templateName: string;
  successWrapper: string;
  pageWrapper: string;
  failWrapper: string;
  isDefault: 0 | 1;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

// ═══════════════════════════════════════════════════════════════════════════
//  API 调用
// ═══════════════════════════════════════════════════════════════════════════

const API_BASE = '/flow-api/response-templates';

const fetchPage = async (params: any) => {
  const { current, pageSize, ...restParams } = params;
  return request(`${API_BASE}/page`, {
    method: 'GET',
    params: {
      ...restParams,
      page: current || 1,
      size: pageSize || 10,
    },
  });
};

const createTemplate = async (data: any) =>
  request(API_BASE, { method: 'POST', data });

const updateTemplate = async (id: string, data: any) =>
  request(`${API_BASE}/${id}`, { method: 'PUT', data });

const deleteTemplate = async (id: string) =>
  request(`${API_BASE}/${id}`, { method: 'DELETE' });

const setDefaultTemplate = async (id: string) =>
  request(`${API_BASE}/${id}/set-default`, { method: 'PUT' });

// ═══════════════════════════════════════════════════════════════════════════
//  Extra 辅助提示组件
// ═══════════════════════════════════════════════════════════════════════════

/** 统一的 extra 提示样式 */
const ExtraHint: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div style={{
    marginTop: 4,
    padding: '8px 12px',
    background: '#f6f8fa',
    borderRadius: 6,
    border: '1px solid #ebeef5',
    fontSize: 12,
    color: '#666',
    lineHeight: 1.8,
  }}>
    {children}
  </div>
);

const CodeSnippet: React.FC<{ children: string }> = ({ children }) => (
  <code style={{
    display: 'block',
    marginTop: 4,
    padding: '6px 10px',
    background: '#282c34',
    color: '#abb2bf',
    borderRadius: 4,
    fontFamily: "'Menlo', 'Consolas', monospace",
    fontSize: 11.5,
    lineHeight: 1.6,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-all',
  }}>
    {children}
  </code>
);

// ═══════════════════════════════════════════════════════════════════════════
//  主组件
// ═══════════════════════════════════════════════════════════════════════════

const ResponseTemplateManage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalVisible, setModalVisible] = useState(false);
  const [currentRow, setCurrentRow] = useState<ResponseTemplateItem | null>(null);
  const isEdit = !!currentRow?.id;

  const handleAdd = () => {
    setCurrentRow(null);
    setModalVisible(true);
  };

  const handleEdit = (record: ResponseTemplateItem) => {
    setCurrentRow(record);
    setModalVisible(true);
  };

  const handleDelete = async (id: string) => {
    const hide = message.loading('正在删除...');
    try {
      await deleteTemplate(id);
      hide();
      message.success('删除成功');
      actionRef.current?.reload();
    } catch (err: any) {
      hide();
      message.error(err?.message || '删除失败');
    }
  };

  const handleSetDefault = async (id: string) => {
    const hide = message.loading('正在设置...');
    try {
      await setDefaultTemplate(id);
      hide();
      message.success('已设为全局默认');
      actionRef.current?.reload();
    } catch (err: any) {
      hide();
      message.error(err?.message || '设置失败');
    }
  };

  const columns: ProColumns<ResponseTemplateItem>[] = [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      width: 200,
      render: (_, record) => (
        <Space>
          {record.templateName}
          {record.isDefault === 1 && (
            <Tag icon={<StarOutlined />} color="gold">
              默认
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '成功包装体',
      dataIndex: 'successWrapper',
      width: 200,
      search: false,
      render: (text) => {
        if (!text) return '-';
        return (
          <Tooltip title={<pre style={{ margin: 0, color: '#fff', fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{text}</pre>} placement="topLeft">
            <code style={{
              display: 'block',
              fontSize: 12,
              color: '#389e0d',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 180,
              cursor: 'pointer'
            }}>
              {text}
            </code>
          </Tooltip>
        );
      },
    },
    {
      title: '分页包装体',
      dataIndex: 'pageWrapper',
      width: 200,
      search: false,
      render: (text) => {
        if (!text) return '-';
        return (
          <Tooltip title={<pre style={{ margin: 0, color: '#fff', fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{text}</pre>} placement="topLeft">
            <code style={{
              display: 'block',
              fontSize: 12,
              color: '#1677ff',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 180,
              cursor: 'pointer'
            }}>
              {text}
            </code>
          </Tooltip>
        );
      },
    },
    {
      title: '失败包装体',
      dataIndex: 'failWrapper',
      width: 200,
      search: false,
      render: (text) => {
        if (!text) return '-';
        return (
          <Tooltip title={<pre style={{ margin: 0, color: '#fff', fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{text}</pre>} placement="topLeft">
            <code style={{
              display: 'block',
              fontSize: 12,
              color: '#cf1322',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 180,
              cursor: 'pointer'
            }}>
              {text}
            </code>
          </Tooltip>
        );
      },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      width: 180,
      search: false,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Space split={<span style={{ color: '#d9d9d9' }}>|</span>}>
          <a onClick={() => handleEdit(record)}>编辑</a>
          {record.isDefault !== 1 && (
            <Popconfirm
              title="确认设为全局默认？"
              description="原默认模板将自动取消"
              onConfirm={() => handleSetDefault(record.id)}
            >
              <a style={{ color: '#faad14' }}>设为默认</a>
            </Popconfirm>
          )}
          <Popconfirm
            title="确认删除该模板？"
            description={record.isDefault === 1 ? '提示：默认模板无法删除' : undefined}
            onConfirm={() => handleDelete(record.id)}
            okButtonProps={{ disabled: record.isDefault === 1 }}
          >
            <a style={{ color: record.isDefault === 1 ? '#d9d9d9' : '#ff4d4f' }}>删除</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <ProTable<ResponseTemplateItem>
        className="fh-table"
        headerTitle="响应模板列表"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 'max-content', y: 100000 }}
        search={{
          labelWidth: 120,
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新建模板
          </Button>,
        ]}
        request={async (params) => {
          const data = await fetchPage(params);
          return {
            data: data?.items || [],
            success: true,
            total: data?.total || 0,
          };
        }}
        columns={columns}
        pagination={{
          defaultPageSize: 10,
          showSizeChanger: true,
          showQuickJumper: true,
        }}
      />

      {/* ── 新增/编辑弹窗 ── */}
      <ModalForm<SaveFormValues>
        title={isEdit ? '编辑响应模板' : '新建响应模板'}
        width={920}
        open={modalVisible}
        onOpenChange={(visible) => {
          if (!visible) {
            setCurrentRow(null);
          }
          setModalVisible(visible);
        }}
        modalProps={{
          destroyOnClose: true,
          maskClosable: false,
          bodyStyle: { padding: '12px 24px 24px' }
        }}
        initialValues={
          currentRow
            ? {
              templateName: currentRow.templateName,
              successWrapper: currentRow.successWrapper,
              pageWrapper: currentRow.pageWrapper,
              failWrapper: currentRow.failWrapper,
              isDefault: currentRow.isDefault === 1,
              remark: currentRow.remark,
            }
            : {
              isDefault: false,
              successWrapper: '{"code": 200, "data": "$"}',
              pageWrapper: '{"code": 200, "data": {"list": "$.items", "total": "$.total"}}',
              failWrapper: '{"code": "$.code", "message": "$.msg", "data": null}',
            }
        }
        onFinish={async (values) => {
          const payload = {
            ...values,
            isDefault: values.isDefault ? 1 : 0,
          };
          const hide = message.loading(isEdit ? '正在更新...' : '正在创建...');
          try {
            if (isEdit && currentRow?.id) {
              await updateTemplate(currentRow.id, payload);
            } else {
              await createTemplate(payload);
            }
            hide();
            message.success(isEdit ? '更新成功' : '创建成功');
            setModalVisible(false);
            actionRef.current?.reload();
            return true;
          } catch (err: any) {
            hide();
            message.error(err?.message || (isEdit ? '更新失败' : '创建失败'));
            return false;
          }
        }}
      >
        <Row gutter={24}>
          {/* 左侧：模板属性与备注 */}
          <Col span={9}>
            <ProFormText
              name="templateName"
              label="模板名称"
              placeholder="如: 标准App响应"
              rules={[{ required: true, message: '模板名称不能为空' }]}
            />

            <div style={{ marginBottom: 20 }}>
              <ProFormSwitch
                name="isDefault"
                label="全局默认模板"
                tooltip="开启后，该模板将自动成为未指定模板的 API 的全局默认包装格式"
              />
            </div>

            <ProFormTextArea
              name="remark"
              label="备注"
              placeholder="请输入对此模板的备注说明信息..."
              fieldProps={{ rows: 7 }}
            />
          </Col>

          {/* 右侧：IDE风格包装体配置区域 */}
          <Col span={15}>
            <div style={{
              background: '#fafafa',
              border: '1px solid #f0f0f0',
              borderRadius: '8px',
              padding: '16px',
              height: '100%',
              display: 'flex',
              flexDirection: 'column'
            }}>
              <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: 12, color: '#262626' }}>
                包装体模板配置 (JSONPath)
              </div>
              <Tabs
                defaultActiveKey="success"
                type="card"
                items={[
                  {
                    key: 'success',
                    label: '🟢 成功包装体',
                    children: (
                      <div style={{ paddingTop: 8 }}>
                        <ProFormTextArea
                          name="successWrapper"
                          placeholder={'{"code": 200, "data": "$"}'}
                          noStyle
                          fieldProps={{
                            rows: 8,
                            style: {
                              fontFamily: 'Consolas, Monaco, monospace',
                              backgroundColor: '#ffffff',
                              fontSize: '13px',
                              border: '1px solid #d9d9d9',
                            },
                          }}
                        />
                        <div style={{ marginTop: 8, fontSize: '12px', color: '#8c8c8c', lineHeight: '1.6' }}>
                          💡 使用 JSONPath（以 <code>$</code> 开头）提取接口返回的对象。示例:
                          <code style={{ display: 'block', marginTop: 4, padding: '6px 10px', background: '#f5f5f5', color: '#595959', borderRadius: 4, fontFamily: 'monospace' }}>
                            {"{\"code\": 200, \"data\": \"$\"}"}
                          </code>
                        </div>
                      </div>
                    )
                  },
                  {
                    key: 'page',
                    label: '🔵 分页包装体',
                    children: (
                      <div style={{ paddingTop: 8 }}>
                        <ProFormTextArea
                          name="pageWrapper"
                          placeholder={'{"code": 200, "data": {"list": "$.items", "total": "$.total"}}'}
                          noStyle
                          fieldProps={{
                            rows: 8,
                            style: {
                              fontFamily: 'Consolas, Monaco, monospace',
                              backgroundColor: '#ffffff',
                              fontSize: '13px',
                              border: '1px solid #d9d9d9',
                            },
                          }}
                        />
                        <div style={{ marginTop: 8, fontSize: '12px', color: '#8c8c8c', lineHeight: '1.6' }}>
                          💡 底层分页包含 <code>items</code>, <code>total</code> 等。配置示例:
                          <code style={{ display: 'block', marginTop: 4, padding: '6px 10px', background: '#f5f5f5', color: '#595959', borderRadius: 4, fontFamily: 'monospace' }}>
                            {"{\"code\": 200, \"data\": {\"list\": \"$.items\", \"total\": \"$.total\"}}"}
                          </code>
                        </div>
                      </div>
                    )
                  },
                  {
                    key: 'fail',
                    label: '🔴 失败包装体',
                    children: (
                      <div style={{ paddingTop: 8 }}>
                        <ProFormTextArea
                          name="failWrapper"
                          placeholder={'{"code": "$.code", "message": "$.msg", "data": null}'}
                          noStyle
                          fieldProps={{
                            rows: 8,
                            style: {
                              fontFamily: 'Consolas, Monaco, monospace',
                              backgroundColor: '#ffffff',
                              fontSize: '13px',
                              border: '1px solid #d9d9d9',
                            },
                          }}
                        />
                        <div style={{ marginTop: 8, fontSize: '12px', color: '#8c8c8c', lineHeight: '1.6' }}>
                          💡 异常结构包含 <code>code</code>, <code>msg</code> 等。配置示例:
                          <code style={{ display: 'block', marginTop: 4, padding: '6px 10px', background: '#f5f5f5', color: '#595959', borderRadius: 4, fontFamily: 'monospace' }}>
                            {"{\"code\": \"$.code\", \"message\": \"$.msg\", \"data\": null}"}
                          </code>
                        </div>
                      </div>
                    )
                  }
                ]}
              />
            </div>
          </Col>
        </Row>
      </ModalForm>
    </PageContainer>
  );
};

// ── 内部表单值类型 ──
interface SaveFormValues {
  templateName: string;
  successWrapper?: string;
  pageWrapper?: string;
  failWrapper?: string;
  isDefault?: boolean;
  remark?: string;
}

export default ResponseTemplateManage;
