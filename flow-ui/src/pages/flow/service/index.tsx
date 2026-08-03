import React, { useState } from 'react';
import { ProColumns } from '@ant-design/pro-components';
import { Divider, message, Popconfirm, Switch, Tag, Tooltip } from 'antd';
import { history } from '@umijs/max';
import {
  queryServiceFlowPage,
  createServiceFlow,
  updateServiceFlow,
  batchDeleteServiceFlow,
  enableServiceFlow,
  disableServiceFlow,
  updateServiceFlowLogEnabled,
  getServiceFlow,
  publishServiceFlow,
  unpublishServiceFlow,
  FlowServiceFlow,
} from '@/services/flow/serviceFlowService';
import ServiceFlowForm from './components/ServiceFlowForm';
import ServiceManualRunModal from './components/ServiceManualRunModal';
import { confirmServiceUnpublish } from './components/confirmServiceUnpublish';
import AssetDirectoryListShell, {
  type AssetListShellContext,
} from '@/components/flow/AssetDirectoryListShell';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';

const handleAdd = async (fields: Partial<FlowServiceFlow>) => {
  const hide = message.loading('正在添加');
  try {
    await createServiceFlow(fields);
    hide();
    message.success('添加成功');
    return true;
  } catch {
    hide();
    message.error('添加失败，请重试');
    return false;
  }
};

const handleUpdate = async (id: string, fields: Partial<FlowServiceFlow>) => {
  const hide = message.loading('正在更新');
  try {
    await updateServiceFlow(id, fields);
    hide();
    message.success('更新成功');
    return true;
  } catch {
    hide();
    message.error('更新失败，请重试');
    return false;
  }
};

const handleRemove = async (selectedRows: FlowServiceFlow[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteServiceFlow(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功');
    return true;
  } catch (e: any) {
    hide();
    // 引用拦截等业务错误已由 request 拦截器提示；此处兜底
    if (!e?.message) message.error('删除失败，请重试');
    return false;
  }
};

const ServiceFlowManagement: React.FC = () => {
  const [manualRunOpen, setManualRunOpen] = useState(false);
  const [manualRunTarget, setManualRunTarget] = useState<FlowServiceFlow | null>(null);

  const buildColumns = (
    ctx: AssetListShellContext<FlowServiceFlow>,
  ): ProColumns<FlowServiceFlow>[] => [
    {
      title: '服务名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 200,
      render: (_, record) => (
        <a onClick={() => ctx.openEdit(record)} title={record.name}>
          {record.name}
        </a>
      ),
    },
    {
      title: '所属目录',
      dataIndex: 'directoryName',
      width: 120,
      hideInSearch: true,
      ellipsis: true,
      render: (_, record) =>
        record.directoryName ? <Tag>{record.directoryName}</Tag> : '-',
    },
    {
      title: '启用状态',
      dataIndex: 'enabled',
      width: 90,
      valueType: 'select',
      valueEnum: {
        true: { text: '启用' },
        false: { text: '停用' },
      },
      render: (_, record) => (
        <Switch
          size="small"
          checked={!!record.enabled}
          onChange={async (checked) => {
            try {
              if (checked) {
                await enableServiceFlow(record.id);
              } else {
                await disableServiceFlow(record.id);
              }
              message.success(checked ? '已启用' : '已停用');
              ctx.reload();
            } catch {
              message.error('操作失败');
            }
          }}
        />
      ),
    },
    {
      title: '发布状态',
      dataIndex: 'publishStatus',
      width: 120,
      valueType: 'select',
      valueEnum: {
        0: { text: '未发布', status: 'Default' },
        1: { text: '已发布', status: 'Success' },
      },
      render: (_, record) => {
        if (record.publishStatus === 1 && record.hasUnpublishedChanges) {
          return (
            <Tooltip title="存在未发布的草稿修改">
              <Tag color="warning">待更新发布</Tag>
            </Tooltip>
          );
        }
        return record.publishStatus === 1
          ? <Tag color="success">已发布</Tag>
          : <Tag>未发布</Tag>;
      },
    },
    {
      title: '运行健康',
      dataIndex: 'runtimeHealth',
      width: 100,
      hideInSearch: true,
      render: (_, record) => renderHealthTag(ctx.healthMap[record.id]),
    },
    {
      title: (
        <Tooltip title="日志策略模式：继承全局 / 仅错误时记录 / 全量记录 / 完全关闭">
          <span>日志策略</span>
        </Tooltip>
      ),
      dataIndex: 'logMode',
      width: 100,
      hideInSearch: true,
      render: (_, record) => {
        const mode = record.logMode || (record.logEnabled === false ? 'OFF' : (record.logEnabled === true ? 'ALL' : 'SYSTEM_DEFAULT'));
        switch (mode) {
          case 'ALL':
            return <Tag color="blue">全量记录</Tag>;
          case 'ERROR_ONLY':
            return <Tag color="warning">仅错误</Tag>;
          case 'OFF':
            return <Tag color="default">完全关闭</Tag>;
          case 'SYSTEM_DEFAULT':
          default:
            return <Tag color="cyan">继承全局</Tag>;
        }
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      hideInSearch: true,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 380,
      render: (_, record) => [
        <a key="edit" onClick={() => ctx.openEdit(record)}>
          编辑
        </a>,
        <Divider key="d0" type="vertical" />,
        <a
          key="run"
          onClick={() => {
            setManualRunTarget(record);
            setManualRunOpen(true);
          }}
        >
          手动调用
        </a>,
        <Divider key="d1" type="vertical" />,
        record.publishStatus === 1 ? (
          <a
            key="unpublish"
            onClick={async () => {
              const ok = await confirmServiceUnpublish(record.id, record.name);
              if (!ok) return;
              try {
                await unpublishServiceFlow(record.id);
                message.success('已下线');
                ctx.reload();
              } catch (e: any) {
                message.error(e?.message || '下线失败');
              }
            }}
          >
            下线
          </a>
        ) : (
          <a
            key="publish"
            onClick={async () => {
              try {
                const { confirmPublishWithGate } = await import(
                  '@/components/flow/release/confirmPublishWithGate'
                );
                const envCode = await confirmPublishWithGate({
                  assetType: 'SERVICE',
                  assetId: record.id,
                  assetName: record.name,
                });
                if (!envCode) return;
                await publishServiceFlow(record.id, envCode);
                message.success('发布成功');
                ctx.reload();
              } catch (e: any) {
                message.error(e?.message || '发布失败');
              }
            }}
          >
            发布
          </a>
        ),
        <Divider key="d2" type="vertical" />,
        <a
          key="logs"
          onClick={() => history.push(`/log/service?serviceId=${record.id}`)}
        >
          查看日志
        </a>,
        <Divider key="d3" type="vertical" />,
        <Popconfirm
          key="delete"
          title="确定删除该服务？"
          onConfirm={() => ctx.removeAndReload([record])}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <AssetDirectoryListShell<FlowServiceFlow>
      bizType="service"
      pageTitle="服务管理"
      entityLabel="服务"
      listTitle="服务列表"
      emptyHint="沉淀可复用的编排流程，供接口 / 任务作为子流程调用"
      metricsAssetType="SERVICE"
      deepLinkParam="serviceId"
      fetchDetail={getServiceFlow}
      isFiltered={({ directoryId, name, enabled, publishStatus }) =>
        !!directoryId || !!name || enabled !== undefined || publishStatus !== undefined
      }
      fetchPage={async (params) => {
        const { current, pageSize, directoryId, name, enabled, publishStatus } = params;
        const enabledParam =
          enabled === true || enabled === 'true'
            ? true
            : enabled === false || enabled === 'false'
              ? false
              : undefined;
        const publishParam =
          publishStatus === 0 || publishStatus === '0'
            ? 0
            : publishStatus === 1 || publishStatus === '1'
              ? 1
              : undefined;
        const result = await queryServiceFlowPage({
          directoryId,
          name,
          enabled: enabledParam,
          publishStatus: publishParam,
          page: (current || 1) - 1,
          size: pageSize || 20,
        });
        const data = (result as any)?.data || result;
        return { items: data?.items || [], total: data?.total || 0 };
      }}
      submitCreate={handleAdd}
      submitUpdate={handleUpdate}
      removeRows={handleRemove}
      buildColumns={buildColumns}
      renderForm={(form) => (
        <ServiceFlowForm
          visible={form.visible}
          isEdit={form.isEdit}
          initialValues={form.currentRow}
          initialTab={form.initialTab}
          onCancel={form.close}
          onSubmit={form.submit}
          onPublished={form.onPublished}
        />
      )}
    >
      <ServiceManualRunModal
        open={manualRunOpen}
        serviceId={manualRunTarget?.id}
        serviceName={manualRunTarget?.name}
        onClose={() => {
          setManualRunOpen(false);
          setManualRunTarget(null);
        }}
      />
    </AssetDirectoryListShell>
  );
};

export default ServiceFlowManagement;
