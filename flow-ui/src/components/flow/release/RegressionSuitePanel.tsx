import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import {
  createRegressionCase,
  createRegressionSuite,
  deleteRegressionCase,
  deleteRegressionSuite,
  getRegressionSuite,
  listReleaseEnvs,
  pageRegressionSuites,
  runRegressionSuite,
  type FlowEnv,
  type RegressionCase,
  type RegressionRun,
  type RegressionSuite,
} from '@/services/flow/releaseService';

type Props = {
  open: boolean;
  onClose: () => void;
  assetType: 'API' | 'TASK' | 'SERVICE';
  assetId: string;
  assetName?: string;
};

/**
 * 资产侧回归套件管理（内部引擎执行，无外连 URL）。
 */
export default function RegressionSuitePanel({
  open,
  onClose,
  assetType,
  assetId,
  assetName,
}: Props) {
  const [suites, setSuites] = useState<RegressionSuite[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeSuite, setActiveSuite] = useState<RegressionSuite | null>(null);
  const [envs, setEnvs] = useState<FlowEnv[]>([]);
  const [envCode, setEnvCode] = useState('DEV');
  const [lastRun, setLastRun] = useState<RegressionRun | null>(null);
  const [caseModalOpen, setCaseModalOpen] = useState(false);
  const [caseForm] = Form.useForm();

  const reload = useCallback(async () => {
    if (!assetId) return;
    setLoading(true);
    try {
      const page = await pageRegressionSuites({ assetType, assetId, current: 1, pageSize: 20 });
      setSuites(page.data || []);
    } catch {
      setSuites([]);
    } finally {
      setLoading(false);
    }
  }, [assetType, assetId]);

  useEffect(() => {
    if (!open) return;
    reload();
    listReleaseEnvs()
      .then((list) => setEnvs(list || []))
      .catch(() => setEnvs([]));
  }, [open, reload]);

  const openSuite = async (id: string) => {
    const detail = await getRegressionSuite(id, true);
    setActiveSuite(detail);
    setLastRun(null);
  };

  const ensureSuite = async () => {
    if (suites.length > 0) {
      await openSuite(suites[0].id);
      return;
    }
    const created = await createRegressionSuite({
      name: `${assetName || assetType} 回归套件`,
      assetType,
      assetId,
      enabled: 1,
    });
    message.success('已创建默认套件');
    await reload();
    await openSuite(created.id);
  };

  const handleRun = async () => {
    if (!activeSuite?.id) return;
    const hide = message.loading('正在运行回归（内部引擎）…', 0);
    try {
      const run = await runRegressionSuite(activeSuite.id, envCode);
      setLastRun(run);
      message.success(run.status === 'PASSED' ? '回归全部通过' : `回归结束：${run.summary}`);
    } catch (e: any) {
      message.error(e?.message || '运行失败');
    } finally {
      hide();
    }
  };

  const submitCase = async () => {
    if (!activeSuite?.id) return;
    const values = await caseForm.validateFields();
    await createRegressionCase(activeSuite.id, {
      name: values.name,
      enabled: 1,
      sortOrder: values.sortOrder ?? 0,
      body: values.body,
      headersJson: values.headersJson,
      queryJson: values.queryJson,
      expectTraceStatus: values.expectTraceStatus,
      expectJsonPath: values.expectJsonPath,
      expectValue: values.expectValue,
      timeoutMs: values.timeoutMs ?? 10000,
    });
    message.success('用例已添加');
    setCaseModalOpen(false);
    caseForm.resetFields();
    await openSuite(activeSuite.id);
  };

  return (
    <Drawer
      title={`回归测试 · ${assetName || assetId}`}
      width={720}
      open={open}
      onClose={onClose}
      destroyOnClose
      extra={
        <Space>
          <Select
            style={{ width: 160 }}
            value={envCode}
            onChange={setEnvCode}
            options={(envs.length ? envs : [{ code: 'DEV', name: '开发' }]).map((e) => ({
              value: e.code,
              label: `${e.name}（${e.code}）`,
            }))}
          />
          <Button type="primary" disabled={!activeSuite} onClick={handleRun}>
            运行套件
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary">
        用例仅通过内部 FlowEngine 执行草稿 DSL，禁止填写外连 URL；敏感请求头会被拒绝保存。
        Database 写操作默认事务回滚，不会污染业务库。
      </Typography.Paragraph>

      {!activeSuite ? (
        <Space direction="vertical">
          <Table
            rowKey="id"
            loading={loading}
            size="small"
            pagination={false}
            dataSource={suites}
            columns={[
              { title: '套件', dataIndex: 'name' },
              { title: '用例数', dataIndex: 'caseCount', width: 80 },
              {
                title: '操作',
                width: 180,
                render: (_, row) => (
                  <Space>
                    <a onClick={() => openSuite(row.id)}>打开</a>
                    <a
                      onClick={async () => {
                        await deleteRegressionSuite(row.id);
                        message.success('已删除');
                        reload();
                      }}
                    >
                      删除
                    </a>
                  </Space>
                ),
              },
            ]}
          />
          <Button type="dashed" onClick={ensureSuite}>
            {suites.length ? '打开首个套件' : '创建默认套件'}
          </Button>
        </Space>
      ) : (
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <Space>
            <Button onClick={() => setActiveSuite(null)}>返回套件列表</Button>
            <Typography.Text strong>{activeSuite.name}</Typography.Text>
            <Button
              type="primary"
              onClick={() => {
                caseForm.resetFields();
                setCaseModalOpen(true);
              }}
            >
              添加用例
            </Button>
          </Space>
          <Table
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={activeSuite.cases || []}
            columns={[
              { title: '名称', dataIndex: 'name' },
              { title: '期望状态', dataIndex: 'expectTraceStatus', width: 100, render: (v) => v || '-' },
              { title: 'JSONPath', dataIndex: 'expectJsonPath', ellipsis: true, render: (v) => v || '-' },
              {
                title: '操作',
                width: 80,
                render: (_, row: RegressionCase) => (
                  <a
                    onClick={async () => {
                      if (!row.id || !activeSuite.id) return;
                      await deleteRegressionCase(row.id);
                      message.success('已删除');
                      await openSuite(activeSuite.id);
                    }}
                  >
                    删除
                  </a>
                ),
              },
            ]}
          />
          {lastRun && (
            <>
              <Space>
                <Tag color={lastRun.status === 'PASSED' ? 'success' : 'error'}>{lastRun.status}</Tag>
                <Typography.Text>{lastRun.summary}</Typography.Text>
                <Typography.Text type="secondary">环境 {lastRun.envCode}</Typography.Text>
              </Space>
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={lastRun.cases || []}
                columns={[
                  { title: '用例', dataIndex: 'caseName' },
                  {
                    title: '结果',
                    dataIndex: 'status',
                    width: 90,
                    render: (v) => (
                      <Tag color={v === 'PASSED' ? 'success' : 'error'}>{v}</Tag>
                    ),
                  },
                  { title: '耗时(ms)', dataIndex: 'durationMs', width: 90 },
                  { title: '说明', dataIndex: 'message', ellipsis: true },
                ]}
              />
            </>
          )}
        </Space>
      )}

      <Modal
        title="添加回归用例"
        open={caseModalOpen}
        onCancel={() => setCaseModalOpen(false)}
        onOk={submitCase}
        destroyOnClose
        width={560}
      >
        <Form form={caseForm} layout="vertical" initialValues={{ expectTraceStatus: 'success', timeoutMs: 10000 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '必填' }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="body" label="请求 Body（JSON 文本，≤32KB）">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            name="headersJson"
            label="Headers JSON"
            extra="禁止 Authorization / Cookie / *Token* 等敏感头"
          >
            <Input.TextArea rows={2} placeholder='{"X-Request-Id":"demo"}' />
          </Form.Item>
          <Form.Item name="queryJson" label="Query JSON">
            <Input.TextArea rows={2} placeholder='{"page":"1"}' />
          </Form.Item>
          <Form.Item name="expectTraceStatus" label="期望 trace.status">
            <Select
              allowClear
              options={[
                { value: 'success', label: 'success' },
                { value: 'error', label: 'error' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="expectJsonPath"
            label="断言 JSONPath"
            extra="仅简单路径，如 $.status 或 $.outputs.code"
          >
            <Input placeholder="$.status" maxLength={128} />
          </Form.Item>
          <Form.Item name="expectValue" label="期望值（字符串比较）">
            <Input maxLength={500} />
          </Form.Item>
          <Form.Item name="timeoutMs" label="超时(ms)">
            <InputNumber min={1000} max={10000} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  );
}
