import React, { useEffect, useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormDependency,
  ProTable,
  ModalForm,
} from '@ant-design/pro-components';
import { Button, Popconfirm, Space, Switch, Tabs, Tag, message } from 'antd';
import {
  AlertChannel,
  AlertRule,
  createAlertChannel,
  createAlertRule,
  deleteAlertChannel,
  deleteAlertRule,
  listAlertChannels,
  pageAlertChannels,
  pageAlertRules,
  runAlertRuleOnce,
  testAlertChannel,
  updateAlertChannel,
  updateAlertRule,
} from '@/services/flow/alertService';
import '@/styles/fullHeightTable.css';

const AlertRulesPage: React.FC = () => {
  const ruleRef = useRef<ActionType>();
  const channelRef = useRef<ActionType>();
  const [channels, setChannels] = useState<AlertChannel[]>([]);
  const [ruleModal, setRuleModal] = useState<{ open: boolean; record?: AlertRule }>({ open: false });
  const [channelModal, setChannelModal] = useState<{ open: boolean; record?: AlertChannel }>({
    open: false,
  });

  const reloadChannels = async () => {
    try {
      const list = await listAlertChannels();
      setChannels(list || []);
    } catch {
      /* ignore */
    }
  };

  useEffect(() => {
    reloadChannels();
  }, []);

  const ruleColumns: ProColumns<AlertRule>[] = [
    { title: '名称', dataIndex: 'name', width: 160 },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 90,
      valueEnum: { 1: { text: '启用' }, 0: { text: '停用' } },
      render: (_, r) => (
        <Switch
          size="small"
          checked={r.enabled === 1}
          onChange={async (checked) => {
            try {
              await updateAlertRule(r.id, { enabled: checked ? 1 : 0 });
              message.success(checked ? '已启用' : '已停用');
              ruleRef.current?.reload();
            } catch {
              /* interceptor */
            }
          }}
        />
      ),
    },
    { title: '窗口', dataIndex: 'window', width: 80, search: false },
    { title: '最低健康度', dataIndex: 'minHealth', width: 100, search: false },
    { title: 'TopN', dataIndex: 'topN', width: 70, search: false },
    {
      title: '资产范围',
      dataIndex: 'scopeAssetTypes',
      width: 160,
      search: false,
      ellipsis: true,
      render: (_, r) => r.scopeAssetTypes || '全部',
    },
    {
      title: '通道',
      dataIndex: 'channelIds',
      width: 160,
      search: false,
      ellipsis: true,
      render: (_, r) => {
        let ids: string[] = [];
        try {
          ids = r.channelIds ? JSON.parse(r.channelIds) : [];
        } catch {
          ids = [];
        }
        if (!ids.length) return <Tag>未绑定</Tag>;
        const names = ids
          .map((id) => channels.find((c) => c.id === id)?.name || id)
          .join(', ');
        return names;
      },
    },
    {
      title: '间隔/去重(分)',
      search: false,
      width: 120,
      render: (_, r) => `${r.intervalMinutes}/${r.dedupMinutes}`,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, r) => (
        <Space>
          <a onClick={() => setRuleModal({ open: true, record: r })}>编辑</a>
          <a
            onClick={async () => {
              try {
                await runAlertRuleOnce(r.id);
                message.success('已触发一次扫描');
              } catch {
                /* interceptor */
              }
            }}
          >
            立即执行
          </a>
          <Popconfirm
            title="确认删除该规则？"
            onConfirm={async () => {
              await deleteAlertRule(r.id);
              message.success('已删除');
              ruleRef.current?.reload();
            }}
          >
            <a>删除</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const channelColumns: ProColumns<AlertChannel>[] = [
    { title: '名称', dataIndex: 'name', width: 160 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      valueEnum: {
        WEBHOOK: { text: 'Webhook' },
        EMAIL: { text: 'Email' },
      },
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 90,
      search: false,
      render: (_, r) =>
        r.enabled === 1 ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '配置',
      dataIndex: 'configJson',
      search: false,
      ellipsis: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, r) => (
        <Space>
          <a onClick={() => setChannelModal({ open: true, record: r })}>编辑</a>
          <a
            onClick={async () => {
              try {
                const res: any = await testAlertChannel(r.id);
                if (res?.success !== false) {
                  message.success('测试已发送');
                } else {
                  message.error('测试失败');
                }
              } catch {
                /* interceptor */
              }
            }}
          >
            测试
          </a>
          <Popconfirm
            title="确认删除该通道？"
            onConfirm={async () => {
              await deleteAlertChannel(r.id);
              message.success('已删除');
              channelRef.current?.reload();
              reloadChannels();
            }}
          >
            <a>删除</a>
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
      <Tabs
        className="fh-tabs"
        items={[
          {
            key: 'rules',
            label: '告警规则',
            children: (
              <div className="fh-tab-pane">
                <ProTable<AlertRule>
                  className="fh-table"
                  actionRef={ruleRef}
                  rowKey="id"
                  columns={ruleColumns}
                  request={pageAlertRules}
                  search={{ labelWidth: 'auto' }}
                  tableLayout="fixed"
                  toolBarRender={() => [
                    <Button
                      key="add"
                      type="primary"
                      onClick={() => setRuleModal({ open: true })}
                    >
                      新建规则
                    </Button>,
                  ]}
                  pagination={{ defaultPageSize: 20 }}
                  scroll={{ y: 100000 }}
                />
              </div>
            ),
          },
          {
            key: 'channels',
            label: '告警通道',
            children: (
              <div className="fh-tab-pane">
                <ProTable<AlertChannel>
                  className="fh-table"
                  actionRef={channelRef}
                  rowKey="id"
                  columns={channelColumns}
                  request={pageAlertChannels}
                  search={{ labelWidth: 'auto' }}
                  tableLayout="fixed"
                  toolBarRender={() => [
                    <Button
                      key="add"
                      type="primary"
                      onClick={() => setChannelModal({ open: true })}
                    >
                      新建通道
                    </Button>,
                  ]}
                  pagination={{ defaultPageSize: 20 }}
                  scroll={{ y: 100000 }}
                />
              </div>
            ),
          },
        ]}
      />

      <ModalForm
        title={ruleModal.record ? '编辑规则' : '新建规则'}
        open={ruleModal.open}
        layout="vertical"
        modalProps={{
          destroyOnClose: true,
          onCancel: () => setRuleModal({ open: false }),
        }}
        initialValues={
          ruleModal.record
            ? {
                ...ruleModal.record,
                enabled: ruleModal.record.enabled === 1,
                channelIdList: (() => {
                  try {
                    return ruleModal.record.channelIds
                      ? JSON.parse(ruleModal.record.channelIds)
                      : [];
                  } catch {
                    return [];
                  }
                })(),
                scopeList: ruleModal.record.scopeAssetTypes
                  ? ruleModal.record.scopeAssetTypes.split(',').filter(Boolean)
                  : [],
              }
            : {
                enabled: true,
                window: '24h',
                minHealth: 'error',
                topN: 10,
                intervalMinutes: 15,
                dedupMinutes: 60,
                channelIdList: [],
                scopeList: [],
              }
        }
        onFinish={async (values) => {
          const payload = {
            name: values.name,
            enabled: values.enabled ? 1 : 0,
            window: values.window,
            minHealth: values.minHealth,
            topN: values.topN,
            intervalMinutes: values.intervalMinutes,
            dedupMinutes: values.dedupMinutes,
            scopeAssetTypes: (values.scopeList || []).join(','),
            channelIds: JSON.stringify(values.channelIdList || []),
          };
          if (ruleModal.record?.id) {
            await updateAlertRule(ruleModal.record.id, payload);
          } else {
            await createAlertRule(payload);
          }
          message.success('已保存');
          setRuleModal({ open: false });
          ruleRef.current?.reload();
          return true;
        }}
      >
        <ProFormText name="name" label="规则名称" rules={[{ required: true }]} />
        <ProFormSwitch name="enabled" label="启用" />
        <ProFormSelect
          name="window"
          label="指标窗口"
          options={[
            { label: '1h', value: '1h' },
            { label: '24h', value: '24h' },
            { label: '7d', value: '7d' },
          ]}
          rules={[{ required: true }]}
        />
        <ProFormSelect
          name="minHealth"
          label="最低告警健康度"
          options={[
            { label: 'error（仅严重）', value: 'error' },
            { label: 'warn（含预警）', value: 'warn' },
          ]}
          rules={[{ required: true }]}
        />
        <ProFormDigit name="topN" label="TopN" min={1} max={100} rules={[{ required: true }]} />
        <ProFormDigit
          name="intervalMinutes"
          label="扫描间隔（分钟）"
          min={1}
          rules={[{ required: true }]}
        />
        <ProFormDigit
          name="dedupMinutes"
          label="去重静默（分钟）"
          min={1}
          rules={[{ required: true }]}
        />
        <ProFormSelect
          name="scopeList"
          label="资产范围"
          mode="multiple"
          placeholder="空=全部"
          options={[
            { label: 'API', value: 'API' },
            { label: 'TASK', value: 'TASK' },
            { label: 'SERVICE', value: 'SERVICE' },
            { label: 'PLATFORM', value: 'PLATFORM' },
          ]}
        />
        <ProFormSelect
          name="channelIdList"
          label="推送通道"
          mode="multiple"
          options={channels.map((c) => ({
            label: `${c.name} (${c.type})`,
            value: c.id,
          }))}
          rules={[{ required: true, message: '请选择至少一个通道' }]}
        />
      </ModalForm>

      <ModalForm
        title={channelModal.record ? '编辑通道' : '新建通道'}
        open={channelModal.open}
        layout="vertical"
        modalProps={{
          destroyOnClose: true,
          onCancel: () => setChannelModal({ open: false }),
        }}
        initialValues={(() => {
          if (channelModal.record) {
            let url = '';
            let to = '';
            let cc = '';
            let subject = '';
            try {
              const cfg = JSON.parse(channelModal.record.configJson || '{}');
              url = cfg.url || '';
              to = cfg.to || '';
              cc = cfg.cc || '';
              subject = cfg.subject || '';
            } catch {
              /* ignore */
            }
            return {
              name: channelModal.record.name,
              type: channelModal.record.type,
              enabled: channelModal.record.enabled === 1,
              url,
              to,
              cc,
              subject,
            };
          }
          return {
            type: 'WEBHOOK',
            enabled: true,
            url: '',
            to: '',
            cc: '',
            subject: '',
          };
        })()}
        onFinish={async (values) => {
          const configJson =
            values.type === 'EMAIL'
              ? JSON.stringify({
                  to: values.to || '',
                  cc: values.cc || '',
                  subject: values.subject || '',
                })
              : JSON.stringify({ url: values.url || '' });
          const payload = {
            name: values.name,
            type: values.type,
            configJson,
            enabled: values.enabled ? 1 : 0,
          };
          if (channelModal.record?.id) {
            await updateAlertChannel(channelModal.record.id, payload);
          } else {
            await createAlertChannel(payload);
          }
          message.success('已保存');
          setChannelModal({ open: false });
          channelRef.current?.reload();
          reloadChannels();
          return true;
        }}
      >
        <ProFormText name="name" label="通道名称" rules={[{ required: true }]} />
        <ProFormSelect
          name="type"
          label="类型"
          options={[
            { label: 'Webhook', value: 'WEBHOOK' },
            { label: 'Email', value: 'EMAIL' },
          ]}
          rules={[{ required: true }]}
        />
        <ProFormSwitch name="enabled" label="启用" />
        <ProFormDependency name={['type']}>
          {({ type }) =>
            type === 'EMAIL' ? (
              <>
                <ProFormText
                  name="to"
                  label="收件人"
                  extra="多个地址用逗号分隔。SMTP 在「系统配置 → 邮件 SMTP」中配置"
                  rules={[{ required: true, message: '请输入收件人' }]}
                />
                <ProFormText name="cc" label="抄送" />
                <ProFormText name="subject" label="主题覆盖" placeholder="空则使用告警默认主题" />
              </>
            ) : (
              <ProFormText
                name="url"
                label="Webhook URL"
                fieldProps={{
                  placeholder: 'https://oapi.dingtalk.com/robot/send?access_token=...',
                }}
                rules={[{ required: true, message: '请输入 Webhook URL' }]}
              />
            )
          }
        </ProFormDependency>
      </ModalForm>
    </PageContainer>
  );
};

export default AlertRulesPage;
