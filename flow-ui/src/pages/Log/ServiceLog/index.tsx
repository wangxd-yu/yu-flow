import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Drawer, message, Popconfirm, Tag, Typography } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import FlowEditor from '@/components/flow/FlowEditor';
import { FlowTrace } from '@/components/flow/debugger/FlowDebugger';
import {
  queryServiceLogPage,
  getServiceLog,
  getServiceFlow,
  clearServiceLog,
  FlowServiceLog,
} from '@/services/flow/serviceFlowService';
import { LogStatusTag, LogDuration, type LogStatusKind, LogDeepLinkBar } from '../shared';
import '../shared/logPageLayout.css';

const { Text } = Typography;

const STATUS_TAG: Record<string, { kind: LogStatusKind; text: string }> = {
  SUCCESS: { kind: 'success', text: '执行成功' },
  FAILED: { kind: 'error', text: '执行失败' },
  RUNNING: { kind: 'processing', text: '运行中' },
};

const ServiceLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<FlowServiceLog | null>(null);
  const [currentDsl, setCurrentDsl] = useState('');
  const [currentTrace, setCurrentTrace] = useState<FlowTrace | null>(null);

  const urlParams = new URLSearchParams(window.location.search);
  const initialServiceId = urlParams.get('serviceId') || undefined;

  const handleViewTrace = async (record: FlowServiceLog) => {
    if (!record.hasTrace) {
      message.warning('该服务日志没有关联的追踪快照数据');
      return;
    }

    try {
      const detail = await getServiceLog(record.id);
      const log: FlowServiceLog = (detail as any)?.data || detail;
      if (!log?.traceData) {
        message.warning('未找到该服务日志的追踪快照数据');
        return;
      }

      const trace: FlowTrace & { dslSnapshot?: string; dslContentHash?: string } = JSON.parse(log.traceData);
      let dslContent = trace.dslSnapshot || '';
      if (!dslContent && log.serviceId) {
        try {
          const svcInfo: any = await getServiceFlow(log.serviceId);
          const svc = svcInfo?.data || svcInfo;
          dslContent = svc?.dslContent || '';
        } catch (e) {
          console.error('getServiceFlow failed:', e);
        }
      }
      if (!dslContent) {
        message.warning('无法加载服务 DSL，画布将显示为空');
      }

      setCurrentLog(log);
      setCurrentTrace(trace);
      setCurrentDsl(dslContent);
      setDrawerVisible(true);
    } catch (e) {
      message.error('解析执行快照失败');
      console.error(e);
    }
  };

  const columns: ProColumns<FlowServiceLog>[] = [
    {
      title: '序号',
      valueType: 'index',
      width: 60,
      fixed: 'left',
      search: false,
    },
    {
      title: '服务名称',
      dataIndex: 'serviceName',
      width: 160,
      fixed: 'left',
      ellipsis: true,
      render: (_, record) => (
        <Text strong style={{ color: '#1677ff' }}>
          {record.serviceName || '未命名'}
        </Text>
      ),
    },
    {
      title: '触发类型',
      dataIndex: 'triggerType',
      width: 110,
      valueEnum: {
        MANUAL: { text: 'MANUAL' },
        CALL: { text: 'CALL' },
        DEBUG: { text: 'DEBUG' },
      },
      render: (_, record) =>
        record.triggerType ? (
          <Tag color={record.triggerType === 'MANUAL' ? 'blue' : 'purple'}>
            {record.triggerType}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '执行状态',
      dataIndex: 'status',
      width: 120,
      valueEnum: {
        SUCCESS: { text: '成功', status: 'Success' },
        FAILED: { text: '失败', status: 'Error' },
        RUNNING: { text: '运行中', status: 'Processing' },
      },
      render: (_, record) => {
        const s = STATUS_TAG[record.status || ''];
        if (s) {
          return <LogStatusTag kind={s.kind} text={s.text} />;
        }
        return record.status || '-';
      },
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 100,
      search: false,
      render: (_, record) => <LogDuration ms={record.costTimeMs} />,
    },
    {
      title: '执行时间',
      dataIndex: 'createTime',
      width: 180,
      valueType: 'dateTimeRange',
      fieldProps: {
        placeholder: ['开始时间', '结束时间'],
      },
      render: (_, record) => record.createTime || '-',
      search: {
        transform: (value) => ({
          createTime: value,
        }),
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="view"
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewTrace(record)}
          disabled={!record.hasTrace}
        >
          查看快照
        </Button>,
      ],
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: '服务日志',
        subTitle: '监控与追溯内部服务的手动/编排调用执行状态与快照',
      }}
    >
      <ProTable<FlowServiceLog>
        className="fh-table"
        rowKey="id"
        actionRef={actionRef}
        headerTitle={
          initialServiceId ? (
            <LogDeepLinkBar
              label={`serviceId=${initialServiceId}`}
              clearPath="/log/service"
            />
          ) : (
            '全部服务日志'
          )
        }
        tableLayout="fixed"
        scroll={{ x: 1000, y: 100000 }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
        }}
        toolBarRender={() => [
          initialServiceId && (
            <Popconfirm
              key="clear"
              title="确定清空该服务的全部日志？"
              onConfirm={async () => {
                await clearServiceLog(initialServiceId);
                message.success('日志已清空');
                actionRef.current?.reload();
              }}
            >
              <Button danger>清空日志</Button>
            </Popconfirm>
          ),
        ]}
        params={{ serviceId: initialServiceId }}
        request={async (params) => {
          const { createTime, ...rest } = params;
          let startTime: string | undefined;
          let endTime: string | undefined;
          if (createTime && Array.isArray(createTime)) {
            startTime = createTime[0];
            endTime = createTime[1];
          }

          const result = await queryServiceLogPage({
            serviceId: rest.serviceId || initialServiceId,
            serviceName: rest.serviceName,
            status: rest.status,
            triggerType: rest.triggerType,
            startTime,
            endTime,
            page: (rest.current || 1) - 1,
            size: rest.pageSize || 20,
          });
          const data = (result as any)?.data || result;
          return {
            data: data?.items || [],
            success: true,
            total: data?.total || 0,
          };
        }}
        columns={columns}
        rowClassName={(record) =>
          record.status === 'FAILED' ? 'log-row-fail' : ''
        }
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      {drawerVisible && (
        <Drawer
          title={`服务快照复原 - ${currentLog?.serviceName || ''}`}
          width="100%"
          open={drawerVisible}
          onClose={() => setDrawerVisible(false)}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          {currentTrace ? (
            <FlowEditor
              value={currentDsl}
              isEdit={false}
              readonlyTrace={currentTrace}
              defaultEntryNode="service"
            />
          ) : null}
        </Drawer>
      )}
    </PageContainer>
  );
};

export default ServiceLogPage;
