/**
 * 出站隐私拦截：库内密文识别 → 按所选方案解密 → 按规则明文 / 脱敏 / 去掉字段。
 */
import PrivacyMaskRuleList from '@/components/flow/PrivacyMaskRuleList';
import { FieldActionEditor, PrincipalMatchRuleList } from '@/components/flow/PrincipalMatchFields';
import {
  EMPTY_PRIVACY_RULE,
  type PrivacyAccessRule,
} from '@/utils/principalMatch';
import {
  getHostPrivacyProfiles,
  PRIVACY_BUILTIN_PROFILE_ID,
  privacySpecLabel,
  type HostPrivacyProfile,
} from '@/services/flow/hostConfig';
import { Alert, Col, Form, Input, Radio, Row, Select, Switch } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';

const MODE_OPTIONS = [
  { label: '继承上级', value: 'INHERIT' },
  { label: '启用拦截', value: 'ON' },
  { label: '关闭拦截', value: 'OFF' },
];

function optionLabel(p: HostPrivacyProfile): string {
  const n = p.rules?.length ?? 0;
  return `${p.name || p.id}（${privacySpecLabel(p)} · ${n} 条脱敏）`;
}

const PrivacySection: React.FC<{
  compact?: boolean;
  /** overlay=目录/接口可继承上级；platform=全局兜底（含默认方案） */
  variant?: 'overlay' | 'platform';
}> = ({ compact, variant = 'overlay' }) => {
  const platform = variant === 'platform';
  const [profiles, setProfiles] = useState<HostPrivacyProfile[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getHostPrivacyProfiles()
      .then((data) => {
        if (!cancelled) setProfiles(Array.isArray(data.profiles) ? data.profiles : []);
      })
      .catch(() => {
        if (!cancelled) setProfiles([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const profileOptions = useMemo(
    () => [
      {
        value: PRIVACY_BUILTIN_PROFILE_ID,
        label: '系统内置（YAML SM4 + 默认手机/姓名/身份证）',
      },
      ...profiles
        .filter((p) => p.id)
        .map((p) => ({ value: p.id as string, label: optionLabel(p) })),
    ],
    [profiles],
  );

  return (
    <>
      {compact || platform ? null : (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="接口级需发布后生效；目录未覆盖字段即时生效"
          description="合并顺序：接口显式值 → 所属目录链 → 平台默认规则 → 系统默认（默认关闭）。解密算法在方案里配；脱敏规则目录/接口可覆盖方案（同一套精确/包含/留头尾）。谁看明文按下面的规则从上到下第一条命中；未命中一律脱敏。"
        />
      )}

      <Row gutter={compact ? [12, 0] : [16, 0]}>
        {platform ? (
          <Col span={24}>
            <div className="dir-section-desc" style={{ marginBottom: 8 }}>
              目录/接口未配置时继承这里。从上到下第一条命中；未命中脱敏。方案内容在「平台设置 → 宿主机配置」维护。
            </div>
          </Col>
        ) : (
          <>
            <Col span={compact ? 16 : 24}>
              <Form.Item
                name="privacyMode"
                label="本级策略"
                extra={compact ? undefined : '关闭=本目录/接口不拦。继承=未填项沿上级。'}
                tooltip={compact ? '关闭=本级不拦。继承=未填项沿上级。' : undefined}
              >
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  size={compact ? 'small' : 'middle'}
                  options={MODE_OPTIONS}
                />
              </Form.Item>
            </Col>
            <Col span={compact ? 8 : 24}>
              <Form.Item
                name="privacyInherit"
                label="未填项"
                valuePropName="checked"
                extra={compact ? undefined : '关闭后不再读取父目录（仍套系统默认识别规则）'}
                tooltip={compact ? '关闭后不再读取父目录' : undefined}
              >
                <Switch
                  size={compact ? 'small' : 'default'}
                  checkedChildren="继承"
                  unCheckedChildren="截断"
                />
              </Form.Item>
            </Col>
          </>
        )}
        <Col span={24}>
          <Form.Item
            name="privacyProfileId"
            label="解密 / 脱敏方案"
            extra={
              platform
                ? '目录/接口未选方案时用这里。方案内容在「平台设置 → 宿主机配置」维护。'
                : compact
                  ? undefined
                  : '留空继承上级。脱敏规则可在下方覆盖方案，与方案编辑器相同。'
            }
            tooltip={
              compact
                ? platform
                  ? '目录/接口未选方案时用这里'
                  : '留空继承上级；无上级时用系统内置'
                : undefined
            }
          >
            <Select
              allowClear={!platform}
              showSearch
              loading={loading}
              placeholder={platform ? '系统内置' : '继承上级'}
              optionFilterProp="label"
              options={profileOptions}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="privacyFieldSuffix"
            label="密文列后缀"
            extra={compact ? undefined : '如 phone_encrypt；留空则用方案或默认 _encrypt'}
            tooltip={compact ? '如 phone_encrypt；留空则用方案或默认 _encrypt' : undefined}
          >
            <Input placeholder={platform ? '留空则用方案或默认' : '留空则继承'} allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="privacyExtraFields"
            label="补充字段"
            extra={compact ? undefined : '无 _encrypt 后缀也要解密的列。脱敏长什么样在下面「脱敏规则」或所选方案里配。'}
            tooltip={compact ? '无后缀字段，精确匹配、忽略大小写' : undefined}
          >
            <Select mode="tags" tokenSeparators={[',']} placeholder="如 loginPhone、authPhone" />
          </Form.Item>
        </Col>
        {platform ? null : (
          <Col span={24}>
            <Form.Item
              label="脱敏规则"
              extra={
                compact
                  ? undefined
                  : '与「隐私方案」同一套：精确/包含、任意字段名、留头尾或前3后4。不填则用所选方案；填写后整表覆盖方案，不逐条合并。'
              }
              tooltip={compact ? '不填用方案；填写后覆盖方案' : undefined}
            >
              <PrivacyMaskRuleList name="privacyMaskRules" />
            </Form.Item>
          </Col>
        )}
        <Col span={24}>
          <Form.Item
            label="输出去掉后缀"
            extra={compact ? undefined : 'phone_encrypt → phone，包装 JSONPath 请写去后缀后的名'}
            tooltip={compact ? '覆盖后 phone_encrypt → phone' : undefined}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {platform ? (
                <Form.Item name="privacyStripSuffix" valuePropName="checked" noStyle>
                  <Switch
                    size={compact ? 'small' : 'default'}
                    checkedChildren="去掉"
                    unCheckedChildren="保留"
                  />
                </Form.Item>
              ) : (
                <>
                  <Form.Item name="privacyStripSuffixOverride" valuePropName="checked" noStyle>
                    <Switch
                      size={compact ? 'small' : 'default'}
                      checkedChildren="覆盖"
                      unCheckedChildren="继承"
                    />
                  </Form.Item>
                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, cur) =>
                      prev.privacyStripSuffixOverride !== cur.privacyStripSuffixOverride
                    }
                  >
                    {({ getFieldValue }) =>
                      getFieldValue('privacyStripSuffixOverride') ? (
                        <Form.Item name="privacyStripSuffix" valuePropName="checked" noStyle>
                          <Switch
                            size={compact ? 'small' : 'default'}
                            checkedChildren="去掉"
                            unCheckedChildren="保留"
                          />
                        </Form.Item>
                      ) : null
                    }
                  </Form.Item>
                </>
              )}
            </div>
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item
            label={compact || platform ? undefined : '谁看什么'}
            extra={compact || platform ? undefined : '整表覆盖上级，不逐行合并。Flow 管理端权限 flow:privacy:reveal / * 仍可作为控制台预览逃生口。'}
            tooltip={compact ? (platform ? '未命中一律脱敏' : '整表覆盖上级，不逐行合并') : undefined}
          >
            <Form.Item name="privacyRules" noStyle>
              <PrincipalMatchRuleList<PrivacyAccessRule>
                accent="privacy"
                createEmpty={() => ({ ...EMPTY_PRIVACY_RULE, principals: 'MATCH', privacy: 'MASK' })}
                extra={(rule, patch) => (
                  <>
                    <div className="principal-match-row">
                      <span className="principal-match-label">出站</span>
                      <Radio.Group
                        optionType="button"
                        buttonStyle="solid"
                        size="small"
                        value={rule.privacy || 'MASK'}
                        options={[
                          { label: '脱敏', value: 'MASK' },
                          { label: '明文', value: 'REVEAL' },
                        ]}
                        onChange={(e) => patch({ privacy: e.target.value })}
                      />
                    </div>
                    <FieldActionEditor
                      value={rule.fields}
                      onChange={(fields) => patch({ fields })}
                    />
                  </>
                )}
              />
            </Form.Item>
          </Form.Item>
        </Col>
      </Row>
    </>
  );
};

export default PrivacySection;
