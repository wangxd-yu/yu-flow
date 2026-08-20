/**
 * 目录 / 平台默认共用的「访问控制」表单区块。
 * 平台默认没有上级可继承，也不含路径前缀。
 */
import { PrincipalMatchRuleList } from '@/components/flow/PrincipalMatchFields';
import PrivacySection from '@/pages/flow/controller/components/panels/PrivacySection';
import { EMPTY_CALLER_RULE, type CallerAccessRule } from '@/utils/principalMatch';
import { Alert, Form, Input, InputNumber, Modal, Select, Switch, Tooltip } from 'antd';
import React from 'react';

export type DirectoryAccessVariant = 'directory' | 'platform';

const AUTH_OPTIONS_DIRECTORY = [
  { label: '继承上级/全局', value: 'INHERIT' },
  { label: '无鉴权 (NONE)', value: 'NONE' },
  { label: '宿主登录 (HOST)', value: 'HOST' },
  { label: '开放平台 (OPEN)', value: 'OPEN' },
];

const AUTH_OPTIONS_PLATFORM = AUTH_OPTIONS_DIRECTORY.filter((o) => o.value !== 'INHERIT');

const DirectoryAccessPane: React.FC<{ variant: DirectoryAccessVariant }> = ({
  variant,
}) => {
  const form = Form.useFormInstance();
  const platform = variant === 'platform';
  const rateOverride = Form.useWatch('secRateLimitOverride');
  const ipOverride = Form.useWatch('secIpOverride');
  const timeoutOverride = Form.useWatch('secTimeoutOverride');
  const callerOverride = Form.useWatch('secCallerOverride');
  const callerOn = !!Form.useWatch('secCallerEnabled');
  const authMode = Form.useWatch('secAuthMode');

  const showRateFields = platform || rateOverride;
  const showTimeoutField = platform || timeoutOverride;
  const showIpField = platform || ipOverride;
  const showCallerEditor = platform || callerOverride;

  return (
    <div className="dir-access-pane">
      <div className="dir-access-summary">
        {platform
          ? '目录和接口未覆盖时使用这里，保存后即时生效。不含路径前缀。'
          : '未覆盖即时生效：接口 → 目录链 → 平台默认'}
      </div>

      <div className="dir-form-section dir-security-section">
        <div className="dir-section-title">基础防护</div>
        <div className="dir-protect-list">
          <div className="dir-protect-row">
            <span>鉴权</span>
            <Form.Item name="secAuthMode" initialValue={platform ? 'NONE' : 'INHERIT'} noStyle>
              <Select
                style={{ flex: 1, minWidth: 0 }}
                options={platform ? AUTH_OPTIONS_PLATFORM : AUTH_OPTIONS_DIRECTORY}
              />
            </Form.Item>
          </div>
          <div className="dir-protect-row">
            <span>
              限流
              <Tooltip title="生产改限流请评估影响面">
                <i className="dir-protect-tip">?</i>
              </Tooltip>
            </span>
            {platform ? null : (
              <Form.Item name="secRateLimitOverride" valuePropName="checked" noStyle>
                <Switch size="small" />
              </Form.Item>
            )}
            {!showRateFields ? (
              <span className="dir-inherit-text">继承上级</span>
            ) : (
              <>
                <Form.Item name="secRateLimitEnabled" valuePropName="checked" noStyle>
                  <Switch size="small" checkedChildren="开" unCheckedChildren="关" />
                </Form.Item>
                <Form.Item name="secRateLimitQps" noStyle>
                  <InputNumber min={1} max={100000} placeholder="QPS" style={{ width: 96 }} />
                </Form.Item>
              </>
            )}
          </div>
          <div className="dir-protect-row">
            <span>超时</span>
            {platform ? null : (
              <Form.Item name="secTimeoutOverride" valuePropName="checked" noStyle>
                <Switch size="small" />
              </Form.Item>
            )}
            {!showTimeoutField ? (
              <span className="dir-inherit-text">继承上级</span>
            ) : (
              <Form.Item name="secTimeoutMs" noStyle>
                <InputNumber min={0} placeholder="毫秒" style={{ width: 120 }} />
              </Form.Item>
            )}
          </div>
          <div className="dir-protect-row">
            <span>IP 白名单</span>
            {platform ? null : (
              <Form.Item name="secIpOverride" valuePropName="checked" noStyle>
                <Switch size="small" />
              </Form.Item>
            )}
            {platform ? (
              <span className="dir-custom-text">全局默认</span>
            ) : (
              <span className={ipOverride ? 'dir-custom-text' : 'dir-inherit-text'}>
                {ipOverride ? '自定义规则' : '继承上级'}
              </span>
            )}
          </div>
          {showIpField ? (
            <Form.Item name="secIpAllowlist" style={{ marginBottom: 0 }}>
              <Input placeholder="逗号分隔 IP/CIDR；空串=明确不限制" allowClear />
            </Form.Item>
          ) : null}
        </div>
      </div>

      <div className="dir-form-section dir-caller-section">
        <div className="dir-section-head">
          <div className="dir-section-head-top">
            <div className="dir-section-title">谁可以调用</div>
            <div className="dir-override-control">
              {showCallerEditor ? (
                <>
                  <span>启用</span>
                  <Form.Item
                    name="secCallerEnabled"
                    valuePropName="checked"
                    noStyle
                    getValueFromEvent={(checked: boolean) => {
                      if (checked && form.getFieldValue('secAuthMode') === 'NONE') {
                        Modal.warning({
                          title: '无法启用调用方策略',
                          content: '匿名 (NONE) 不能启用调用方策略，请先将鉴权方式改为 HOST。',
                        });
                        return false;
                      }
                      return checked;
                    }}
                  >
                    <Switch size="small" checkedChildren="开" unCheckedChildren="关" />
                  </Form.Item>
                </>
              ) : null}
              {platform ? null : (
                <>
                  <span>{callerOverride ? '本级覆盖' : '继承上级'}</span>
                  <Form.Item name="secCallerOverride" valuePropName="checked" noStyle>
                    <Switch size="small" />
                  </Form.Item>
                </>
              )}
            </div>
          </div>
          <div className="dir-section-desc">
            只管能不能调；启用后未命中即拒绝
          </div>
        </div>
        {!showCallerEditor ? (
          <div className="dir-empty-state">
            继承上级目录；上级也未配置时使用平台默认
          </div>
        ) : (
          <>
            {authMode === 'OPEN' && callerOn ? (
              <Alert
                type="warning"
                showIcon
                banner
                className="dir-inline-alert"
                message="OPEN 下调用方策略不参与匹配，仅在改回 HOST 后生效。"
              />
            ) : null}
            {authMode === 'NONE' && callerOn ? (
              <Alert
                type="error"
                showIcon
                banner
                className="dir-inline-alert"
                message="NONE 不能启用调用方策略，请先改为 HOST 或关闭策略。"
              />
            ) : null}
            {callerOn ? (
              <Form.Item name="secCallerRules" noStyle>
                <PrincipalMatchRuleList<CallerAccessRule>
                  accent="ingress"
                  createEmpty={() => ({ ...EMPTY_CALLER_RULE })}
                />
              </Form.Item>
            ) : (
              <div className="dir-empty-state">
                {platform
                  ? '未启用：目录和接口未覆盖时不限制调用方身份'
                  : '已覆盖但未启用：不限制调用方身份'}
              </div>
            )}
          </>
        )}
      </div>

      <div className="dir-form-section">
        <div className="dir-section-title">谁看什么</div>
        <PrivacySection compact variant={platform ? 'platform' : 'overlay'} />
      </div>
    </div>
  );
};

export default DirectoryAccessPane;
