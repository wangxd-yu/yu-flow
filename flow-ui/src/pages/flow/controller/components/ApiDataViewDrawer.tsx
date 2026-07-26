/**
 * 管理端：DB 接口数据查看 / Excel 导出 / 公司模板灌数
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormInstance } from 'antd/es/form';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Space,
  Switch,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ExportOutlined,
} from '@ant-design/icons';
import {
  createApiExcelExportLink,
  deleteApiExcelTemplate,
  downloadApiExcelTemplateFile,
  downloadApiExcelTemplateSample,
  exportApiDataExcel,
  getApiExcelTemplateMeta,
  previewApiData,
  publishApi,
  queryAutoApiConfigDetail,
  republishApi,
  updateAutoApiConfig,
  uploadApiExcelTemplate,
  type ApiDataPreviewResult,
  type ApiExcelTemplateMeta,
  type FlowController,
  type ViewExportColumn,
  type ViewExportConfig,
} from '@/services/flow/flowController';
import { confirmPublishWithGate } from '@/components/flow/release/confirmPublishWithGate';
import { copyText } from '@/utils/apiDocsActions';
import { request } from '@umijs/max';
import {
  FALLBACK_LABEL,
  formatBytes,
  parseContractParams,
  syncColumnsFromContract,
  buildParamsFromForm,
  type ParamField,
} from './apiDataViewUtils';
import ApiDataViewDataTab from './ApiDataViewDataTab';
import ApiDataViewColumnsTab from './ApiDataViewColumnsTab';
import ApiDataViewTemplateTab from './ApiDataViewTemplateTab';
import ApiDataViewOpenExportTab from './ApiDataViewOpenExportTab';

type Props = {
  open: boolean;
  onClose: () => void;
  apiId: string;
  apiName?: string;
  /** 抽屉内完成发布后回调（用于刷新列表/编辑页发布状态） */
  onPublished?: () => void;
};

const ApiDataViewDrawer: React.FC<Props> = ({ open, onClose, apiId, apiName, onPublished }) => {
  const [detail, setDetail] = useState<FlowController | null>(null);
  const [systemPrefix, setSystemPrefix] = useState('/flow-api');
  const [openEntryPrefix, setOpenEntryPrefix] = useState('/flow-api/open');
  const [signingLink, setSigningLink] = useState(false);
  const [signedLinkPreview, setSignedLinkPreview] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [useDraft, setUseDraft] = useState(true);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<ApiDataPreviewResult | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [paramForm] = Form.useForm();
  const [cfgForm] = Form.useForm();
  const [columns, setColumns] = useState<ViewExportColumn[]>([]);
  const [templateMeta, setTemplateMeta] = useState<ApiExcelTemplateMeta | null>(null);
  const [uploading, setUploading] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [activeTab, setActiveTab] = useState('data');

  const paramFields = useMemo(() => parseContractParams(detail?.contract), [detail?.contract]);
  const exportEnabled = Form.useWatch('enabled', cfgForm) !== false;
  const exportMode = Form.useWatch('exportMode', cfgForm) === 'TEMPLATE' ? 'TEMPLATE' : 'DYNAMIC';
  const openExportEnabled = Form.useWatch('openExportEnabled', cfgForm) === true;
  const signedLinkEnabled = Form.useWatch('signedLinkEnabled', cfgForm) !== false;
  const isPublished = Number(detail?.publishStatus) === 1;

  const markDirty = useCallback(() => setDirty(true), []);

  const loadTemplateMeta = useCallback(async () => {
    if (!apiId) return;
    try {
      const meta = await getApiExcelTemplateMeta(apiId);
      setTemplateMeta(meta);
    } catch {
      setTemplateMeta({ apiId, present: false });
    }
  }, [apiId]);

  const loadDetail = useCallback(async (): Promise<FlowController | null> => {
    if (!apiId) return null;
    const res: any = await queryAutoApiConfigDetail(apiId);
    const d: FlowController = res?.data || res;
    setDetail(d);
    let cfg: ViewExportConfig = {};
    try {
      cfg = d.viewExportConfig
        ? typeof d.viewExportConfig === 'string'
          ? JSON.parse(d.viewExportConfig)
          : d.viewExportConfig
        : {};
    } catch {
      cfg = {};
    }
    cfgForm.setFieldsValue({
      enabled: cfg.enabled !== false,
      sheetName: cfg.sheetName || '数据',
      maxExportRows: cfg.maxExportRows || 50000,
      exportMode: cfg.exportMode === 'TEMPLATE' ? 'TEMPLATE' : 'DYNAMIC',
      templateSheetNo: cfg.templateSheetNo ?? 0,
      openExportEnabled: cfg.openExportEnabled === true,
      signedLinkEnabled: cfg.signedLinkEnabled !== false,
      signedLinkTtlSeconds: cfg.signedLinkTtlSeconds || 300,
    });
    setSignedLinkPreview(null);
    setColumns(
      (cfg.columns?.length ? cfg.columns : syncColumnsFromContract(d.contract)).map((c) => ({
        ...c,
        templateKey: c.templateKey || c.field,
      })),
    );
    const draftPreferred = d.publishStatus !== 1;
    if (draftPreferred) {
      setUseDraft(true);
    }
    setDirty(false);
    await loadTemplateMeta();
    return d;
  }, [apiId, cfgForm, loadTemplateMeta]);

  const runPreview = useCallback(
    async (nextPage = 0, nextSize = pageSize, draftFlag?: boolean) => {
      if (!apiId) return;
      setLoading(true);
      try {
        const params = buildParamsFromForm(paramForm, paramFields);
        const data = await previewApiData(apiId, {
          useDraft: draftFlag ?? useDraft,
          ...params,
          page: nextPage,
          size: nextSize,
        });
        setResult(data);
        if (data.columns?.length) {
          setColumns((prev) => {
            if (prev.length) return prev;
            return data.columns!.map((c) => ({ ...c, templateKey: c.templateKey || c.field }));
          });
        }
        setPage(data.page ?? nextPage);
        setPageSize(data.size ?? nextSize);
      } catch (e: any) {
        message.error(e?.message || '预览失败');
      } finally {
        setLoading(false);
      }
    },
    [apiId, pageSize, paramForm, paramFields, useDraft],
  );

  useEffect(() => {
    if (!open) return;
    request('/flow-api/sys-configs/key/SYSTEM_PREFIX', { method: 'GET' })
      .then((res: any) => {
        const data = typeof res === 'string' ? res : res?.data;
        setSystemPrefix(data || '/flow-api');
      })
      .catch(() => setSystemPrefix('/flow-api'));
    request('/flow-api/open-platforms/meta/entry', { method: 'GET' })
      .then((res: any) => {
        const data = res?.data ?? res;
        setOpenEntryPrefix(data?.entryPrefix || '/flow-api/open');
      })
      .catch(() => setOpenEntryPrefix('/flow-api/open'));
  }, [open]);

  useEffect(() => {
    if (!open || !apiId) return;
    let cancelled = false;
    setResult(null);
    setPage(0);
    setActiveTab('data');
    setDirty(false);
    paramForm.resetFields();
    (async () => {
      try {
        const d = await loadDetail();
        if (cancelled || !d) return;
        const st = (d.serviceType || '').toUpperCase();
        if (st !== 'DB') return;
        const draft = d.publishStatus !== 1 ? true : useDraft;
        await runPreview(0, pageSize, draft);
      } catch {
        if (!cancelled) message.error('加载接口失败');
      }
    })();
    return () => {
      cancelled = true;
    };
    // 仅在打开抽屉 / 切换接口时自动拉数；勿依赖 runPreview 以免改参反复请求
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, apiId]);

  const buildParams = () => buildParamsFromForm(paramForm, paramFields);

  const joinUrl = (prefix: string, path: string) => {
    const p = (prefix || '').replace(/\/+$/, '');
    const u = (path || '').startsWith('/') ? path : `/${path || ''}`;
    return `${p}${u}`.replace(/(?<!:)\/\/+/g, '/');
  };

  const exportDirectUrl = useMemo(() => {
    const apiUrl = detail?.url || '';
    if (!apiUrl) return '';
    return `${joinUrl(systemPrefix, apiUrl)}/export`;
  }, [detail?.url, systemPrefix]);

  const exportOpenUrl = useMemo(() => {
    const apiUrl = detail?.url || '';
    if (!apiUrl) return '';
    return `${joinUrl(openEntryPrefix, apiUrl)}/export`;
  }, [detail?.url, openEntryPrefix]);

  const handleCopyUrl = async (text: string) => {
    if (!text) return;
    const ok = await copyText(text);
    if (ok) message.success('已复制');
    else message.error('复制失败');
  };

  const buildViewExportConfig = (override?: Partial<ViewExportConfig>): ViewExportConfig => {
    const cfgValues = cfgForm.getFieldsValue();
    const base: ViewExportConfig = {
      enabled: cfgValues.enabled !== false,
      sheetName: cfgValues.sheetName || '数据',
      maxExportRows: cfgValues.maxExportRows || 50000,
      exportMode: cfgValues.exportMode === 'TEMPLATE' ? 'TEMPLATE' : 'DYNAMIC',
      templateFileId: templateMeta?.present ? templateMeta.id : undefined,
      templateSheetNo: cfgValues.templateSheetNo ?? 0,
      openExportEnabled: cfgValues.openExportEnabled === true,
      signedLinkEnabled: cfgValues.signedLinkEnabled !== false,
      signedLinkTtlSeconds: cfgValues.signedLinkTtlSeconds || 300,
      columns: columns.map((c) => ({
        ...c,
        templateKey: (c.templateKey || c.field || '').trim() || c.field,
      })),
    };
    return { ...base, ...override };
  };

  const issueSignedLinkCore = async () => {
    const ttl = cfgForm.getFieldValue('signedLinkTtlSeconds') || 300;
    const link = await createApiExcelExportLink(apiId, {
      ttlSeconds: ttl,
      ...buildParams(),
    });
    const abs = link.url?.startsWith('http')
      ? link.url
      : `${typeof window !== 'undefined' ? window.location.origin : ''}${link.url}`;
    setSignedLinkPreview(abs);
    const ok = await copyText(abs);
    message.success(
      ok
        ? `已签发并复制（${link.ttlSeconds || ttl}s，过期 ${link.expireAt || '-'}）`
        : `已签发（${link.ttlSeconds || ttl}s，过期 ${link.expireAt || '-'}）`,
    );
  };

  /** 保存导出配置到草稿；成功返回最新详情 */
  const saveColumnConfig = async (
    override?: Partial<ViewExportConfig>,
    silent?: boolean,
  ): Promise<FlowController | null> => {
    if (!apiId || !detail) return null;
    setSaving(true);
    const viewExportConfig = buildViewExportConfig(override);
    try {
      const res: any = await queryAutoApiConfigDetail(apiId);
      const latest: FlowController = res?.data || res;
      await updateAutoApiConfig(apiId, {
        ...latest,
        viewExportConfig: JSON.stringify(viewExportConfig),
      } as any);
      if (!silent) {
        message.success('导出配置已保存到草稿');
      }
      setDirty(false);
      const d = await loadDetail();
      return d;
    } catch (e: any) {
      const msg = e?.message || '';
      if (msg && !msg.includes('status code') && !msg.includes('VALIDATION_ERROR')) {
        message.error(msg || '保存失败');
      }
      return null;
    } finally {
      setSaving(false);
    }
  };

  /** 保存当前导出配置并发布 */
  const saveAndPublishExport = async (opts?: { quiet?: boolean }): Promise<boolean> => {
    if (!apiId) return false;
    if (!openExportEnabled) {
      message.warning('请先开启「对外 Excel 下载」');
      return false;
    }
    setPublishing(true);
    try {
      const afterSave = await saveColumnConfig(undefined, true);
      if (!afterSave) return false;

      const envCode = await confirmPublishWithGate({
        assetType: 'API',
        assetId: apiId,
        assetName: apiName || afterSave.name,
      });
      if (!envCode) return false;

      const alreadyPublished = Number(afterSave.publishStatus) === 1;
      const hide = message.loading(alreadyPublished ? '正在发布更新…' : '正在发布…', 0);
      try {
        if (alreadyPublished) {
          await republishApi(apiId, envCode);
        } else {
          await publishApi(apiId, envCode);
        }
        await loadDetail();
        onPublished?.();
        if (!opts?.quiet) {
          message.success(alreadyPublished ? '已保存并发布更新，对外导出已生效' : '已保存并发布，对外导出已生效');
        }
        return true;
      } catch (e: any) {
        const msg = e?.message || '';
        if (msg && !msg.includes('status code') && !msg.includes('VALIDATION_ERROR')) {
          message.error(msg || '发布失败');
        }
        return false;
      } finally {
        hide();
      }
    } finally {
      setPublishing(false);
    }
  };

  const handleIssueSignedLink = async () => {
    if (!apiId) return;
    if (!openExportEnabled) {
      message.warning('请先开启「对外 Excel 下载」');
      return;
    }
    if (!signedLinkEnabled) {
      message.warning('已关闭短期下载链');
      return;
    }
    setSigningLink(true);
    try {
      const needsPublishPipeline = !isPublished || dirty;
      if (needsPublishPipeline) {
        const ok = await saveAndPublishExport({ quiet: true });
        if (!ok) return;
      }
      await issueSignedLinkCore();
    } catch (e: any) {
      const msg = e?.message || '';
      if (msg && !msg.includes('status code') && !msg.includes('VALIDATION_ERROR')) {
        message.error(msg);
      }
    } finally {
      setSigningLink(false);
    }
  };

  const handleExport = async () => {
    if (!apiId) return;
    if (!exportEnabled) {
      message.warning('已关闭导出，请在「列配置」中开启');
      return;
    }
    setExporting(true);
    const hide = message.loading('正在导出 Excel…', 0);
    try {
      const meta = await exportApiDataExcel(apiId, { useDraft, ...buildParams() });
      const rowTip = meta.rows != null ? `（${meta.rows} 行）` : '';
      if (meta.fallback) {
        const label = FALLBACK_LABEL[meta.fallback] || meta.fallback;
        message.warning(meta.fallbackMessage || `已回退动态表头导出：${label}${rowTip}`);
      } else if (meta.exportMode === 'TEMPLATE') {
        message.success(`已按公司模板填充导出${rowTip}`);
      } else {
        message.success(`导出成功${rowTip}`);
      }
    } catch (e: any) {
      message.error(e?.message || '导出失败');
    } finally {
      hide();
      setExporting(false);
    }
  };

  const handleUploadTemplate = async (file: File) => {
    if (!apiId) return false;
    const name = (file.name || '').toLowerCase();
    if (name.endsWith('.xlsm') || name.endsWith('.xls') || name.endsWith('.xlsb')) {
      message.error('仅支持 .xlsx（禁止宏包 .xlsm）');
      return false;
    }
    if (!name.endsWith('.xlsx')) {
      message.error('仅支持 .xlsx 文件');
      return false;
    }
    if (file.size > 2 * 1024 * 1024) {
      message.error('模板不能超过 2MB');
      return false;
    }
    setUploading(true);
    try {
      const meta = await uploadApiExcelTemplate(apiId, file);
      setTemplateMeta(meta);
      cfgForm.setFieldsValue({ exportMode: 'TEMPLATE' });
      await saveColumnConfig({ exportMode: 'TEMPLATE', templateFileId: meta.id }, true);
      if (meta.warning) {
        message.warning(meta.warning);
      } else {
        message.success('模板已上传，导出模式已切为「模板填充」（发布后随快照生效）');
      }
    } catch (e: any) {
      message.error(e?.message || '上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const handleDeleteTemplate = async () => {
    if (!apiId) return;
    try {
      await deleteApiExcelTemplate(apiId);
      setTemplateMeta({ apiId, present: false });
      cfgForm.setFieldsValue({ exportMode: 'DYNAMIC' });
      await saveColumnConfig({ exportMode: 'DYNAMIC', templateFileId: undefined }, true);
      message.success('模板已删除，已回退为动态表头');
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  };

  const resetTemplateKeys = () => {
    setColumns((prev) => prev.map((c) => ({ ...c, templateKey: c.field })));
    markDirty();
    message.info('已将模板 key 重置为字段名');
  };

  const isObject = (result?.responseType || detail?.responseType || '').toUpperCase() === 'OBJECT';
  const tableColumns = (result?.columns || columns)
    .filter((c) => c.visible !== false)
    .map((c) => ({
      title: c.header || c.field,
      dataIndex: c.field,
      key: c.field,
      ellipsis: true,
      width: c.width ? c.width * 8 : 140,
    }));

  const dbOk = (detail?.serviceType || '').toUpperCase() === 'DB';
  const exportBtnLabel =
    exportMode === 'TEMPLATE' && templateMeta?.present
      ? '导出 Excel（模板）'
      : '导出 Excel（动态）';

  return (
    <Drawer
      title={`数据查看 · ${apiName || detail?.name || apiId}`}
      width={980}
      open={open}
      onClose={onClose}
      destroyOnClose
      styles={{
        body: {
          padding: '12px 16px',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
        },
      }}
      extra={
        <Space>
          <span>
            使用草稿{' '}
            <Switch
              checked={useDraft}
              onChange={(checked) => {
                setUseDraft(checked);
                runPreview(0, pageSize, checked);
              }}
              disabled={detail?.publishStatus !== 1}
            />
          </span>
          <Button onClick={() => runPreview(0, pageSize)} loading={loading} type="primary">
            查询
          </Button>
          <Tooltip
            title={
              !exportEnabled
                ? '导出已关闭'
                : exportMode === 'TEMPLATE' && !templateMeta?.present
                  ? '未上传模板，将按动态表头导出'
                  : undefined
            }
          >
            <Button
              icon={<ExportOutlined />}
              onClick={handleExport}
              disabled={!dbOk || !exportEnabled}
              loading={exporting}
            >
              {exportBtnLabel}
            </Button>
          </Tooltip>
        </Space>
      }
    >
      <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {!dbOk && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12, flexShrink: 0 }}
          message="当前仅支持 DB 模式（PAGE / LIST / OBJECT）接口"
        />
      )}
      {dirty && (
        <Alert
          type="warning"
          showIcon
          closable
          style={{ marginBottom: 12, flexShrink: 0 }}
          message="导出配置有未保存修改"
          action={
            <Button size="small" type="primary" loading={saving} onClick={() => saveColumnConfig()}>
              保存到草稿
            </Button>
          }
        />
      )}
      <Typography.Paragraph type="secondary" style={{ marginTop: 0, marginBottom: 8, flexShrink: 0 }}>
        业务发布 URL 仍只返回 JSON；此处为管理端旁路。列中文名优先读响应契约「中文名」；公司表头请用「导出模板」上传
        .xlsx（无需安装 Office）。
      </Typography.Paragraph>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}
        className="api-data-view-tabs"
        items={[
          {
            key: 'data',
            label: '数据',
            children: (
              <ApiDataViewDataTab
                paramFields={paramFields}
                paramForm={paramForm}
                result={result}
                loading={loading}
                page={page}
                pageSize={pageSize}
                setPage={setPage}
                setPageSize={setPageSize}
                runPreview={runPreview}
                isObject={isObject}
                columns={columns}
                tableColumns={tableColumns}
              />
            ),
          },
          {
            key: 'columns',
            label: '列配置',
            children: (
              <ApiDataViewColumnsTab
                cfgForm={cfgForm}
                columns={columns}
                setColumns={setColumns}
                markDirty={markDirty}
                saving={saving}
                saveColumnConfig={saveColumnConfig}
                detail={detail}
              />
            ),
          },
          {
            key: 'template',
            label: (
              <span>
                导出模板{' '}
                {templateMeta?.present ? (
                  <Tag color="success" style={{ marginInlineEnd: 0 }}>
                    已上传
                  </Tag>
                ) : (
                  <Tag style={{ marginInlineEnd: 0 }}>未上传</Tag>
                )}
              </span>
            ),
            children: (
              <ApiDataViewTemplateTab
                apiId={apiId}
                cfgForm={cfgForm}
                templateMeta={templateMeta}
                columns={columns}
                setColumns={setColumns}
                markDirty={markDirty}
                uploading={uploading}
                saving={saving}
                handleUploadTemplate={handleUploadTemplate}
                handleDeleteTemplate={handleDeleteTemplate}
                resetTemplateKeys={resetTemplateKeys}
                saveColumnConfig={saveColumnConfig}
              />
            ),
          },
          {
            key: 'open-export',
            label: (
              <span>
                对外下载{' '}
                {openExportEnabled ? (
                  <Tag color={isPublished && !dirty ? 'success' : 'warning'} style={{ marginInlineEnd: 0 }}>
                    {isPublished && !dirty ? '已生效' : isPublished ? '待发布更新' : '未发布'}
                  </Tag>
                ) : (
                  <Tag style={{ marginInlineEnd: 0 }}>默认关</Tag>
                )}
              </span>
            ),
            children: (
              <ApiDataViewOpenExportTab
                cfgForm={cfgForm}
                detail={detail}
                openExportEnabled={openExportEnabled}
                signedLinkEnabled={signedLinkEnabled}
                signedLinkPreview={signedLinkPreview}
                exportDirectUrl={exportDirectUrl}
                exportOpenUrl={exportOpenUrl}
                isPublished={isPublished}
                dirty={dirty}
                dbOk={dbOk}
                saving={saving}
                publishing={publishing}
                signingLink={signingLink}
                saveAndPublishExport={saveAndPublishExport}
                handleIssueSignedLink={handleIssueSignedLink}
                saveColumnConfig={saveColumnConfig}
                handleCopyUrl={handleCopyUrl}
              />
            ),
          },
        ]}
      />
      </div>
      <style>{`
        .api-data-view-tabs .ant-tabs-content-holder {
          flex: 1;
          min-height: 0;
          overflow: auto;
        }
        .api-data-view-tabs .ant-tabs-content,
        .api-data-view-tabs .ant-tabs-tabpane {
          height: 100%;
        }
        .api-data-view-tabs.ant-tabs {
          overflow: hidden;
        }
      `}</style>
    </Drawer>
  );
};

export default ApiDataViewDrawer;
