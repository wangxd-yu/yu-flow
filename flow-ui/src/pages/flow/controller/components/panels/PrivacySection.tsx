/**
 * 出站隐私拦截：库内密文识别 → 按所选方案解密 → 按宿主角色脱敏或明文。
 */
import {
  getHostPrivacyProfiles,
  PRIVACY_BUILTIN_PROFILE_ID,
  privacySpecLabel,
  type HostPrivacyProfile,
} from '@/services/flow/hostConfig';
import { Alert, Col, Form, Input, Radio, Row, Select, Switch, Typography } from 'antd';
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

const PrivacySection: React.FC = () => {
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
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="接口级需发布后生效；目录未覆盖字段即时生效"
        description="合并顺序：接口显式值 → 所属目录链 → 系统默认（默认关闭）。解密算法、密钥和脱敏方式在「宿主机配置 → 隐私解密与脱敏方案」维护，本处只选方案。谁看明文仍由宿主角色决定。"
      />

      <Row gutter={[16, 0]}>
        <Col span={24}>
          <Form.Item
            name="privacyMode"
            label="本级策略"
            extra="关闭=本目录/接口不拦。继承=未填项沿上级。"
          >
            <Radio.Group optionType="button" buttonStyle="solid" options={MODE_OPTIONS} />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item
            name="privacyInherit"
            label="未填项继续向上继承"
            valuePropName="checked"
            extra="关闭后不再读取父目录（仍套系统默认识别规则）"
          >
            <Switch checkedChildren="继承" unCheckedChildren="截断" />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item
            name="privacyProfileId"
            label="解密 / 脱敏方案"
            extra="留空继承上级；无上级时用系统内置。方案在「平台设置 → 宿主机配置」维护。"
          >
            <Select
              allowClear
              showSearch
              loading={loading}
              placeholder="继承上级"
              optionFilterProp="label"
              options={profileOptions}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="privacyFieldSuffix"
            label="密文列后缀"
            extra="如 phone_encrypt；留空则用方案或默认 _encrypt"
          >
            <Input placeholder="留空则继承" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="privacyExtraFields"
            label="补充字段（无后缀）"
            extra="精确匹配，忽略大小写"
          >
            <Select mode="tags" tokenSeparators={[',']} placeholder="如 id_no、mobile" />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item
            label="输出去掉后缀"
            extra="phone_encrypt → phone，包装 JSONPath 请写去后缀后的名"
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Form.Item name="privacyStripSuffixOverride" valuePropName="checked" noStyle>
                <Switch checkedChildren="覆盖" unCheckedChildren="继承" />
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, cur) =>
                prev.privacyStripSuffixOverride !== cur.privacyStripSuffixOverride
              }>
                {({ getFieldValue }) =>
                  getFieldValue('privacyStripSuffixOverride') ? (
                    <Form.Item name="privacyStripSuffix" valuePropName="checked" noStyle>
                      <Switch checkedChildren="去掉" unCheckedChildren="保留" />
                    </Form.Item>
                  ) : null
                }
              </Form.Item>
            </div>
          </Form.Item>
        </Col>
        <Col span={24}>
          <Typography.Text type="secondary">
            明文 / 脱敏角色仍在「宿主机配置 → 当前用户解析」填写，两档都命中时明文优先。
          </Typography.Text>
        </Col>
      </Row>
    </>
  );
};

export default PrivacySection;
