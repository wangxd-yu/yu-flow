/**
 * 目录新建 / 编辑弹框（可扩展：路径前缀、入站防护等）
 */
import CallerPolicyFields from '@/components/flow/CallerPolicyFields';
import { fetchStackedDirectoryPathPrefix } from '@/utils/apiPathPrefix';
import type { CallerPolicy } from '@/utils/callerPolicy';
import { request } from '@umijs/max';
import {
  Alert,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Switch,
  Tabs,
  message,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import './DirectoryFormModal.less';
import type { DirectoryBizType } from './DirectoryTreeLayout';
import {
  buildPrivacyConfigFromForm,
  parsePrivacyConfigToForm,
  stringifyPrivacyConfig,
} from '@/pages/flow/controller/components/privacyConfig';
import PrivacySection from '@/pages/flow/controller/components/panels/PrivacySection';

export type DirectoryFormMode = 'create' | 'edit';

export type DirectoryFormValues = {
  name: string;
  parentId?: string;
  sort?: number;
  remark?: string;
  pathPrefix?: string;
  /** 入站：未开「覆盖」的字段不写入 JSON（继承上级/全局） */
  secAuthMode?: string;
  secRateLimitOverride?: boolean;
  secRateLimitEnabled?: boolean;
  secRateLimitQps?: number;
  secIpOverride?: boolean;
  secIpAllowlist?: string;
  secTimeoutOverride?: boolean;
  secTimeoutMs?: number;
  secCallerOverride?: boolean;
  secCallerEnabled?: boolean;
  secCallerMatch?: 'ALL' | 'ANY';
  secCallerUserTypes?: string[];
  secCallerRoles?: string[];
  secCallerPermissions?: string[];
  secCallerDeptIds?: string[];
  secCallerDeptIncludeChildren?: boolean;
  secCallerUserIds?: string[];
  privacyMode?: 'INHERIT' | 'ON' | 'OFF';
  privacyInherit?: boolean;
  privacyProfileId?: string;
  privacyFieldSuffix?: string;
  privacyExtraFields?: string[];
  privacyStripSuffixOverride?: boolean;
  privacyStripSuffix?: boolean;
};

type Props = {
  open: boolean;
  mode: DirectoryFormMode;
  bizType?: DirectoryBizType;
  /** 新建子目录时的父 ID；编辑时忽略 */
  parentId?: string;
  /** 编辑时的目录 ID */
  directoryId?: string;
  onCancel: () => void;
  onSuccess: () => void;
};

function buildSecurityConfigJson(values: DirectoryFormValues): string | null {
  const cfg: Record<string, unknown> = {};
  if (values.secAuthMode && values.secAuthMode !== 'INHERIT') {
    cfg.authMode = values.secAuthMode;
  }
  if (values.secRateLimitOverride) {
    cfg.rateLimitEnabled = !!values.secRateLimitEnabled;
    if (values.secRateLimitQps != null) {
      cfg.rateLimitQps = values.secRateLimitQps;
    }
  }
  if (values.secIpOverride) {
    cfg.ipAllowlist = values.secIpAllowlist ?? '';
  }
  if (values.secTimeoutOverride && values.secTimeoutMs != null) {
    cfg.timeoutMs = values.secTimeoutMs;
  }
  if (values.secCallerOverride) {
    const callerPolicy: CallerPolicy = {
      enabled: !!values.secCallerEnabled,
      match: values.secCallerMatch === 'ANY' ? 'ANY' : 'ALL',
      userTypes: values.secCallerUserTypes || [],
      roles: values.secCallerRoles || [],
      permissions: values.secCallerPermissions || [],
      deptIds: values.secCallerDeptIds || [],
      deptIncludeChildren: values.secCallerDeptIncludeChildren !== false,
      userIds: values.secCallerUserIds || [],
    };
    cfg.callerPolicy = callerPolicy;
  }
  if (Object.keys(cfg).length === 0) {
    return null;
  }
  return JSON.stringify(cfg);
}

function parseSecurityToForm(
  json?: string | null,
): Partial<DirectoryFormValues> {
  if (!json || !json.trim()) {
    return {
      secAuthMode: 'INHERIT',
      secRateLimitOverride: false,
      secIpOverride: false,
      secTimeoutOverride: false,
      secCallerOverride: false,
      secCallerEnabled: false,
      secCallerMatch: 'ALL',
      secCallerDeptIncludeChildren: true,
    };
  }
  try {
    const cfg = JSON.parse(json);
    const cp = cfg.callerPolicy || null;
    return {
      secAuthMode:
        cfg.authMode && cfg.authMode !== 'INHERIT' ? cfg.authMode : 'INHERIT',
      secRateLimitOverride:
        cfg.rateLimitEnabled != null || cfg.rateLimitQps != null,
      secRateLimitEnabled: cfg.rateLimitEnabled ?? false,
      secRateLimitQps: cfg.rateLimitQps,
      secIpOverride: cfg.ipAllowlist != null,
      secIpAllowlist: cfg.ipAllowlist ?? '',
      secTimeoutOverride: cfg.timeoutMs != null,
      secTimeoutMs: cfg.timeoutMs,
      secCallerOverride: cp != null,
      secCallerEnabled: !!cp?.enabled,
      secCallerMatch: cp?.match === 'ANY' ? 'ANY' : 'ALL',
      secCallerUserTypes: Array.isArray(cp?.userTypes) ? cp.userTypes : [],
      secCallerRoles: Array.isArray(cp?.roles) ? cp.roles : [],
      secCallerPermissions: Array.isArray(cp?.permissions)
        ? cp.permissions
        : [],
      secCallerDeptIds: Array.isArray(cp?.deptIds) ? cp.deptIds : [],
      secCallerDeptIncludeChildren: cp?.deptIncludeChildren !== false,
      secCallerUserIds: Array.isArray(cp?.userIds) ? cp.userIds : [],
    };
  } catch {
    return { secAuthMode: 'INHERIT' };
  }
}

const DirectoryFormModal: React.FC<Props> = ({
  open,
  mode,
  bizType,
  parentId,
  directoryId,
  onCancel,
  onSuccess,
}) => {
  const [form] = Form.useForm<DirectoryFormValues>();
  const [loading, setLoading] = useState(false);
  const [effectivePrefix, setEffectivePrefix] = useState<string | undefined>();

  const title = useMemo(
    () =>
      mode === 'create' ? (parentId ? '新建子目录' : '新建根目录') : '编辑目录',
    [mode, parentId],
  );

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      if (mode === 'edit' && directoryId) {
        setLoading(true);
        try {
          const res: any = await request(
            `/flow-api/directories/${directoryId}`,
            { method: 'GET' },
          );
          const data = res?.data ?? res;
          if (cancelled) return;
          form.setFieldsValue({
            name: data?.name,
            sort: data?.sort ?? 0,
            remark: data?.remark ?? '',
            pathPrefix: data?.pathPrefix ?? '',
            ...parseSecurityToForm(data?.securityConfig),
            ...parsePrivacyConfigToForm(data?.privacyConfig),
          });
          // 前端沿父链叠加，避免后端旧包「就近覆盖」展示不准
          const stacked = await fetchStackedDirectoryPathPrefix(directoryId);
          if (!cancelled) setEffectivePrefix(stacked || undefined);
        } catch {
          message.error('加载目录失败');
        } finally {
          if (!cancelled) setLoading(false);
        }
      } else {
        form.resetFields();
        form.setFieldsValue({
          name: '',
          sort: 0,
          remark: '',
          pathPrefix: '',
          secAuthMode: 'INHERIT',
          secRateLimitOverride: false,
          secIpOverride: false,
          secTimeoutOverride: false,
          secCallerOverride: false,
          secCallerEnabled: false,
          secCallerMatch: 'ALL',
          secCallerDeptIncludeChildren: true,
          privacyMode: 'INHERIT',
          privacyInherit: true,
          privacyProfileId: '',
          privacyFieldSuffix: '',
          privacyExtraFields: [],
          privacyStripSuffixOverride: false,
          privacyStripSuffix: true,
        });
        setEffectivePrefix(undefined);
        if (parentId) {
          try {
            const stacked = await fetchStackedDirectoryPathPrefix(parentId);
            if (!cancelled) setEffectivePrefix(stacked || undefined);
          } catch {
            /* ignore */
          }
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, mode, directoryId, parentId, form]);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (
        values.secAuthMode === 'NONE' &&
        values.secCallerOverride &&
        values.secCallerEnabled
      ) {
        message.error('匿名 (NONE) 时不能启用调用方策略');
        return;
      }
      setLoading(true);
      const securityConfig = buildSecurityConfigJson(values);
      const privacyConfig = stringifyPrivacyConfig(buildPrivacyConfigFromForm(values));
      const payload = {
        name: values.name.trim(),
        sort: values.sort ?? 0,
        remark: values.remark ?? '',
        pathPrefix: values.pathPrefix ?? '',
        securityConfig: securityConfig ?? '',
        privacyConfig: privacyConfig ?? '',
        bizType,
        parentId: mode === 'create' ? parentId : undefined,
      };
      if (mode === 'create') {
        await request('/flow-api/directories', {
          method: 'POST',
          data: payload,
        });
        message.success('目录创建成功');
      } else if (directoryId) {
        await request(`/flow-api/directories/${directoryId}`, {
          method: 'PUT',
          data: payload,
        });
        message.success('目录已更新');
      }
      onSuccess();
    } catch (e: any) {
      if (e?.errorFields) return;
      // 其它错误由拦截器提示
    } finally {
      setLoading(false);
    }
  };

  const showApiExtras = !bizType || bizType === 'api';
  const rateOverride = Form.useWatch('secRateLimitOverride', form);
  const ipOverride = Form.useWatch('secIpOverride', form);
  const timeoutOverride = Form.useWatch('secTimeoutOverride', form);
  const callerOverride = Form.useWatch('secCallerOverride', form);
  const callerOn = !!Form.useWatch('secCallerEnabled', form);
  const authMode = Form.useWatch('secAuthMode', form);

  return (
    <Modal
      className="directory-form-modal"
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={loading}
      width={760}
      centered
      destroyOnClose
      maskClosable={false}
      styles={{
        body: {
          paddingTop: 8,
          paddingBottom: 4,
          maxHeight: 'calc(100vh - 180px)',
          overflowY: 'auto',
          overflowX: 'hidden',
        },
      }}
    >
      <Form
        form={form}
        layout="vertical"
        size="small"
        preserve={false}
        className="dir-form-modal"
      >
        <Tabs
          size="small"
          items={
            [
              {
                key: 'basic',
                label: '基本信息',
                children: (
                  <div className="dir-form-section">
                    <Row gutter={12}>
                      <Col span={16}>
                        <Form.Item
                          name="name"
                          label="目录名称"
                          rules={[
                            { required: true, message: '请输入目录名称' },
                          ]}
                        >
                          <Input
                            placeholder="如：公开接口、管理端、合作方"
                            maxLength={128}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="sort" label="排序">
                          <InputNumber
                            min={0}
                            max={99999}
                            style={{ width: '100%' }}
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Form.Item name="remark" label="备注">
                      <Input.TextArea
                        rows={2}
                        maxLength={512}
                        placeholder="可选"
                      />
                    </Form.Item>
                  </div>
                ),
              },
              showApiExtras
                ? {
                    key: 'path',
                    label: '路径前缀',
                    children: (
                      <div className="dir-form-section">
                        <Alert
                          type="info"
                          showIcon
                          banner
                          style={{ marginBottom: 8 }}
                          message="非必填。根→叶叠加；新建接口时前缀在路径框前，输入框只填相对段。改前缀不改已有接口 path。"
                        />
                        {effectivePrefix ? (
                          <Alert
                            type="success"
                            showIcon
                            banner
                            style={{ marginBottom: 8 }}
                            message={`当前有效前缀：${effectivePrefix}`}
                          />
                        ) : null}
                        <Form.Item
                          name="pathPrefix"
                          label="本目录 pathPrefix"
                          extra="示例：/api/public、/v1；留空则本级不追加"
                        >
                          <Input placeholder="留空=本级不追加" allowClear />
                        </Form.Item>
                      </div>
                    ),
                  }
                : null,
              showApiExtras
                ? {
                    key: 'ingress',
                    label: '入站防护',
                    children: (
                      <>
                        <Alert
                          type="warning"
                          showIcon
                          banner
                          style={{ marginBottom: 8 }}
                          message="未覆盖字段即时生效：接口 → 目录链 → 全局。生产改限流/IP 请评估影响面。"
                        />
                        <div className="dir-form-section dir-security-section">
                          <div className="dir-section-title">基础防护</div>
                          <Row gutter={12}>
                            <Col span={24}>
                              <Form.Item
                                name="secAuthMode"
                                label="鉴权方式"
                                initialValue="INHERIT"
                              >
                                <Select
                                  options={[
                                    {
                                      label: '继承上级/全局',
                                      value: 'INHERIT',
                                    },
                                    { label: '无鉴权 (NONE)', value: 'NONE' },
                                    { label: '宿主登录 (HOST)', value: 'HOST' },
                                    { label: '开放平台 (OPEN)', value: 'OPEN' },
                                  ]}
                                />
                              </Form.Item>
                            </Col>
                            <Col span={8}>
                              <div className="dir-security-card">
                                <div className="dir-security-card-head">
                                  <span>限流</span>
                                  <Form.Item
                                    name="secRateLimitOverride"
                                    valuePropName="checked"
                                    noStyle
                                  >
                                    <Switch size="small" />
                                  </Form.Item>
                                </div>
                                <div className="dir-security-card-body">
                                  {!rateOverride ? (
                                    <span className="dir-inherit-text">
                                      继承上级
                                    </span>
                                  ) : (
                                    <>
                                      <Form.Item
                                        name="secRateLimitEnabled"
                                        valuePropName="checked"
                                        noStyle
                                      >
                                        <Switch
                                          size="small"
                                          checkedChildren="开"
                                          unCheckedChildren="关"
                                        />
                                      </Form.Item>
                                      <Form.Item name="secRateLimitQps" noStyle>
                                        <InputNumber
                                          min={1}
                                          max={100000}
                                          placeholder="QPS"
                                          style={{ width: 92 }}
                                        />
                                      </Form.Item>
                                    </>
                                  )}
                                </div>
                              </div>
                            </Col>
                            <Col span={8}>
                              <div className="dir-security-card">
                                <div className="dir-security-card-head">
                                  <span>超时</span>
                                  <Form.Item
                                    name="secTimeoutOverride"
                                    valuePropName="checked"
                                    noStyle
                                  >
                                    <Switch size="small" />
                                  </Form.Item>
                                </div>
                                <div className="dir-security-card-body">
                                  {!timeoutOverride ? (
                                    <span className="dir-inherit-text">
                                      继承上级
                                    </span>
                                  ) : (
                                    <Form.Item name="secTimeoutMs" noStyle>
                                      <InputNumber
                                        min={0}
                                        placeholder="毫秒"
                                        style={{ width: '100%' }}
                                      />
                                    </Form.Item>
                                  )}
                                </div>
                              </div>
                            </Col>
                            <Col span={8}>
                              <div className="dir-security-card">
                                <div className="dir-security-card-head">
                                  <span>IP 白名单</span>
                                  <Form.Item
                                    name="secIpOverride"
                                    valuePropName="checked"
                                    noStyle
                                  >
                                    <Switch size="small" />
                                  </Form.Item>
                                </div>
                                <div className="dir-security-card-body">
                                  <span
                                    className={
                                      ipOverride
                                        ? 'dir-custom-text'
                                        : 'dir-inherit-text'
                                    }
                                  >
                                    {ipOverride ? '自定义规则' : '继承上级'}
                                  </span>
                                </div>
                              </div>
                            </Col>
                            {ipOverride ? (
                              <Col span={24}>
                                <Form.Item
                                  name="secIpAllowlist"
                                  label="允许访问的 IP / CIDR"
                                >
                                  <Input
                                    placeholder="逗号分隔 IP/CIDR；空串=明确不限制"
                                    allowClear
                                  />
                                </Form.Item>
                              </Col>
                            ) : null}
                          </Row>
                        </div>
                        <div className="dir-form-section dir-caller-section">
                          <div className="dir-section-head">
                            <div>
                              <div className="dir-section-title">
                                调用方策略
                              </div>
                              <div className="dir-section-desc">
                                限制下属接口可访问的宿主身份；只管能不能调，不区分行
                              </div>
                            </div>
                            <div className="dir-override-control">
                              <span>
                                {callerOverride ? '本级覆盖' : '继承上级'}
                              </span>
                              <Form.Item
                                name="secCallerOverride"
                                valuePropName="checked"
                                noStyle
                              >
                                <Switch size="small" />
                              </Form.Item>
                            </div>
                          </div>
                          {!callerOverride ? (
                            <div className="dir-empty-state">
                              当前继承上级目录；上级也未配置时，不限制调用方身份。
                            </div>
                          ) : (
                            <>
                              {authMode === 'OPEN' && callerOn ? (
                                <Alert
                                  type="warning"
                                  showIcon
                                  banner
                                  style={{ marginBottom: 8 }}
                                  message="OPEN 下调用方策略不参与匹配，仅在改回 HOST 后生效。"
                                />
                              ) : null}
                              {authMode === 'NONE' && callerOn ? (
                                <Alert
                                  type="error"
                                  showIcon
                                  banner
                                  style={{ marginBottom: 8 }}
                                  message="NONE 不能启用调用方策略，请先改为 HOST 或关闭策略。"
                                />
                              ) : null}
                              <Row gutter={12}>
                                <CallerPolicyFields
                                  compact
                                  names={{
                                    enabled: 'secCallerEnabled',
                                    match: 'secCallerMatch',
                                    userTypes: 'secCallerUserTypes',
                                    roles: 'secCallerRoles',
                                    permissions: 'secCallerPermissions',
                                    deptIds: 'secCallerDeptIds',
                                    deptIncludeChildren:
                                      'secCallerDeptIncludeChildren',
                                    userIds: 'secCallerUserIds',
                                  }}
                                  enabled={callerOn}
                                  enabledLabel="启用"
                                  enabledExtra="接口未单独启用调用方策略时，本目录策略对下属接口即时生效；「普通用户查自己」需在接口 SQL 里用 ${@AUTH.userId} 收敛"
                                  beforeEnable={() => {
                                    if (
                                      form.getFieldValue('secAuthMode') ===
                                      'NONE'
                                    ) {
                                      Modal.warning({
                                        title: '无法启用调用方策略',
                                        content:
                                          '匿名 (NONE) 不能启用调用方策略，请先将鉴权方式改为 HOST。',
                                      });
                                      return false;
                                    }
                                    return true;
                                  }}
                                />
                              </Row>
                            </>
                          )}
                        </div>
                      </>
                    ),
                  }
                : null,
              showApiExtras
                ? {
                    key: 'privacy',
                    label: '隐私拦截',
                    children: (
                      <>
                        <Alert
                          type="info"
                          showIcon
                          banner
                          style={{ marginBottom: 8 }}
                          message="未覆盖字段即时生效：接口 → 目录链 → 系统默认（默认关闭）。角色码在宿主机配置，不认用户类型。"
                        />
                        <PrivacySection />
                      </>
                    ),
                  }
                : null,
            ].filter(Boolean) as any
          }
        />
      </Form>
    </Modal>
  );
};

export default DirectoryFormModal;
