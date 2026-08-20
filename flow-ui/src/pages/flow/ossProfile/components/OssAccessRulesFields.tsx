import {
  catalogItemToOption,
  catalogSelectItems,
  getHostIdentityCatalog,
  HOST_IDENTITY_CATALOG_CHANGED,
  HostIdentityCatalog,
} from '@/services/flow/hostIdentityCatalog';
import { getHostPrincipalOverview } from '@/services/flow/hostConfig';
import { PrincipalMatchRuleList } from '@/components/flow/PrincipalMatchFields';
import {
  EMPTY_ACCESS_RULE,
  MAX_OSS_ACCESS_RULES,
  OSS_ACCESS_PRESETS,
  buildOssAccessPreset,
  guessOpsUserTypes,
  previewAccessRules,
  type OssAccessPresetKey,
  type OssAccessRule,
  type OssDownloadScope,
} from '@/utils/ossAccessRules';
import { history } from '@umijs/max';
import { Alert, Button, Col, Form, Select, Switch, Tag, message } from 'antd';
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
  const privateVis = visibility !== 'PUBLIC';

  useEffect(() => {
    let cancelled = false;
    const loadCatalog = () => {
      getHostIdentityCatalog().then((data) => {
        if (!cancelled) setCatalog(data);
      });
    };
    loadCatalog();
    const onCatalogChanged = () => loadCatalog();
    window.addEventListener(HOST_IDENTITY_CATALOG_CHANGED, onCatalogChanged);
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
      window.removeEventListener(HOST_IDENTITY_CATALOG_CHANGED, onCatalogChanged);
    };
  }, []);

  const userTypeOptions = useMemo(
    () => catalogSelectItems(catalog, 'USER_TYPE').map(catalogItemToOption),
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

        <Form.Item name="accessRules" noStyle>
          <PrincipalMatchRuleList<OssAccessRule>
            accent="oss"
            max={MAX_OSS_ACCESS_RULES}
            createEmpty={() => ({
              ...EMPTY_ACCESS_RULE,
              name: '',
              principals: 'MATCH',
              upload: false,
              downloadScope: privateVis ? 'SELF' : 'OFF',
            })}
            extra={(rule, patch) => (
              <div className="principal-match-row">
                <span className="principal-match-label">可上传</span>
                <Switch
                  size="small"
                  checked={!!rule.upload}
                  onChange={(v) => patch({ upload: v })}
                />
                {privateVis ? (
                  <>
                    <span className="principal-match-label" style={{ marginLeft: 16 }}>
                      下载范围
                    </span>
                    <Select
                      size="small"
                      style={{ minWidth: 168 }}
                      value={rule.downloadScope}
                      options={SCOPE_OPTIONS}
                      onChange={(v) => patch({ downloadScope: v })}
                    />
                  </>
                ) : (
                  <Tag style={{ marginLeft: 12 }}>公有可读，无需下载范围</Tag>
                )}
              </div>
            )}
          />
        </Form.Item>
      </div>
    </Col>
  );
};

export default OssAccessRulesFields;
