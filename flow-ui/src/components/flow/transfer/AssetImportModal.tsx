/**
 * 导入资产包：上传 → 预检 → 确认导入。
 *
 * 导入按 ID upsert，且只写草稿列——目标环境的线上快照与发布状态不受影响，
 * 导入完成后需要再走一次发布（含发布门禁）才会对外生效。
 */
import React, { useMemo, useState } from 'react';
import {
  FileZipOutlined,
  InboxOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Input,
  Modal,
  Space,
  Steps,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd';
import {
  ACTION_LABELS,
  ASSET_TYPE_LABELS,
  REQUIREMENT_LABELS,
  importAssetBundle,
  preflightAssetBundle,
  type AssetBundle,
  type AssetTransferType,
  type TransferAction,
  type TransferItem,
  type TransferReport,
} from '@/services/flow/assetTransfer';

const { Text } = Typography;

interface Props {
  open: boolean;
  onCancel: () => void;
  /** 导入成功后刷新列表 */
  onSuccess: () => void;
}

const ACTION_COLORS: Record<TransferAction, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  SKIP: 'default',
  CONFLICT: 'red',
};

/** 统计卡片配色：与表格里的处理标签保持同一套语义色 */
const ACTION_ACCENTS: Record<TransferAction, string> = {
  CREATE: '#52c41a',
  UPDATE: '#1677ff',
  SKIP: '#8c8c8c',
  CONFLICT: '#ff4d4f',
};

const ACTION_HINTS: Record<TransferAction, string> = {
  CREATE: '目标环境不存在，将新建',
  UPDATE: '同 ID 已存在，将覆盖草稿',
  SKIP: '保留目标环境现状，不做改动',
  CONFLICT: '存在占用或校验不通过，需先处理',
};

const MAX_BUNDLE_BYTES = 20 * 1024 * 1024;

const BUNDLE_SECTIONS: Array<{ key: keyof AssetBundle; label: string }> = [
  { key: 'apis', label: '接口' },
  { key: 'services', label: '内部服务' },
  { key: 'tasks', label: '定时任务' },
  { key: 'directories', label: '目录' },
  { key: 'regressionSuites', label: '回归套件' },
];

/** 可点击的统计卡片，点选后筛选下方明细 */
const StatCard: React.FC<{
  action: TransferAction;
  value: number;
  active: boolean;
  onClick: () => void;
}> = ({ action, value, active, onClick }) => {
  const accent = ACTION_ACCENTS[action];
  const highlight = value > 0;
  return (
    <Tooltip title={ACTION_HINTS[action]}>
      <div
        onClick={onClick}
        style={{
          flex: 1,
          cursor: 'pointer',
          padding: '10px 12px',
          borderRadius: 8,
          border: `1px solid ${active ? accent : '#f0f0f0'}`,
          background: active ? `${accent}0f` : '#fafafa',
          transition: 'all .2s',
        }}
      >
        <div
          style={{
            fontSize: 22,
            lineHeight: '28px',
            fontWeight: 600,
            color: highlight ? accent : 'rgba(0,0,0,0.25)',
          }}
        >
          {value}
        </div>
        <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>{ACTION_LABELS[action]}</div>
      </div>
    </Tooltip>
  );
};

const AssetImportModal: React.FC<Props> = ({ open, onCancel, onSuccess }) => {
  const [bundle, setBundle] = useState<AssetBundle | null>(null);
  const [fileName, setFileName] = useState<string>('');
  const [overwrite, setOverwrite] = useState(true);
  const [report, setReport] = useState<TransferReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionFilter, setActionFilter] = useState<TransferAction | null>(null);
  const [keyword, setKeyword] = useState('');

  const reset = () => {
    setBundle(null);
    setFileName('');
    setReport(null);
    setOverwrite(true);
    setLoading(false);
    setActionFilter(null);
    setKeyword('');
  };

  const close = () => {
    reset();
    onCancel();
  };

  /** 只展示有内容的资产类型，避免一排 0 干扰 */
  const sections = useMemo(() => {
    if (!bundle) return [];
    return BUNDLE_SECTIONS.map(({ key, label }) => ({
      label,
      count: (bundle[key] as any[] | undefined)?.length || 0,
    })).filter((s) => s.count > 0);
  }, [bundle]);

  const missing = (report?.requirements || []).filter((r) => r.satisfied === false);
  const imported = !!report && !report.dryRun;

  const visibleItems = useMemo(() => {
    const items = report?.items || [];
    const kw = keyword.trim().toLowerCase();
    return items.filter((item) => {
      if (actionFilter && item.action !== actionFilter) return false;
      if (!kw) return true;
      return `${item.name || ''} ${item.id || ''}`.toLowerCase().includes(kw);
    });
  }, [report, actionFilter, keyword]);

  const runPreflight = async (target: AssetBundle, overwriteExisting: boolean) => {
    setLoading(true);
    try {
      const result = await preflightAssetBundle(target, overwriteExisting);
      setReport(result);
      setActionFilter(result?.blocked ? 'CONFLICT' : null);
      if (result?.blocked) {
        message.warning('存在冲突项，需处理后才能导入');
      }
    } catch (error: any) {
      setReport(null);
      if (!error?.message) message.error('预检失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  const readFile = async (file: File) => {
    if (file.size > MAX_BUNDLE_BYTES) {
      message.error('资产包超过 20MB，请拆分后再导入');
      return;
    }
    try {
      const text = await file.text();
      const parsed = JSON.parse(text) as AssetBundle;
      if (parsed?.kind && parsed.kind !== 'yu-flow/asset-bundle') {
        message.error('这不是 Yu Flow 资产包');
        return;
      }
      setBundle(parsed);
      setFileName(file.name);
      setReport(null);
      setActionFilter(null);
      setKeyword('');
      await runPreflight(parsed, overwrite);
    } catch {
      message.error('文件解析失败，请确认是导出的 .json 资产包');
    }
  };

  const runImport = async () => {
    if (!bundle) return;
    setLoading(true);
    try {
      const result = await importAssetBundle(bundle, overwrite);
      setReport(result);
      setActionFilter(null);
      message.success(
        `导入完成：新增 ${result?.createCount || 0}，更新 ${result?.updateCount || 0}，跳过 ${result?.skipCount || 0}`,
      );
      onSuccess();
    } catch (error: any) {
      if (!error?.message) message.error('导入失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    {
      title: '类型',
      dataIndex: 'assetType',
      width: 96,
      render: (v: AssetTransferType) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {ASSET_TYPE_LABELS[v] || v}
        </Text>
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 220,
      render: (_: string, record: TransferItem) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.name || '-'}</div>
          {record.id ? (
            <div style={{ fontSize: 11, color: 'rgba(0,0,0,0.35)', fontFamily: 'monospace' }}>
              {record.id}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      title: '处理',
      dataIndex: 'action',
      width: 76,
      render: (v: TransferAction) => <Tag color={ACTION_COLORS[v]}>{ACTION_LABELS[v] || v}</Tag>,
    },
    {
      title: '说明',
      dataIndex: 'message',
      render: (v?: string) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {v || '-'}
        </Text>
      ),
    },
  ];

  return (
    <Modal
      title="导入资产包"
      width={900}
      open={open}
      onCancel={close}
      confirmLoading={loading}
      okText={imported ? '完成' : '确认导入'}
      okButtonProps={{ disabled: !report || report.blocked }}
      onOk={() => {
        if (!report) {
          return;
        }
        if (imported) {
          close();
          return;
        }
        Modal.confirm({
          title: '确认导入到当前环境？',
          content:
            '导入只覆盖草稿，不会改动线上已发布的快照；资产需要重新发布后才对外生效。',
          okText: '确认导入',
          cancelText: '再看看',
          onOk: runImport,
        });
      }}
      cancelText="关闭"
    >
      <Steps
        size="small"
        style={{ margin: '4px 0 16px' }}
        current={!bundle ? 0 : imported ? 2 : 1}
        items={[
          { title: '选择资产包' },
          { title: '预检差异' },
          { title: '写入草稿' },
        ]}
      />

      {!bundle ? (
        <Upload.Dragger
          accept=".json"
          maxCount={1}
          showUploadList={false}
          beforeUpload={(file) => {
            readFile(file as unknown as File);
            return false;
          }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽资产包到此处</p>
          <p className="ant-upload-hint">
            仅支持导出生成的 .json 文件，单个不超过 20MB；上传后会自动预检，不会立即写入
          </p>
        </Upload.Dragger>
      ) : (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '12px 14px',
              border: '1px solid #f0f0f0',
              borderRadius: 8,
              background: '#fafafa',
            }}
          >
            <FileZipOutlined style={{ fontSize: 24, color: '#1677ff' }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  fontWeight: 500,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={fileName}
              >
                {fileName}
              </div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                导出于 {bundle.exportedAt || '未知时间'} · 来源环境 {bundle.sourceEnv || '未标注'}
                {bundle.exportedBy ? ` · ${bundle.exportedBy}` : ''}
              </Text>
            </div>
            <Space size={4} wrap>
              {sections.length ? (
                sections.map((s) => (
                  <Tag key={s.label} style={{ margin: 0 }}>
                    {s.label} {s.count}
                  </Tag>
                ))
              ) : (
                <Tag style={{ margin: 0 }}>空资产包</Tag>
              )}
            </Space>
            <Button size="small" icon={<SwapOutlined />} onClick={reset} disabled={loading}>
              换文件
            </Button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Switch
              size="small"
              checked={overwrite}
              disabled={loading || imported}
              onChange={(v) => {
                setOverwrite(v);
                if (bundle) runPreflight(bundle, v);
              }}
            />
            <div style={{ flex: 1 }}>
              <span>覆盖目标环境已存在的同 ID 资产</span>
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                仅覆盖草稿，线上快照与发布状态不变；关闭后同 ID 资产一律跳过
              </Text>
            </div>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              loading={loading}
              disabled={imported}
              onClick={() => bundle && runPreflight(bundle, overwrite)}
            >
              重新预检
            </Button>
          </div>

          {report && (
            <>
              {imported && (
                <Alert
                  type="success"
                  showIcon
                  message="导入完成，资产已写入草稿"
                  description="线上仍是导入前的版本，需要在列表里逐个发布（走发布门禁）后才对外生效。"
                />
              )}

              {report.blocked && (
                <Alert
                  type="error"
                  showIcon
                  message="存在冲突项，整包禁止导入"
                  description="请在源环境调整后重新导出，或先处理目标环境的占用项。"
                />
              )}

              {missing.length > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  message="目标环境缺少以下外部依赖，导入后资产可能无法正常运行"
                  description={
                    <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
                      {missing.map((r) => (
                        <li key={`${r.kind}:${r.key}`}>
                          {REQUIREMENT_LABELS[r.kind] || r.kind}：<code>{r.key}</code>
                          {r.usedBy?.length ? `（被 ${r.usedBy.join('、')} 引用）` : ''}
                        </li>
                      ))}
                    </ul>
                  }
                />
              )}

              {report.warnings?.length > 0 && (
                <Alert
                  type="info"
                  showIcon
                  message="导出时的提示"
                  description={
                    <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
                      {report.warnings.map((w, idx) => (
                        <li key={idx}>{w}</li>
                      ))}
                    </ul>
                  }
                />
              )}

              <div style={{ display: 'flex', gap: 8 }}>
                {(['CREATE', 'UPDATE', 'SKIP', 'CONFLICT'] as TransferAction[]).map((action) => {
                  const value =
                    action === 'CREATE'
                      ? report.createCount
                      : action === 'UPDATE'
                        ? report.updateCount
                        : action === 'SKIP'
                          ? report.skipCount
                          : report.conflictCount;
                  return (
                    <StatCard
                      key={action}
                      action={action}
                      value={value}
                      active={actionFilter === action}
                      onClick={() => setActionFilter(actionFilter === action ? null : action)}
                    />
                  );
                })}
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Text type="secondary" style={{ fontSize: 12, flex: 1 }}>
                  {actionFilter
                    ? `仅看「${ACTION_LABELS[actionFilter]}」${visibleItems.length} 条，点卡片可取消筛选`
                    : `共 ${report.items?.length || 0} 条明细，点上方卡片可按处理方式筛选`}
                </Text>
                <Input.Search
                  size="small"
                  allowClear
                  style={{ width: 220 }}
                  placeholder="按名称 / ID 搜索"
                  onChange={(e) => setKeyword(e.target.value)}
                />
              </div>

              <Table<TransferItem>
                size="small"
                rowKey={(r) => `${r.assetType}:${r.id}`}
                columns={columns as any}
                dataSource={visibleItems}
                pagination={false}
                scroll={{ y: 280 }}
                locale={{ emptyText: '当前筛选下没有条目' }}
              />
            </>
          )}
        </Space>
      )}
    </Modal>
  );
};

export default AssetImportModal;
