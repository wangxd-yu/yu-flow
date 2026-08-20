/**
 * 脱敏规则列表：与「隐私方案」同一套（精确/包含、别名、留头尾等）。
 * 目录 / 接口填写后整表覆盖方案，不填则继承方案。
 */
import type { PrivacyMaskRule } from '@/services/flow/hostConfig';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, InputNumber, Radio, Select } from 'antd';
import React from 'react';
import './PrivacyMaskRuleList.less';

export const PRIVACY_MASK_MATCH_OPTIONS = [
  { value: 'EXACT', label: '精确' },
  { value: 'CONTAINS', label: '包含' },
];

export const PRIVACY_MASK_METHOD_OPTIONS = [
  { value: 'KEEP_HEAD_TAIL', label: '留头尾、藏中间' },
  { value: 'NAME_KEEP_ENDS', label: '姓名藏中间' },
  { value: 'PHONE', label: '手机前3后4' },
  { value: 'ID_CARD', label: '身份证留首尾' },
  { value: 'KEEP_HEAD', label: '只留开头' },
  { value: 'KEEP_TAIL', label: '只留末尾' },
  { value: 'FULL', label: '全部隐藏' },
];

export const EMPTY_PRIVACY_MASK_RULE: PrivacyMaskRule = {
  matchMode: 'EXACT',
  method: 'KEEP_HEAD',
  keepHead: 1,
  keepTail: 0,
  aliases: [],
};

export function normalizePrivacyMaskRule(
  raw?: Partial<PrivacyMaskRule> | null,
): PrivacyMaskRule {
  const method = String(raw?.method || 'KEEP_HEAD_TAIL').trim() || 'KEEP_HEAD_TAIL';
  const matchMode = raw?.matchMode === 'CONTAINS' ? 'CONTAINS' : 'EXACT';
  const aliases = Array.isArray(raw?.aliases)
    ? raw!.aliases.map((x) => String(x || '').trim()).filter(Boolean)
    : [];
  return {
    aliases,
    matchMode,
    method,
    keepHead: raw?.keepHead ?? null,
    keepTail: raw?.keepTail ?? null,
    maskLen: raw?.maskLen ?? null,
    maskChar: raw?.maskChar,
  };
}

function needsKeep(method?: string): boolean {
  return method === 'KEEP_HEAD_TAIL' || method === 'KEEP_HEAD' || method === 'KEEP_TAIL';
}

type Props = {
  /** Form.List 字段名，方案页用 rules，目录/接口用 privacyMaskRules */
  name?: string;
};

const PrivacyMaskRuleList: React.FC<Props> = ({ name = 'rules' }) => (
  <Form.List name={name}>
    {(fields, { add, remove }) => (
      <div className="host-privacy-rule-list">
        {fields.map(({ key, name: fieldName, ...restField }) => (
          <div key={key} className="host-privacy-rule">
            <div className="host-privacy-rule-row">
              <Form.Item
                {...restField}
                name={[fieldName, 'matchMode']}
                initialValue="EXACT"
                className="host-privacy-rule-match"
              >
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  size="small"
                  options={PRIVACY_MASK_MATCH_OPTIONS}
                />
              </Form.Item>
              <Form.Item
                {...restField}
                name={[fieldName, 'aliases']}
                rules={[{ required: true, message: '填写别名' }]}
                className="host-privacy-rule-grow"
              >
                <Select
                  mode="tags"
                  size="small"
                  tokenSeparators={[',']}
                  placeholder="loginPhone、phone"
                />
              </Form.Item>
              <Button
                type="text"
                size="small"
                danger
                htmlType="button"
                icon={<MinusCircleOutlined />}
                onClick={() => remove(fieldName)}
              />
            </div>
            <div className="host-privacy-rule-row">
              <Form.Item
                {...restField}
                name={[fieldName, 'method']}
                rules={[{ required: true, message: '选择方式' }]}
                className="host-privacy-rule-grow"
              >
                <Select size="small" options={PRIVACY_MASK_METHOD_OPTIONS} placeholder="脱敏方式" />
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {({ getFieldValue }) => {
                  const method = getFieldValue([name, fieldName, 'method']);
                  if (!needsKeep(method)) return null;
                  return (
                    <>
                      {method === 'KEEP_HEAD' || method === 'KEEP_HEAD_TAIL' ? (
                        <Form.Item
                          {...restField}
                          name={[fieldName, 'keepHead']}
                          className="host-privacy-rule-num"
                        >
                          <InputNumber size="small" min={0} max={32} addonBefore="头" />
                        </Form.Item>
                      ) : null}
                      {method === 'KEEP_TAIL' || method === 'KEEP_HEAD_TAIL' ? (
                        <Form.Item
                          {...restField}
                          name={[fieldName, 'keepTail']}
                          className="host-privacy-rule-num"
                        >
                          <InputNumber size="small" min={0} max={32} addonBefore="尾" />
                        </Form.Item>
                      ) : null}
                    </>
                  );
                }}
              </Form.Item>
            </div>
          </div>
        ))}
        <Button
          type="dashed"
          size="small"
          block
          htmlType="button"
          icon={<PlusOutlined />}
          onClick={() => add({ ...EMPTY_PRIVACY_MASK_RULE, aliases: [] })}
        >
          添加规则
        </Button>
      </div>
    )}
  </Form.List>
);

export default PrivacyMaskRuleList;
