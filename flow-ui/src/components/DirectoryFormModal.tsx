/**
 * 目录新建 / 编辑抽屉（路径前缀、访问控制）
 */
import DirectoryAccessPane from '@/components/DirectoryAccessPane';
import { fetchStackedDirectoryPathPrefix } from '@/utils/apiPathPrefix';
import {
  type CallerAccessRule,
  type PrivacyAccessRule,
  parseCallerAccessRules,
  normalizeCallerAccessRule,
} from '@/utils/principalMatch';
import { request } from '@umijs/max';
import {
  Button,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Row,
  Space,
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
import type { PrivacyMaskRule } from '@/services/flow/hostConfig';

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
  secCallerRules?: CallerAccessRule[];
  privacyMode?: 'INHERIT' | 'ON' | 'OFF';
  privacyInherit?: boolean;
  privacyProfileId?: string;
  privacyFieldSuffix?: string;
  privacyExtraFields?: string[];
  privacyMaskRules?: PrivacyMaskRule[];
  privacyStripSuffixOverride?: boolean;
  privacyStripSuffix?: boolean;
  privacyRules?: PrivacyAccessRule[];
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
    cfg.callerPolicy = {
      enabled: !!values.secCallerEnabled,
      rules: !!values.secCallerEnabled
        ? (values.secCallerRules || []).map((r) => normalizeCallerAccessRule(r))
        : [],
    };
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
      secCallerRules: [],
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
      secCallerRules: parseCallerAccessRules(cp),
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
  const [tab, setTab] = useState('basic');

  const title = useMemo(
    () =>
      mode === 'create' ? (parentId ? '新建子目录' : '新建根目录') : '编辑目录',
    [mode, parentId],
  );

  useEffect(() => {
    if (!open) return;
    setTab('basic');
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
          secCallerRules: [],
          privacyMode: 'INHERIT',
          privacyInherit: true,
          privacyProfileId: '',
          privacyFieldSuffix: '',
          privacyExtraFields: [],
          privacyMaskRules: [],
          privacyStripSuffixOverride: false,
          privacyStripSuffix: true,
          privacyRules: [],
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

  return (
    <Drawer
      className="directory-form-drawer"
      title={title}
      placement="right"
      width="min(920px, 96vw)"
      open={open}
      onClose={onCancel}
      destroyOnClose
      maskClosable={false}
      styles={{
        body: { padding: 0, display: 'flex', flexDirection: 'column' },
      }}
      footer={
        <div className="dir-form-drawer-footer">
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" loading={loading} onClick={() => void handleOk()}>
              确定
            </Button>
          </Space>
        </div>
      }
    >
      <Form
        form={form}
        layout="vertical"
        size="small"
        preserve={false}
        className="dir-form-modal dir-form-drawer"
      >
        <Tabs
          className="dir-form-tabs"
          size="small"
          activeKey={tab}
          onChange={setTab}
          items={
            [
              {
                key: 'basic',
                label: '基本信息',
                children: (
                  <>
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
                      <Form.Item
                        name="remark"
                        label="备注"
                        style={{ marginBottom: showApiExtras ? 12 : 0 }}
                      >
                        <Input.TextArea
                          rows={2}
                          maxLength={512}
                          placeholder="可选"
                        />
                      </Form.Item>
                      {showApiExtras ? (
                        <>
                          <div className="dir-section-title">路径前缀</div>
                          <p className="dir-form-hint">
                            非必填。根→叶叠加；新建接口只填相对段。改前缀不改已有接口
                            path。
                          </p>
                          {effectivePrefix ? (
                            <div className="dir-prefix-pill">
                              当前有效前缀
                              <code>{effectivePrefix}</code>
                            </div>
                          ) : null}
                          <Form.Item
                            name="pathPrefix"
                            style={{ marginBottom: 0 }}
                            extra="示例：/api/public、/v1；留空则本级不追加"
                          >
                            <Input placeholder="留空=本级不追加" allowClear />
                          </Form.Item>
                        </>
                      ) : null}
                    </div>
                  </>
                ),
              },
              showApiExtras
                ? {
                    key: 'access',
                    label: '访问控制',
                    children: <DirectoryAccessPane variant="directory" />,
                  }
                : null,
            ].filter(Boolean) as any
          }
        />
      </Form>
    </Drawer>
  );
};

export default DirectoryFormModal;
