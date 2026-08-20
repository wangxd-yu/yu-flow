import React, { useEffect, useRef, useState } from 'react';
import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProTable,
  ModalForm,
} from '@ant-design/pro-components';
import { Button, Divider, Dropdown, Drawer, Form, Input, Modal, message, Tag, Popconfirm, Space, Tooltip, Table, Spin } from 'antd';
import type { MenuProps } from 'antd';
import { CloudServerOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons';
import { history, useAccess, useLocation } from '@umijs/max';
import {
  queryAutoApiConfigDetail,
  queryAutoApiConfigList,
  deleteAutoApiConfig,
  batchDeleteAutoApiConfig,
  batchMoveAutoApiConfig,
  batchApplyDirPrefix,
  listApiCacheEntries,
  getApiCacheEntryContent,
  clearApiCache,
  clearApiCacheEntry,
  publishApi,
  unpublishApi,
  supportsApiDataView,
  batchHostApiProbe,
  checkHostApiRouteExists,
  FlowController,
  ApiCacheEntry,
  HostApiProbeResult,
} from '@/services/flow/flowController';
import ApiConfigForm from './components/ControllerForm';
import ApiCopyModal from './components/ApiCopyModal';
import AssetExportModal from '@/components/flow/transfer/AssetExportModal';
import AssetImportModal from '@/components/flow/transfer/AssetImportModal';
import ApiDataViewDrawer from './components/ApiDataViewDrawer';
import HostApiImportModal from './components/HostApiImportModal';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import TableEmpty from '@/components/TableEmpty';
import CodeEditor from '@/components/flow/flow-editor/components/CodeEditor';
import { batchAssetHealth, type AssetHealth } from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import {
  fetchStackedDirectoryPathPrefix,
  longestCommonPathPrefix,
  rewritePathWithPrefix,
} from '@/utils/apiPathPrefix';

import '@/styles/fullHeightTable.css';

/** 列表「防护」列：一眼扫 auth / 限流 / 超时 */
function renderIngressSummary(securityConfig?: string) {
  let cfg: any = {};
  try {
    if (securityConfig) cfg = JSON.parse(securityConfig);
  } catch {
    /* ignore */
  }
  const mode = (cfg?.authMode || 'INHERIT') as string;
  const modeLabel =
    mode === 'INHERIT' ? '继承' : mode === 'NONE' ? 'NONE' : mode === 'HOST' ? 'HOST' : mode === 'OPEN' ? 'OPEN' : mode;
  const modeColor =
    mode === 'NONE' ? 'default' : mode === 'HOST' ? 'blue' : mode === 'OPEN' ? 'purple' : 'geekblue';

  let rlText = '限流·继承';
  let rlColor: string = 'default';
  if (cfg?.rateLimitEnabled === true) {
    rlText = `限流·开${cfg.rateLimitQps ? `@${cfg.rateLimitQps}` : ''}`;
    rlColor = 'orange';
  } else if (cfg?.rateLimitEnabled === false) {
    rlText = '限流·关';
  }

  let toText = '超时·继承';
  if (cfg?.timeoutMs !== null && cfg?.timeoutMs !== undefined) {
    toText = cfg.timeoutMs <= 0 ? '超时·不限' : `超时·${cfg.timeoutMs}ms`;
  }

  const callerOn = !!cfg?.callerPolicy?.enabled;
  const ruleNames = Array.isArray(cfg?.callerPolicy?.rules)
    ? cfg.callerPolicy.rules.map((r: { name?: string }) => r?.name).filter(Boolean)
    : [];
  const types = Array.isArray(cfg?.callerPolicy?.userTypes)
    ? cfg.callerPolicy.userTypes.filter(Boolean)
    : [];
  const callerText = callerOn
    ? (ruleNames.length
      ? `调用方·${ruleNames.slice(0, 2).join('/')}`
      : (types.length ? `调用方·${types.slice(0, 2).join('/')}` : '调用方·开'))
    : null;

  return (
    <Space size={4} wrap>
      <Tag color={modeColor} style={{ margin: 0 }}>{modeLabel}</Tag>
      <Tag color={rlColor} style={{ margin: 0 }}>{rlText}</Tag>
      <Tag style={{ margin: 0 }}>{toText}</Tag>
      {callerText ? (
        <Tag color="cyan" style={{ margin: 0 }}>{callerText}</Tag>
      ) : null}
    </Space>
  );
}
/** 单次批量发布的接口数上限，与批量回归保持一致 */
const BATCH_PUBLISH_LIMIT = 20;

/** 超过该字符数关闭自动换行，减轻大 JSON 渲染压力 */
const CACHE_VIEW_WORDWRAP_LIMIT = 200_000;
/** 超过该字符数跳过 pretty-print，避免主线程卡顿 */
const CACHE_VIEW_PRETTY_LIMIT = 500_000;

/** 尽量美化 JSON；失败或过大则原样返回 */
const formatCacheJson = (raw: string): string => {
  if (!raw || raw.length > CACHE_VIEW_PRETTY_LIMIT) return raw;
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
};


/** 解析 cacheConfig，判断是否已开启响应缓存 */
const isCacheEnabled = (cacheConfig?: string): boolean => {
  if (!cacheConfig) return false;
  try {
    const cfg = typeof cacheConfig === 'string' ? JSON.parse(cacheConfig) : cacheConfig;
    return !!cfg?.enabled;
  } catch {
    return false;
  }
};

/**
 * 删除配置
 */
const handleRemove = async (selectedRows: FlowController[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteAutoApiConfig(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功，即将刷新');
    return true;
  } catch (error: any) {
    hide();
    // 引用拦截 / DEMO 等已由 request 拦截器提示
    if (!error?.message) {
      message.error('删除失败，请重试');
    }
    return false;
  }
};

const AutoApiConfigList: React.FC = () => {
  const location = useLocation();
  const access = useAccess();
  const canWrite = !!(access as any)?.canApiWrite;
  const [hostImportOpen, setHostImportOpen] = useState(false);
  const [hostImportDirectoryId, setHostImportDirectoryId] = useState<string | undefined>();
  const actionRef = useRef<ActionType>();
  const [selectedRowsState, setSelectedRows] = useState<FlowController[]>([]);
  const [batchMoveModalVisible, setBatchMoveModalVisible] = useState<boolean>(false);
  const [batchPrefixOpen, setBatchPrefixOpen] = useState(false);
  const [batchOldPrefix, setBatchOldPrefix] = useState('');
  const [batchPrefixSubmitting, setBatchPrefixSubmitting] = useState(false);
  const [batchPrefixPreview, setBatchPrefixPreview] = useState<
    Array<{ id: string; name?: string; from: string; to: string; note?: string }>
  >([]);
  const [batchPrefixPreviewLoading, setBatchPrefixPreviewLoading] = useState(false);
  // 空态区分：是否处于筛选（目录 / 搜索条件）
  const [emptyFiltered, setEmptyFiltered] = useState<boolean>(false);

  const rebuildBatchPrefixPreview = async (rows: FlowController[], oldPrefix: string) => {
    setBatchPrefixPreviewLoading(true);
    try {
      const dirIds = Array.from(
        new Set(rows.map((r) => r.directoryId).filter(Boolean) as string[]),
      );
      const prefixMap: Record<string, string> = {};
      await Promise.all(
        dirIds.map(async (dirId) => {
          prefixMap[dirId] = await fetchStackedDirectoryPathPrefix(dirId);
        }),
      );
      const preview = rows.map((r) => {
        const from = r.url || '';
        const method = (r.method || 'GET').toUpperCase();
        if (r.interceptMode === 'WRAP' || r.serviceType === 'HOST') {
          return { id: r.id, name: r.name, from, to: from, method, note: '包裹模式，将跳过', willUpdate: false };
        }
        if (!r.directoryId) {
          return { id: r.id, name: r.name, from, to: from, method, note: '未归属目录，将跳过', willUpdate: false };
        }
        const newPrefix = prefixMap[r.directoryId] || '';
        if (!newPrefix) {
          return { id: r.id, name: r.name, from, to: from, method, note: '目录无有效前缀，将跳过', willUpdate: false };
        }
        const to = rewritePathWithPrefix(from, oldPrefix, newPrefix);
        if (to == null) {
          return { id: r.id, name: r.name, from, to: from, method, note: '旧前缀不匹配，将跳过', willUpdate: false };
        }
        if (to === from) {
          return { id: r.id, name: r.name, from, to, method, note: '无需变更', willUpdate: false };
        }
        return { id: r.id, name: r.name, from, to, method, note: undefined as string | undefined, willUpdate: true };
      });
      // 勾选内：目标 path 与「保留不动」的 path、或其它将更新目标 冲突时标红
      const occupied = new Map<string, string>();
      preview.forEach((p) => {
        if (!p.willUpdate) {
          occupied.set(`${p.method} ${p.to}`, p.name || p.id);
        }
      });
      preview.forEach((p) => {
        if (!p.willUpdate) return;
        const key = `${p.method} ${p.to}`;
        const other = occupied.get(key);
        if (other) {
          p.note = `冲突：与「${other}」同为 ${key}`;
        } else {
          occupied.set(key, p.name || p.id);
        }
      });
      setBatchPrefixPreview(
        preview.map(({ id, name, from, to, note }) => ({ id, name, from, to, note })),
      );
    } finally {
      setBatchPrefixPreviewLoading(false);
    }
  };

  const openBatchApplyDirPrefix = async () => {
    if (!selectedRowsState.length) {
      message.warning('请先勾选接口');
      return;
    }
    const lcp = longestCommonPathPrefix(
      selectedRowsState
        .filter((r) => r.interceptMode !== 'WRAP' && r.serviceType !== 'HOST')
        .map((r) => r.url),
    );
    setBatchOldPrefix(lcp);
    setBatchPrefixOpen(true);
    await rebuildBatchPrefixPreview(selectedRowsState, lcp);
  };

  // 状态定义
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<FlowController>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [formInitialTab, setFormInitialTab] = useState<
    'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime' | undefined
  >();
  const [dataViewOpen, setDataViewOpen] = useState(false);
  const [dataViewApi, setDataViewApi] = useState<{ id: string; name?: string } | null>(null);
  const [copyOpen, setCopyOpen] = useState(false);
  const [copySource, setCopySource] = useState<FlowController | null>(null);
  const [exportOpen, setExportOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(location.search || '');
    const apiId = params.get('apiId');
    const tab = params.get('tab') || undefined;
    if (!apiId) return;
    let cancelled = false;
    (async () => {
      try {
        const detail = await queryAutoApiConfigDetail(apiId);
        if (cancelled) return;
        setCurrentRow(detail);
        setIsEditMode(true);
        setFormInitialTab(
          tab === 'runtime' || tab === 'implementation' || tab === 'req-schema'
            || tab === 'res-schema' || tab === 'basic-info'
            ? tab
            : 'runtime',
        );
        setFormVisible(true);
      } catch {
        if (!cancelled) message.error('打开接口详情失败');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [location.search]);

  // 响应缓存查看
  const [cacheDrawerVisible, setCacheDrawerVisible] = useState(false);
  const [cacheDrawerApi, setCacheDrawerApi] = useState<FlowController | null>(null);
  const [cacheEntries, setCacheEntries] = useState<ApiCacheEntry[]>([]);
  const [cacheLoading, setCacheLoading] = useState(false);

  // 单条缓存内容预览
  const [cacheContentVisible, setCacheContentVisible] = useState(false);
  const [cacheContentLoading, setCacheContentLoading] = useState(false);
  const [cacheContentKey, setCacheContentKey] = useState('');
  const [cacheContentText, setCacheContentText] = useState('');
  const [cacheContentTruncated, setCacheContentTruncated] = useState(false);
  const [healthMap, setHealthMap] = useState<Record<string, AssetHealth>>({});
  const [probeMap, setProbeMap] = useState<Record<string, HostApiProbeResult>>({});

  /** 新建直进表单；路径前缀由表单 addonBefore 展示，输入框只填相对段 */
  const openCreateForm = (directoryId?: string) => {
    if (!directoryId) {
      message.warning('请先选择目录，或在目录上右键「新建接口」');
      return;
    }
    setCurrentRow({
      directoryId,
      interceptMode: 'REPLACE',
      serviceType: 'FLOW',
      logEnabled: true,
    });
    setIsEditMode(false);
    setFormInitialTab(undefined);
    setFormVisible(true);
  };

  const openHostImport = (directoryId?: string) => {
    if (!directoryId) {
      message.warning('请先选择目录，或在目录上右键「从宿主导入」');
      return;
    }
    setHostImportDirectoryId(directoryId);
    setHostImportOpen(true);
  };

  // 编辑配置
  const handleEdit = async (record: FlowController) => {
    const hide = message.loading('正在获取详情');
    try {
      const detail = await queryAutoApiConfigDetail(record.id);
      hide();
      setCurrentRow(detail);
      setIsEditMode(true);
      setFormInitialTab(undefined);
      setFormVisible(true);
    } catch (error) {
      hide();
      message.error('获取详情失败，请重试');
    }
  };

  /** 分页后补齐运行健康 / 宿主探活；失败降级为空，不影响列表 */
  const loadRowExtras = async (items: FlowController[]) => {
    const ids = items.filter((i) => i.id).map((i) => i.id);
    const wrapIds = items
      .filter((i) => i.id && (i.interceptMode === 'WRAP' || i.serviceType === 'HOST'))
      .map((i) => i.id);

    const healthTask = ids.length
      ? batchAssetHealth(ids.map((id) => ({ assetType: 'API' as const, assetId: id })))
          .then((health) => {
            const map: Record<string, AssetHealth> = {};
            (health || []).forEach((h) => {
              map[h.assetId] = h;
            });
            setHealthMap(map);
          })
          .catch(() => setHealthMap({}))
      : Promise.resolve(setHealthMap({}));

    const probeTask = wrapIds.length
      ? batchHostApiProbe(wrapIds)
          .then((probes: any) => {
            const list = Array.isArray(probes) ? probes : (probes?.data ?? []);
            const pmap: Record<string, HostApiProbeResult> = {};
            list.forEach((p: HostApiProbeResult) => {
              if (p?.apiId) pmap[p.apiId] = p;
            });
            setProbeMap(pmap);
          })
          .catch(() => setProbeMap({}))
      : Promise.resolve(setProbeMap({}));

    await Promise.all([healthTask, probeTask]);
  };

  /** REPLACE 模式发布前提示宿主同名路由会被接管 */
  const confirmHostTakeover = async (record: FlowController) => {
    const isWrap = record.interceptMode === 'WRAP' || record.serviceType === 'HOST';
    if (isWrap || !record.method || !record.url) return true;
    let hostExists = false;
    try {
      const existsRes: any = await checkHostApiRouteExists(record.method, record.url);
      hostExists = existsRes?.exists === true || existsRes?.data?.exists === true;
    } catch {
      hostExists = false;
    }
    if (!hostExists) return true;
    return new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '确认发布「同名替换」？',
        content: `检测到宿主已注册 ${record.method} ${record.url}。发布后将接管该路径，宿主同名接口不再被调用。`,
        okText: '确认替换并发布',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  };

  const handlePublish = async (record: FlowController) => {
    try {
      if (!(await confirmHostTakeover(record))) return;
      const { confirmPublishWithGate } = await import(
        '@/components/flow/release/confirmPublishWithGate'
      );
      const envCode = await confirmPublishWithGate({
        assetType: 'API',
        assetId: record.id,
        assetName: record.name,
      });
      if (!envCode) return;
      await publishApi(record.id, envCode);
      message.success('发布成功');
      actionRef.current?.reload();
    } catch {
      /* 拦截器已提示 */
    }
  };

  /**
   * 批量发布：与单条发布一致地走环境选择，逐个发布并汇总失败原因。
   * 逐条串行是为了避免每次发布触发的路由缓存刷新事件在集群里扎堆广播。
   */
  const handleBatchPublish = async () => {
    const rows = selectedRowsState;
    if (!rows.length) return;
    if (rows.length > BATCH_PUBLISH_LIMIT) {
      message.warning(`单次批量发布最多 ${BATCH_PUBLISH_LIMIT} 个接口，请减少选择`);
      return;
    }
    const { confirmBatchPublish } = await import(
      '@/components/flow/release/confirmBatchPublish'
    );
    const envCode = await confirmBatchPublish({ count: rows.length, assetLabel: '接口' });
    if (!envCode) return;

    const hide = message.loading(`正在发布 ${rows.length} 个接口（${envCode}）…`, 0);
    const failures: Array<{ name: string; reason: string }> = [];
    let ok = 0;
    for (const row of rows) {
      try {
        await publishApi(row.id, envCode);
        ok += 1;
      } catch (e: any) {
        failures.push({ name: row.name || row.id, reason: e?.message || '未知错误' });
      }
    }
    hide();
    if (!failures.length) {
      message.success(`已发布 ${ok} 个接口（${envCode}）`);
    } else {
      Modal.warning({
        title: `批量发布完成（${envCode}）`,
        width: 640,
        content: (
          <div>
            <p>成功 {ok} / 失败 {failures.length}</p>
            <ul style={{ maxHeight: 280, overflow: 'auto', paddingLeft: 18 }}>
              {failures.map((f) => (
                <li key={f.name}>
                  <b>{f.name}</b>：{f.reason}
                </li>
              ))}
            </ul>
          </div>
        ),
      });
    }
    setSelectedRows([]);
    actionRef.current?.clearSelected?.();
    actionRef.current?.reloadAndRest?.();
  };

  const handleUnpublish = async (record: FlowController) => {
    try {
      await unpublishApi(record.id);
      message.success('已下线');
      actionRef.current?.reload();
    } catch {
      /* 拦截器已提示 */
    }
  };

  const loadCacheEntries = async (apiId: string) => {
    setCacheLoading(true);
    try {
      const res: any = await listApiCacheEntries(apiId);
      const list = Array.isArray(res) ? res : (res?.data ?? []);
      setCacheEntries(list);
    } catch {
      message.error('加载缓存列表失败');
      setCacheEntries([]);
    } finally {
      setCacheLoading(false);
    }
  };

  const handleViewCache = async (record: FlowController) => {
    if (!isCacheEnabled(record.cacheConfig)) {
      message.info('该接口未开启响应缓存');
      return;
    }
    setCacheDrawerApi(record);
    setCacheDrawerVisible(true);
    await loadCacheEntries(record.id);
  };

  const handleClearAllCache = async (record: FlowController) => {
    try {
      const res: any = await clearApiCache(record.id);
      const count = typeof res === 'number' ? res : (res?.data ?? 0);
      message.success(`已清除 ${count} 条响应缓存`);
      if (cacheDrawerVisible && cacheDrawerApi?.id === record.id) {
        await loadCacheEntries(record.id);
      }
    } catch {
      message.error('清除缓存失败');
    }
  };

  const handleViewCacheContent = async (entry: ApiCacheEntry) => {
    if (!cacheDrawerApi) return;
    setCacheContentKey(entry.key);
    setCacheContentText('');
    setCacheContentTruncated(false);
    setCacheContentVisible(true);
    setCacheContentLoading(true);
    try {
      const res: any = await getApiCacheEntryContent(cacheDrawerApi.id, entry.key);
      const data = res?.data ?? res;
      const raw = typeof data?.content === 'string' ? data.content : '';
      setCacheContentText(data?.truncated ? raw : formatCacheJson(raw));
      setCacheContentTruncated(!!data?.truncated);
      if (data?.truncated) {
        message.warning('内容过大，已截断展示');
      }
    } catch {
      message.error('加载缓存内容失败');
      setCacheContentVisible(false);
    } finally {
      setCacheContentLoading(false);
    }
  };

  const columns: ProColumns<FlowController>[] = [
    {
      title: '接口名称',
      dataIndex: 'name',
      tip: '接口名称',
      width: 200,
      ellipsis: true,
      formItemProps: {
        rules: [
          {
            required: true,
            message: '名称为必填项',
          },
        ],
      },
      render: (_, record) => (
        <a onClick={() => handleEdit(record)} title={record.name}>
          {record.name}
        </a>
      ),
    },
    {
      title: '所属目录',
      dataIndex: 'directoryName',
      hideInSearch: true,
      width: 120,
      ellipsis: true,
      render: (_, record) =>
        record.directoryName ? <Tag color="blue">{record.directoryName}</Tag> : '-',
    },
    {
      title: '请求方法',
      dataIndex: 'method',
      width: 96,
      valueEnum: {
        GET: { text: 'GET', status: 'GET' },
        POST: { text: 'POST', status: 'POST' },
        PUT: { text: 'PUT', status: 'PUT' },
        DELETE: { text: 'DELETE', status: 'DELETE' },
      },
      render: (_, record) => {
        const colorMap: Record<string, string> = {
          GET: 'blue',
          POST: 'green',
          PUT: 'orange',
          DELETE: 'red',
        };
        return <Tag color={colorMap[record.method || 'GET']}>{record.method}</Tag>;
      },
    },
    {
      title: 'URL',
      dataIndex: 'url',
      valueType: 'text',
      width: 220,
      ellipsis: true,
    },
    {
      title: '发布状态',
      dataIndex: 'publishStatus',
      width: 120,
      ellipsis: true,
      valueEnum: {
        0: { text: '未发布', status: 'Default' },
        1: { text: '已发布', status: 'Success' },
      },
      render: (dom, record) => {
        if (record.publishStatus === 1 && record.hasUnpublishedChanges) {
          return (
            <Tooltip title="存在未发布的草稿修改">
              <span style={{ color: '#faad14', fontWeight: 500, whiteSpace: 'nowrap' }}>
                <span style={{ marginRight: 6 }}>●</span>
                待更新发布
              </span>
            </Tooltip>
          );
        }
        return dom;
      },
    },
    {
      title: '拦截',
      dataIndex: 'interceptMode',
      width: 88,
      hideInSearch: true,
      render: (_, record) => {
        const wrap = record.interceptMode === 'WRAP' || record.serviceType === 'HOST';
        return wrap
          ? <Tag color="cyan" style={{ margin: 0 }}>包裹</Tag>
          : <Tag color="orange" style={{ margin: 0 }}>替换</Tag>;
      },
    },
    {
      title: '实现方式',
      dataIndex: 'serviceType',
      width: 110,
      ellipsis: true,
      valueEnum: {
        FLOW: { text: '逻辑编排', status: 'Processing' },
        DB: { text: '数据库', status: 'Success' },
        JSON: { text: '静态 JSON', status: 'Warning' },
        STRING: { text: '静态文本', status: 'Default' },
        HOST: { text: '宿主转发', status: 'Default' },
      },
    },
    {
      title: (
        <Tooltip title="日志策略模式：继承全局 / 仅错误时记录 / 全量记录 / 完全关闭">
          <span>日志策略</span>
        </Tooltip>
      ),
      dataIndex: 'logMode',
      hideInSearch: true,
      width: 100,
      align: 'center',
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
      title: '标签',
      dataIndex: 'tags',
      valueType: 'text',
      search: false,
      width: 140,
      ellipsis: true,
      render: (_, record) => {
        if (!record.tags) {
          return '-';
        }

        let tagsArray: string[] = [];

        if (Array.isArray(record.tags)) {
          tagsArray = record.tags;
        } else if (typeof record.tags === 'string') {
          tagsArray = (record.tags as string)
            .split(',')
            .map((tag: string) => tag.trim())
            .filter((tag: string) => tag.length > 0);
        }

        if (tagsArray.length === 0) {
          return '-';
        }

        return (
          <span style={{ whiteSpace: 'nowrap' }}>
            {tagsArray.map((tag: string) => (
              <Tag key={tag} color="blue" style={{ marginInlineEnd: 4 }}>
                {tag}
              </Tag>
            ))}
          </span>
        );
      },
    },
    {
      title: '响应缓存',
      dataIndex: 'cacheConfig',
      hideInSearch: true,
      width: 100,
      render: (_, record) => {
        const on = isCacheEnabled(record.cacheConfig);
        return (
          <Tag color={on ? 'processing' : 'default'}>
            {on ? '已开启' : '未开启'}
          </Tag>
        );
      },
    },
    {
      title: '运行健康',
      dataIndex: 'runtimeHealth',
      hideInSearch: true,
      width: 100,
      render: (_, record) => renderHealthTag(healthMap[record.id], probeMap[record.id]),
    },
    {
      title: '防护',
      dataIndex: 'securityConfig',
      hideInSearch: true,
      width: 220,
      ellipsis: true,
      render: (_, record) => (
        <Tooltip title="草稿配置；已发布接口以发布快照为准，改完需发布">
          <span style={{ whiteSpace: 'nowrap' }}>{renderIngressSummary(record.securityConfig)}</span>
        </Tooltip>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      valueType: 'dateTime',
      search: false,
      width: 170,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      search: false,
      width: 170,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        const dataViewable = supportsApiDataView(record);
        const cacheOn = isCacheEnabled(record.cacheConfig);
        const moreItems: MenuProps['items'] = [
          ...(canWrite ? [{ key: 'copy', label: '复制' }] : []),
          {
            key: 'dataView',
            label: dataViewable ? '数据查看' : (
              <Tooltip title="仅 DB 模式且响应类型为 PAGE / LIST / OBJECT 的查询接口可用">
                <span>数据查看</span>
              </Tooltip>
            ),
            disabled: !dataViewable,
          },
          { key: 'logs', label: '查看日志' },
          {
            key: 'cache',
            label: cacheOn ? '查看缓存' : (
              <Tooltip title="未开启响应缓存">
                <span>查看缓存</span>
              </Tooltip>
            ),
            disabled: !cacheOn,
          },
          ...(canWrite
            ? [{ type: 'divider' as const }, { key: 'delete', label: '删除', danger: true }]
            : []),
        ];

        const onMenuClick: MenuProps['onClick'] = ({ key }) => {
          if (key === 'copy') {
            setCopySource(record);
            setCopyOpen(true);
            return;
          }
          if (key === 'dataView') {
            setDataViewApi({ id: record.id, name: record.name });
            setDataViewOpen(true);
            return;
          }
          if (key === 'logs') {
            history.push(`/log/execution?apiId=${record.id}`);
            return;
          }
          if (key === 'cache') {
            handleViewCache(record);
            return;
          }
          if (key === 'delete') {
            Modal.confirm({
              title: '确认删除该接口吗？',
              content: `接口「${record.name}」删除后不可恢复；若仍被其他资产引用会被拦截。`,
              okType: 'danger',
              onOk: async () => {
                await deleteAutoApiConfig(record.id);
                actionRef.current?.reload();
              },
            });
          }
        };

        return (
          <span style={{ whiteSpace: 'nowrap' }}>
            <a onClick={() => handleEdit(record)}>{canWrite ? '编辑' : '查看'}</a>
            {canWrite && (
              <>
                <Divider type="vertical" />
                {record.publishStatus === 1 ? (
                  <a onClick={() => handleUnpublish(record)}>下线</a>
                ) : (
                  <a onClick={() => handlePublish(record)}>发布</a>
                )}
              </>
            )}
            <Divider type="vertical" />
            <Dropdown menu={{ items: moreItems, onClick: onMenuClick }}>
              <a>
                更多 <DownOutlined style={{ fontSize: 10 }} />
              </a>
            </Dropdown>
          </span>
        );
      },
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      header={{
        title: '接口管理',
      }}
      style={{
        height: 'calc(100vh - 26px)',
        overflow: 'hidden',
      }}
    >
      <DirectoryTreeLayout
        bizType="api"
        height="calc(100vh - 90px)"
        onCreateApi={(directoryId) => openCreateForm(directoryId)}
        onHostImport={(directoryId) => openHostImport(directoryId)}
      >
        {(selectedDirectoryId, selectedDirectoryName) => (
          <>

            <ProTable<FlowController>
              // 操作列 fixed: 'right'，不能叠加 fh-table-fit：后者把表格压到容器宽度，
              // sticky 偏移量测量失真会导致表头与表体错位
              className="fh-table"
              headerTitle={`接口列表 (${selectedDirectoryName || '全部'})`}
              tableLayout="fixed"
              scroll={{ x: 2150, y: 100000 }}
              pagination={{
                defaultPageSize: 20,
                showSizeChanger: true,
                showQuickJumper: true,
                style: { marginBottom: 0 },
              }}
            actionRef={actionRef}
            rowKey="id"
            search={{
              labelWidth: 120,
            }}
            toolBarRender={() =>
              canWrite
                ? [
                    <Button
                      key="create"
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => openCreateForm(selectedDirectoryId)}
                    >
                      新建接口
                    </Button>,
                    <Button
                      key="host-import"
                      icon={<CloudServerOutlined />}
                      onClick={() => openHostImport(selectedDirectoryId)}
                    >
                      从宿主导入
                    </Button>,
                    <Button key="bundle-import" onClick={() => setImportOpen(true)}>
                      导入资产包
                    </Button>,
                  ]
                : []
            }
            params={{ directoryId: selectedDirectoryId }}
            request={async (params = {}, sort, filter) => {
              const { current, pageSize, directoryId, ...restParams } = params as any;
              setEmptyFiltered(
                !!directoryId ||
                  Object.values(restParams).some(
                    (v) => v !== undefined && v !== null && v !== '',
                  ),
              );
              const data = await queryAutoApiConfigList({
                ...restParams,
                directoryId,
                page: (current || 1) - 1,
                size: pageSize || 20,
              });
              const items: FlowController[] = data?.items || [];
              // 健康度与宿主探活只影响两列的标签，异步补齐即可；
              // 串行 await 会让整张表等两次额外请求才渲染
              void loadRowExtras(items);
              return {
                data: items,
                success: true,
                total: data?.total,
              };
            }}
            columns={columns}
            locale={{
              emptyText: (
                <TableEmpty
                  entityName="接口"
                  filtered={emptyFiltered}
                  hint="支持 SQL 一键成接口、可视化编排，或从 cURL / 宿主路由导入"
                  onCreate={canWrite ? () => openCreateForm(selectedDirectoryId) : undefined}
                  extraActions={
                    canWrite ? (
                      <Button
                        icon={<CloudServerOutlined />}
                        onClick={() => openHostImport(selectedDirectoryId)}
                      >
                        从宿主导入
                      </Button>
                    ) : undefined
                  }
                />
              ),
            }}
            rowSelection={{
              onChange: (_, selectedRows) => setSelectedRows(selectedRows),
            }}
            tableAlertOptionRender={() => {
              return (
                <Space size={16}>
                  {canWrite && (
                    <a
                      onClick={() => {
                        setBatchMoveModalVisible(true);
                      }}
                    >
                      批量移动
                    </a>
                  )}
                  {canWrite && <a onClick={() => openBatchApplyDirPrefix()}>按目录前缀重写</a>}
                  <a onClick={() => setExportOpen(true)}>批量导出</a>
                </Space>
              );
            }}
          />
          </>
        )}
      </DirectoryTreeLayout>

      {canWrite && selectedRowsState?.length > 0 && (
        <FooterToolbar
          extra={
            <div>
              已选择{' '}
              <a style={{ fontWeight: 600 }}>{selectedRowsState.length}</a>{' '}
              项&nbsp;&nbsp;
            </div>
          }
        >
          <Button onClick={() => openBatchApplyDirPrefix()}>按目录前缀重写</Button>
          <Button
            onClick={async () => {
              try {
                await handleRemove(selectedRowsState);
                setSelectedRows([]);
                actionRef.current?.reloadAndRest?.();
              } catch (error) {
                // 已处理
              }
            }}
          >
            批量删除
          </Button>
          <Button
            onClick={async () => {
              if (selectedRowsState.length > 20) {
                message.warning('单次批量回归最多 20 个接口，请减少选择');
                return;
              }
              const { confirmBatchRegression } = await import(
                '@/components/flow/release/confirmBatchRegression'
              );
              const envCode = await confirmBatchRegression({
                count: selectedRowsState.length,
                assetLabel: '接口',
              });
              if (!envCode) return;
              const hide = message.loading(
                `正在对 ${selectedRowsState.length} 个接口执行回归（${envCode}）…`,
                0,
              );
              try {
                const { batchRunRegression } = await import('@/services/flow/releaseService');
                const result = await batchRunRegression({
                  assetType: 'API',
                  assetIds: selectedRowsState.map((r) => r.id),
                  envCode,
                });
                hide();
                const summary = `通过 ${result.passed} / 失败 ${result.failed} / 跳过 ${result.skipped} / 错误 ${result.error}`;
                if (result.failed + result.error === 0) {
                  message.success(`批量回归完成（${envCode}）：${summary}`);
                } else {
                  Modal.warning({
                    title: `批量回归完成（${envCode}）`,
                    width: 640,
                    content: (
                      <div>
                        <p>{summary}</p>
                        <ul style={{ maxHeight: 280, overflow: 'auto', paddingLeft: 18 }}>
                          {(result.items || [])
                            .filter((i) => i.status !== 'PASSED' && i.status !== 'SKIPPED')
                            .map((i) => (
                              <li key={i.assetId}>
                                <b>{i.assetName || i.assetId}</b>：{i.status} — {i.message}
                              </li>
                            ))}
                        </ul>
                      </div>
                    ),
                  });
                }
              } catch (e: any) {
                hide();
                message.error(e?.message || '批量回归失败');
              }
            }}
          >
            批量回归
          </Button>
          <Button type="primary" onClick={handleBatchPublish}>
            批量发布
          </Button>
        </FooterToolbar>
      )}
      <HostApiImportModal
        open={hostImportOpen}
        directoryId={hostImportDirectoryId}
        onCancel={() => setHostImportOpen(false)}
        onImported={() => {
          setHostImportOpen(false);
          actionRef.current?.reload();
        }}
      />
      <ApiCopyModal
        open={copyOpen}
        source={copySource}
        onCancel={() => {
          setCopyOpen(false);
          setCopySource(null);
        }}
        onSuccess={() => {
          setCopyOpen(false);
          setCopySource(null);
          actionRef.current?.reload();
        }}
      />
      <AssetExportModal
        open={exportOpen}
        assetType="API"
        ids={selectedRowsState.map((r) => r.id).filter(Boolean)}
        onCancel={() => setExportOpen(false)}
      />
      <AssetImportModal
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        onSuccess={() => actionRef.current?.reload()}
      />
      <ApiConfigForm
        isEdit={isEditMode}
        modalVisible={formVisible}
        initialTab={formInitialTab}
        onCancel={() => {
          setFormVisible(false);
          setFormInitialTab(undefined);
        }}
        onSubmit={(success) => {
          if (success) {
            setFormVisible(false);
            setFormInitialTab(undefined);
            actionRef.current?.reload();
          }
        }}
        values={currentRow}
      />
      <ModalForm
        title="批量移动至"
        width="400px"
        open={batchMoveModalVisible}
        onOpenChange={setBatchMoveModalVisible}
        modalProps={{
          destroyOnClose: true,
        }}
        onFinish={async (values) => {
          const targetDir = values.targetDirectoryId || '0';
          const selectedRowKeys = selectedRowsState.map(r => r.id);
          try {
            await batchMoveAutoApiConfig(selectedRowKeys, targetDir);
            message.success('批量移动成功');
            setBatchMoveModalVisible(false);
            actionRef.current?.clearSelected?.();
            setSelectedRows([]);
            actionRef.current?.reload();
            return true;
          } catch (error: any) {
            if (!error?.message?.includes('DEMO_RESTRICTED')) {
              message.error('批量移动失败');
            }
            return false;
          }
        }}
      >
        <DirectoryTreeSelect bizType="api" />
      </ModalForm>

      <Modal
        title="按目录前缀重写"
        open={batchPrefixOpen}
        width={720}
        destroyOnClose
        okText="确认重写"
        confirmLoading={batchPrefixSubmitting}
        onCancel={() => setBatchPrefixOpen(false)}
        onOk={async () => {
          setBatchPrefixSubmitting(true);
          try {
            const res: any = await batchApplyDirPrefix(
              selectedRowsState.map((r) => r.id),
              batchOldPrefix.trim() || undefined,
            );
            const data = res?.data ?? res;
            const updated = data?.updated ?? 0;
            const skipped = data?.skipped ?? 0;
            const failed = data?.failed ?? 0;
            const publishedTouched = data?.publishedTouched ?? 0;
            const summary = `更新 ${updated} / 跳过 ${skipped} / 失败 ${failed}`;
            if (failed > 0) {
              Modal.warning({
                title: '批量重写完成（含失败）',
                width: 640,
                content: (
                  <div>
                    <p>{summary}</p>
                    {publishedTouched > 0 && (
                      <p>其中 {publishedTouched} 条已发布，仅改草稿，需重新发布后线上路由才变。</p>
                    )}
                    <ul style={{ maxHeight: 280, overflow: 'auto', paddingLeft: 18 }}>
                      {(data?.items || [])
                        .filter((i: any) => i.status !== 'updated')
                        .map((i: any) => (
                          <li key={i.id}>
                            <b>{i.name || i.id}</b>：{i.status} — {i.message}
                            {i.from && i.to && i.from !== i.to ? `（${i.from} → ${i.to}）` : ''}
                          </li>
                        ))}
                    </ul>
                  </div>
                ),
              });
            } else {
              message.success(
                publishedTouched > 0
                  ? `${summary}；其中 ${publishedTouched} 条已发布需再发布才上线`
                  : summary,
              );
            }
            setBatchPrefixOpen(false);
            actionRef.current?.clearSelected?.();
            setSelectedRows([]);
            actionRef.current?.reload();
          } catch (e: any) {
            if (!e?.message?.includes('DEMO_RESTRICTED')) {
              message.error(e?.message || '批量重写失败');
            }
          } finally {
            setBatchPrefixSubmitting(false);
          }
        }}
      >
        <p style={{ marginBottom: 12, color: 'rgba(0,0,0,0.65)' }}>
          用各接口所属目录的当前有效前缀（根→叶叠加）重写草稿 path；相对段保留。已发布接口只改草稿，需再发布才上线。
        </p>
        <Form layout="vertical">
          <Form.Item
            label="旧前缀（从现有 path 剥离）"
            extra="默认取勾选接口 URL 的最长公共前缀，可按需修改"
          >
            <Input
              value={batchOldPrefix}
              placeholder="例如 /api/public"
              allowClear
              onChange={(e) => setBatchOldPrefix(e.target.value)}
              onBlur={() => rebuildBatchPrefixPreview(selectedRowsState, batchOldPrefix)}
              onPressEnter={() => rebuildBatchPrefixPreview(selectedRowsState, batchOldPrefix)}
            />
          </Form.Item>
        </Form>
        <div style={{ marginBottom: 8 }}>
          <Button
            size="small"
            loading={batchPrefixPreviewLoading}
            onClick={() => rebuildBatchPrefixPreview(selectedRowsState, batchOldPrefix)}
          >
            刷新预览
          </Button>
        </div>
        <Table
          size="small"
          loading={batchPrefixPreviewLoading}
          rowKey="id"
          pagination={false}
          scroll={{ y: 280 }}
          dataSource={batchPrefixPreview}
          columns={[
            { title: '名称', dataIndex: 'name', width: 140, ellipsis: true },
            { title: '原 path', dataIndex: 'from', ellipsis: true },
            { title: '新 path', dataIndex: 'to', ellipsis: true },
            {
              title: '说明',
              dataIndex: 'note',
              width: 140,
              render: (v?: string) => v || '将更新',
            },
          ]}
        />
      </Modal>

      <Drawer
        title={`响应缓存 — ${cacheDrawerApi?.name || ''}`}
        width={720}
        open={cacheDrawerVisible}
        onClose={() => {
          setCacheDrawerVisible(false);
          setCacheDrawerApi(null);
          setCacheEntries([]);
        }}
        extra={
          <Space>
            <Button
              loading={cacheLoading}
              onClick={() => cacheDrawerApi && loadCacheEntries(cacheDrawerApi.id)}
            >
              刷新
            </Button>
            <Popconfirm
              title="确认清除该接口全部响应缓存？"
              onConfirm={() => cacheDrawerApi && handleClearAllCache(cacheDrawerApi)}
            >
              <Button danger disabled={!cacheEntries.length}>全部清除</Button>
            </Popconfirm>
          </Space>
        }
      >
        <Table<ApiCacheEntry>
          rowKey="key"
          loading={cacheLoading}
          dataSource={cacheEntries}
          size="small"
          pagination={{ pageSize: 20 }}
          columns={[
            {
              title: '缓存 Key',
              dataIndex: 'key',
              ellipsis: true,
              render: (text: string) => (
                <Tooltip title={text}>
                  <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{text}</span>
                </Tooltip>
              ),
            },
            {
              title: '剩余 TTL',
              dataIndex: 'ttlSeconds',
              width: 100,
              render: (ttl: number) => {
                if (ttl === -1) return '永久';
                if (ttl < 0) return '-';
                if (ttl < 60) return `${ttl}s`;
                return `${Math.floor(ttl / 60)}m ${ttl % 60}s`;
              },
            },
            {
              title: '大小',
              dataIndex: 'sizeBytes',
              width: 90,
              render: (size: number) => {
                if (size == null) return '-';
                if (size < 1024) return `${size} B`;
                return `${(size / 1024).toFixed(1)} KB`;
              },
            },
            {
              title: '操作',
              width: 120,
              render: (_, entry) => (
                <Space size="middle">
                  <a onClick={() => handleViewCacheContent(entry)}>查看</a>
                  <Popconfirm
                    title="确认删除该条缓存？"
                    onConfirm={async () => {
                      if (!cacheDrawerApi) return;
                      try {
                        await clearApiCacheEntry(cacheDrawerApi.id, entry.key);
                        message.success('已删除');
                        await loadCacheEntries(cacheDrawerApi.id);
                      } catch {
                        message.error('删除失败');
                      }
                    }}
                  >
                    <a>删除</a>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
          locale={{ emptyText: '暂无生效中的响应缓存' }}
        />
      </Drawer>

      <Modal
        title="缓存内容"
        open={cacheContentVisible}
        onCancel={() => {
          setCacheContentVisible(false);
          setCacheContentText('');
          setCacheContentKey('');
          setCacheContentTruncated(false);
        }}
        footer={null}
        width={860}
        destroyOnClose
        styles={{ body: { paddingTop: 12 } }}
      >
        <div style={{ marginBottom: 8, fontSize: 12, color: '#8c8c8c', wordBreak: 'break-all' }}>
          <Tooltip title={cacheContentKey}>
            <span style={{ fontFamily: 'monospace' }}>{cacheContentKey}</span>
          </Tooltip>
          {cacheContentTruncated && (
            <Tag color="orange" style={{ marginLeft: 8 }}>已截断</Tag>
          )}
        </div>
        <Spin spinning={cacheContentLoading}>
          <CodeEditor
            value={cacheContentText}
            onChange={() => {}}
            language="json"
            readOnly
            height="60vh"
            wordWrap={cacheContentText.length < CACHE_VIEW_WORDWRAP_LIMIT}
            showFormat={false}
          />
        </Spin>
      </Modal>

      {dataViewApi && (
        <ApiDataViewDrawer
          open={dataViewOpen}
          onClose={() => {
            setDataViewOpen(false);
            setDataViewApi(null);
          }}
          apiId={dataViewApi.id}
          apiName={dataViewApi.name}
          onPublished={() => actionRef.current?.reload()}
        />
      )}
    </PageContainer>
  );
};

export default AutoApiConfigList;
