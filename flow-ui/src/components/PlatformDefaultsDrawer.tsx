/**
 * 接口管理「平台默认」访问控制。UI 复用目录编辑的访问控制区块，写入系统配置 + 宿主主体设置。
 */
import DirectoryAccessPane from '@/components/DirectoryAccessPane';
import {
  getPlatformAccessDefaults,
  savePlatformAccessDefaults,
  PRIVACY_BUILTIN_PROFILE_ID,
} from '@/services/flow/hostConfig';
import {
  normalizeCallerAccessRule,
  normalizePrivacyAccessRule,
} from '@/utils/principalMatch';
import { Button, Drawer, Form, Space, message } from 'antd';
import React, { useEffect, useState } from 'react';
import './DirectoryFormModal.less';

type Props = {
  open: boolean;
  onClose: () => void;
};

const PlatformDefaultsDrawer: React.FC<Props> = ({ open, onClose }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const data = await getPlatformAccessDefaults();
        if (cancelled) return;
        form.setFieldsValue({
          secAuthMode: data.authMode || 'NONE',
          secRateLimitEnabled: !!data.rateLimitEnabled,
          secRateLimitQps: data.rateLimitQps,
          secIpAllowlist: data.ipAllowlist ?? '',
          secTimeoutMs: data.timeoutMs,
          secCallerEnabled: !!data.ingressCallerEnabled,
          secCallerRules: Array.isArray(data.ingressRules) ? data.ingressRules : [],
          privacyProfileId: data.privacyProfileId || PRIVACY_BUILTIN_PROFILE_ID,
          privacyFieldSuffix: data.privacyFieldSuffix ?? '',
          privacyExtraFields: Array.isArray(data.privacyExtraFields)
            ? data.privacyExtraFields
            : [],
          privacyStripSuffix: data.privacyStripSuffix !== false,
          privacyRules: Array.isArray(data.privacyRules) ? data.privacyRules : [],
        });
      } catch {
        message.error('加载平台默认失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, form]);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (values.secAuthMode === 'NONE' && values.secCallerEnabled) {
        message.error('匿名 (NONE) 时不能启用调用方策略');
        return;
      }
      setLoading(true);
      await savePlatformAccessDefaults({
        authMode: values.secAuthMode,
        rateLimitEnabled: !!values.secRateLimitEnabled,
        rateLimitQps: values.secRateLimitQps,
        ipAllowlist: values.secIpAllowlist ?? '',
        timeoutMs: values.secTimeoutMs,
        ingressCallerEnabled: !!values.secCallerEnabled,
        ingressRules: (values.secCallerRules || []).map((r: any) =>
          normalizeCallerAccessRule(r),
        ),
        privacyProfileId: values.privacyProfileId || PRIVACY_BUILTIN_PROFILE_ID,
        privacyFieldSuffix: values.privacyFieldSuffix ?? '',
        privacyExtraFields: Array.isArray(values.privacyExtraFields)
          ? values.privacyExtraFields
          : [],
        privacyStripSuffix: values.privacyStripSuffix !== false,
        privacyRules: (values.privacyRules || []).map((r: any) =>
          normalizePrivacyAccessRule(r),
        ),
      });
      message.success('平台默认已保存，即时生效');
      onClose();
    } catch (e: any) {
      if (e?.errorFields) return;
    } finally {
      setLoading(false);
    }
  };

  return (
    <Drawer
      className="directory-form-drawer"
      title="平台默认"
      placement="right"
      width="min(920px, 96vw)"
      open={open}
      onClose={onClose}
      destroyOnClose
      maskClosable={false}
      styles={{
        body: { padding: 0, display: 'flex', flexDirection: 'column' },
      }}
      footer={
        <div className="dir-form-drawer-footer">
          <Space>
            <Button onClick={onClose}>取消</Button>
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
        <div className="dir-form-body">
          <DirectoryAccessPane variant="platform" />
        </div>
      </Form>
    </Drawer>
  );
};

export default PlatformDefaultsDrawer;
