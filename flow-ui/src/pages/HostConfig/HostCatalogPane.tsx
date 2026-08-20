import {
  previewHostCatalog,
  type HostCatalogApiMeta,
  type HostCatalogDimBinding,
} from '@/services/flow/hostConfig';
import {
  buildCatalogTree,
  catalogItemToOption,
  catalogItemsHaveTree,
  type HostCatalogDimension,
  type HostCatalogItem,
} from '@/services/flow/hostIdentityCatalog';
import { ApartmentOutlined, RightOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Input,
  Select,
  Switch,
  Tag,
  Tooltip,
  TreeSelect,
  Typography,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';

export const HOST_CATALOG_DIMS: {
  key: HostCatalogDimension;
  title: string;
  hint: string;
}[] = [
  { key: 'USER_TYPE', title: '用户类型', hint: '写入策略的 userTypes' },
  { key: 'ROLE', title: '角色', hint: '宿主角色码，不是 Flow RBAC' },
  { key: 'PERMISSION', title: '权限', hint: '宿主权限码；* 视为超管' },
  { key: 'DEPT', title: '部门', hint: '有 parentId 时按树勾选，默认含下级' },
  { key: 'USER', title: '用户', hint: '用户 ID；量大时建议远程搜' },
];

export const emptyBinding = (): HostCatalogDimBinding => ({
  enabled: false,
  valueField: 'value',
  labelField: 'label',
  parentField: 'parentId',
  searchable: false,
});

const FIELD_ADDON: React.CSSProperties = {
  display: 'inline-block',
  width: 48,
  textAlign: 'center',
};

export type CatalogDimRow = {
  key: HostCatalogDimension;
  title: string;
  hint: string;
  bind: HostCatalogDimBinding;
  meta?: HostCatalogApiMeta;
};

const PreviewSelect: React.FC<{
  dimension: HostCatalogDimension;
  enabled: boolean;
  stale?: boolean;
}> = ({ dimension, enabled, stale }) => {
  const [items, setItems] = useState<HostCatalogItem[]>([]);
  const [loading, setLoading] = useState(false);
  const loaded = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestSeq = useRef(0);

  const run = useCallback(
    async (keyword: string) => {
      const seq = ++requestSeq.current;
      setLoading(true);
      try {
        const next = await previewHostCatalog(
          dimension,
          keyword,
          dimension === 'DEPT' ? 200 : 50,
        );
        if (seq === requestSeq.current) setItems(next);
      } catch {
        if (seq === requestSeq.current) setItems([]);
      } finally {
        if (seq === requestSeq.current) setLoading(false);
      }
    },
    [dimension],
  );

  useEffect(
    () => () => {
      requestSeq.current += 1;
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const tree = useMemo(() => catalogItemsHaveTree(items), [items]);
  const treeData = useMemo(() => buildCatalogTree(items), [items]);
  const options = useMemo(() => items.map(catalogItemToOption), [items]);
  const shared = {
    size: 'middle' as const,
    style: { width: '100%' },
    placeholder: stale
      ? '保存后可预览'
      : enabled
        ? tree
          ? '预览组织树'
          : '预览下拉'
        : '启用后可预览',
    disabled: !enabled || stale,
    loading,
    showSearch: true,
    maxTagCount: 'responsive' as const,
    notFoundContent: loading ? '加载中…' : '无匹配项',
    onDropdownVisibleChange: (open: boolean) => {
      if (open && !loaded.current) {
        loaded.current = true;
        run('');
      }
    },
  };

  if (tree) {
    return (
      <TreeSelect
        {...shared}
        multiple
        treeCheckable
        treeCheckStrictly
        treeDefaultExpandAll={items.length <= 40}
        treeNodeFilterProp="title"
        treeData={treeData}
      />
    );
  }

  return (
    <Select
      {...shared}
      mode="tags"
      options={options}
      filterOption={false}
      tokenSeparators={[',']}
      onSearch={(value) => {
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => run(value), 300);
      }}
    />
  );
};

const DimStatus: React.FC<{ row: CatalogDimRow }> = ({ row }) => {
  const published = row.meta?.publishStatus === 1;
  return (
    <div className="host-dim-tags">
      {row.bind.enabled ? (
        <Tag color="success">已启用</Tag>
      ) : (
        <Tag>未启用</Tag>
      )}
      {published ? <Tag color="success">已发布</Tag> : <Tag>未发布</Tag>}
      {row.meta?.hasUnpublishedChanges ? <Tag color="warning">有草稿</Tag> : null}
      <Tag bordered={false}>{row.meta?.serviceType || 'JSON'}</Tag>
    </div>
  );
};

type Props = {
  rows: CatalogDimRow[];
  activeDim: HostCatalogDimension | null;
  spiOverride: boolean;
  dirty: boolean;
  canCompose: boolean;
  writeDisabled: boolean;
  rawPreview: Record<string, string>;
  onSelectDim: (dim: HostCatalogDimension) => void;
  patch: (key: HostCatalogDimension, partial: Partial<HostCatalogDimBinding>) => void;
  openEditor: (meta?: HostCatalogApiMeta) => void;
  loadRaw: (dim: HostCatalogDimension) => void;
};

const HostCatalogPane: React.FC<Props> = ({
  rows,
  activeDim,
  spiOverride,
  dirty,
  canCompose,
  writeDisabled,
  rawPreview,
  onSelectDim,
  patch,
  openEditor,
  loadRaw,
}) => {
  const enabledCount = rows.filter((row) => row.bind.enabled).length;
  const publishedCount = rows.filter((row) => row.meta?.publishStatus === 1).length;
  const active = activeDim ? rows.find((r) => r.key === activeDim) : null;

  return (
    <div className={`host-catalog-pane ${active ? 'is-detail' : 'is-overview'}`}>
      <Alert
        type={spiOverride ? 'warning' : 'info'}
        showIcon
        className="host-catalog-notice"
        message={
          spiOverride
            ? '当前进程已注入 Java SPI，运行时优先使用 SPI；本页配置保留为降级方案。'
            : '启用的维度会出现在 OSS、接口/目录「访问规则」的勾选里；未发布目录使用草稿数据。'
        }
      />

      {!active ? (
        <>
          <div className="host-catalog-summary">
            <div>
              <span>已启用维度</span>
              <strong>{enabledCount}</strong>
              <small>/ {HOST_CATALOG_DIMS.length}</small>
            </div>
            <div>
              <span>已发布目录</span>
              <strong>{publishedCount}</strong>
              <small>/ {HOST_CATALOG_DIMS.length}</small>
            </div>
            <div>
              <span>当前数据源</span>
              <strong className="is-text">{spiOverride ? 'Java SPI' : '保留接口'}</strong>
            </div>
          </div>

          <div className="host-dim-list">
            {rows.map((row) => (
              <div
                key={row.key}
                role="button"
                tabIndex={0}
                className="host-dim-list-item"
                onClick={() => onSelectDim(row.key)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onSelectDim(row.key);
                  }
                }}
              >
                <div className="host-dim-list-main">
                  <div className="host-dim-title-row">
                    <div className="host-dim-title">{row.title}</div>
                    <Typography.Text type="secondary" className="host-dim-hint">
                      {row.hint}
                    </Typography.Text>
                  </div>
                  <DimStatus row={row} />
                </div>
                <div
                  className="host-dim-list-side"
                  onClick={(e) => e.stopPropagation()}
                  onKeyDown={(e) => e.stopPropagation()}
                >
                  <Tooltip title="关闭后，OSS / 接口访问规则表单不再展示该维；已保存的约束仍会匹配，避免策略被悄悄放宽">
                    <span className="host-dim-enable">
                      启用
                      <Switch
                        size="small"
                        checked={!!row.bind.enabled}
                        disabled={writeDisabled}
                        onChange={(v) => patch(row.key, { enabled: v })}
                      />
                    </span>
                  </Tooltip>
                  <RightOutlined className="host-dim-chevron" />
                </div>
              </div>
            ))}
          </div>
        </>
      ) : (
        <DimEditor
          row={active}
          dirty={dirty}
          canCompose={canCompose}
          writeDisabled={writeDisabled}
          rawPreview={rawPreview[active.key]}
          patch={patch}
          openEditor={openEditor}
          loadRaw={loadRaw}
        />
      )}
    </div>
  );
};

const DimEditor: React.FC<{
  row: CatalogDimRow;
  dirty: boolean;
  canCompose: boolean;
  writeDisabled: boolean;
  rawPreview?: string;
  patch: (key: HostCatalogDimension, partial: Partial<HostCatalogDimBinding>) => void;
  openEditor: (meta?: HostCatalogApiMeta) => void;
  loadRaw: (dim: HostCatalogDimension) => void;
}> = ({ row, dirty, canCompose, writeDisabled, rawPreview, patch, openEditor, loadRaw }) => (
  <div className="host-dim-editor">
    <div className="host-dim-editor-head">
      <div>
        <div className="host-dim-title">{row.title}</div>
        <Typography.Text type="secondary" className="host-dim-hint">
          {row.hint}
        </Typography.Text>
        <DimStatus row={row} />
        {row.meta?.url ? <code className="host-dim-url is-full">{row.meta.url}</code> : null}
      </div>
      <Tooltip
        title={
          !canCompose
            ? '编排保留接口需要宿主机配置管理和接口写入权限'
            : '编辑该维度的保留接口'
        }
      >
        <Button
          icon={<ApartmentOutlined />}
          disabled={!canCompose}
          onClick={() => openEditor(row.meta)}
        >
          编排
        </Button>
      </Tooltip>
    </div>

    {row.bind.enabled && row.meta?.publishStatus !== 1 ? (
      <Alert
        type="warning"
        showIcon
        className="host-catalog-notice"
        message="该维度已启用但尚未发布，当前运行时使用草稿数据。"
      />
    ) : null}

    <div className="host-dim-section">
      <div className="host-dim-section-title">目录绑定</div>
      <div className="host-binding-switches">
        <Tooltip title="关闭后，OSS / 接口访问规则表单不再展示该维；已保存的约束仍会匹配，避免策略被悄悄放宽">
          <span>
            启用
            <Switch
              checked={!!row.bind.enabled}
              disabled={writeDisabled}
              onChange={(v) => patch(row.key, { enabled: v })}
            />
          </span>
        </Tooltip>
        <Tooltip title="选项较多时打开，下拉按关键字查询">
          <span>
            远程搜索
            <Switch
              checked={!!row.bind.searchable}
              disabled={writeDisabled}
              onChange={(v) => patch(row.key, { searchable: v })}
            />
          </span>
        </Tooltip>
      </div>
      <div className={`host-field-grid ${row.key === 'DEPT' ? 'is-dept' : ''}`}>
        <Input
          addonBefore={<span style={FIELD_ADDON}>value</span>}
          value={row.bind.valueField}
          disabled={writeDisabled}
          onChange={(e) => patch(row.key, { valueField: e.target.value })}
        />
        <Input
          addonBefore={<span style={FIELD_ADDON}>label</span>}
          value={row.bind.labelField}
          disabled={writeDisabled}
          onChange={(e) => patch(row.key, { labelField: e.target.value })}
        />
        {row.key === 'DEPT' ? (
          <Input
            addonBefore={<span style={FIELD_ADDON}>parent</span>}
            value={row.bind.parentField || 'parentId'}
            disabled={writeDisabled}
            onChange={(e) => patch(row.key, { parentField: e.target.value })}
          />
        ) : null}
      </div>
    </div>

    <div className="host-dim-section">
      <div className="host-dim-section-title">数据预览</div>
      <PreviewSelect
        key={`${row.key}-${row.bind.valueField}-${row.bind.labelField}-${row.bind.parentField}`}
        dimension={row.key}
        enabled={!!row.bind.enabled}
        stale={dirty}
      />
    </div>

    <div className="host-dim-section">
      <div className="host-raw-toolbar">
        <div className="host-dim-section-title">接口原始结果</div>
        <Button size="small" disabled={dirty} onClick={() => loadRaw(row.key)}>
          刷新 JSON
        </Button>
      </div>
      <pre className="host-raw-pre">
        {rawPreview || '点击「刷新 JSON」查看原始返回值（最多 50 条）'}
      </pre>
    </div>
  </div>
);

export default HostCatalogPane;
