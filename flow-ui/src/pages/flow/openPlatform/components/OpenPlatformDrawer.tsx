import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, DatePicker, Drawer, Form, Input, InputNumber, Modal, Select, Space, Switch,
  Table, Tabs, Tag, Tree, Typography, message,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import dayjs from 'dayjs';
import { request } from '@umijs/max';
import {
  createCredential,
  disableCredential,
  getOpenEntryMeta,
  getOpenPlatform,
  getPlatformGuide,
  getPlatformMarkdown,
  getPlatformOpenApi,
  getPlatformOpenApiYaml,
  getPlatformPostman,
  listCredentials,
  listGrantDetails,
  listGrants,
  pageOpenCallLogs,
  purgeInvalidGrants,
  replaceGrants,
  rotateCredential,
  updateOpenPlatform,
  type OpenCallLogItem,
  type OpenCredential,
  type OpenGrantItem,
  type OpenPlatform,
} from '@/services/flow/openPlatformService';
import { queryAutoApiConfigList } from '@/services/flow/flowController';
import AssetRuntimePanel from '@/components/flow/AssetRuntimePanel';

const { Text, Paragraph } = Typography;

interface Props {
  platformId: string;
  open: boolean;
  onClose: () => void;
  defaultTab?: string;
}

type ApiRow = { id: string; name?: string; method?: string; url?: string; directoryId?: string };

function downloadText(filename: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
}

function collectApiIdsUnderDir(dirId: string, dirChildren: Record<string, string[]>, apisByDir: Record<string, ApiRow[]>): string[] {
  const out: string[] = [];
  const walk = (id: string) => {
    (apisByDir[id] || []).forEach((a) => out.push(a.id));
    (dirChildren[id] || []).forEach(walk);
  };
  walk(dirId);
  return out;
}

const OpenPlatformDrawer: React.FC<Props> = ({ platformId, open, onClose, defaultTab }) => {
  const [loading, setLoading] = useState(false);
  const [platform, setPlatform] = useState<OpenPlatform | null>(null);
  const [creds, setCreds] = useState<OpenCredential[]>([]);
  const [grantIds, setGrantIds] = useState<string[]>([]);
  const [draftGrantIds, setDraftGrantIds] = useState<string[]>([]);
  /** apiId → 允许的 HTTP 方法；空数组=跟随接口 method */
  const [draftAllowMethods, setDraftAllowMethods] = useState<Record<string, string[]>>({});
  const [savedAllowMethods, setSavedAllowMethods] = useState<Record<string, string[]>>({});
  const [grantDetails, setGrantDetails] = useState<OpenGrantItem[]>([]);
  const [apis, setApis] = useState<ApiRow[]>([]);
  const [dirTree, setDirTree] = useState<any[]>([]);
  const [entryPrefix, setEntryPrefix] = useState('/flow-api/open');
  const [form] = Form.useForm();
  const [secretModal, setSecretModal] = useState<{ appKey: string; appSecret: string } | null>(null);
  const [activeTab, setActiveTab] = useState(defaultTab || 'basic');
  const [callLogs, setCallLogs] = useState<OpenCallLogItem[]>([]);
  const [callLogTotal, setCallLogTotal] = useState(0);
  const [callLogPage, setCallLogPage] = useState(1);
  const [savingGrants, setSavingGrants] = useState(false);
  const [docPreview, setDocPreview] = useState<{ title: string; content: string } | null>(null);
  const [docPreviewLoading, setDocPreviewLoading] = useState(false);

  const invalidGrantCount = useMemo(
    () => grantDetails.filter((g) => !g.valid).length,
    [grantDetails],
  );

  const grantsDirty = useMemo(() => {
    const a = [...grantIds].sort().join(',');
    const b = [...draftGrantIds].sort().join(',');
    if (a !== b) return true;
    const keys = new Set([...Object.keys(savedAllowMethods), ...Object.keys(draftAllowMethods)]);
    for (const k of keys) {
      const s = (savedAllowMethods[k] || []).slice().sort().join(',');
      const d = (draftAllowMethods[k] || []).slice().sort().join(',');
      if (s !== d) return true;
    }
    return false;
  }, [grantIds, draftGrantIds, savedAllowMethods, draftAllowMethods]);

  const { treeData, dirChildren, apisByDir } = useMemo(() => {
    const dirChildren: Record<string, string[]> = {};
    const apisByDir: Record<string, ApiRow[]> = {};
    apis.forEach((a) => {
      const d = a.directoryId || '__root__';
      if (!apisByDir[d]) apisByDir[d] = [];
      apisByDir[d].push(a);
    });
    const walk = (nodes: any[]): DataNode[] =>
      (nodes || []).map((n) => {
        const id = n.id;
        const kids = n.children || [];
        dirChildren[id] = kids.map((c: any) => c.id);
        const childNodes = walk(kids);
        const apiNodes: DataNode[] = (apisByDir[id] || []).map((a) => ({
          key: `api:${a.id}`,
          title: `${a.name || a.id}  ${a.method || ''} ${a.url || ''}`,
          isLeaf: true,
          disableCheckbox: false,
        }));
        return {
          key: `dir:${id}`,
          title: n.name || id,
          children: [...childNodes, ...apiNodes],
        };
      });
    const roots = walk(dirTree);
    const knownDirs = new Set(Object.keys(dirChildren));
    dirTree.forEach(function collect(n: any) {
      knownDirs.add(n.id);
      (n.children || []).forEach(collect);
    });
    const uncategorized = apis.filter((a) => !a.directoryId || !knownDirs.has(a.directoryId));
    if (uncategorized.length) {
      roots.push({
        key: 'dir:__uncategorized__',
        title: '未归类',
        children: uncategorized.map((a) => ({
          key: `api:${a.id}`,
          title: `${a.name || a.id}  ${a.method || ''} ${a.url || ''}`,
          isLeaf: true,
        })),
      });
    }
    return { treeData: roots, dirChildren, apisByDir };
  }, [dirTree, apis]);

  const checkedKeys = useMemo(() => draftGrantIds.map((id) => `api:${id}`), [draftGrantIds]);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [p, c, g, details, meta, apisRes, dirs] = await Promise.all([
        getOpenPlatform(platformId),
        listCredentials(platformId),
        listGrants(platformId),
        listGrantDetails(platformId).catch(() => [] as OpenGrantItem[]),
        getOpenEntryMeta().catch(() => ({ entryPrefix: '/flow-api/open' })),
        queryAutoApiConfigList({ page: 0, size: 500, publishStatus: 1 } as any),
        request<any[]>('/flow-api/directories/tree', { method: 'GET', params: { bizType: 'api' } }),
      ]);
      setPlatform(p);
      setCreds(c || []);
      const ids = g || [];
      setGrantIds(ids);
      setDraftGrantIds(ids);
      setGrantDetails(details || []);
      const methods: Record<string, string[]> = {};
      (details || []).forEach((d) => {
        if (d.allowMethods) {
          methods[d.apiId] = d.allowMethods
            .split(/[,;\s]+/)
            .map((m) => m.trim().toUpperCase())
            .filter(Boolean);
        }
      });
      setSavedAllowMethods(methods);
      setDraftAllowMethods(methods);
      setEntryPrefix(meta?.entryPrefix || '/flow-api/open');
      let ipTags: string[] = [];
      try {
        if (p?.ipAllowlist) ipTags = JSON.parse(p.ipAllowlist);
      } catch {
        ipTags = [];
      }
      form.setFieldsValue({
        ...p,
        expireAt: p?.expireAt ? dayjs(p.expireAt) : undefined,
        status: p?.status !== 0,
        openCallLogEnabled: p?.openCallLogEnabled !== 0,
        ipTags,
        rateLimitQps: p?.rateLimitQps ?? undefined,
      });
      const items = (apisRes as any)?.items || (apisRes as any)?.data?.items || [];
      setApis(
        (items as any[])
          .filter((a) => a?.id)
          .map((a) => ({
            id: a.id,
            name: a.name,
            method: a.method,
            url: a.url,
            directoryId: a.directoryId,
          })),
      );
      const dirData = (dirs as any)?.data !== undefined ? (dirs as any).data : dirs;
      setDirTree(Array.isArray(dirData) ? dirData : []);
    } catch (e: any) {
      message.error(e?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [platformId, form]);

  const loadCallLogs = useCallback(
    async (page = 1) => {
      try {
        const data = await pageOpenCallLogs(platformId, { page: page - 1, size: 20 });
        setCallLogs(data?.items || []);
        setCallLogTotal(data?.total || 0);
        setCallLogPage(page);
      } catch (e: any) {
        message.error(e?.message || '加载调用日志失败');
      }
    },
    [platformId],
  );

  useEffect(() => {
    if (open) {
      setActiveTab(defaultTab || 'basic');
      reload();
    }
  }, [open, reload, defaultTab]);

  useEffect(() => {
    if (open && activeTab === 'callLogs') loadCallLogs(1);
  }, [open, activeTab, loadCallLogs]);

  const statusTag = (s: number) => {
    if (s === 1) return <Tag color="success">启用</Tag>;
    if (s === 2) return <Tag color="warning">已轮换</Tag>;
    return <Tag>停用</Tag>;
  };

  const onTreeCheck = (keys: any) => {
    const list = (Array.isArray(keys) ? keys : keys.checked) as string[];
    const apiIds = list.filter((k) => k.startsWith('api:')).map((k) => k.slice(4));
    // 目录勾选：并入该目录下全部接口
    list
      .filter((k) => k.startsWith('dir:'))
      .forEach((k) => {
        const dirId = k.slice(4);
        if (dirId === '__uncategorized__') return;
        collectApiIdsUnderDir(dirId, dirChildren, apisByDir).forEach((id) => {
          if (!apiIds.includes(id)) apiIds.push(id);
        });
      });
    setDraftGrantIds(Array.from(new Set(apiIds)));
  };

  const saveGrants = async () => {
    setSavingGrants(true);
    try {
      const allowMethodsByApiId: Record<string, string> = {};
      draftGrantIds.forEach((id) => {
        const ms = draftAllowMethods[id];
        if (ms?.length) {
          allowMethodsByApiId[id] = ms.join(',');
        }
      });
      await replaceGrants(platformId, draftGrantIds, allowMethodsByApiId);
      message.success('授权已保存');
      reload();
    } catch (e: any) {
      message.error(e?.message || '保存授权失败');
    } finally {
      setSavingGrants(false);
    }
  };

  const openDocPreview = async (kind: 'guide' | 'markdown') => {
    setDocPreviewLoading(true);
    try {
      const content =
        kind === 'guide' ? await getPlatformGuide(platformId) : await getPlatformMarkdown(platformId);
      setDocPreview({
        title: kind === 'guide' ? '对接说明预览' : 'Markdown 文档预览',
        content: content || '',
      });
    } catch (e: any) {
      message.error(e?.message || '加载文档失败');
    } finally {
      setDocPreviewLoading(false);
    }
  };

  return (
    <Drawer
      title={platform ? `开放平台 · ${platform.name}` : '开放平台'}
      width="90%"
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={`第三方调用入口：${entryPrefix}/{真实发布path}`}
        description="默认仅 HMAC；演示可配置 YU_FLOW_OPEN_ALLOW_PLAIN_SECRET=true。"
      />
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'basic',
            label: '基本信息',
            children: (
              <Form
                form={form}
                layout="vertical"
                style={{ maxWidth: 560 }}
                onFinish={async (values) => {
                  try {
                    const ipTags: string[] = values.ipTags || [];
                    await updateOpenPlatform(platformId, {
                      name: values.name,
                      code: values.code,
                      contact: values.contact,
                      remark: values.remark,
                      status: values.status ? 1 : 0,
                      openCallLogEnabled: values.openCallLogEnabled ? 1 : 0,
                      rateLimitQps: values.rateLimitQps ?? null,
                      ipAllowlist: ipTags.length ? JSON.stringify(ipTags) : '',
                      expireAt: values.expireAt
                        ? dayjs(values.expireAt).format('YYYY-MM-DD HH:mm:ss')
                        : undefined,
                    });
                    message.success('已保存');
                    reload();
                  } catch (e: any) {
                    message.error(e?.message || '保存失败');
                  }
                }}
              >
                <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
                <Form.Item name="code" label="编码" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
                <Form.Item name="status" label="启用" valuePropName="checked">
                  <Switch checkedChildren="启用" unCheckedChildren="停用" />
                </Form.Item>
                <Form.Item name="expireAt" label="平台到期时间">
                  <DatePicker showTime style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="contact" label="联系人">
                  <Input />
                </Form.Item>
                <Form.Item
                  name="ipTags"
                  label="IP 白名单"
                  extra="输入 IP 或 CIDR 后回车；空=不限"
                >
                  <Select mode="tags" tokenSeparators={[',']} placeholder="如 1.2.3.4 或 10.0.0.0/8" />
                </Form.Item>
                <Form.Item
                  name="rateLimitQps"
                  label="限流 QPS"
                  extra="空或不填表示不限；按平台秒级固定窗口"
                >
                  <InputNumber min={1} max={100000} style={{ width: '100%' }} placeholder="不限" />
                </Form.Item>
                <Form.Item name="openCallLogEnabled" label="入站摘要日志" valuePropName="checked">
                  <Switch checkedChildren="开" unCheckedChildren="关" />
                </Form.Item>
                <Form.Item name="remark" label="备注">
                  <Input.TextArea rows={2} />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={loading}>
                  保存
                </Button>
              </Form>
            ),
          },
          {
            key: 'creds',
            label: '凭证',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button
                    type="primary"
                    onClick={async () => {
                      try {
                        const c = await createCredential(platformId);
                        setSecretModal({ appKey: c.appKey, appSecret: c.appSecret || '' });
                        reload();
                      } catch (e: any) {
                        message.error(e?.message || '颁发失败');
                      }
                    }}
                  >
                    颁发新凭证
                  </Button>
                </Space>
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={creds}
                  pagination={false}
                  columns={[
                    { title: 'AppKey', dataIndex: 'appKey', ellipsis: true },
                    {
                      title: 'Secret 提示',
                      dataIndex: 'secretHint',
                      width: 100,
                      render: (v) => `****${v || ''}`,
                    },
                    { title: '状态', dataIndex: 'status', width: 90, render: statusTag },
                    {
                      title: '过期/宽限至',
                      dataIndex: 'expireAt',
                      width: 170,
                      render: (v) => v || '—',
                    },
                    { title: '创建时间', dataIndex: 'createTime', width: 170 },
                    {
                      title: '操作',
                      width: 160,
                      render: (_, r) => (
                        <Space>
                          {r.status === 1 && (
                            <a
                              onClick={async () => {
                                try {
                                  const c = await rotateCredential(platformId, r.id);
                                  setSecretModal({
                                    appKey: c.appKey,
                                    appSecret: c.appSecret || '',
                                  });
                                  reload();
                                } catch (e: any) {
                                  message.error(e?.message || '轮换失败');
                                }
                              }}
                            >
                              轮换
                            </a>
                          )}
                          {r.status === 1 && (
                            <a
                              style={{ color: '#ff4d4f' }}
                              onClick={async () => {
                                await disableCredential(platformId, r.id);
                                message.success('已停用');
                                reload();
                              }}
                            >
                              停用
                            </a>
                          )}
                        </Space>
                      ),
                    },
                  ]}
                />
              </>
            ),
          },
          {
            key: 'grants',
            label: '接口授权',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                {invalidGrantCount > 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    message={`${invalidGrantCount} 个授权已失效（接口已下线或不存在）`}
                    action={
                      <Button
                        size="small"
                        onClick={async () => {
                          try {
                            const r = await purgeInvalidGrants(platformId);
                            message.success(`已清理 ${r?.removed ?? 0} 条失效授权`);
                            reload();
                          } catch (e: any) {
                            message.error(e?.message || '清理失败');
                          }
                        }}
                      >
                        清理失效授权
                      </Button>
                    }
                  />
                )}
                <Space>
                  <Button type="primary" disabled={!grantsDirty} loading={savingGrants} onClick={saveGrants}>
                    保存授权
                  </Button>
                  <Button
                    disabled={!grantsDirty}
                    onClick={() => {
                      setDraftGrantIds(grantIds);
                      setDraftAllowMethods(savedAllowMethods);
                    }}
                  >
                    重置
                  </Button>
                  <Text type="secondary">
                    已选 {draftGrantIds.length} 个接口；允许方法留空=跟随接口发布 method
                  </Text>
                </Space>
                <Tree
                  checkable
                  defaultExpandAll
                  checkedKeys={checkedKeys}
                  onCheck={onTreeCheck}
                  treeData={treeData}
                  height={320}
                  style={{ border: '1px solid #f0f0f0', padding: 8, borderRadius: 6 }}
                />
                {draftGrantIds.length > 0 && (
                  <Table
                    size="small"
                    rowKey="id"
                    pagination={false}
                    dataSource={draftGrantIds.map((id) => {
                      const api = apis.find((a) => a.id === id);
                      const detail = grantDetails.find((g) => g.apiId === id);
                      return {
                        id,
                        name: api?.name || detail?.apiName || id,
                        method: api?.method || detail?.method || '',
                        url: api?.url || detail?.url || '',
                      };
                    })}
                    columns={[
                      { title: '接口', dataIndex: 'name', ellipsis: true },
                      { title: '发布方法', dataIndex: 'method', width: 90 },
                      { title: '路径', dataIndex: 'url', ellipsis: true },
                      {
                        title: '允许方法',
                        width: 220,
                        render: (_, r) => (
                          <Select
                            mode="multiple"
                            allowClear
                            placeholder="跟随接口"
                            style={{ width: '100%' }}
                            options={['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map((m) => ({
                              label: m,
                              value: m,
                            }))}
                            value={draftAllowMethods[r.id] || []}
                            onChange={(v) =>
                              setDraftAllowMethods((prev) => ({ ...prev, [r.id]: v as string[] }))
                            }
                          />
                        ),
                      },
                    ]}
                  />
                )}
              </Space>
            ),
          },
          {
            key: 'docs',
            label: '文档导出',
            children: (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Space wrap>
                  <Button loading={docPreviewLoading} onClick={() => openDocPreview('guide')}>
                    预览对接说明
                  </Button>
                  <Button loading={docPreviewLoading} onClick={() => openDocPreview('markdown')}>
                    预览 Markdown
                  </Button>
                </Space>
                <Button
                  onClick={async () => {
                    const doc = await getPlatformOpenApi(platformId);
                    downloadText(
                      `openapi-${platform?.code || platformId}.json`,
                      JSON.stringify(doc, null, 2),
                      'application/json',
                    );
                  }}
                >
                  下载 OpenAPI JSON
                </Button>
                <Button
                  onClick={async () => {
                    const yaml = await getPlatformOpenApiYaml(platformId);
                    downloadText(
                      `openapi-${platform?.code || platformId}.yaml`,
                      yaml || '',
                      'application/x-yaml',
                    );
                  }}
                >
                  下载 OpenAPI YAML
                </Button>
                <Button
                  onClick={async () => {
                    const col = await getPlatformPostman(platformId);
                    downloadText(
                      `postman-${platform?.code || platformId}.json`,
                      JSON.stringify(col, null, 2),
                      'application/json',
                    );
                  }}
                >
                  下载 Postman Collection
                </Button>
                <Button
                  onClick={async () => {
                    const md = await getPlatformMarkdown(platformId);
                    downloadText(`openapi-${platform?.code || platformId}.md`, md || '', 'text/markdown');
                  }}
                >
                  下载 Markdown（含鉴权说明与 curl）
                </Button>
                <Button
                  onClick={async () => {
                    const guide = await getPlatformGuide(platformId);
                    downloadText(`guide-${platform?.code || platformId}.md`, guide || '', 'text/markdown');
                  }}
                >
                  下载对接说明（仅鉴权，不含接口清单）
                </Button>
                <Button
                  onClick={async () => {
                    try {
                      const base = `${window.location.origin}${entryPrefix}`;
                      await navigator.clipboard.writeText(base);
                      message.success('已复制完整 Base URL');
                    } catch {
                      message.info(entryPrefix);
                    }
                  }}
                >
                  复制完整 Base URL
                </Button>
              </Space>
            ),
          },
          {
            key: 'runtime',
            label: '运行',
            children: <AssetRuntimePanel assetType="PLATFORM" assetId={platformId} />,
          },
          {
            key: 'callLogs',
            label: '调用日志',
            children: (
              <Table
                rowKey="id"
                size="small"
                dataSource={callLogs}
                columns={[
                  { title: '时间', dataIndex: 'createTime', width: 170 },
                  { title: 'AppKey', dataIndex: 'appKey', ellipsis: true, width: 160 },
                  { title: '方法', dataIndex: 'method', width: 80 },
                  { title: '路径', dataIndex: 'path', ellipsis: true },
                  { title: '状态', dataIndex: 'status', width: 80 },
                  {
                    title: '耗时',
                    dataIndex: 'costMs',
                    width: 80,
                    render: (v) => (v != null ? `${v}ms` : '—'),
                  },
                  { title: '错误码', dataIndex: 'errorCode', width: 160, ellipsis: true },
                ]}
                pagination={{
                  current: callLogPage,
                  total: callLogTotal,
                  pageSize: 20,
                  onChange: (p) => loadCallLogs(p),
                }}
              />
            ),
          },
        ]}
      />

      <Modal
        title="请妥善保存密钥（仅显示一次）"
        open={!!secretModal}
        onCancel={() => setSecretModal(null)}
        onOk={() => setSecretModal(null)}
        okText="我已保存"
      >
        {secretModal && (
          <>
            <Alert type="warning" showIcon message="关闭后无法再次查看完整 Secret" style={{ marginBottom: 12 }} />
            <Paragraph copyable>{`AppKey: ${secretModal.appKey}`}</Paragraph>
            <Paragraph copyable>{`AppSecret: ${secretModal.appSecret}`}</Paragraph>
          </>
        )}
      </Modal>

      <Modal
        title={docPreview?.title || '文档预览'}
        open={!!docPreview}
        onCancel={() => setDocPreview(null)}
        onOk={() => setDocPreview(null)}
        width={860}
        okText="关闭"
        cancelButtonProps={{ style: { display: 'none' } }}
      >
        <pre
          style={{
            maxHeight: '60vh',
            overflow: 'auto',
            margin: 0,
            padding: 12,
            background: '#fafafa',
            border: '1px solid #f0f0f0',
            borderRadius: 6,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontSize: 13,
            lineHeight: 1.55,
          }}
        >
          {docPreview?.content}
        </pre>
      </Modal>
    </Drawer>
  );
};

export default OpenPlatformDrawer;
