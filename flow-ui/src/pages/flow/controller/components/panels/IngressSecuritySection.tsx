/**
 * 入站防护：继承全局 yu.flow.ingress，或按接口覆盖。改完需发布后生效。
 */
import { PrincipalMatchRuleList } from '@/components/flow/PrincipalMatchFields';
import { EMPTY_CALLER_RULE, type CallerAccessRule, previewAccessControl } from '@/utils/principalMatch';
import React from 'react';
import { Alert, Col, Divider, Form, Row, Switch } from 'antd';
import {
  ProFormDependency,
  ProFormDigit,
  ProFormRadio,
  ProFormText,
} from '@ant-design/pro-components';

const AUTH_OPTIONS = [
  { label: '继承全局', value: 'INHERIT' },
  { label: '无鉴权 (NONE)', value: 'NONE' },
  { label: '宿主登录 (HOST)', value: 'HOST' },
  { label: '开放平台 (OPEN)', value: 'OPEN' },
];

const IngressSecuritySection: React.FC = () => {
  return (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="接口级入站防护需发布后生效；目录级覆盖对未设置字段即时生效"
        description="合并顺序：接口显式值 → 所属目录链 → 全局 yu.flow.ingress。调用方策略：接口未启用时用目录（再沿父目录）。开放入口 /flow-api/open/** 始终走开放鉴权与 grant。调用方策略仅对 HOST（及未放开匿名时的 NONE→HOST）生效；OPEN 以平台授权为准。"
      />

      <ProFormDependency name={['secAuthMode']}>
        {({ secAuthMode }) =>
          secAuthMode === 'NONE' ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="NONE = 匿名可调"
              description="生产默认禁止保存/发布 authMode=NONE。确需公开接口请改用开放平台 OPEN，或由管理员设置 YU_FLOW_ALLOW_INGRESS_AUTH_NONE=true。匿名模式不能启用调用方策略。"
            />
          ) : null
        }
      </ProFormDependency>

      <Row gutter={[16, 0]}>
        <Col span={24}>
          <ProFormRadio.Group
            name="secAuthMode"
            label="鉴权方式"
            options={AUTH_OPTIONS}
            radioType="button"
            fieldProps={{ buttonStyle: 'solid' }}
          />
        </Col>

        <ProFormDependency name={['secAuthMode', 'secAntiReplayOverride']}>
          {({ secAuthMode, secAntiReplayOverride }) => {
            const openLike = secAuthMode === 'OPEN' || secAuthMode === 'INHERIT';
            if (!openLike) return null;
            return (
              <Col span={24}>
                <Form.Item
                  label="防重放（仅 OPEN 生效）"
                  extra="关闭「覆盖」时继承全局 default-anti-replay"
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Form.Item name="secAntiReplayOverride" valuePropName="checked" noStyle>
                      <Switch checkedChildren="覆盖" unCheckedChildren="继承" />
                    </Form.Item>
                    {secAntiReplayOverride ? (
                      <Form.Item name="secAntiReplay" valuePropName="checked" noStyle>
                        <Switch checkedChildren="开" unCheckedChildren="关" />
                      </Form.Item>
                    ) : null}
                  </div>
                </Form.Item>
              </Col>
            );
          }}
        </ProFormDependency>

        <Col span={24}>
          <Form.Item
            label="限流"
            extra="关闭「覆盖」时继承全局 default-rate-limit-*"
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <Form.Item name="secRateLimitOverride" valuePropName="checked" noStyle>
                <Switch checkedChildren="覆盖" unCheckedChildren="继承" />
              </Form.Item>
              <ProFormDependency name={['secRateLimitOverride']}>
                {({ secRateLimitOverride }) =>
                  secRateLimitOverride ? (
                    <>
                      <Form.Item name="secRateLimitEnabled" valuePropName="checked" noStyle>
                        <Switch checkedChildren="启用" unCheckedChildren="关闭" />
                      </Form.Item>
                      <ProFormDigit
                        name="secRateLimitQps"
                        label="QPS"
                        min={1}
                        max={100000}
                        fieldProps={{ precision: 0, style: { width: 120 } }}
                        formItemProps={{ style: { marginBottom: 0 } }}
                      />
                    </>
                  ) : null
                }
              </ProFormDependency>
            </div>
          </Form.Item>
        </Col>

        <Col span={24}>
          <Form.Item
            label="IP 白名单"
            extra="关闭「覆盖」继承全局；覆盖后留空表示本接口不限制"
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <Form.Item name="secIpOverride" valuePropName="checked" noStyle>
                <Switch checkedChildren="覆盖" unCheckedChildren="继承" />
              </Form.Item>
              <ProFormDependency name={['secIpOverride']}>
                {({ secIpOverride }) =>
                  secIpOverride ? (
                    <ProFormText
                      name="secIpAllowlist"
                      placeholder="如 10.0.0.1,192.168.1.0/24"
                      formItemProps={{ style: { marginBottom: 0 } }}
                    />
                  ) : null
                }
              </ProFormDependency>
            </div>
          </Form.Item>
        </Col>

        <Col span={24}>
          <Form.Item
            label="执行超时"
            extra="关闭「覆盖」继承全局 INGRESS_DEFAULT_TIMEOUT_MS；≤0 表示不限制。改完需发布"
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <Form.Item name="secTimeoutOverride" valuePropName="checked" noStyle>
                <Switch checkedChildren="覆盖" unCheckedChildren="继承" />
              </Form.Item>
              <ProFormDependency name={['secTimeoutOverride']}>
                {({ secTimeoutOverride }) =>
                  secTimeoutOverride ? (
                    <ProFormDigit
                      name="secTimeoutMs"
                      label="毫秒"
                      min={0}
                      max={600000}
                      fieldProps={{ precision: 0, style: { width: 140 } }}
                      formItemProps={{ style: { marginBottom: 0 } }}
                      extra="0 = 不限制"
                    />
                  ) : null
                }
              </ProFormDependency>
            </div>
          </Form.Item>
        </Col>
      </Row>

      <Divider orientation="left" plain>
        谁可以调用
      </Divider>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="多行允许规则，与 OSS 访问规则同一套身份勾选"
        description="启用后未命中任何一行即拒绝（403）。开放应用不会被「任何已登录」覆盖，需单独一行。关闭时只做鉴权方式门禁。需发布后生效。"
      />

      <ProFormDependency name={['secAuthMode', 'secCallerEnabled', 'secCallerRules', 'privacyMode', 'privacyRules']}>
        {({ secAuthMode, secCallerEnabled, secCallerRules, privacyMode, privacyRules }) => (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={previewAccessControl({
                callerEnabled: !!secCallerEnabled,
                callerRules: secCallerRules,
                privacyEnabled: privacyMode !== 'OFF',
                privacyRules,
              })}
            />
            {secAuthMode === 'OPEN' && secCallerEnabled ? (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 16 }}
                message="OPEN 模式下调用方策略不参与匹配"
                description="第三方访问以开放平台 grant 为准；此处配置仅在改回 HOST 后生效。"
              />
            ) : null}
            {secAuthMode === 'NONE' && secCallerEnabled ? (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 16 }}
                message="NONE 不能启用调用方策略"
                description="请先将鉴权方式改为 HOST，或关闭调用方策略后再保存。"
              />
            ) : null}

            <Row gutter={[16, 0]}>
              <Col span={24}>
                <Form.Item
                  label="启用调用方策略"
                  extra="需发布后生效；匹配失败返回 403 INGRESS_CALLER_DENIED"
                  style={{ marginBottom: 8 }}
                >
                  <Form.Item name="secCallerEnabled" valuePropName="checked" noStyle>
                    <Switch checkedChildren="开" unCheckedChildren="关" />
                  </Form.Item>
                </Form.Item>
              </Col>
              {secCallerEnabled ? (
                <Col span={24}>
                  <Form.Item name="secCallerRules" noStyle>
                    <PrincipalMatchRuleList<CallerAccessRule>
                      accent="ingress"
                      createEmpty={() => ({ ...EMPTY_CALLER_RULE })}
                      description="一行一类人，组间或。结果只有「允许」。"
                    />
                  </Form.Item>
                </Col>
              ) : null}
            </Row>
          </>
        )}
      </ProFormDependency>
    </>
  );
};

export default IngressSecuritySection;
