/**
 * ControllerFormHeader
 * ─────────────────────────────────────────────────────────────────────────────
 * ControllerForm 顶部 Header 区域：路径/方法/名称输入 + 右侧工具栏。
 */
import React, { useMemo } from 'react';
import {
  Button, Dropdown, Input, Popover, Select, Space, Tag, Tooltip,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  SaveOutlined, CloseOutlined, CopyOutlined, CloudUploadOutlined,
  CloudDownloadOutlined, RollbackOutlined, FileTextOutlined, CodeOutlined,
  ImportOutlined, HistoryOutlined, DatabaseOutlined, ExperimentOutlined,
  ToolOutlined, DownOutlined,
} from '@ant-design/icons';
import { message } from 'antd';
import ApiPathInput from './ApiPathInput';
import { buildApiCurl, copyText, openPublishedApiDocCenter } from '@/utils/apiDocsActions';
import { supportsApiDataView } from '@/services/flow/flowController';
import type { HostApiRoute } from '@/services/flow/flowController';
import { rollbackApi, unpublishApi } from '@/services/flow/flowController';

export type InterceptMode = 'REPLACE' | 'WRAP';

const METHOD_COLORS: Record<string, string> = {
  GET: '#52c41a',
  POST: '#1677ff',
  PUT: '#faad14',
  DELETE: '#ff4d4f',
  PATCH: '#722ed1',
};

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

export interface ControllerFormHeaderProps {
  /** 拦截模式 */
  interceptMode: InterceptMode;
  /** 当前 HTTP 方法 */
  method: string;
  onMethodChange: (method: string) => void;
  /** 当前 URL / 业务路径 */
  url: string;
  onUrlChange: (url: string) => void;
  /** 接口名称 */
  name: string;
  onNameChange: (name: string) => void;
  /** 表单提交时校验标记 */
  submitAttempted: boolean;
  /** URL 冲突提示 */
  urlConflictMsg: string | null;
  /** 发布状态 */
  publishStatus: 0 | 1;
  /** WRAP 模式下的宿主路由列表 */
  hostRoutes: HostApiRoute[];
  hostRoutesLoading: boolean;
  /** 当前编辑的接口 ID */
  valuesId?: string;
  /** 是否编辑态 */
  isEdit: boolean;
  /** 是否存在未发布的变更 */
  hasUnpublishedChanges?: boolean;
  /** 引擎模式与响应类型，用于判断数据查看入口是否可用 */
  engineMode: string;
  responseType?: string;
  /** 静态 JSON 模式错误 */
  staticJsonError: string | null;
  /** 保存 / 发布 / 关闭 */
  onSubmit: () => void;
  onPublish: () => void;
  onCancel: () => void;
  /** 抽屉开关 */
  onOpenHistory: () => void;
  onOpenDataView: () => void;
  onOpenRegression: () => void;
  onOpenCurlImport: () => void;
  /** 提交成功后回调 */
  onSubmitSuccess: (success: boolean) => void;
  /** 渲染子元素，接收 title 和 extra */
  children: (title: React.ReactNode, extra: React.ReactNode) => React.ReactNode;
}

const ControllerFormHeader: React.FC<ControllerFormHeaderProps> = (props) => {
  const {
    interceptMode,
    method,
    onMethodChange,
    url,
    onUrlChange,
    name,
    onNameChange,
    submitAttempted,
    urlConflictMsg,
    publishStatus,
    hostRoutes,
    hostRoutesLoading,
    valuesId,
    isEdit,
    hasUnpublishedChanges,
    engineMode,
    responseType,
    staticJsonError,
    onSubmit,
    onPublish,
    onCancel,
    onOpenHistory,
    onOpenDataView,
    onOpenRegression,
    onOpenCurlImport,
    onSubmitSuccess,
  } = props;

  const headerCtrlSize = 'middle' as const;
  const headerCtrlHeight = 32;

  const hostRouteSelectValue = url?.trim() ? `${method} ${url.trim()}` : undefined;

  const hostRouteOptions = useMemo(() => {
    const opts = hostRoutes.map((r) => {
      const value = `${r.method} ${r.path}`;
      const takenByOther = !!r.managed && !!r.managedApiId && r.managedApiId !== valuesId;
      return {
        value,
        disabled: takenByOther,
        label: (
          <Space size={6} wrap={false}>
            <span style={{ color: METHOD_COLORS[r.method] || undefined, fontWeight: 700, fontFamily: 'monospace' }}>
              {r.method}
            </span>
            <span style={{ fontFamily: 'ui-monospace, Menlo, Consolas, monospace' }}>{r.path}</span>
            {takenByOther ? (
              <Tag color="default" style={{ margin: 0 }}>已纳管</Tag>
            ) : r.managed ? (
              <Tag color="blue" style={{ margin: 0 }}>当前</Tag>
            ) : null}
          </Space>
        ),
        searchText: `${r.method} ${r.path} ${r.handlerClass || ''} ${r.handlerMethod || ''} ${r.managedApiName || ''}`,
      };
    });
    if (hostRouteSelectValue && !opts.some((o) => o.value === hostRouteSelectValue)) {
      opts.unshift({
        value: hostRouteSelectValue,
        disabled: false,
        label: (
          <Space size={6}>
            <span style={{ fontFamily: 'monospace' }}>{hostRouteSelectValue}</span>
            <Tag style={{ margin: 0 }}>未在宿主扫描中</Tag>
          </Space>
        ),
        searchText: hostRouteSelectValue,
      });
    }
    return opts;
  }, [hostRoutes, hostRouteSelectValue, valuesId]);

  const headerTitle = (
    <Space.Compact
      className="yf-header-title-compact"
      style={{ display: 'flex', width: '100%', height: headerCtrlHeight }}
      size={headerCtrlSize}
    >
      {interceptMode === 'WRAP' ? (
        <Tooltip title="包裹模式请从宿主已有接口中选择路径（方法随选项带入）">
          <div className="yf-header-path-wrap" style={{ flex: 1, minWidth: 360, width: '100%' }}>
            <Select
              size={headerCtrlSize}
              showSearch
              allowClear
              loading={hostRoutesLoading}
              value={hostRouteSelectValue}
              placeholder="选择宿主接口，例如 GET /yu-demo/host-ping"
              status={submitAttempted && !url?.trim() ? 'error' : (urlConflictMsg ? 'error' : undefined)}
              style={{ width: '100%', height: headerCtrlHeight }}
              options={hostRouteOptions}
              optionFilterProp="searchText"
              filterOption={(input, option) => {
                const text = String((option as any)?.searchText || option?.value || '').toLowerCase();
                return text.includes(input.trim().toLowerCase());
              }}
              onChange={(v) => {
                if (!v) {
                  onUrlChange('');
                  return;
                }
                const idx = String(v).indexOf(' ');
                if (idx <= 0) {
                  onUrlChange(String(v));
                  return;
                }
                const nextMethod = String(v).slice(0, idx).trim().toUpperCase();
                const nextPath = String(v).slice(idx + 1).trim();
                onMethodChange(nextMethod);
                onUrlChange(nextPath);
                if (!name?.trim()) {
                  const short = nextPath.replace(/\//g, '_').replace(/^_ | _$/g, '').slice(0, 14);
                  onNameChange(`${nextMethod}_${short || 'host'}`.slice(0, 20));
                }
              }}
              notFoundContent={hostRoutesLoading ? '加载宿主路由…' : '未扫描到可纳管的宿主接口'}
            />
          </div>
        </Tooltip>
      ) : (
        <>
          <Select
            size={headerCtrlSize}
            value={method}
            onChange={onMethodChange}
            style={{ width: 116, height: headerCtrlHeight, flexShrink: 0 }}
            popupMatchSelectWidth={false}
          >
            {METHOD_OPTIONS.map((m) => (
              <Select.Option key={m} value={m}>
                <span style={{ color: METHOD_COLORS[m], fontWeight: 700, fontFamily: 'monospace' }}>{m}</span>
              </Select.Option>
            ))}
          </Select>
          <Popover
            content={
              urlConflictMsg ||
              (publishStatus === 1
                ? '可修改草稿路径；重新发布后线上路由才会切换'
                : undefined)
            }
            open={!!urlConflictMsg}
            placement="bottomLeft"
            overlayInnerStyle={urlConflictMsg ? { color: '#ff4d4f' } : undefined}
          >
            <div className="yf-header-path-wrap" style={{ flex: 1, minWidth: 280, width: '100%' }}>
              <ApiPathInput
                size={headerCtrlSize}
                value={url}
                onChange={onUrlChange}
                status={submitAttempted && !url?.trim() ? 'error' : (urlConflictMsg ? 'error' : undefined)}
                style={{ flex: 1, width: '100%', height: headerCtrlHeight, minWidth: 0 }}
              />
            </div>
          </Popover>
        </>
      )}
      <Input
        size={headerCtrlSize}
        value={name}
        onChange={(e) => onNameChange(e.target.value)}
        placeholder="接口名称"
        style={{ width: 280, height: headerCtrlHeight, flexShrink: 0 }}
        status={submitAttempted && !name?.trim() ? 'error' : undefined}
      />
    </Space.Compact>
  );

  const dataViewSupported = supportsApiDataView({ serviceType: engineMode, responseType });

  const toolMenuItems: MenuProps['items'] = [
    ...(isEdit
      ? [
          {
            key: 'history',
            icon: <HistoryOutlined />,
            label: '历史版本',
            disabled: !valuesId,
            onClick: () => onOpenHistory(),
          },
        ]
      : []),
    {
      key: 'docs',
      icon: <FileTextOutlined />,
      label: 'API 文档',
      onClick: () => {
        const r = openPublishedApiDocCenter({
          apiId: valuesId,
          publishStatus,
        });
        if (r.reason === 'unpublished') {
          message.warning('请先发布该接口后再查看文档');
        } else if (r.reason === 'missing_id') {
          message.warning('请先保存接口后再查看文档');
        }
      },
    },
    {
      key: 'curl-import',
      icon: <ImportOutlined />,
      label: '从 cURL 导入',
      onClick: () => onOpenCurlImport(),
    },
    {
      key: 'curl',
      icon: <CodeOutlined />,
      label: '复制 cURL',
      onClick: async () => {
        const curl = buildApiCurl(method, url);
        const ok = await copyText(curl);
        if (ok) message.success('cURL 已复制');
        else message.error('复制失败');
      },
    },
    ...(isEdit && valuesId
      ? [
          { type: 'divider' as const },
          {
            key: 'data-view',
            icon: <DatabaseOutlined />,
            label: '数据查看 / Excel',
            disabled: !dataViewSupported,
            title: dataViewSupported
              ? undefined
              : '仅 DB 模式且响应类型为 PAGE / LIST / OBJECT 可用',
            onClick: () => onOpenDataView(),
          },
          {
            key: 'regression',
            icon: <ExperimentOutlined />,
            label: '回归测试',
            onClick: () => onOpenRegression(),
          },
        ]
      : []),
    ...(isEdit && publishStatus === 1 && hasUnpublishedChanges
      ? [
          { type: 'divider' as const },
          {
            key: 'rollback',
            icon: <RollbackOutlined />,
            label: '回滚草稿到线上',
            danger: true,
            onClick: async () => {
              if (!valuesId) return;
              const hide = message.loading('正在回滚...');
              try {
                await rollbackApi(valuesId);
                hide();
                message.success('已回滚到线上版本');
                onSubmitSuccess(true);
              } catch {
                hide();
              }
            },
          },
        ]
      : []),
    ...(isEdit && publishStatus === 1
      ? [
          { type: 'divider' as const },
          {
            key: 'unpublish',
            icon: <CloudDownloadOutlined />,
            label: '下线',
            danger: true,
            onClick: async () => {
              if (!valuesId) return;
              const hide = message.loading('正在下线...');
              try {
                await unpublishApi(valuesId);
                hide();
                message.success('下线成功');
                onSubmitSuccess(true);
              } catch {
                hide();
              }
            },
          },
        ]
      : []),
  ];

  const headerExtra = (
    <Space
      className="yf-header-extra-actions"
      size={8}
      align="center"
      wrap={false}
      style={{ height: headerCtrlHeight }}
    >
      <Tag
        color={publishStatus === 1 ? 'success' : 'default'}
        style={{
          margin: 0,
          height: headerCtrlHeight,
          lineHeight: `${headerCtrlHeight}px`,
          paddingInline: 10,
          fontSize: 13,
          borderRadius: 6,
          display: 'inline-flex',
          alignItems: 'center',
          boxSizing: 'border-box',
        }}
      >
        {publishStatus === 1 ? '● 已发布' : '○ 未发布'}
      </Tag>

      <Dropdown menu={{ items: toolMenuItems }}>
        <Button size={headerCtrlSize} icon={<ToolOutlined />}>
          工具 <DownOutlined style={{ fontSize: 10 }} />
        </Button>
      </Dropdown>

      <Tooltip title={staticJsonError || undefined}>
        <span style={{ display: 'inline-flex', alignItems: 'center', height: headerCtrlHeight }}>
          <Button
            size={headerCtrlSize}
            icon={<SaveOutlined />}
            disabled={!!staticJsonError}
            onClick={onSubmit}
          >
            保存草稿
          </Button>
        </span>
      </Tooltip>
      {(publishStatus === 0 || isEdit) && (
        <Tooltip title={staticJsonError || undefined}>
          <span style={{ display: 'inline-flex', alignItems: 'center', height: headerCtrlHeight }}>
            <Button
              size={headerCtrlSize}
              type="primary"
              icon={<CloudUploadOutlined />}
              disabled={!!staticJsonError}
              onClick={onPublish}
            >
              {publishStatus === 1 ? '保存并发布' : '发布上线'}
            </Button>
          </span>
        </Tooltip>
      )}

      <Tooltip title="关闭">
        <Button
          size={headerCtrlSize}
          type="text"
          icon={<CloseOutlined />}
          onClick={onCancel}
          aria-label="关闭"
          style={{ width: headerCtrlHeight, paddingInline: 0 }}
        />
      </Tooltip>
    </Space>
  );

  return <>{props.children(headerTitle, headerExtra)}</>;
};

export default ControllerFormHeader;
