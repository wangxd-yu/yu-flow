import React, { useEffect, useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, message, Popconfirm } from 'antd';
import { useLocation } from '@umijs/max';
import DirectoryTreeLayout, { type DirectoryBizType } from '@/components/DirectoryTreeLayout';
import TableEmpty from '@/components/TableEmpty';
import { batchAssetHealth, type AssetHealth } from '@/services/flow/assetMetrics';
import '@/styles/fullHeightTable.css';

/** 列构建上下文：列定义留在页面，通过它访问壳内状态 */
export interface AssetListShellContext<T> {
  healthMap: Record<string, AssetHealth>;
  /** fetchRowExtra 的返回值，未配置时为空对象 */
  extraMap: Record<string, any>;
  /** 打开编辑抽屉（先拉详情，失败提示「加载{名词}详情失败」）；可指定初始 Tab（如 'logs'） */
  openEdit: (record: T, tab?: string) => void;
  /** 刷新表格 */
  reload: () => void;
  /** 页面自定义删除逻辑后调用（等价原 handleRemove + reload） */
  removeAndReload: (rows: T[]) => Promise<void>;
}

/** 表单渲染上下文：壳负责可见性 / 编辑态 / 提交编排，表单组件由页面提供 */
export interface AssetFormContext<T> {
  visible: boolean;
  isEdit: boolean;
  currentRow: Partial<T>;
  initialTab?: string;
  close: () => void;
  submit: (values: Partial<T>) => Promise<void>;
  onPublished: (detail: Partial<T>) => void;
}

export interface AssetDirectoryListShellProps<T extends { id: string; directoryId?: string }> {
  /** 目录树业务类型：task / service */
  bizType: DirectoryBizType;
  /** 页头标题，如「任务管理」 */
  pageTitle: string;
  /** 资产名词，用于按钮 / 提示文案，如「任务」「服务」 */
  entityLabel: string;
  /** 表格标题前缀，如「任务列表」 */
  listTitle: string;
  /** 空态提示语 */
  emptyHint: string;
  /** 运行健康批查的资产类型 */
  metricsAssetType: 'TASK' | 'SERVICE' | 'MQ_TASK';
  /** URL 深链参数名（taskId / serviceId），命中则直接打开详情 */
  deepLinkParam: string;
  /** 拉取单条详情（深链与编辑共用） */
  fetchDetail: (id: string) => Promise<any>;
  /** 分页查询：入参为 ProTable request 原始 params，页面内完成参数映射 */
  fetchPage: (params: Record<string, any>) => Promise<{ items: T[]; total: number }>;
  /** 空态区分：当前 params 是否处于筛选（目录 / 搜索条件） */
  isFiltered: (params: Record<string, any>) => boolean;
  /** 新建提交（页面保留自身 loading / 错误文案），返回是否成功 */
  submitCreate: (fields: Partial<T>) => Promise<boolean>;
  /** 更新提交，返回是否成功 */
  submitUpdate: (id: string, fields: Partial<T>) => Promise<boolean>;
  /** 批量 / 单条删除（页面保留自身错误文案），返回是否成功 */
  removeRows: (rows: T[]) => Promise<boolean>;
  /** 列定义（含操作列），每次渲染基于最新上下文构建 */
  buildColumns: (ctx: AssetListShellContext<T>) => ProColumns<T>[];
  /**
   * 行附加数据（如 MQ 任务的消费订阅存活状态），与健康批查一同在分页后拉取。
   * <p>失败不影响列表渲染（降级为空对象）；返回值经 ctx.extraMap 给列使用。</p>
   */
  fetchRowExtra?: (items: T[]) => Promise<Record<string, any>>;
  /** 表格横向滚动宽度，列较多的页面可调大（默认 1400） */
  scrollX?: number;
  /** 表单弹层：仅在 visible 时渲染 */
  renderForm: (ctx: AssetFormContext<T>) => React.ReactNode;
  /** 页面级附加弹层（如手动调用 Modal），始终渲染 */
  children?: React.ReactNode;
}

/**
 * 资产目录列表壳：收敛「目录树 + ProTable + 新建/编辑表单 + 运行健康」的通用骨架。
 * <p>Task / Service 列表页仅需提供列定义、表单组件与差异化 CRUD 回调，行为保持不变。</p>
 */
function AssetDirectoryListShell<T extends { id: string; directoryId?: string }>(
  props: AssetDirectoryListShellProps<T>,
) {
  const {
    bizType,
    pageTitle,
    entityLabel,
    listTitle,
    emptyHint,
    metricsAssetType,
    deepLinkParam,
    fetchDetail,
    fetchPage,
    isFiltered,
    submitCreate,
    submitUpdate,
    removeRows,
    buildColumns,
    renderForm,
    fetchRowExtra,
    scrollX = 1400,
    children,
  } = props;

  const location = useLocation();
  const actionRef = useRef<ActionType>();
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<T>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [selectedRowsState, setSelectedRows] = useState<T[]>([]);
  const [healthMap, setHealthMap] = useState<Record<string, AssetHealth>>({});
  const [extraMap, setExtraMap] = useState<Record<string, any>>({});
  const [formInitialTab, setFormInitialTab] = useState<string | undefined>();
  // 空态区分：是否处于筛选（目录 / 搜索条件）
  const [emptyFiltered, setEmptyFiltered] = useState<boolean>(false);

  useEffect(() => {
    const params = new URLSearchParams(location.search || '');
    const targetId = params.get(deepLinkParam);
    const tab = params.get('tab') || undefined;
    if (!targetId) return;
    let cancelled = false;
    (async () => {
      try {
        const detail: any = await fetchDetail(targetId);
        if (cancelled) return;
        setCurrentRow(detail?.data || detail || ({ id: targetId } as Partial<T>));
        setIsEditMode(true);
        setFormInitialTab(tab || 'runtime');
        setFormVisible(true);
      } catch {
        if (!cancelled) message.error(`打开${entityLabel}详情失败`);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  const reload = () => actionRef.current?.reload();

  const handleAddAction = (directoryId?: string) => {
    setCurrentRow({ directoryId } as Partial<T>);
    setIsEditMode(false);
    setFormInitialTab(undefined);
    setFormVisible(true);
  };

  const handleEditAction = async (record: T, tab?: string) => {
    try {
      const detail: any = await fetchDetail(record.id);
      setCurrentRow(detail?.data || detail || record);
      setIsEditMode(true);
      setFormInitialTab(tab);
      setFormVisible(true);
    } catch {
      message.error(`加载${entityLabel}详情失败`);
    }
  };

  const handleFormSubmit = async (values: Partial<T>) => {
    if (isEditMode && currentRow?.id) {
      const ok = await submitUpdate(currentRow.id, values);
      if (ok) {
        setFormVisible(false);
        reload();
      }
    } else {
      const ok = await submitCreate({
        ...values,
        directoryId: values.directoryId || currentRow?.directoryId,
      });
      if (ok) {
        setFormVisible(false);
        reload();
      }
    }
  };

  const removeAndReload = async (rows: T[]) => {
    await removeRows(rows);
    reload();
  };

  const columnCtx: AssetListShellContext<T> = {
    healthMap,
    extraMap,
    openEdit: handleEditAction,
    reload,
    removeAndReload,
  };

  return (
    <PageContainer
      className="fh-container"
      header={{ title: pageTitle }}
      style={{
        height: 'calc(100vh - 26px)',
        overflow: 'hidden',
      }}
    >
      <DirectoryTreeLayout bizType={bizType} height="calc(100vh - 90px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <ProTable<T>
            className="fh-table fh-table-fit"
            headerTitle={`${listTitle} (${selectedDirectoryName || '全部'})`}
            tableLayout="fixed"
            scroll={{ x: scrollX, y: 100000 }}
            pagination={{
              defaultPageSize: 20,
              showSizeChanger: true,
              showQuickJumper: true,
              style: { marginBottom: 0 },
            }}
            actionRef={actionRef}
            rowKey="id"
            search={{ labelWidth: 80 }}
            toolBarRender={() => [
              <Button
                key="add"
                type="primary"
                onClick={() => handleAddAction(selectedDirectoryId)}
              >
                新建{entityLabel}
              </Button>,
              selectedRowsState?.length > 0 && (
                <Popconfirm
                  key="batchDelete"
                  title={`确定删除选中的 ${selectedRowsState.length} 个${entityLabel}？`}
                  onConfirm={async () => {
                    await removeRows(selectedRowsState);
                    setSelectedRows([]);
                    reload();
                  }}
                >
                  <Button danger>批量删除</Button>
                </Popconfirm>
              ),
            ]}
            params={{ directoryId: selectedDirectoryId }}
            request={async (params = {}) => {
              setEmptyFiltered(isFiltered(params as Record<string, any>));
              const { items, total } = await fetchPage(params as Record<string, any>);
              try {
                const health = await batchAssetHealth(
                  items
                    .filter((i) => i.id)
                    .map((i) => ({ assetType: metricsAssetType, assetId: i.id })),
                );
                const map: Record<string, AssetHealth> = {};
                (health || []).forEach((h) => {
                  map[h.assetId] = h;
                });
                setHealthMap(map);
              } catch {
                setHealthMap({});
              }
              if (fetchRowExtra) {
                try {
                  setExtraMap(await fetchRowExtra(items));
                } catch {
                  setExtraMap({});
                }
              }
              return {
                data: items,
                success: true,
                total,
              };
            }}
            columns={buildColumns(columnCtx)}
            locale={{
              emptyText: (
                <TableEmpty
                  entityName={entityLabel}
                  filtered={emptyFiltered}
                  hint={emptyHint}
                  onCreate={() => handleAddAction(selectedDirectoryId)}
                />
              ),
            }}
            rowSelection={{
              onChange: (_, selectedRows) => setSelectedRows(selectedRows),
            }}
          />
        )}
      </DirectoryTreeLayout>

      {formVisible &&
        renderForm({
          visible: formVisible,
          isEdit: isEditMode,
          currentRow,
          initialTab: formInitialTab,
          close: () => {
            setFormVisible(false);
            setFormInitialTab(undefined);
          },
          submit: handleFormSubmit,
          onPublished: (detail) => {
            setCurrentRow(detail);
            reload();
          },
        })}

      {children}
    </PageContainer>
  );
}

export default AssetDirectoryListShell;
