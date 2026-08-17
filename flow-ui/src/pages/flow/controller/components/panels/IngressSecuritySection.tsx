/**
 * 入站防护：继承全局 yu.flow.ingress，或按接口覆盖。改完需发布后生效。
 */
import React from 'react';
import { Alert, Col, Divider, Form, Row, Switch } from 'antd';
import {
  ProFormDependency,
  ProFormDigit,
  ProFormRadio,
  ProFormText,
} from '@ant-design/pro-components';
import CallerPolicyFields from '@/components/flow/CallerPolicyFields';

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
        调用方策略
      </Divider>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="按用户类型 / 角色 / 权限限制谁能调用本接口"
        description="依赖宿主实现 FlowHostPrincipalProvider（内置 JWT 固定为 ADMIN）。下拉选项仅在宿主实现 FlowHostIdentityCatalogProvider 后出现。关闭时仅校验鉴权方式。维度全空且开启时，仅要求能解析到主体。"
      />

      <ProFormDependency name={['secAuthMode', 'secCallerEnabled']}>
        {({ secAuthMode, secCallerEnabled }) => (
          <>
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
              <CallerPolicyFields
                names={{
                  enabled: 'secCallerEnabled',
                  match: 'secCallerMatch',
                  userTypes: 'secCallerUserTypes',
                  roles: 'secCallerRoles',
                  permissions: 'secCallerPermissions',
                  deptIds: 'secCallerDeptIds',
                  deptIncludeChildren: 'secCallerDeptIncludeChildren',
                  userIds: 'secCallerUserIds',
                }}
                enabled={!!secCallerEnabled}
                enabledLabel="启用调用方策略"
                enabledExtra="需发布后生效；匹配失败返回 403 INGRESS_CALLER_DENIED"
              />
            </Row>
          </>
        )}
      </ProFormDependency>
    </>
  );
};

export default IngressSecuritySection;
