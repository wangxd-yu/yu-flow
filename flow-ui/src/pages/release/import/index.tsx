import React, { useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Input,
  List,
  Result,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import {
  ACTION_LABELS,
  ASSET_TYPE_LABELS,
  PLACEHOLDER_KINDS,
  REQUIREMENT_LABELS,
  type TransferItem,
  type TransferRequirement,
} from '@/services/flow/assetTransfer';
import {
  createReleasePlaceholder,
  executeReleaseImport,
  inspectReleasePackage,
  SIGNATURE_LABELS,
  type ReleaseGateItem,
  type ReleaseImportLog,
  type ReleaseInspectResult,
} from '@/services/flow/releaseImport';
import DiffModal from './components/DiffModal';

const ACTION_COLORS: Record<string, string> = {
  CREATE: 'success',
  UPDATE: 'processing',
  SKIP: 'default',
  CONFLICT: 'error',
  OFFLINE: 'volcano',
};

/**
 * 目标环境导入发布包：上传 → 预检 → 输入版本号确认 → 导入并发布。
 */
const ReleaseImport: React.FC = () => {
  const [file, setFile] = useState<File>();
  const [inspect, setInspect] = useState<ReleaseInspectResult>();
  const [inspecting, setInspecting] = useState(false);
  const [confirmCode, setConfirmCode] = useState('');
  const [privilegedAck, setPrivilegedAck] = useState(false);
  const [importing, setImporting] = useState(false);
  const [done, setDone] = useState<ReleaseImportLog>();
  const [diffTarget, setDiffTarget] = useState<{ digest: string; assetType: string; key: string; name?: string }>();
  const [creating, setCreating] = useState<string>();

  const reinspect = async () => {
    if (!file) return;
    setInspect(await inspectReleasePackage(file));
  };

  const createPlaceholder = async (r: TransferRequirement) => {
    setCreating(`${r.kind}:${r.key}`);
    try {
      const created = await createReleasePlaceholder({ kind: r.kind, key: r.key, attributes: r.attributes, remark: r.remark });
      message.success(
        created
          ? `已创建停用的占位「${r.key}」，请到对应管理页补填并启用后重新预检`
          : `「${r.key}」已存在，请到对应管理页确认已启用并补填`,
      );
      await reinspect();
    } finally {
      setCreating(undefined);
    }
  };

  const reset = () => {
    setFile(undefined);
    setInspect(undefined);
    setConfirmCode('');
    setPrivilegedAck(false);
    setDone(undefined);
  };

  const onPick = async (picked: File) => {
    reset();
    setFile(picked);
    setInspecting(true);
    try {
      setInspect(await inspectReleasePackage(picked));
    } catch {
      setFile(undefined);
    } finally {
      setInspecting(false);
    }
    return false;
  };

  const doImport = async () => {
    if (!file || !inspect) return;
    setImporting(true);
    try {
      const log = await executeReleaseImport(file, confirmCode, privilegedAck);
      setDone(log);
      if (log.runtimeIssues?.length) {
        message.warning('导入并发布完成，但运行时自检发现问题，请查看');
      } else {
        message.success('导入并发布完成');
      }
    } finally {
      setImporting(false);
    }
  };

  const missing = useMemo(
    () => (inspect?.report.requirements || []).filter((r) => r.satisfied === false),
    [inspect],
  );
  const privileged = inspect?.privilegedChanges || [];

  if (done) {
    const issues = done.runtimeIssues || [];
    return (
      <PageContainer>
        <Card>
          <Result
            status={issues.length ? 'warning' : 'success'}
            title={`版本 ${done.releaseCode} 已导入并发布`}
            subTitle={done.summary}
            extra={[
              <Button key="records" type="primary" onClick={() => history.push('/release/imports')}>
                查看导入记录
              </Button>,
              <Button key="again" onClick={reset}>
                导入其它发布包
              </Button>,
            ]}
          >
            {issues.length > 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 16 }}
                message="运行时自检发现问题（数据已导入，以下任务可能没有运行）"
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {issues.map((i) => (
                      <li key={i}>{i}</li>
                    ))}
                  </ul>
                }
              />
            )}
            <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
              如验证发现问题，可在「导入记录」中一键回滚到导入前。
            </Typography.Paragraph>
          </Result>
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card>
          <Upload.Dragger
            accept=".yfpkg"
            multiple={false}
            showUploadList={false}
            beforeUpload={(f) => onPick(f)}
            disabled={inspecting || importing}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">{file ? file.name : '点击或拖入发布包（.yfpkg）'}</p>
            <p className="ant-upload-hint">
              先做完整性校验与预检，不会修改任何数据；确认后才会导入并发布。导入前会自动备份，可一键回滚。
            </p>
          </Upload.Dragger>
        </Card>

        {inspecting && <Card loading />}

        {inspect && (
          <>
            <Card title={`发布包 ${inspect.releaseCode}${inspect.releaseName ? ` · ${inspect.releaseName}` : ''}`}>
              <Descriptions size="small" column={3}>
                <Descriptions.Item label="环境">
                  <Space>
                    <Tag>{inspect.sourceEnv || '-'}</Tag>→<Tag color="red">{inspect.targetEnv}</Tag>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="导出">
                  {inspect.exportedAt} · {inspect.exportedBy}
                </Descriptions.Item>
                <Descriptions.Item label="包摘要">
                  <Typography.Text code copyable={{ text: inspect.packageDigest }}>
                    {inspect.packageDigest.slice(0, 12)}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label="资产">{inspect.itemCount} 项</Descriptions.Item>
                <Descriptions.Item label="动作">
                  新增 {inspect.report.createCount} · 更新 {inspect.report.updateCount} · 下线{' '}
                  {inspect.report.offlineCount || 0} · 跳过 {inspect.report.skipCount} · 冲突 {inspect.report.conflictCount}
                </Descriptions.Item>
                <Descriptions.Item label="签名">
                  {inspect.signatureStatus && (
                    <Tag color={SIGNATURE_LABELS[inspect.signatureStatus].color}>
                      {SIGNATURE_LABELS[inspect.signatureStatus].text}
                    </Tag>
                  )}
                  {inspect.signatureRequired && <Typography.Text type="secondary">本环境要求签名</Typography.Text>}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            {inspect.blocked && (
              <Alert
                type="error"
                showIcon
                message="预检未通过，不能导入"
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {inspect.blockReasons.map((r) => (
                      <li key={r}>{r}</li>
                    ))}
                  </ul>
                }
              />
            )}
            {inspect.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message="请留意"
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {inspect.warnings.map((w) => (
                      <li key={w}>{w}</li>
                    ))}
                  </ul>
                }
              />
            )}
            {privileged.length > 0 && (
              <Card
                title={
                  <Space>
                    <Tag color="red">需逐项核对</Tag>
                    高权限变更（{privileged.length}）
                  </Space>
                }
              >
                <Typography.Paragraph type="secondary">
                  全局宏可以读取系统配置，开放平台授权决定外部系统能调哪些接口。请与发布说明逐项核对，确认都是本次计划内的变更。
                </Typography.Paragraph>
                <List
                  size="small"
                  dataSource={privileged}
                  renderItem={(c) => (
                    <List.Item>
                      <Space direction="vertical" size={2} style={{ width: '100%' }}>
                        <Space>
                          <Tag>{ASSET_TYPE_LABELS[c.assetType as keyof typeof ASSET_TYPE_LABELS] || c.assetType}</Tag>
                          <strong>{c.name || c.key}</strong>
                          <Typography.Text type="secondary">{c.key}</Typography.Text>
                        </Space>
                        <Typography.Text style={{ wordBreak: 'break-all' }}>{c.description}</Typography.Text>
                      </Space>
                    </List.Item>
                  )}
                />
                <Checkbox
                  checked={privilegedAck}
                  disabled={inspect.blocked}
                  onChange={(e) => setPrivilegedAck(e.target.checked)}
                >
                  我已逐项核对以上高权限变更，确认来源可信
                </Checkbox>
              </Card>
            )}

            <Card bodyStyle={{ paddingTop: 8 }}>
              <Tabs
                items={[
                  {
                    key: 'items',
                    label: `逐项动作（${inspect.report.items.length}）`,
                    children: (
                      <Table<TransferItem>
                        size="small"
                        rowKey={(r) => `${r.assetType}:${r.id}:${r.action}`}
                        dataSource={inspect.report.items}
                        pagination={{ pageSize: 20, hideOnSinglePage: true }}
                        columns={[
                          { title: '类型', dataIndex: 'assetType', width: 110, render: (t) => ASSET_TYPE_LABELS[t as keyof typeof ASSET_TYPE_LABELS] || t },
                          { title: '名称', dataIndex: 'name', ellipsis: true },
                          {
                            title: '动作',
                            dataIndex: 'action',
                            width: 90,
                            render: (a) => <Tag color={ACTION_COLORS[a]}>{ACTION_LABELS[a as keyof typeof ACTION_LABELS] || a}</Tag>,
                          },
                          {
                            title: '变化字段',
                            dataIndex: 'changedFields',
                            ellipsis: true,
                            render: (_, r) =>
                              r.action !== 'UPDATE' ? (
                                r.message
                              ) : r.changedFields?.length ? (
                                <Space size={4}>
                                  <Typography.Text ellipsis style={{ maxWidth: 320 }}>
                                    {r.changedFields.join('、')}
                                  </Typography.Text>
                                  <Typography.Link
                                    onClick={() =>
                                      setDiffTarget({
                                        digest: inspect.packageDigest,
                                        assetType: r.assetType,
                                        key: r.id || '',
                                        name: r.name,
                                      })
                                    }
                                  >
                                    查看差异
                                  </Typography.Link>
                                </Space>
                              ) : (
                                <Typography.Text type="secondary">内容无变化</Typography.Text>
                              ),
                          },
                        ]}
                      />
                    ),
                  },
                  {
                    key: 'requirements',
                    label: (
                      <span>
                        依赖检查 {missing.length > 0 && <Tag color="error">缺 {missing.length}</Tag>}
                      </span>
                    ),
                    children: (
                      <Table<TransferRequirement>
                        size="small"
                        rowKey={(r) => `${r.kind}:${r.key}`}
                        dataSource={inspect.report.requirements}
                        pagination={false}
                        locale={{ emptyText: '无外部依赖' }}
                        columns={[
                          { title: '类型', dataIndex: 'kind', width: 110, render: (k) => REQUIREMENT_LABELS[k as keyof typeof REQUIREMENT_LABELS] || k },
                          { title: '名称', dataIndex: 'key' },
                          { title: '说明', dataIndex: 'remark', ellipsis: true },
                          { title: '引用方', dataIndex: 'usedBy', ellipsis: true, render: (u?: string[]) => (u || []).join('、') },
                          {
                            title: '本环境',
                            dataIndex: 'satisfied',
                            width: 90,
                            render: (s) => (s ? <Tag color="success">已具备</Tag> : <Tag color="error">缺失</Tag>),
                          },
                          {
                            title: '操作',
                            width: 110,
                            render: (_, r) =>
                              !r.satisfied && PLACEHOLDER_KINDS.includes(r.kind) ? (
                                <Button
                                  size="small"
                                  loading={creating === `${r.kind}:${r.key}`}
                                  onClick={() => createPlaceholder(r)}
                                >
                                  创建占位
                                </Button>
                              ) : null,
                          },
                        ]}
                      />
                    ),
                  },
                  ...(inspect.modelTables?.length
                    ? [
                        {
                          key: 'models',
                          label: `模型建表（${inspect.modelTables.filter((t) => t.exists === false).length} 缺失）`,
                          children: (
                            <List
                              dataSource={inspect.modelTables}
                              renderItem={(t) => (
                                <List.Item>
                                  <Space direction="vertical" style={{ width: '100%' }}>
                                    <Space>
                                      <strong>{t.modelName}</strong>
                                      <Typography.Text type="secondary">
                                        {t.datasource} / {t.tableName}
                                      </Typography.Text>
                                      {t.exists === true && <Tag color="success">表已存在</Tag>}
                                      {t.exists === false && <Tag color="error">表不存在</Tag>}
                                      {t.exists == null && <Tag>{t.message}</Tag>}
                                    </Space>
                                    {t.ddl && (
                                      <Typography.Paragraph copyable={{ text: t.ddl }} style={{ marginBottom: 0 }}>
                                        <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontSize: 12 }}>{t.ddl}</pre>
                                      </Typography.Paragraph>
                                    )}
                                  </Space>
                                </List.Item>
                              )}
                            />
                          ),
                        },
                      ]
                    : []),
                  {
                    key: 'gates',
                    label: `发布门禁（${inspect.gates.length}）`,
                    children: (
                      <Table<ReleaseGateItem>
                        size="small"
                        rowKey={(r) => `${r.assetType}:${r.assetId}`}
                        dataSource={inspect.gates}
                        pagination={{ pageSize: 20, hideOnSinglePage: true }}
                        columns={[
                          { title: '类型', dataIndex: 'assetType', width: 110, render: (t) => ASSET_TYPE_LABELS[t as keyof typeof ASSET_TYPE_LABELS] || t },
                          { title: '名称', dataIndex: 'assetName', ellipsis: true },
                          {
                            title: '结果',
                            dataIndex: 'passed',
                            width: 90,
                            render: (p) => (p ? <Tag color="success">通过</Tag> : <Tag color="error">未通过</Tag>),
                          },
                          { title: '说明', dataIndex: 'message', ellipsis: true },
                        ]}
                      />
                    ),
                  },
                  {
                    key: 'changelog',
                    label: '发布说明',
                    children: (
                      <pre style={{ whiteSpace: 'pre-wrap', maxHeight: 480, overflow: 'auto', margin: 0 }}>
                        {inspect.changelog}
                      </pre>
                    ),
                  },
                  {
                    key: 'warnings',
                    label: '导出提示',
                    children: (
                      <List
                        size="small"
                        dataSource={inspect.report.warnings}
                        locale={{ emptyText: '无' }}
                        renderItem={(w) => <List.Item>{w}</List.Item>}
                      />
                    ),
                  },
                ]}
              />
            </Card>

            <Card>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Typography.Text>
                  确认导入请输入版本号 <Typography.Text code>{inspect.releaseCode}</Typography.Text>
                  。导入后所有资产会立即在 <Tag color="red">{inspect.targetEnv}</Tag> 发布生效。
                </Typography.Text>
                <Space>
                  <Input
                    style={{ width: 280 }}
                    placeholder={inspect.releaseCode}
                    value={confirmCode}
                    onChange={(e) => setConfirmCode(e.target.value)}
                    disabled={inspect.blocked}
                  />
                  <Button
                    type="primary"
                    danger
                    loading={importing}
                    disabled={
                      inspect.blocked ||
                      confirmCode.trim() !== inspect.releaseCode ||
                      (privileged.length > 0 && !privilegedAck)
                    }
                    onClick={doImport}
                  >
                    导入并发布
                  </Button>
                  <Button onClick={reset} disabled={importing}>
                    取消
                  </Button>
                </Space>
              </Space>
            </Card>
          </>
        )}
      </Space>
      <DiffModal target={diffTarget} onCancel={() => setDiffTarget(undefined)} />
    </PageContainer>
  );
};

export default ReleaseImport;
