/**
 * BasicInfoPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「基础信息」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理接口的元数据（描述、模块、版本、优先级、标签）和返回包装配置
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React from 'react';
import { Collapse, Flex, Input } from 'antd';
import type { FormInstance } from 'antd';
import {
  ProForm, ProFormText, ProFormSelect, ProFormDigit, ProFormTextArea,
} from '@ant-design/pro-components';

// ═══════════════════════════════════════════════════════════════════════════
//  Props
// ═══════════════════════════════════════════════════════════════════════════

export interface BasicInfoPanelProps {
  form: FormInstance;
  wrapSuccess: string;
  onWrapSuccessChange: (v: string) => void;
  wrapError: string;
  onWrapErrorChange: (v: string) => void;
}

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const BasicInfoPanel: React.FC<BasicInfoPanelProps> = ({
  form, wrapSuccess, onWrapSuccessChange, wrapError, onWrapErrorChange,
}) => {
  return (
    <ProForm
      form={form}
      submitter={false}
      layout="horizontal"
      labelCol={{ span: 4 }}
      wrapperCol={{ span: 18 }}
      style={{ maxWidth: 800, margin: '0 auto', paddingTop: 8 }}
    >
      {/* ── 高级设置：自动包装统一返回体 ── */}
      <Collapse ghost size="small" style={{ marginBottom: 24 }}>
        <Collapse.Panel header="高级设置：自动包装统一返回体" key="1">
          <Flex gap={16}>
            <div style={{ flex: 1 }}>
              <div style={{ marginBottom: 8, color: '#666' }}>成功返回包装</div>
              <Input.TextArea
                rows={3}
                value={wrapSuccess}
                onChange={(e) => onWrapSuccessChange(e.target.value)}
                placeholder={'例如: { "code": 200, "data": @Result }'}
              />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ marginBottom: 8, color: '#666' }}>失败返回包装</div>
              <Input.TextArea
                rows={3}
                value={wrapError}
                onChange={(e) => onWrapErrorChange(e.target.value)}
                placeholder={'例如: { "code": 500, "msg": @Error }'}
              />
            </div>
          </Flex>
        </Collapse.Panel>
      </Collapse>

      <ProFormTextArea
        name="info"
        label="描述"
        placeholder="请输入接口描述，描述该接口的用途和注意事项"
        fieldProps={{ autoSize: { minRows: 3, maxRows: 6 } }}
      />

      <ProFormText
        name="module"
        label="模块"
        placeholder="请输入所属模块，如: user、order"
      />

      <ProFormText
        name="version"
        label="版本"
        placeholder="请输入版本号，如: v1、v2"
      />

      <ProFormDigit
        name="level"
        label="优先级"
        placeholder="1-10"
        min={1}
        max={10}
        fieldProps={{ precision: 0 }}
      />

      <ProFormSelect
        name="tags"
        label="标签"
        mode="tags"
        placeholder="输入后回车添加标签，最多 5 个"
        fieldProps={{ maxTagCount: 5, tokenSeparators: [','] }}
      />
    </ProForm>
  );
};

export default React.memo(BasicInfoPanel);
