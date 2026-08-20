import { FieldActionEditor, PrincipalMatchRuleList } from '@/components/flow/PrincipalMatchFields';
import {
  EMPTY_CALLER_RULE,
  EMPTY_PRIVACY_RULE,
  normalizePrivacyAccessRule,
  type CallerAccessRule,
  type PrivacyAccessRule,
} from '@/utils/principalMatch';
import {
  getHostPrincipalOverview,
  previewHostCatalog,
  saveHostPrincipalSettings,
  testHostPrincipal,
  type HostCatalogApiMeta,
  type HostPrincipalField,
  type HostPrincipalSettings,
  type HostPrincipalTestResult,
} from '@/services/flow/hostConfig';
import { ApartmentOutlined, ExperimentOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';

const FIELD_LABELS: Record<HostPrincipalField, string> = {
  userId: '用户 ID',
  username: '用户名',
  userType: '用户类型',
  deptId: '部门 ID',
  deptIds: '部门列表',
  roles: '角色',
  permissions: '权限',
};

const FIELDS: HostPrincipalField[] = [
  'userId',
  'username',
  'userType',
  'deptId',
  'deptIds',
  'roles',
  'permissions',
];

const DEFAULT_SETTINGS: HostPrincipalSettings = {
  enabled: false,
  mode: 'API',
  cacheSeconds: 30,
  forwardHeaders: ['Authorization', 'Cookie'],
  fields: {},
  headerNames: {},
  trustProxyHeaders: false,
  adminUserTypes: [],
  privacyRules: [],
  privacyRevealRoles: [],
  privacyMaskRoles: [],
  ingressCallerEnabled: false,
  ingressRules: [],
};

function migrateHostPrivacyRules(settings: HostPrincipalSettings): PrivacyAccessRule[] {
  if (Array.isArray(settings.privacyRules) && settings.privacyRules.length) {
    return settings.privacyRules.map((r) => normalizePrivacyAccessRule(r));
  }
  const roles = (settings.privacyRevealRoles || []).map((x) => String(x || '').trim()).filter(Boolean);
  if (!roles.length) return [];
  return [
    normalizePrivacyAccessRule({
      ...EMPTY_PRIVACY_RULE,
      name: '隐私明文角色',
      principals: 'MATCH',
      roles,
      privacy: 'REVEAL',
    }),
  ];
}

type Props = {
  canWrite: boolean;
  canCompose: boolean;
  onCompose: (meta?: HostCatalogApiMeta) => void;
  /** 保留接口编排保存后由父页触发刷新 */
  refreshToken?: number;
};

const PrincipalResolverCard: React.FC<Props> = ({
  canWrite,
  canCompose,
  onCompose,
  refreshToken,
}) => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [spiOverride, setSpiOverride] = useState(false);
  const [settings, setSettings] =
    useState<HostPrincipalSettings>(DEFAULT_SETTINGS);
  const [api, setApi] = useState<HostCatalogApiMeta | undefined>();
  const [defaultHeaderNames, setDefaultHeaderNames] = useState<
    Record<string, string>
  >({});
  const [userTypeOptions, setUserTypeOptions] = useState<
    { label: string; value: string }[]
  >([]);
  const [result, setResult] = useState<HostPrincipalTestResult | null>(null);

  const load = useCallback(async (keepDirty = false) => {
    setLoading(true);
    try {
      const data = await getHostPrincipalOverview();
      setSpiOverride(!!data.spiOverride);
      setApi(data.api);
      setDefaultHeaderNames(data.defaultHeaderNames || {});
      if (!keepDirty) {
        setSettings({
          ...DEFAULT_SETTINGS,
          ...(data.settings || {}),
          privacyRules: migrateHostPrivacyRules({
            ...DEFAULT_SETTINGS,
            ...(data.settings || {}),
          }),
          ingressRules: Array.isArray(data.settings?.ingressRules)
            ? data.settings.ingressRules
            : [],
        });
        setDirty(false);
      }
    } catch {
      message.error('加载主体解析配置失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (refreshToken) load(true);
  }, [refreshToken, load]);

  useEffect(() => {
    previewHostCatalog('USER_TYPE', '', 50)
      .then((items) =>
        setUserTypeOptions(
          items.map((item) => ({
            label: item.label ? `${item.label}（${item.value}）` : item.value,
            value: item.value,
          })),
        ),
      )
      .catch(() => setUserTypeOptions([]));
  }, []);

  const patch = useCallback((partial: Partial<HostPrincipalSettings>) => {
    setSettings((prev) => ({ ...prev, ...partial }));
    setDirty(true);
  }, []);

  const headerMode = settings.mode === 'HEADER';
  const published = api?.publishStatus === 1;

  const handleSave = async () => {
    if (settings.enabled && headerMode && !settings.trustProxyHeaders) {
      message.warning('请求头模式需先确认「Flow 不直接暴露公网」');
      return;
    }
    setSaving(true);
    try {
      const payload: HostPrincipalSettings = {
        ...settings,
        privacyRules: settings.privacyRules || [],
        privacyRevealRoles: [],
        privacyMaskRoles: [],
        ingressCallerEnabled: !!settings.ingressCallerEnabled,
        ingressRules: settings.ingressRules || [],
      };
      const saved = await saveHostPrincipalSettings(payload);
      setSettings({
        ...DEFAULT_SETTINGS,
        ...(saved.settings || {}),
        privacyRules: migrateHostPrivacyRules({
          ...DEFAULT_SETTINGS,
          ...(saved.settings || {}),
        }),
        ingressRules: Array.isArray(saved.settings?.ingressRules)
          ? saved.settings.ingressRules
          : [],
      });
      setApi(saved.api);
      setSpiOverride(!!saved.spiOverride);
      setDirty(false);
      message.success('已保存');
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    setTesting(true);
    try {
      if (dirty) {
        await saveHostPrincipalSettings(settings);
        setDirty(false);
      }
      setResult(await testHostPrincipal());
    } catch {
      message.error('测试失败');
    } finally {
      setTesting(false);
    }
  };

  const statusTag = useMemo(() => {
    if (spiOverride) return <Tag color="warning">Java SPI 接管</Tag>;
    if (!settings.enabled) return <Tag>未启用 · 使用内置 JWT</Tag>;
    if (headerMode) return <Tag color="success">网关请求头</Tag>;
    return published ? (
      <Tag color="success">宿主接口 · 已发布</Tag>
    ) : (
      <Tag color="error">宿主接口 · 未发布</Tag>
    );
  }, [spiOverride, settings.enabled, headerMode, published]);

  return (
    <Card
      className="host-principal-card"
      loading={loading}
      title={
        <Space size={10}>
          <span>当前用户解析</span>
          {statusTag}
          {dirty ? <Tag color="processing">有未保存更改</Tag> : null}
        </Space>
      }
      extra={
        <Space>
          <Tooltip title="用你当前这次请求跑一遍解析（允许草稿），用来核对字段名">
            <Button
              size="small"
              icon={<ExperimentOutlined />}
              loading={testing}
              disabled={!canWrite || !settings.enabled}
              onClick={handleTest}
            >
              测试解析
            </Button>
          </Tooltip>
          <Button
            size="small"
            type="primary"
            loading={saving}
            disabled={!canWrite || !dirty}
            onClick={handleSave}
          >
            保存
          </Button>
        </Space>
      }
    >
      <Alert
        type={spiOverride ? 'warning' : 'info'}
        showIcon
        className="host-principal-notice"
        message={
          spiOverride
            ? '当前进程已注入 Java FlowHostPrincipalProvider，运行时以 SPI 为准，本卡片配置不生效。'
            : '不启用时，所有登录用户都被当作 Flow 管理员（userType=ADMIN），无法区分「本人」与「运营」。启用后即可零 Java 代码接入宿主用户体系。'
        }
      />

      <div className="host-principal-row">
        <label>启用配置式解析</label>
        <Switch
          checked={!!settings.enabled}
          disabled={!canWrite || spiOverride}
          onChange={(v) => patch({ enabled: v })}
        />
        <Typography.Text type="secondary">
          解析不到宿主会话时自动回退内置 JWT，保证管理端仍可登录
        </Typography.Text>
      </div>

      <div className="host-principal-row">
        <label>解析方式</label>
        <Radio.Group
          value={settings.mode || 'API'}
          disabled={!canWrite || spiOverride}
          onChange={(e) => patch({ mode: e.target.value })}
          optionType="button"
          buttonStyle="solid"
          size="small"
          options={[
            { label: '调用宿主接口', value: 'API' },
            { label: '读取网关请求头', value: 'HEADER' },
          ]}
        />
        <Typography.Text type="secondary">
          {headerMode
            ? '适合 Flow 部署在网关之后、由网关注入身份头的场景'
            : '宿主提供一个「我是谁」接口，Flow 带上原始凭证反查'}
        </Typography.Text>
      </div>

      {headerMode ? (
        <>
          <Alert
            type="warning"
            showIcon
            className="host-principal-notice"
            message="请求头可被客户端伪造。只有确保 Flow 不直接暴露公网、且网关会剥离客户端传入的同名头时才能使用。"
          />
          <div className="host-principal-row">
            <label>已确认部署前提</label>
            <Switch
              checked={!!settings.trustProxyHeaders}
              disabled={!canWrite || spiOverride}
              onChange={(v) => patch({ trustProxyHeaders: v })}
            />
          </div>
          <div className="host-principal-grid">
            {FIELDS.map((field) => (
              <Input
                key={field}
                size="small"
                addonBefore={
                  <span className="host-principal-addon">
                    {FIELD_LABELS[field]}
                  </span>
                }
                placeholder={defaultHeaderNames[field]}
                value={settings.headerNames?.[field]}
                disabled={!canWrite || spiOverride}
                onChange={(e) =>
                  patch({
                    headerNames: {
                      ...settings.headerNames,
                      [field]: e.target.value,
                    },
                  })
                }
              />
            ))}
          </div>
        </>
      ) : (
        <>
          <div className="host-principal-api">
            <div>
              <div className="host-principal-api-name">{api?.name}</div>
              <code>{api?.url}</code>
              <Typography.Text type="secondary">
                入参：{'headers'}（转发的请求头）、{'token'}、{'clientIp'}；
                返回一行主体字段。编排里用 {"$.request.headers['Cookie']"} 或{' '}
                {'${token}'} 取用。
              </Typography.Text>
            </div>
            <Tooltip
              title={
                canCompose
                  ? '编辑解析接口的实现'
                  : '需要宿主机配置管理和接口写入权限'
              }
            >
              <Button
                size="small"
                icon={<ApartmentOutlined />}
                disabled={!canCompose}
                onClick={() => onCompose(api)}
              >
                编排
              </Button>
            </Tooltip>
          </div>
          {settings.enabled && !published ? (
            <Alert
              type="error"
              showIcon
              className="host-principal-notice"
              message="解析接口尚未发布。运行时只认已发布版本，当前会一直回退内置 JWT。"
            />
          ) : null}
          <div className="host-principal-row">
            <label>转发请求头</label>
            <Select
              mode="tags"
              size="small"
              style={{ minWidth: 320 }}
              value={settings.forwardHeaders}
              disabled={!canWrite || spiOverride}
              tokenSeparators={[',']}
              placeholder="Authorization, Cookie"
              onChange={(v) => patch({ forwardHeaders: v })}
            />
            <label className="is-inline">缓存</label>
            <InputNumber
              size="small"
              min={0}
              max={600}
              addonAfter="秒"
              value={settings.cacheSeconds}
              disabled={!canWrite || spiOverride}
              onChange={(v) => patch({ cacheSeconds: Number(v) || 0 })}
            />
            <Typography.Text type="secondary">
              按凭证指纹缓存，0 表示每次请求都反查宿主
            </Typography.Text>
          </div>
          <div className="host-principal-subtitle">
            字段映射（留空表示与主体字段同名，支持下划线风格）
          </div>
          <div className="host-principal-grid">
            {FIELDS.map((field) => (
              <Input
                key={field}
                size="small"
                addonBefore={
                  <span className="host-principal-addon">
                    {FIELD_LABELS[field]}
                  </span>
                }
                placeholder={field}
                value={settings.fields?.[field]}
                disabled={!canWrite || spiOverride}
                onChange={(e) =>
                  patch({
                    fields: { ...settings.fields, [field]: e.target.value },
                  })
                }
              />
            ))}
          </div>
        </>
      )}

      <div className="host-principal-row is-scope">
        <label>运营用户类型</label>
        <Select
          mode="tags"
          size="small"
          style={{ minWidth: 320 }}
          value={settings.adminUserTypes}
          options={userTypeOptions}
          disabled={!canWrite || spiOverride}
          tokenSeparators={[',']}
          placeholder="例如 ADMIN、OPERATOR"
          onChange={(v) => patch({ adminUserTypes: v })}
        />
        <Typography.Text type="secondary">
          只影响接口侧全局 DataScope（管理员看全部 / 其余看本人）。OSS 文件可见范围请在上传场景的「访问规则」里按身份配置，不再读取这里。
        </Typography.Text>
      </div>

      <div className="host-principal-row is-scope" style={{ alignItems: 'flex-start' }}>
        <label>平台默认入站</label>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Switch
              size="small"
              checked={!!settings.ingressCallerEnabled}
              disabled={!canWrite || spiOverride}
              onChange={(v) => patch({ ingressCallerEnabled: v })}
            />
            <Typography.Text type="secondary">
              目录/接口未写调用方策略时生效。启用后未命中拒绝。也可在接口管理左侧「平台默认」维护同一份配置。
            </Typography.Text>
          </div>
          {settings.ingressCallerEnabled ? (
            <PrincipalMatchRuleList<CallerAccessRule>
              accent="ingress"
              value={settings.ingressRules || []}
              onChange={(ingressRules) => patch({ ingressRules })}
              createEmpty={() => ({ ...EMPTY_CALLER_RULE })}
            />
          ) : null}
        </div>
      </div>

      <div className="host-principal-row is-scope" style={{ alignItems: 'flex-start' }}>
        <label>平台默认隐私</label>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            目录/接口未配置「谁看什么」时继承这里。从上到下第一条命中；未命中脱敏。开放应用不会被「任何已登录」覆盖。也可在接口管理左侧「平台默认」维护。
          </Typography.Text>
          <PrincipalMatchRuleList<PrivacyAccessRule>
            accent="privacy"
            value={settings.privacyRules || []}
            onChange={(privacyRules) => patch({ privacyRules })}
            createEmpty={() => ({ ...EMPTY_PRIVACY_RULE, principals: 'MATCH' })}
            extra={(rule, patchRule) => (
              <>
                <div className="principal-match-row">
                  <span className="principal-match-label">出站</span>
                  <Radio.Group
                    optionType="button"
                    buttonStyle="solid"
                    size="small"
                    value={rule.privacy || 'MASK'}
                    disabled={!canWrite || spiOverride}
                    options={[
                      { label: '脱敏', value: 'MASK' },
                      { label: '明文', value: 'REVEAL' },
                    ]}
                    onChange={(e) => patchRule({ privacy: e.target.value })}
                  />
                </div>
                <FieldActionEditor
                  value={rule.fields}
                  onChange={(fields) => patchRule({ fields })}
                />
              </>
            )}
          />
        </div>
      </div>

      {result ? (
        <div className="host-principal-result">
          <Alert
            type={result.resolved ? 'success' : 'warning'}
            showIcon
            message={
              result.resolved
                ? `解析成功：${result.principal?.userId}（userType=${
                    result.principal?.userType || '未提供'
                  }）`
                : result.message || '未解析出主体'
            }
          />
          <pre>
            {JSON.stringify(
              { principal: result.principal, raw: result.raw },
              null,
              2,
            )}
          </pre>
        </div>
      ) : null}
    </Card>
  );
};

export default PrincipalResolverCard;
