import {
  buildCatalogTree,
  catalogDimensionEnabled,
  catalogItemsHaveTree,
  catalogItemToOption,
  catalogSelectItems,
  catalogSlice,
  getHostIdentityCatalog,
  HostCatalogDimension,
  HostCatalogItem,
  HostIdentityCatalog,
  searchHostIdentityCatalog,
} from '@/services/flow/hostIdentityCatalog';
import {
  ProFormRadio,
  ProFormSelect,
  ProFormTreeSelect,
} from '@ant-design/pro-components';
import { Col, Form, Radio, Switch, Tag, Tooltip } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';

export type CallerPolicyFieldNames = {
  enabled: string;
  match: string;
  userTypes: string;
  roles: string;
  permissions: string;
  deptIds: string;
  deptIncludeChildren?: string;
  userIds: string;
};

export type CallerPolicyFieldsProps = {
  names: CallerPolicyFieldNames;
  enabled?: boolean;
  enabledLabel?: string;
  enabledExtra?: string;
  /** 说明改走 tooltip，减少纵向占位（OSS 抽屉） */
  compact?: boolean;
  /** 标题与开关同一行（左标题、右开关），用于 OSS 分区头 */
  title?: string;
  /** 返回 false 则保持关闭，不展开策略字段 */
  beforeEnable?: () => boolean;
};

const DIM_FIELDS: {
  key: HostCatalogDimension;
  nameKey: keyof CallerPolicyFieldNames;
  label: string;
  semantic: string;
  placeholder: string;
  span?: number;
}[] = [
  {
    key: 'USER_TYPE',
    nameKey: 'userTypes',
    label: '用户类型',
    semantic: '对应 Principal.userType（宿主权限，非 Flow RBAC）',
    placeholder: '可多选；从身份目录选择，或手输后回车',
  },
  {
    key: 'ROLE',
    nameKey: 'roles',
    label: '角色',
    semantic: '对应 Principal.roles',
    placeholder: '可多选；从身份目录选择，或手输后回车',
  },
  {
    key: 'PERMISSION',
    nameKey: 'permissions',
    label: '权限',
    semantic: '对应 Principal.permissions；* 视为超管',
    placeholder: '可多选；从身份目录选择，或手输后回车',
  },
  {
    key: 'DEPT',
    nameKey: 'deptIds',
    label: '部门',
    semantic: '对应 Principal.deptId / deptIds',
    placeholder: '可多选；搜索或选择部门',
  },
  {
    key: 'USER',
    nameKey: 'userIds',
    label: '用户',
    semantic: '对应 Principal.userId',
    placeholder: '可多选；搜索或选择用户',
  },
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
      title={
        fromCatalog ? '从身份目录选择，按 value 匹配' : '手输码，按该字符串匹配'
      }
      style={{ marginInlineEnd: 4 }}
    >
      {props.label}
    </Tag>
  );
}

function dimensionExtra(
  catalog: HostIdentityCatalog | null,
  dimension: HostCatalogDimension,
  semantic: string,
) {
  const slice = catalogSlice(catalog, dimension);
  return `${semantic}。${
    slice.searchable ? '选项来自宿主目录，可搜索' : '选项来自宿主目录'
  }。蓝标签=目录选择，金标签=手输`;
}

const HostCatalogSelect: React.FC<{
  name: string;
  label: string;
  dimension: HostCatalogDimension;
  catalog: HostIdentityCatalog | null;
  placeholder: string;
  extra: string;
  span?: number;
  compact?: boolean;
}> = ({
  name,
  label,
  dimension,
  catalog,
  placeholder,
  extra,
  span = 12,
  compact,
}) => {
  const slice = catalogSlice(catalog, dimension);
  const searchable = !!slice.searchable;
  const [remoteItems, setRemoteItems] = useState<HostCatalogItem[]>([]);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestSeqRef = useRef(0);
  const loadedOnce = useRef(false);

  const catalogItems = useMemo(
    () => catalogSelectItems(catalog, dimension, remoteItems),
    [catalog, dimension, remoteItems],
  );
  const staticOptions = useMemo(
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
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    },
    [],
  );

  const fieldProps: Record<string, unknown> = {
    tokenSeparators: [','],
    showSearch: true,
    maxTagCount: 'responsive',
    filterOption: searchable
      ? false
      : (input: string, option: { label?: string; value?: string }) => {
          const q = (input || '').toLowerCase();
          return (
            String(option?.label || '')
              .toLowerCase()
              .includes(q) ||
            String(option?.value || '')
              .toLowerCase()
              .includes(q)
          );
        },
    notFoundContent: '无匹配项',
    tagRender: (tagProps: {
      label?: React.ReactNode;
      value?: string | number;
      closable?: boolean;
      onClose?: (e?: React.MouseEvent<HTMLElement>) => void;
    }) => renderSourceTag(knownValues, tagProps),
  };

  if (searchable) {
    fieldProps.onSearch = (value: string) => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => runSearch(value), 300);
    };
    fieldProps.onDropdownVisibleChange = (open: boolean) => {
      if (open && !loadedOnce.current) {
        loadedOnce.current = true;
        runSearch('');
      }
    };
  }

  return (
    <Col span={span}>
      <ProFormSelect
        name={name}
        label={label}
        mode="tags"
        options={staticOptions}
        placeholder={placeholder}
        extra={compact ? undefined : extra}
        tooltip={compact ? extra : undefined}
        formItemProps={compact ? { style: { marginBottom: 6 } } : undefined}
        fieldProps={fieldProps as any}
      />
    </Col>
  );
};

const HostDeptField: React.FC<{
  names: CallerPolicyFieldNames;
  catalog: HostIdentityCatalog | null;
  compact?: boolean;
}> = ({ names, catalog, compact }) => {
  const includeChildren = Form.useWatch(names.deptIncludeChildren) !== false;
  const [remoteItems, setRemoteItems] = useState<HostCatalogItem[]>([]);
  const items = catalogSelectItems(catalog, 'DEPT', remoteItems);
  const tree = catalogItemsHaveTree(items);
  const treeData = useMemo(() => buildCatalogTree(items), [items]);

  useEffect(() => {
    if (!catalogDimensionEnabled(catalog, 'DEPT')) {
      return;
    }
    if (items.length) {
      return;
    }
    searchHostIdentityCatalog('DEPT', '', 200).then(setRemoteItems);
  }, [catalog, items.length]);

  const extra = dimensionExtra(
    catalog,
    'DEPT',
    tree
      ? includeChildren
        ? '对应 Principal.deptId / deptIds。勾选哪一级就存哪一级，运行时再覆盖其下级，不会连父级一起勾上'
        : '对应 Principal.deptId / deptIds。只匹配选中的部门本身'
      : '对应 Principal.deptId / deptIds',
  );

  const includeSwitch =
    tree && names.deptIncludeChildren ? (
      <Form.Item
        name={names.deptIncludeChildren}
        valuePropName="checked"
        noStyle
      >
        <Switch
          checkedChildren="含下级"
          unCheckedChildren="仅本级"
          size={compact ? 'small' : 'default'}
        />
      </Form.Item>
    ) : null;

  return (
    <>
      {includeSwitch && !compact ? (
        <Col span={24}>
          <Form.Item
            label="含下级部门"
            extra="开启后匹配选中部门及其下级；关闭则只匹配选中的部门本身"
            style={{ marginBottom: 12 }}
          >
            {includeSwitch}
          </Form.Item>
        </Col>
      ) : null}
      {tree ? (
        <Col span={12}>
          <ProFormTreeSelect
            name={names.deptIds}
            label={
              compact && includeSwitch ? (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                >
                  部门
                  {includeSwitch}
                </span>
              ) : (
                '部门'
              )
            }
            placeholder="按组织树勾选部门"
            extra={compact ? undefined : extra}
            tooltip={compact ? extra : undefined}
            formItemProps={{
              style: compact ? { marginBottom: 6 } : undefined,
              getValueFromEvent: (vals: unknown) => {
                if (!Array.isArray(vals)) {
                  return [];
                }
                return vals
                  .map((v) =>
                    v && typeof v === 'object' && 'value' in (v as object)
                      ? String((v as { value: unknown }).value ?? '')
                      : String(v ?? ''),
                  )
                  .map((s) => s.trim())
                  .filter(Boolean);
              },
              getValueProps: (ids?: string[]) => ({
                value: (ids || []).map((id) => ({
                  value: id,
                  label: items.find((i) => i.value === id)?.label || id,
                })),
              }),
            }}
            fieldProps={{
              treeData,
              multiple: true,
              treeCheckable: true,
              treeCheckStrictly: true,
              showSearch: true,
              allowClear: true,
              treeDefaultExpandAll: items.length <= 40,
              treeNodeFilterProp: 'title',
              maxTagCount: 'responsive',
              placeholder: '按组织树勾选部门',
              tagRender: (tagProps: {
                label?: React.ReactNode;
                value?: string | number;
                closable?: boolean;
                onClose?: (e?: React.MouseEvent<HTMLElement>) => void;
              }) => renderSourceTag(knownCatalogValues(items), tagProps),
            }}
          />
        </Col>
      ) : (
        <HostCatalogSelect
          name={names.deptIds}
          label="部门"
          dimension="DEPT"
          catalog={catalog}
          placeholder="可多选；搜索或选择部门"
          extra={extra}
          compact={compact}
        />
      )}
    </>
  );
};

/** 宿主调用方策略。维度显隐完全跟「宿主身份目录」启用走。 */
const CallerPolicyFields: React.FC<CallerPolicyFieldsProps> = ({
  names,
  enabled,
  enabledLabel = '启用',
  enabledExtra,
  compact,
  title,
  beforeEnable,
}) => {
  const [catalog, setCatalog] = useState<HostIdentityCatalog | null>(null);

  useEffect(() => {
    let cancelled = false;
    getHostIdentityCatalog().then((data) => {
      if (!cancelled) {
        setCatalog(data);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const visibleDims = DIM_FIELDS.filter((d) =>
    catalogDimensionEnabled(catalog, d.key),
  );

  const enableSwitch = (
    <Form.Item
      name={names.enabled}
      valuePropName="checked"
      noStyle
      getValueFromEvent={(checked: boolean) => {
        if (checked && beforeEnable && beforeEnable() === false) {
          return false;
        }
        return checked;
      }}
    >
      <Switch
        checkedChildren="开"
        unCheckedChildren="关"
        size={compact || title ? 'small' : 'default'}
      />
    </Form.Item>
  );

  return (
    <>
      {title ? (
        <Col span={24}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              margin: '4px 0 8px',
            }}
          >
            <span
              style={{
                color: 'rgba(0,0,0,0.45)',
                fontSize: 14,
                whiteSpace: 'nowrap',
              }}
            >
              {title}
            </span>
            <div style={{ flex: 1, borderTop: '1px solid rgba(5,5,5,0.06)' }} />
            <Tooltip title={enabledExtra}>
              <span>{enableSwitch}</span>
            </Tooltip>
          </div>
        </Col>
      ) : (
        <Col span={compact ? 8 : 24}>
          <Form.Item
            label={enabledLabel}
            extra={compact ? undefined : enabledExtra}
            tooltip={compact ? enabledExtra : undefined}
            style={{ marginBottom: compact ? 6 : 8 }}
          >
            {enableSwitch}
          </Form.Item>
        </Col>
      )}
      {enabled && catalog && visibleDims.length === 0 ? (
        <Col span={24}>
          <div
            style={{
              color: '#8c8c8c',
              fontSize: 12,
              marginBottom: compact ? 6 : 12,
            }}
          >
            尚未在「平台设置 →
            宿主机配置」启用任何维度，这里没有可配置的权限类型。
          </div>
        </Col>
      ) : null}
      {enabled && visibleDims.length > 0 ? (
        <>
          {compact && !title ? (
            <Col span={16}>
              <Form.Item
                name={names.match}
                label="多维度组合"
                tooltip="仅对已启用且已填写的维度生效"
                style={{ marginBottom: 6 }}
              >
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  size="small"
                  options={[
                    { label: '全部满足 (ALL)', value: 'ALL' },
                    { label: '任一满足 (ANY)', value: 'ANY' },
                  ]}
                />
              </Form.Item>
            </Col>
          ) : (
            <ProFormRadio.Group
              name={names.match}
              label="多维度组合"
              options={[
                { label: '全部满足 (ALL)', value: 'ALL' },
                { label: '任一满足 (ANY)', value: 'ANY' },
              ]}
              radioType="button"
              fieldProps={{
                buttonStyle: 'solid',
                size: compact ? 'small' : 'middle',
              }}
              extra={compact ? undefined : '仅对已启用且已填写的维度生效'}
              tooltip={compact ? '仅对已启用且已填写的维度生效' : undefined}
              formItemProps={
                compact ? { style: { marginBottom: 6 } } : undefined
              }
              colProps={{ span: 24 }}
            />
          )}
          {visibleDims.map((d) =>
            d.key === 'DEPT' ? (
              <HostDeptField
                key={d.key}
                names={names}
                catalog={catalog}
                compact={compact}
              />
            ) : (
              <HostCatalogSelect
                key={d.key}
                name={names[d.nameKey]!}
                label={d.label}
                dimension={d.key}
                catalog={catalog}
                placeholder={compact ? '选择或回车手输' : d.placeholder}
                extra={dimensionExtra(catalog, d.key, d.semantic)}
                span={compact ? 12 : d.span}
                compact={compact}
              />
            ),
          )}
        </>
      ) : null}
    </>
  );
};

export default CallerPolicyFields;
