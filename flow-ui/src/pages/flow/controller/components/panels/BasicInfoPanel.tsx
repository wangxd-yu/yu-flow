/**
 * BasicInfoPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「基础信息」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理接口的元数据（描述、模块、版本、优先级、标签）和返回包装配置
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React from 'react';
import { Row, Col } from 'antd';
import type { FormInstance } from 'antd';
import {
  ProForm, ProFormText, ProFormSelect, ProFormDigit, ProFormTextArea,
} from '@ant-design/pro-components';
import { GiftOutlined, InfoCircleOutlined } from '@ant-design/icons';
import ResponseWrapperSection from './ResponseWrapperSection';

// ═══════════════════════════════════════════════════════════════════════════
//  Props
// ═══════════════════════════════════════════════════════════════════════════

export interface BasicInfoPanelProps {
  form: FormInstance;
}

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const BasicInfoPanel: React.FC<BasicInfoPanelProps> = ({ form }) => {
  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '20px 0' }}>
      <ProForm
        form={form}
        submitter={false}
        layout="vertical"
      >
        <Row gutter={24}>
          {/* ── 左侧：接口元信息 (占位较小) ── */}
          <Col xs={24} lg={8}>
            <div
              style={{
                background: '#fff',
                border: '1px solid #ebeef5',
                borderRadius: 8,
                padding: '20px 24px',
                marginBottom: 24,
              }}
            >
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                marginBottom: 20,
                paddingBottom: 12,
                borderBottom: '1px solid #ebeef5',
              }}>
                <InfoCircleOutlined style={{ fontSize: 16, color: '#1677ff' }} />
                <span style={{ fontSize: 15, fontWeight: 600, color: '#1d2129' }}>
                  接口元信息
                </span>
              </div>

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
                placeholder="输入后回车添加标签"
                fieldProps={{ maxTagCount: 5, tokenSeparators: [','] }}
              />
            </div>
          </Col>

          {/* ── 右侧：返回包装配置 (占位较大) ── */}
          <Col xs={24} lg={16}>
            <div
              style={{
                background: '#fafbfc',
                border: '1px solid #ebeef5',
                borderRadius: 8,
                padding: '20px 24px',
                marginBottom: 24,
              }}
            >
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                marginBottom: 20,
                paddingBottom: 12,
                borderBottom: '1px solid #ebeef5',
              }}>
                <GiftOutlined style={{ fontSize: 16, color: '#1677ff' }} />
                <span style={{ fontSize: 15, fontWeight: 600, color: '#1d2129' }}>
                  返回包装配置
                </span>
                <span style={{ fontSize: 12, color: '#a0a5b1', marginLeft: 4 }}>
                  选择基座模板并可通过开关进行局部重载
                </span>
              </div>

              {/* 响应模板配置组件被纳入 ProForm 管辖范围内 */}
              <ResponseWrapperSection />
            </div>
          </Col>
        </Row>
      </ProForm>
    </div>
  );
};

export default React.memo(BasicInfoPanel);
