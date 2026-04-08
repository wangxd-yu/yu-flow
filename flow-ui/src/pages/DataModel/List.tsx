import React, { useRef, useState, useEffect, Suspense } from 'react';
import { request } from '@umijs/max';
import {
  ActionType,
  ModalForm,
  PageContainer,
  ProColumns,
  ProFormSelect,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { queryDataSourceList } from '@/pages/flow/dataSource/services/dataSource';
import {
  Button,
  Divider,
  message,
  Popconfirm,
  Switch,
  Tag,
  Space,
  Modal,
  Spin,
} from 'antd';
import {
  PlusOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
// 懒加载 Designer，避免将 amis-editor 的 ~20MB 打入数据模型列表页的 chunk
const Designer = React.lazy(() => import('../PageManage/Designer'));

import {
  queryModelList,
  addModel,
  updateModel,
  deleteModel,
  getModelDetail,
  batchMoveModel,
} from './services/dataModel';

import FieldEditorDrawer from './components/FieldEditorDrawer';
import GenerateWizardDrawer from './components/GenerateWizardDrawer';
import TemplateSelectorModal from './components/TemplateSelectorModal';
import type { TemplateId } from './components/TemplateSelectorModal';


const DataModelList: React.FC = () => {
  const actionRef = useRef<ActionType>();

  // ---- 弹窗状态 ----
  const [modalVisible, setModalVisible] = useState(false);
  const [currentRow, setCurrentRow] = useState<any>();

  // ---- 抽屉状态 ----
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [activeModelId, setActiveModelId] = useState<string>('');

  // ---- 保存当前选中的目录 ID（用于新建模型时自动关联） ----
  const [currentDirId, setCurrentDirId] = useState<string>();
  // 用 ref 跟踪 render-props 传入的目录 ID，避免在渲染期间 setState
  const latestDirIdRef = useRef<string>();
  useEffect(() => {
    if (latestDirIdRef.current !== currentDirId) {
      setCurrentDirId(latestDirIdRef.current);
    }
  });

  // ---- 批量操作 ----
  const [selectedRowsState, setSelectedRows] = useState<any[]>([]);
  const [batchMoveModalVisible, setBatchMoveModalVisible] = useState(false);

  const [currentDesignerId, setCurrentDesignerId] = useState<string>();
  const [designerModalVisible, setDesignerModalVisible] = useState(false);

  // ---- 模板选择弹窗 + 生成向导抽屉 ----
  const [templateModalVisible, setTemplateModalVisible] = useState(false);
  const [pendingModel, setPendingModel] = useState<any>(null);
  const [wizardVisible, setWizardVisible] = useState(false);
  const [wizardModel, setWizardModel] = useState<any>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<TemplateId>('crud');

  // ---- 点击「生成页面」：拉取模型详情 → 弹出模板选择 ----
  const handleOpenWizard = async (record: any) => {
    const hide = message.loading('正在获取模型配置...', 0);
    try {
      const detailRes = await getModelDetail(record.id);
      const detailRecord = detailRes?.data || detailRes || record;
      setPendingModel(detailRecord);
      setTemplateModalVisible(true);
    } catch (e: any) {
      message.error('获取模型详情失败：' + (e?.message || ''));
    } finally {
      hide();
    }
  };

  // ---- 用户选择模板后 → 关闭选择弹窗 → 打开向导抽屉 ----
  const handleTemplateSelect = (templateId: TemplateId) => {
    setSelectedTemplate(templateId);
    setWizardModel(pendingModel);
    setWizardVisible(true);
  };

  // ---- 列表状态切换 ----
  const handleStatusChange = async (record: any, checked: boolean) => {
    try {
      await updateModel(record.id, { ...record, status: checked ? 1 : 0 });
      message.success('状态更新成功');
      actionRef.current?.reload();
    } catch { }
  };

  // ---- 列定义 ----
  const columns: ProColumns<any>[] = [
    {
      title: '模型名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 180,
    },
    {
      title: '所属目录',
      dataIndex: 'directoryName',
      hideInSearch: true,
      render: (_, record) => record.directoryName ? <Tag color="blue">{record.directoryName}</Tag> : '-',
    },
    {
      title: '目标数据源',
      dataIndex: 'datasourceName',
      hideInSearch: true,
      ellipsis: true,
      render: (_, record) => record.datasourceName ? <Tag color="green">{record.datasourceName}</Tag> : '-',
    },
    {
      title: '物理表名',
      dataIndex: 'tableName',
      ellipsis: true,
      copyable: true,
      width: 200,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      search: false,
      render: (_, record) => (
        <Switch
          checked={record.status === 1 || record.status === true}
          onChange={(val) => handleStatusChange(record, val)}
          checkedChildren="启用"
          unCheckedChildren="停用"
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
      width: 320,
      render: (_, record) => [
        <a
          key="config"
          style={{ fontWeight: 600, color: '#1677ff' }}
          onClick={() => {
            setActiveModelId(record.id);
            setDrawerVisible(true);
          }}
        >
          <SettingOutlined /> 配置字段
        </a>,
        <Divider type="vertical" key="d1" />,
        <a key="edit" onClick={() => { setCurrentRow(record); setModalVisible(true); }}>编辑</a>,
        <Divider type="vertical" key="d2" />,
        <a
          key="generate"
          style={{ color: '#faad14' }}
          onClick={() => handleOpenWizard(record)}
        >
          <ThunderboltOutlined /> 生成页面
        </a>,
        <Divider type="vertical" key="d3" />,
        <Popconfirm
          key="delete"
          title="确定要删除该模型吗？"
          onConfirm={async () => {
            try {
              await deleteModel(record.id);
              message.success('删除成功');
              actionRef.current?.reload();
            } catch { }
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer header={{ title: '数据模型管理' }}>
      <DirectoryTreeLayout>
        {(selectedDirectoryId, selectedDirectoryName) => {
          // 先写入 ref，再通过 useEffect 同步到 state（避免渲染期间 setState 触发警告）
          if (selectedDirectoryId !== latestDirIdRef.current) {
            latestDirIdRef.current = selectedDirectoryId;
          }
          return (
            <ProTable<any>
              headerTitle={`数据模型列表 (${selectedDirectoryName || '全部'})`}
              actionRef={actionRef}
              rowKey="id"
              search={{ labelWidth: 100 }}
              toolBarRender={() => [
                <Button type="primary" key="primary" icon={<PlusOutlined />}
                  onClick={() => { setCurrentRow(undefined); setModalVisible(true); }}>
                  新建模型
                </Button>,
              ]}
              params={{ directoryId: selectedDirectoryId }}
              request={async (params) => {
                const { current = 1, pageSize = 10, name, tableName, directoryId } = params;
                try {
                  const res = await queryModelList({ directoryId, name, tableName, page: current, size: pageSize });
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
          );
        }}
      </DirectoryTreeLayout>

      {/* ========== 新建 / 编辑基础信息弹窗 ========== */}
      <ModalForm
        title={currentRow?.id ? '编辑基础信息' : '新建数据模型'}
        width="480px"
        open={modalVisible}
        onOpenChange={setModalVisible}
        initialValues={currentRow}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (value) => {
          try {
            const payload = { ...value, directoryId: currentDirId || null };
            if (currentRow?.id) {
              await updateModel(currentRow.id, payload);
              message.success('更新成功');
            } else {
              await addModel(payload);
              message.success('创建成功');
            }
            setModalVisible(false);
            actionRef.current?.reload();
          } catch { }
          return true;
        }}
      >
        <ProFormText
          rules={[{ required: true, message: '模型名称为必填项' }]}
          label="模型名称"
          name="name"
          placeholder="例如：用户信息"
        />
        <ProFormText
          rules={[
            { required: true, message: '物理表名为必填项' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含英文字母、数字、下划线' }
          ]}
          label="物理表名"
          name="tableName"
          placeholder="例如：t_user"
        />
        <ProFormSelect
          name="datasource"
          label="目标数据源"
          rules={[{ required: true, message: '目标数据源为必填项' }]}
          request={async () => {
            try {
              const res = await queryDataSourceList();
              return res.map((item: any) => ({ label: item.name, value: item.code }));
            } catch {
              return [];
            }
          }}
          placeholder="请选择目标数据源"
        />
      </ModalForm>

      {/* ========== 字段配置抽屉 ========== */}
      <FieldEditorDrawer
        open={drawerVisible}
        onOpenChange={setDrawerVisible}
        modelId={activeModelId}
        onRefresh={() => actionRef.current?.reload()}
      />

      {/* ========== 模板选择弹窗 ========== */}
      <TemplateSelectorModal
        open={templateModalVisible}
        onOpenChange={setTemplateModalVisible}
        onSelect={handleTemplateSelect}
      />

      {/* ========== 生成页面向导抽屉 ========== */}
      {wizardModel && (
        <GenerateWizardDrawer
          open={wizardVisible}
          onOpenChange={(v) => {
            setWizardVisible(v);
            if (!v) {
              setWizardModel(null);
              setPendingModel(null);
            }
          }}
          model={wizardModel}
          templateId={selectedTemplate}
          onSuccess={(pageId) => {
            setWizardVisible(false);
            setWizardModel(null);
            setPendingModel(null);
            setCurrentDesignerId(pageId);
            setDesignerModalVisible(true);
          }}
        />
      )}

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
            await batchMoveModel(ids, targetDir);
            message.success('批量移动成功');
            setBatchMoveModalVisible(false);
            setSelectedRows([]);
            actionRef.current?.clearSelected?.();
            actionRef.current?.reload();
            return true;
          } catch {
            message.error('批量移动失败');
            return false;
          }
        }}
      >
        <DirectoryTreeSelect />
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

export default DataModelList;
