import React, { useEffect, useRef, useState } from 'react';
import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProDescriptions,
  ProDescriptionsItemProps,
  ProTable,
  ModalForm,
} from '@ant-design/pro-components';
import { Button, Divider, Drawer, Modal, message, Tag, Popconfirm, Space, Switch, Tooltip, Table, Spin } from 'antd';
import { CloudServerOutlined, PlusOutlined } from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import {
  queryAutoApiConfigDetail,
  queryAutoApiConfigList,
  addAutoApiConfig,
  updateAutoApiConfig,
  deleteAutoApiConfig,
  batchDeleteAutoApiConfig,
  batchMoveAutoApiConfig,
  updateAutoApiLogEnabled,
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
import ApiDataViewDrawer from './components/ApiDataViewDrawer';
import HostApiImportModal from './components/HostApiImportModal';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import TableEmpty from '@/components/TableEmpty';
import CodeEditor from '@/components/flow/flow-editor/components/CodeEditor';
import { batchAssetHealth, type AssetHealth } from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';

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

  return (
    <Space size={4} wrap>
      <Tag color={modeColor} style={{ margin: 0 }}>{modeLabel}</Tag>
      <Tag color={rlColor} style={{ margin: 0 }}>{rlText}</Tag>
      <Tag style={{ margin: 0 }}>{toText}</Tag>
    </Space>
  );
}
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
 * 添加配置
 */
const handleAdd = async (fields: Partial<FlowController>) => {
  const hide = message.loading('正在添加');
  try {
    await addAutoApiConfig(fields);
    hide();
    message.success('添加成功');
    return true;
  } catch (error: any) {
    hide();
    if (!error?.message?.includes('DEMO_RESTRICTED')) {
      message.error('添加失败请重试！');
    }
    return false;
  }
};

/**
 * 更新配置
 */
const handleUpdate = async (id: string, fields: Partial<FlowController>) => {
  const hide = message.loading('正在更新');
  try {
    await updateAutoApiConfig(id, fields);
    hide();
    message.success('更新成功');
    return true;
  } catch (error: any) {
    hide();
    if (!error?.message?.includes('DEMO_RESTRICTED')) {
      message.error('更新失败请重试！');
    }
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
  const [createModalVisible, handleModalVisible] = useState<boolean>(false);
  const [hostImportOpen, setHostImportOpen] = useState(false);
  const [hostImportDirectoryId, setHostImportDirectoryId] = useState<string | undefined>();
  const actionRef = useRef<ActionType>();
  const [row, setRow] = useState<FlowController>();
  const [selectedRowsState, setSelectedRows] = useState<FlowController[]>([]);
  const [batchMoveModalVisible, setBatchMoveModalVisible] = useState<boolean>(false);
  // 空态区分：是否处于筛选（目录 / 搜索条件）
  const [emptyFiltered, setEmptyFiltered] = useState<boolean>(false);

  // 状态定义
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<FlowController>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [formInitialTab, setFormInitialTab] = useState<
    'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime' | undefined
  >();
  const [dataViewOpen, setDataViewOpen] = useState(false);
  const [dataViewApi, setDataViewApi] = useState<{ id: string; name?: string } | null>(null);

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

  /** 新建直进表单，模式在页内选择（默认替换） */
  const openCreateForm = (directoryId?: string) => {
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

  const handleLogEnabledChange = async (record: FlowController, checked: boolean) => {
    try {
      await updateAutoApiLogEnabled(record.id, checked);
      message.success(checked ? '已开启执行日志' : '已关闭执行日志');
      actionRef.current?.reload();
    } catch (error: any) {
      if (!error?.message?.includes('DEMO_RESTRICTED')) {
        message.error('更新执行日志开关失败');
      }
      actionRef.current?.reload();
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
      width: 420,
      fixed: 'right',
      render: (_, record) => (
        <span style={{ whiteSpace: 'nowrap' }}>
          <a onClick={() => handleEdit(record)}>编辑</a>
          <Divider type="vertical" />
          {supportsApiDataView(record) ? (
            <a
              onClick={() => {
                setDataViewApi({ id: record.id, name: record.name });
                setDataViewOpen(true);
              }}
            >
              数据查看
            </a>
          ) : (
            <Tooltip title="仅 DB 模式且响应类型为 PAGE / LIST / OBJECT 的查询接口可用">
              <span style={{ color: 'rgba(0,0,0,0.25)', cursor: 'not-allowed' }}>数据查看</span>
            </Tooltip>
          )}
          <Divider type="vertical" />
          {record.publishStatus === 1 ? (
            <a
              onClick={async () => {
                try {
                  await unpublishApi(record.id);
                  message.success('已下线');
                  actionRef.current?.reload();
                } catch {
                  /* 拦截器已提示 */
                }
              }}
            >
              下线
            </a>
          ) : (
            <a
              onClick={async () => {
                try {
                  const isWrap = record.interceptMode === 'WRAP' || record.serviceType === 'HOST';
                  if (!isWrap && record.method && record.url) {
                    let hostExists = false;
                    try {
                      const existsRes: any = await checkHostApiRouteExists(record.method, record.url);
                      hostExists = existsRes?.exists === true || existsRes?.data?.exists === true;
                    } catch {
                      hostExists = false;
                    }
                    if (hostExists) {
                      const ok = await new Promise<boolean>((resolve) => {
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
                      if (!ok) return;
                    }
                  }
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
              }}
            >
              发布
            </a>
          )}
          <Divider type="vertical" />
          <a onClick={() => history.push(`/log/execution?apiId=${record.id}`)}>
            查看日志
          </a>
          <Divider type="vertical" />
          {isCacheEnabled(record.cacheConfig) ? (
            <a onClick={() => handleViewCache(record)}>查看缓存</a>
          ) : (
            <Tooltip title="未开启响应缓存">
              <span style={{ color: 'rgba(0,0,0,0.25)', cursor: 'not-allowed' }}>查看缓存</span>
            </Tooltip>
          )}
          <Divider type="vertical" />
          <Popconfirm
            title="确认删除该接口吗？"
            onConfirm={async () => {
              try {
                await deleteAutoApiConfig(record.id);
                actionRef.current?.reload();
              } catch (error) {
                // 已通过全局拦截器展示
              }
            }}
          >
            <a>删除</a>
          </Popconfirm>
        </span>
      ),
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
      <DirectoryTreeLayout bizType="api" height="calc(100vh - 90px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <>

            <ProTable<FlowController>
              className="fh-table fh-table-fit"
              headerTitle={`接口列表 (${selectedDirectoryName || '全部'})`}
              tableLayout="fixed"
              scroll={{ x: 2240, y: 100000 }}
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
            toolBarRender={() => [
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
                onClick={() => {
                  setHostImportDirectoryId(selectedDirectoryId);
                  setHostImportOpen(true);
                }}
              >
                从宿主导入
              </Button>,
            ]}
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
              try {
                const health = await batchAssetHealth(
                  items.filter((i) => i.id).map((i) => ({ assetType: 'API' as const, assetId: i.id })),
                );
                const map: Record<string, AssetHealth> = {};
                (health || []).forEach((h) => {
                  map[h.assetId] = h;
                });
                setHealthMap(map);
              } catch {
                setHealthMap({});
              }
              try {
                const wrapIds = items
                  .filter((i) => i.id && (i.interceptMode === 'WRAP' || i.serviceType === 'HOST'))
                  .map((i) => i.id);
                if (wrapIds.length) {
                  const probes: any = await batchHostApiProbe(wrapIds);
                  const list = Array.isArray(probes) ? probes : (probes?.data ?? []);
                  const pmap: Record<string, HostApiProbeResult> = {};
                  list.forEach((p: HostApiProbeResult) => {
                    if (p?.apiId) pmap[p.apiId] = p;
                  });
                  setProbeMap(pmap);
                } else {
                  setProbeMap({});
                }
              } catch {
                setProbeMap({});
              }
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
                  onCreate={() => openCreateForm(selectedDirectoryId)}
                  extraActions={
                    <Button
                      icon={<CloudServerOutlined />}
                      onClick={() => {
                        setHostImportDirectoryId(selectedDirectoryId);
                        setHostImportOpen(true);
                      }}
                    >
                      从宿主导入
                    </Button>
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
                  <a
                    onClick={() => {
                      setBatchMoveModalVisible(true);
                    }}
                  >
                    批量移动
                  </a>
                </Space>
              );
            }}
          />
          </>
        )}
      </DirectoryTreeLayout>

      {selectedRowsState?.length > 0 && (
        <FooterToolbar
          extra={
            <div>
              已选择{' '}
              <a style={{ fontWeight: 600 }}>{selectedRowsState.length}</a>{' '}
              项&nbsp;&nbsp;
            </div>
          }
        >
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
          <Button
            type="primary"
            onClick={async () => {
              const hide = message.loading(`正在发布 ${selectedRowsState.length} 个接口...`);
              try {
                let ok = 0;
                let fail = 0;
                for (const row of selectedRowsState) {
                  try {
                    await publishApi(row.id);
                    ok += 1;
                  } catch {
                    fail += 1;
                  }
                }
                hide();
                if (fail === 0) {
                  message.success(`已发布 ${ok} 个接口`);
                } else {
                  message.warning(`发布完成：成功 ${ok}，失败 ${fail}`);
                }
                setSelectedRows([]);
                actionRef.current?.reloadAndRest?.();
              } catch {
                hide();
              }
            }}
          >
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
      <Drawer
        width={600}
        open={!!row}
        onClose={() => {
          setRow(undefined);
        }}
        closable={false}
      >
        {row?.name && (
          <ProDescriptions<FlowController>
            column={2}
            title={row?.name}
            request={async () => ({
              data: row || {},
            })}
            params={{
              id: row?.id,
            }}
            columns={columns.filter((item) => item.dataIndex !== 'option') as ProDescriptionsItemProps<FlowController>[]}
          />
        )}
      </Drawer>

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
