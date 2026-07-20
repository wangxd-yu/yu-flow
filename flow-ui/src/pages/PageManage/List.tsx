import {
  ActionType,
  ModalForm,
  PageContainer,
  ProColumns,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { history } from '@umijs/max';
import {
  Button,
  Divider,
  message,
  Popconfirm,
  Switch,
  Tag,
  Space,
  Modal,
} from 'antd';
import {
  PlusOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import React, { useRef, useState, Suspense } from 'react';
import { Spin } from 'antd';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
// 懒加载 Designer，避免将 amis-editor 的 ~20MB 打入页面管理列表页的 chunk
const Designer = React.lazy(() => import('./Designer'));

import {
  queryPageList,
  addPage,
  updatePage,
  updatePageStatus,
  clonePage,
  deletePage,
  batchMovePage,
} from './services/pageManage';

// ================================================================
// 路由路径校验规则
// ================================================================
const routePathRules = [
  { required: true, message: '访问路径为必填项' },
  {
    pattern: /^\//,
    message: '访问路径必须以 / 开头',
  },
  {
    pattern: /^[a-zA-Z0-9_/\-]+$/,
    message: '只能包含英文字母、数字、下划线 _、中划线 - 和斜杠 /',
  },
];

// ================================================================
// 主组件
// ================================================================
const PageManageList: React.FC = () => {
  const actionRef = useRef<ActionType>();

  // ---- 弹窗相关 ----
  const [modalVisible, setModalVisible] = useState(false);
  const [currentRow, setCurrentRow] = useState<PageManage.PageConfig>();

  const [designerModalVisible, setDesignerModalVisible] = useState(false);
  const [currentDesignerId, setCurrentDesignerId] = useState<string>();

  // ---- 批量操作 ----
  const [selectedRowsState, setSelectedRows] = useState<PageManage.PageConfig[]>([]);
  const [batchMoveModalVisible, setBatchMoveModalVisible] = useState(false);

  // ---- 状态切换 ----
  const handleStatusChange = async (id: string, checked: boolean) => {
    try {
      await updatePageStatus(id, checked ? 1 : 0);
      message.success(checked ? '上线成功' : '下线成功');
      actionRef.current?.reload();
    } catch (error: any) {
      // 错误由全局拦截器处理
    }
  };

  // ---- 克隆页面 ----
  const handleClone = async (record: PageManage.PageConfig) => {
    try {
      await clonePage(record.id as string);
      message.success('克隆成功');
      actionRef.current?.reload();
    } catch (error: any) {
      // 错误由全局拦截器处理
    }
  };

  // ================================================================
  // ProTable 列定义
  // ================================================================
  const columns: ProColumns<PageManage.PageConfig>[] = [
    {
      title: '页面名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 180,
    },
    {
      title: '所属目录',
      dataIndex: 'directoryName',
      hideInSearch: true,
      width: 100,
      render: (_, record) => record.directoryName ? <Tag color="blue">{record.directoryName}</Tag> : '-',
    },
    {
      title: '访问路径',
      dataIndex: 'routePath',
      ellipsis: true,
      copyable: true,
      width: 220,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      search: false,
      render: (_, record) => (
        <Switch
          checked={record.status === 1 || record.status === true}
          onChange={(val) => handleStatusChange(record.id as string, val)}
          checkedChildren="已发布"
          unCheckedChildren="草稿"
          size="small"
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      search: false,
      width: 180,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 340,
      fixed: 'right',
      render: (_, record) => [
        <a
          key="design"
          style={{ fontWeight: 600 }}
          onClick={() => {
            setCurrentDesignerId(record.id as string);
            setDesignerModalVisible(true);
          }}
        >
          设计
        </a>,
        <Divider type="vertical" key="d1" />,
        <a
          key="preview"
          onClick={() => {
            const contextPath = window.__CONTEXT_PATH__ || '';
            window.open(`${contextPath}/flow-ui/page-manage/preview/${record.id}`, '_blank');
          }}
        >
          预览
        </a>,
        <Divider type="vertical" key="d2" />,
        <a
          key="edit"
          onClick={() => {
            setCurrentRow(record);
            setModalVisible(true);
          }}
        >
          编辑
        </a>,
        <Divider type="vertical" key="d3" />,
        <a key="clone" onClick={() => handleClone(record)}>
          <CopyOutlined /> 克隆
        </a>,
        <Divider type="vertical" key="d4" />,
        <Popconfirm
          key="delete"
          title="确定要删除该页面吗？"
          onConfirm={async () => {
            try {
              await deletePage(record.id as string);
              message.success('删除成功');
              actionRef.current?.reload();
            } catch (error: any) {
              // 错误由全局拦截器处理
            }
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  // ---- Full-height ProTable CSS overrides ----
  const fullHeightTableCSS = `
    .fh-container.ant-pro-page-container {
      display: flex !important;
      flex-direction: column !important;
    }
    .fh-container.ant-pro-page-container > .ant-pro-grid-content,
    .fh-container.ant-pro-page-container .ant-pro-grid-content-children {
      flex: 1 !important;
      min-height: 0 !important;
      display: flex !important;
      flex-direction: column !important;
    }
    .fh-container.ant-pro-page-container .ant-pro-page-container-children-container {
      flex: 1 !important;
      min-height: 0 !important;
      display: flex !important;
      flex-direction: column !important;
      height: auto !important;
      padding-block-end: 0 !important;
    }
    .fh-container .dir-tree-layout {
      flex: 1 !important;
      min-height: 0 !important;
      height: 100% !important;
    }
    .fh-table.ant-pro-table {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
    }
    .fh-table .ant-pro-table-search {
      flex-shrink: 0;
    }
    .fh-table > .ant-pro-card:not(.ant-pro-table-search) {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .fh-table > .ant-pro-card:not(.ant-pro-table-search) > .ant-pro-card-body {
      flex: 1;
      min-height: 0;
      display: flex !important;
      flex-direction: column;
      overflow: hidden;
    }
    .fh-table .ant-pro-table-list-toolbar {
      flex-shrink: 0;
    }
    .fh-table .ant-table-wrapper {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .fh-table .ant-spin-nested-loading {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .fh-table .ant-spin-container {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .fh-table .ant-table {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .fh-table .ant-table-container {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    .fh-table .ant-table-header {
      flex-shrink: 0;
      overflow: hidden !important;
    }
    .fh-table .ant-table-body {
      flex: 1;
      min-height: 0;
      max-height: none !important;
      overflow-y: scroll !important;
    }
    .fh-table .ant-table-pagination {
      flex-shrink: 0;
      padding: 6px 0;
      margin: 0 !important;
    }
  `;

  // ================================================================
  // JSX 渲染
  // ================================================================
  return (
    <PageContainer
      className="fh-container"
      header={{ title: '页面可视化管理' }}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <DirectoryTreeLayout bizType="page" height="calc(100vh - 90px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <>
            <style>{fullHeightTableCSS}</style>
            <ProTable<PageManage.PageConfig>
              className="fh-table"
              headerTitle={`页面列表 (${selectedDirectoryName || '全部'})`}
              tableLayout="fixed"
              scroll={{ x: 1120, y: 100000 }}
            actionRef={actionRef}
            rowKey="id"
            search={{ labelWidth: 100 }}
            toolBarRender={() => [
              <Button
                type="primary"
                key="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setCurrentRow(undefined);
                  setModalVisible(true);
                }}
              >
                新建页面
              </Button>,
            ]}
            params={{ directoryId: selectedDirectoryId }}
            request={async (params) => {
              const { current = 1, pageSize = 10, name, routePath, directoryId } = params;
              try {
                const res = await queryPageList({
                  directoryId,
                  name,
                  routePath,
                  page: current - 1,
                  size: pageSize,
                });
                const pageData = res?.items !== undefined ? res : res?.data;
                return {
                  data: pageData?.items ?? [],
                  success: true,
                  total: pageData?.total ?? 0,
                };
              } catch {
                return { data: [], success: false, total: 0 };
              }
            }}
            columns={columns}
            pagination={{ defaultPageSize: 10 }}
            rowSelection={{
              onChange: (_, selectedRows) => setSelectedRows(selectedRows),
            }}
            tableAlertOptionRender={() => (
              <Space size={16}>
                <a onClick={() => setBatchMoveModalVisible(true)}>批量移动</a>
              </Space>
            )}
          />
          </>
        )}
      </DirectoryTreeLayout>

      {/* ========== 新建 / 编辑弹窗 ========== */}
      <ModalForm<PageManage.PageConfig>
        title={currentRow?.id ? '编辑基础信息' : '新建页面'}
        width="480px"
        open={modalVisible}
        onOpenChange={setModalVisible}
        initialValues={currentRow}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (value) => {
          try {
            if (currentRow?.id) {
              await updatePage(currentRow.id as string, value);
              message.success('更新成功');
            } else {
              await addPage(value);
              message.success('创建成功');
            }
            setModalVisible(false);
            actionRef.current?.reload();
          } catch (error: any) {
            // 错误由全局拦截器处理
          }
          return true;
        }}
      >
        <ProFormText
          rules={[{ required: true, message: '名称为必填项' }]}
          label="页面名称"
          name="name"
          placeholder="请输入页面名称"
        />
        <ProFormText
          rules={routePathRules}
          label="访问路径"
          name="routePath"
          placeholder="请输入访问路径，例: /pages/demo"
        />
      </ModalForm>

      {/* ========== 批量移动弹窗 ========== */}
      <ModalForm
        title="批量移动至"
        width="400px"
        open={batchMoveModalVisible}
        onOpenChange={setBatchMoveModalVisible}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          const targetDir = values.targetDirectoryId || '0';
          const ids = selectedRowsState.map((r: any) => r.id);
          try {
            await batchMovePage(ids, targetDir);
            message.success('批量移动成功');
            setBatchMoveModalVisible(false);
            setSelectedRows([]);
            actionRef.current?.clearSelected?.();
            actionRef.current?.reload();
            return true;
          } catch (error: any) {
            // 错误由全局拦截器处理
            return false;
          }
        }}
      >
        <DirectoryTreeSelect bizType="page" />
      </ModalForm>

      {/* ========== 设计器弹窗 ========== */}
      <Modal
        title={null}
        footer={null}
        open={designerModalVisible}
        onCancel={() => setDesignerModalVisible(false)}
        width="100vw"
        style={{ top: 0, padding: 0, margin: 0, maxWidth: '100vw' }}
        bodyStyle={{ height: '100vh', padding: 0 }}
        destroyOnClose
        closable={false}
      >
        {currentDesignerId && (
          <Suspense fallback={<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}><Spin size="large" tip="加载编辑器..." /></div>}>
            <Designer id={currentDesignerId} onBack={() => setDesignerModalVisible(false)} />
          </Suspense>
        )}
      </Modal>
    </PageContainer>
  );
};

export default PageManageList;
