import {
  catalogItemToOption,
  catalogSelectItems,
  getHostIdentityCatalog,
  HostIdentityCatalog,
} from '@/services/flow/hostIdentityCatalog';
import { getHostPrincipalOverview } from '@/services/flow/hostConfig';
import {
  EMPTY_ACCESS_RULE,
  MAX_OSS_ACCESS_RULES,
  OSS_ACCESS_PRESETS,
  buildOssAccessPreset,
  guessOpsUserTypes,
  matchDimensionCount,
  previewAccessRules,
  type OssAccessPresetKey,
  type OssAccessRule,
  type OssDownloadScope,
} from '@/utils/ossAccessRules';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { ProFormSelect } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Alert, Button, Col, Form, Input, Radio, Row, Select, Switch, Tag, message } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';

type Props = {
  visibility?: string;
  requireAuth?: boolean;
};

const SCOPE_OPTIONS: { label: string; value: OssDownloadScope }[] = [
  { label: '不可下载', value: 'OFF' },
  { label: '仅本人', value: 'SELF' },
  { label: '本部门（含下级）', value: 'DEPT' },
  { label: '该场景全部', value: 'ALL' },
];

const OssAccessRulesFields: React.FC<Props> = ({ visibility, requireAuth }) => {
  const form = Form.useFormInstance();
  const rules: OssAccessRule[] = Form.useWatch('accessRules') || [];
  const [catalog, setCatalog] = useState<HostIdentityCatalog | null>(null);
  const [principalReady, setPrincipalReady] = useState<boolean | null>(null);
  const [advanced, setAdvanced] = useState<Record<number, boolean>>({});
  const privateVis = visibility !== 'PUBLIC';

  useEffect(() => {
    let cancelled = false;
    getHostIdentityCatalog().then((data) => {
      if (!cancelled) setCatalog(data);
    });
    getHostPrincipalOverview()
      .then((overview) => {
        if (!cancelled) {
          setPrincipalReady(!!overview.spiOverride || !!overview.settings?.enabled);
        }
      })
      .catch(() => {
        if (!cancelled) setPrincipalReady(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const userTypeOptions = useMemo(
    () => catalogSelectItems(catalog, 'USER_TYPE').map(catalogItemToOption),
    [catalog],
  );
  const roleOptions = useMemo(
    () => catalogSelectItems(catalog, 'ROLE').map(catalogItemToOption),
    [catalog],
  );
  const permOptions = useMemo(
    () => catalogSelectItems(catalog, 'PERMISSION').map(catalogItemToOption),
    [catalog],
  );
  const userOptions = useMemo(
    () => catalogSelectItems(catalog, 'USER').map(catalogItemToOption),
    [catalog],
  );
  const opsTypes = useMemo(
    () =>
      guessOpsUserTypes(
        userTypeOptions.map((item) => ({
          value: String(item.value ?? ''),
          label: String(item.label ?? ''),
        })),
      ),
    [userTypeOptions],
  );

  const preview = previewAccessRules(rules, visibility || 'PRIVATE');

  const applyPreset = (key: OssAccessPresetKey) => {
    const preset = OSS_ACCESS_PRESETS.find((item) => item.key === key);
    form.setFieldValue('accessRules', buildOssAccessPreset(key, opsTypes));
    if (preset) {
      form.setFieldValue('requireAuth', preset.requireAuth);
      if (!preset.requireAuth) {
        form.setFieldValue('uploadPerm', '');
      }
      message.success(`已套用「${preset.label}」：${preset.hint}`);
    }
  };

  return (
    <Col span={24}>
      <div className="oss-access-rules">
        <div className="oss-access-rules-head">
          <span>访问规则</span>
          <Select
            size="small"
            placeholder="套用预置场景"
            style={{ minWidth: 188 }}
            value={undefined}
            options={OSS_ACCESS_PRESETS.map((item) => ({
              value: item.key,
              label: item.label,
              title: item.hint,
            }))}
            onChange={(key: OssAccessPresetKey) => applyPreset(key)}
          />
        </div>
        <div className="oss-access-rules-desc">
          一行一类人：组间或。命中多行时取最宽下载范围。开放应用不会被「已登录用户」覆盖，请用身份「开放应用」。
        </div>
        {principalReady === false ? (
          <Alert
            type="warning"
            showIcon
            className="oss-access-rules-hint"
            message="尚未配置「当前用户解析」"
            description={
              <span>
                不解析宿主用户时，规则里的「本人 / 部门」无法生效。请到
                <Button
                  type="link"
                  size="small"
                  style={{ padding: '0 4px' }}
                  onClick={() => history.push('/sys-host')}
                >
                  平台设置 → 宿主机配置
                </Button>
                开启。
              </span>
            }
          />
        ) : null}
        {!requireAuth ? (
          <Alert
            type="warning"
            showIcon
            className="oss-access-rules-hint"
            message="已关闭「上传要求登录」：匿名可上传；登录用户仍按下面的规则下载。匿名文件没有 uploaded_by，只有下载范围为「该场景全部」的人能看到。"
          />
        ) : null}
        <Alert type={preview.type} showIcon className="oss-access-rules-hint" message={preview.text} />

        <Form.List name="accessRules">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field, index) => {
                const rule = rules[index] || EMPTY_ACCESS_RULE;
                const isMatch = rule.principals === 'MATCH';
                const isOpen = rule.principals === 'OPEN_APP';
                const showMatchMode = isMatch && matchDimensionCount(rule) > 1;
                return (
                  <div key={field.key} className="oss-access-rule-card">
                    <div className="oss-access-rule-card-head">
                      <Form.Item name={[field.name, 'name']} noStyle>
                        <Input placeholder={`规则 ${index + 1}`} maxLength={32} style={{ width: 160 }} />
                      </Form.Item>
                      <Button
                        type="text"
                        danger
                        size="small"
                        icon={<MinusCircleOutlined />}
                        onClick={() => remove(field.name)}
                      />
                    </div>
                    <div className="oss-access-rule-row">
                      <span className="oss-access-rule-label">身份</span>
                      <Form.Item name={[field.name, 'principals']} noStyle>
                        <Radio.Group
                          optionType="button"
                          buttonStyle="solid"
                          size="small"
                          options={[
                            { label: '任何已登录', value: 'ANY_AUTHENTICATED' },
                            { label: '指定身份', value: 'MATCH' },
                            { label: '开放应用', value: 'OPEN_APP' },
                          ]}
                        />
                      </Form.Item>
                    </div>
                    {isOpen ? (
                      <ProFormSelect
                        name={[field.name, 'userIds']}
                        label="指定 AppKey"
                        mode="tags"
                        placeholder="留空=全部开放应用；可填 appKey 或 open:appKey"
                        colProps={{ span: 24 }}
                        formItemProps={{ style: { marginBottom: 8 } }}
                        fieldProps={{ tokenSeparators: [','], maxTagCount: 'responsive' }}
                      />
                    ) : null}
                    {isMatch ? (
                      <Row gutter={[12, 0]}>
                        {showMatchMode ? (
                          <Col span={24}>
                            <Form.Item
                              name={[field.name, 'match']}
                              label="多维度组合"
                              style={{ marginBottom: 8 }}
                            >
                              <Radio.Group
                                optionType="button"
                                buttonStyle="solid"
                                size="small"
                                options={[
                                  { label: '全部满足', value: 'ALL' },
                                  { label: '任一满足', value: 'ANY' },
                                ]}
                              />
                            </Form.Item>
                          </Col>
                        ) : null}
                        <ProFormSelect
                          name={[field.name, 'userTypes']}
                          label="用户类型"
                          mode="tags"
                          options={userTypeOptions}
                          placeholder="选择或回车手输"
                          colProps={{ span: 12 }}
                          formItemProps={{ style: { marginBottom: 8 } }}
                          fieldProps={{ tokenSeparators: [','], maxTagCount: 'responsive' }}
                        />
                        <ProFormSelect
                          name={[field.name, 'roles']}
                          label="角色"
                          mode="tags"
                          options={roleOptions}
                          placeholder="选择或回车手输"
                          colProps={{ span: 12 }}
                          formItemProps={{ style: { marginBottom: 8 } }}
                          fieldProps={{ tokenSeparators: [','], maxTagCount: 'responsive' }}
                        />
                        {advanced[index] ? (
                          <>
                            <ProFormSelect
                              name={[field.name, 'permissions']}
                              label="权限"
                              mode="tags"
                              options={permOptions}
                              placeholder="高级：宿主权限码"
                              colProps={{ span: 12 }}
                              formItemProps={{ style: { marginBottom: 8 } }}
                              fieldProps={{ tokenSeparators: [','], maxTagCount: 'responsive' }}
                            />
                            <ProFormSelect
                              name={[field.name, 'userIds']}
                              label="指定用户"
                              mode="tags"
                              options={userOptions}
                              placeholder="高级：仅这些人匹配本行"
                              colProps={{ span: 12 }}
                              formItemProps={{ style: { marginBottom: 8 } }}
                              fieldProps={{ tokenSeparators: [','], maxTagCount: 'responsive' }}
                            />
                          </>
                        ) : (
                          <Col span={24}>
                            <Button
                              type="link"
                              size="small"
                              style={{ padding: 0, marginBottom: 8 }}
                              onClick={() => setAdvanced((prev) => ({ ...prev, [index]: true }))}
                            >
                              高级：权限 / 指定用户
                            </Button>
                          </Col>
                        )}
                      </Row>
                    ) : null}
                    <div className="oss-access-rule-row">
                      <span className="oss-access-rule-label">可上传</span>
                      <Form.Item name={[field.name, 'upload']} valuePropName="checked" noStyle>
                        <Switch size="small" />
                      </Form.Item>
                      {privateVis ? (
                        <>
                          <span className="oss-access-rule-label" style={{ marginLeft: 16 }}>
                            下载范围
                          </span>
                          <Form.Item name={[field.name, 'downloadScope']} noStyle>
                            <Select
                              size="small"
                              style={{ minWidth: 168 }}
                              options={SCOPE_OPTIONS}
                            />
                          </Form.Item>
                        </>
                      ) : (
                        <Tag style={{ marginLeft: 12 }}>公有可读，无需下载范围</Tag>
                      )}
                    </div>
                  </div>
                );
              })}
              <Button
                type="dashed"
                block
                size="small"
                icon={<PlusOutlined />}
                disabled={fields.length >= MAX_OSS_ACCESS_RULES}
                onClick={() =>
                  add({
                    ...EMPTY_ACCESS_RULE,
                    name: `规则 ${fields.length + 1}`,
                    principals: 'MATCH',
                    upload: false,
                    downloadScope: privateVis ? 'SELF' : 'OFF',
                    userTypes: [],
                    roles: [],
                    permissions: [],
                    userIds: [],
                  })
                }
              >
                添加规则{fields.length >= MAX_OSS_ACCESS_RULES ? '（最多 8 条）' : ''}
              </Button>
            </>
          )}
        </Form.List>
      </div>
    </Col>
  );
};

export default OssAccessRulesFields;
