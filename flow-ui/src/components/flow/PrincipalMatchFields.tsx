import {
  buildCatalogTree,
  catalogDimensionEnabled,
  catalogItemsHaveTree,
  catalogItemToOption,
  catalogSelectItems,
  getHostIdentityCatalog,
  HOST_IDENTITY_CATALOG_CHANGED,
  searchHostIdentityCatalog,
  type HostCatalogDimension,
  type HostCatalogItem,
  type HostIdentityCatalog,
} from '@/services/flow/hostIdentityCatalog';
import {
  EMPTY_PRINCIPAL_MATCH,
  MAX_ACCESS_RULES,
  groupFieldActionRows,
  matchDimensionCount,
  splitFieldNames,
  type FieldAction,
  type PrincipalKind,
  type PrincipalMatch,
} from '@/utils/principalMatch';
import { MinusCircleOutlined, PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Col, Input, Radio, Row, Select, Switch, Tag, Tooltip, TreeSelect } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import './PrincipalMatchFields.less';

type Patch<T> = (partial: Partial<T>) => void;

const MATCH_DIMS: {
  key: HostCatalogDimension;
  label: string;
  valueKey: 'userTypes' | 'roles' | 'permissions' | 'deptIds' | 'userIds';
}[] = [
  { key: 'USER_TYPE', label: '用户类型', valueKey: 'userTypes' },
  { key: 'ROLE', label: '角色', valueKey: 'roles' },
  { key: 'PERMISSION', label: '权限', valueKey: 'permissions' },
  { key: 'DEPT', label: '部门', valueKey: 'deptIds' },
  { key: 'USER', label: '指定用户', valueKey: 'userIds' },
];

function knownCatalogValues(items: { value?: string }[]): Set<string> {
  return new Set(
    items
      .map((i) =>
        String(i.value || '')
          .trim()
          .toLowerCase(),
      )
      .filter(Boolean),
  );
}

function renderSourceTag(
  known: Set<string>,
  props: {
    label?: React.ReactNode;
    value?: string | number;
    closable?: boolean;
    onClose?: (e?: React.MouseEvent<HTMLElement>) => void;
  },
) {
  const fromCatalog = known.has(
    String(props.value ?? '')
      .trim()
      .toLowerCase(),
  );
  return (
    <Tag
      color={fromCatalog ? 'blue' : 'gold'}
      closable={props.closable}
      onClose={props.onClose}
      onMouseDown={(e) => {
        e.preventDefault();
        e.stopPropagation();
      }}
      title={fromCatalog ? '从身份目录选择' : '手输码，按该字符串匹配'}
      style={{ marginInlineEnd: 4 }}
    >
      {props.label}
    </Tag>
  );
}

const CatalogTagSelect: React.FC<{
  dimension: HostCatalogDimension;
  catalog: HostIdentityCatalog | null;
  value?: string[];
  onChange: (next: string[]) => void;
  placeholder?: string;
}> = ({ dimension, catalog, value, onChange, placeholder }) => {
  const slice = catalog?.dimensions?.[dimension];
  const searchable = !!slice?.searchable;
  const [remoteItems, setRemoteItems] = useState<HostCatalogItem[]>([]);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestSeqRef = useRef(0);
  const loadedOnce = useRef(false);

  const catalogItems = useMemo(
    () => catalogSelectItems(catalog, dimension, remoteItems),
    [catalog, dimension, remoteItems],
  );
  const options = useMemo(
    () => catalogItems.map(catalogItemToOption),
    [catalogItems],
  );
  const knownValues = useMemo(
    () => knownCatalogValues(catalogItems),
    [catalogItems],
  );

  const runSearch = (keyword: string) => {
    const requestId = ++requestSeqRef.current;
    searchHostIdentityCatalog(dimension, keyword).then((items) => {
      if (requestId === requestSeqRef.current) {
        setRemoteItems(items);
      }
    });
  };

  useEffect(
    () => () => {
      requestSeqRef.current += 1;
      if (timerRef.current) clearTimeout(timerRef.current);
    },
    [],
  );

  return (
    <Select
      mode="tags"
      size="small"
      options={options}
      placeholder={placeholder || '选择或回车手输'}
      value={value || []}
      tokenSeparators={[',']}
      maxTagCount="responsive"
      showSearch
      filterOption={
        searchable
          ? false
          : (input, option) => {
              const q = (input || '').toLowerCase();
              return (
                String(option?.label || '')
                  .toLowerCase()
                  .includes(q) ||
                String(option?.value || '')
                  .toLowerCase()
                  .includes(q)
              );
            }
      }
      notFoundContent="无匹配项"
      tagRender={(tagProps) => renderSourceTag(knownValues, tagProps)}
      onSearch={
        searchable
          ? (keyword) => {
              if (timerRef.current) clearTimeout(timerRef.current);
              timerRef.current = setTimeout(() => runSearch(keyword), 300);
            }
          : undefined
      }
      onDropdownVisibleChange={
        searchable
          ? (open) => {
              if (open && !loadedOnce.current) {
                loadedOnce.current = true;
                runSearch('');
              }
            }
          : undefined
      }
      onChange={(v) => onChange(v)}
    />
  );
};

const CatalogDeptField: React.FC<{
  catalog: HostIdentityCatalog | null;
  deptIds?: string[];
  deptIncludeChildren?: boolean;
  onChange: (partial: { deptIds?: string[]; deptIncludeChildren?: boolean }) => void;
}> = ({ catalog, deptIds, deptIncludeChildren, onChange }) => {
  const [remoteItems, setRemoteItems] = useState<HostCatalogItem[]>([]);
  const items = catalogSelectItems(catalog, 'DEPT', remoteItems);
  const tree = catalogItemsHaveTree(items);
  const treeData = useMemo(() => buildCatalogTree(items), [items]);

  useEffect(() => {
    if (!catalogDimensionEnabled(catalog, 'DEPT')) return;
    if (items.length) return;
    searchHostIdentityCatalog('DEPT', '', 200).then(setRemoteItems);
  }, [catalog, items.length]);

  if (!tree) {
    return (
      <>
        <span>部门</span>
        <CatalogTagSelect
          dimension="DEPT"
          catalog={catalog}
          value={deptIds}
          onChange={(next) => onChange({ deptIds: next })}
          placeholder="搜索或选择部门"
        />
      </>
    );
  }

  return (
    <>
      <div className="principal-match-field-head">
        <span>部门</span>
        <Switch
          size="small"
          checked={deptIncludeChildren !== false}
          checkedChildren="含下级"
          unCheckedChildren="仅本级"
          onChange={(checked) => onChange({ deptIncludeChildren: checked })}
        />
      </div>
      <TreeSelect
        size="small"
        style={{ width: '100%' }}
        treeData={treeData}
        multiple
        treeCheckable
        treeCheckStrictly
        showSearch
        allowClear
        treeDefaultExpandAll={items.length <= 40}
        treeNodeFilterProp="title"
        maxTagCount="responsive"
        placeholder="按组织树勾选部门"
        value={(deptIds || []).map((id) => ({
          value: id,
          label: items.find((item) => item.value === id)?.label || id,
        }))}
        tagRender={(tagProps) => renderSourceTag(knownCatalogValues(items), tagProps)}
        onChange={(vals) => {
          const ids = (Array.isArray(vals) ? vals : [])
            .map((v) =>
              v && typeof v === 'object' && 'value' in v
                ? String((v as { value: unknown }).value ?? '')
                : String(v ?? ''),
            )
            .map((s) => s.trim())
            .filter(Boolean);
          onChange({ deptIds: ids });
        }}
      />
    </>
  );
};

function leftoverDisabledLabels(rule: PrincipalMatch, catalog: HostIdentityCatalog): string[] {
  return MATCH_DIMS.filter(
    (d) =>
      !catalogDimensionEnabled(catalog, d.key) &&
      Array.isArray(rule[d.valueKey]) &&
      (rule[d.valueKey] as string[]).length > 0,
  ).map((d) => d.label);
}

export function PrincipalMatchFields<T extends PrincipalMatch>(props: {
  value?: T;
  onChange?: (next: T) => void;
  catalog?: HostIdentityCatalog | null;
  extra?: (rule: T, patch: Patch<T>) => React.ReactNode;
}) {
  const rule = (props.value || EMPTY_PRINCIPAL_MATCH) as T;
  const catalog = props.catalog ?? null;
  const patch: Patch<T> = (partial) => {
    props.onChange?.({ ...rule, ...partial });
  };

  const catalogReady = catalog !== null;
  const enabledDims = MATCH_DIMS.filter((d) =>
    catalogDimensionEnabled(catalog, d.key),
  );
  const enabledKeys = enabledDims.map((d) => d.key);
  const isMatch = rule.principals === 'MATCH';
  const isOpen = rule.principals === 'OPEN_APP';
  const showMatchMode =
    isMatch && catalogReady && matchDimensionCount(rule, enabledKeys) > 1;
  const leftovers =
    isMatch && catalogReady && catalog
      ? leftoverDisabledLabels(rule, catalog)
      : [];

  return (
    <>
      <div className="principal-match-row">
        <span className="principal-match-label">身份</span>
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          size="small"
          value={rule.principals}
          options={[
            { label: '任何已登录', value: 'ANY_AUTHENTICATED' },
            { label: '指定身份', value: 'MATCH' },
            { label: '开放应用', value: 'OPEN_APP' },
          ]}
          onChange={(e) => {
            const principals = e.target.value as PrincipalKind;
            const next: Partial<T> = { principals };
            if (principals !== 'MATCH') {
              next.userTypes = [];
              next.roles = [];
              next.permissions = [];
              next.deptIds = [];
              next.match = 'ALL';
            }
            if (principals !== 'OPEN_APP' || rule.principals !== 'OPEN_APP') {
              next.userIds = [];
            }
            patch(next);
          }}
        />
      </div>
      {isOpen ? (
        <Select
          mode="tags"
          size="small"
          style={{ width: '100%', marginBottom: 8 }}
          placeholder="指定 AppKey，留空=全部开放应用"
          value={rule.userIds}
          tokenSeparators={[',']}
          maxTagCount="responsive"
          onChange={(v) => patch({ userIds: v } as Partial<T>)}
        />
      ) : null}
      {isMatch ? (
        <Row gutter={[12, 0]}>
          {showMatchMode ? (
            <Col span={24}>
              <div className="principal-match-row">
                <span className="principal-match-label">多维度组合</span>
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  size="small"
                  value={rule.match || 'ALL'}
                  options={[
                    { label: '全部满足', value: 'ALL' },
                    { label: '任一满足', value: 'ANY' },
                  ]}
                  onChange={(e) => patch({ match: e.target.value } as Partial<T>)}
                />
              </div>
            </Col>
          ) : null}
          {catalogReady && enabledDims.length === 0 ? (
            <Col span={24}>
              <div className="principal-match-hint">
                尚未在
                <Button
                  type="link"
                  size="small"
                  style={{ padding: '0 4px' }}
                  onClick={() => history.push('/sys-host?tab=catalog')}
                >
                  平台设置 → 宿主机配置
                </Button>
                启用任何身份维度，这里没有可勾选的匹配条件。
              </div>
            </Col>
          ) : null}
          {enabledDims.map((d) => (
            <Col span={12} key={d.key}>
              {d.key === 'DEPT' ? (
                <div className="principal-match-field">
                  <CatalogDeptField
                    catalog={catalog}
                    deptIds={rule.deptIds}
                    deptIncludeChildren={rule.deptIncludeChildren}
                    onChange={(partial) => patch(partial as Partial<T>)}
                  />
                </div>
              ) : (
                <div className="principal-match-field">
                  <span>{d.label}</span>
                  <CatalogTagSelect
                    dimension={d.key}
                    catalog={catalog}
                    value={rule[d.valueKey] as string[] | undefined}
                    onChange={(next) =>
                      patch({ [d.valueKey]: next } as Partial<T>)
                    }
                  />
                </div>
              )}
            </Col>
          ))}
          {leftovers.length ? (
            <Col span={24}>
              <div className="principal-match-hint">
                本规则还保存了已停用维度（{leftovers.join('、')}
                ）的条件，运行时仍会匹配。到宿主机配置重新启用以编辑。
              </div>
            </Col>
          ) : null}
        </Row>
      ) : null}
      {props.extra ? props.extra(rule, patch) : null}
    </>
  );
}

export function PrincipalMatchRuleList<T extends PrincipalMatch>(props: {
  value?: T[];
  onChange?: (rules: T[]) => void;
  createEmpty: () => T;
  description?: React.ReactNode;
  addText?: string;
  max?: number;
  extra?: (rule: T, patch: Patch<T>, index: number) => React.ReactNode;
  accent?: 'privacy' | 'ingress' | 'oss';
}) {
  const [catalog, setCatalog] = useState<HostIdentityCatalog | null>(null);
  const rules = Array.isArray(props.value) ? props.value : [];
  const max = props.max ?? MAX_ACCESS_RULES;

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      getHostIdentityCatalog().then((data) => {
        if (!cancelled) setCatalog(data);
      });
    };
    load();
    const onChanged = () => load();
    window.addEventListener(HOST_IDENTITY_CATALOG_CHANGED, onChanged);
    return () => {
      cancelled = true;
      window.removeEventListener(HOST_IDENTITY_CATALOG_CHANGED, onChanged);
    };
  }, []);

  const setAt = (index: number, next: T) => {
    const copy = rules.slice();
    copy[index] = next;
    props.onChange?.(copy);
  };

  return (
    <div className={`principal-match-rules is-${props.accent || 'ingress'}`}>
      {props.description ? (
        <div className="principal-match-desc">{props.description}</div>
      ) : null}
      {rules.map((rule, index) => (
        <div key={index} className="principal-match-card">
          <div className="principal-match-card-head">
            <Input
              placeholder={`规则 ${index + 1}`}
              maxLength={32}
              style={{ width: 160 }}
              value={rule?.name || ''}
              onChange={(e) => setAt(index, { ...rule, name: e.target.value })}
            />
            <Button
              type="text"
              danger
              size="small"
              icon={<MinusCircleOutlined />}
              onClick={() => props.onChange?.(rules.filter((_, i) => i !== index))}
            />
          </div>
          <PrincipalMatchFields
            key={index}
            value={rule}
            catalog={catalog}
            onChange={(next) => setAt(index, next)}
            extra={(r, patch) => props.extra?.(r, patch, index)}
          />
        </div>
      ))}
      <Button
        type="dashed"
        block
        size="small"
        icon={<PlusOutlined />}
        disabled={rules.length >= max}
        onClick={() => {
          const empty = props.createEmpty();
          if (!empty.name) empty.name = `规则 ${rules.length + 1}`;
          props.onChange?.([...rules, empty]);
        }}
      >
        {props.addText || '添加规则'}
        {rules.length >= max ? `（最多 ${max} 条）` : ''}
      </Button>
    </div>
  );
}

export function FieldActionEditor(props: {
  value?: Record<string, FieldAction>;
  onChange?: (next: Record<string, FieldAction>) => void;
}) {
  type Row = { names: string[]; action: FieldAction };
  const committed = groupFieldActionRows(props.value);
  const committedKey = JSON.stringify(props.value || {});
  const [drafts, setDrafts] = useState<Row[]>([]);

  useEffect(() => {
    setDrafts((prev) => prev.filter((row) => row.names.length === 0));
  }, [committedKey]);

  const rows = [...committed, ...drafts];

  const commit = (next: Row[]) => {
    const named: Row[] = [];
    const empty: Row[] = [];
    next.forEach((row) => {
      const names = Array.from(
        new Set((row.names || []).map((n) => String(n).trim()).filter(Boolean)),
      );
      if (names.length) named.push({ names, action: row.action });
      else empty.push({ names: [], action: row.action });
    });
    setDrafts(empty);
    const map: Record<string, FieldAction> = {};
    named.forEach((row) => {
      row.names.forEach((name) => {
        map[name] = row.action;
      });
    });
    props.onChange?.(map);
  };

  return (
    <div className="principal-match-fields-editor">
      <div className="principal-match-label" style={{ marginBottom: 6 }}>
        字段动作（可选）
        <Tooltip title="和脱敏规则一样：回车或逗号生成标签。去掉对任意 JSON 键生效；明文/脱敏只作用于隐私字段（去后缀后的名字，如 loginPhone）。">
          <QuestionCircleOutlined className="principal-match-help" />
        </Tooltip>
      </div>
      {rows.map((row, i) => (
        <div key={`${row.action}-${i}`} className="principal-match-row">
          <Select
            mode="tags"
            size="small"
            tokenSeparators={[',', '，']}
            placeholder="createBy、updateBy"
            style={{ flex: 1, minWidth: 160 }}
            value={row.names}
            onChange={(names) => {
              const copy = rows.slice();
              copy[i] = {
                names: (names as string[]).flatMap((n) => splitFieldNames(n)),
                action: row.action,
              };
              commit(copy);
            }}
          />
          <Select
            size="small"
            style={{ width: 100 }}
            value={row.action}
            options={[
              { label: '明文', value: 'REVEAL' },
              { label: '脱敏', value: 'MASK' },
              { label: '去掉', value: 'DROP' },
            ]}
            onChange={(v) => {
              const copy = rows.slice();
              copy[i] = { names: row.names, action: v };
              commit(copy);
            }}
          />
          <Button
            type="text"
            size="small"
            danger
            htmlType="button"
            icon={<MinusCircleOutlined />}
            onClick={() => commit(rows.filter((_, idx) => idx !== i))}
          />
        </div>
      ))}
      <Button
        type="link"
        size="small"
        htmlType="button"
        style={{ padding: 0 }}
        icon={<PlusOutlined />}
        onClick={() => commit([...rows, { names: [], action: 'DROP' }])}
      >
        按字段覆盖
      </Button>
    </div>
  );
}
