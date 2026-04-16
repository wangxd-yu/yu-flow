/**
 * ResponseWrapperSection.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「返回包装」配置组件 — 用于 API 接口定义页面的 BasicInfoPanel
 *
 * 采用「基座模板 + 局部重载 (Override)」架构，完全利用 ProForm 体系重构：
 *   1. 顶部 ProFormSelect 选择基座模板 (templateId)
 *   2. 下方三个区块，使用 ProFormDependency 实现联动
 *   3. 每个区块独立由 ProFormSwitch 控制重载开关
 *   4. Switch ON 时，允许编辑 ProFormTextArea (customXxxWrapper)
 *   5. Switch OFF 时，TextArea 禁用，展示 selectedTemplate 的预览值
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useEffect, useState, useMemo } from 'react';
import { Space, Typography, Spin, Tag, Badge } from 'antd';
import {
  ProFormSelect,
  ProFormSwitch,
  ProFormTextArea,
  ProFormDependency,
} from '@ant-design/pro-components';
import { StarOutlined, FileTextOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';

const { Text } = Typography;

// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

export interface TemplateOption {
  id: string;
  templateName: string;
  isDefault: number;
  successWrapper?: string;
  pageWrapper?: string;
  failWrapper?: string;
}

// ═══════════════════════════════════════════════════════════════════════════
//  样式 & 常量
// ═══════════════════════════════════════════════════════════════════════════

const MONO_STYLE: React.CSSProperties = {
  fontFamily: "'Menlo', 'Consolas', 'Monaco', 'Courier New', monospace",
  fontSize: 12,
  lineHeight: 1.7,
};

/** code 标签的统一内联样式 */
const CODE_TAG_STYLE: React.CSSProperties = {
  fontFamily: "'Menlo', 'Consolas', monospace",
  fontSize: 11,
  color: '#595959',
  background: '#f5f5f5',
  padding: '1px 5px',
  borderRadius: 3,
  border: '1px solid #e8e8e8',
};

/** 三种 Wrapper 的静态业务配置 */
const WRAPPER_CONFIG = {
  success: {
    label: '成功包装体',
    badgeStatus: 'success' as const,
    templateField: 'successWrapper' as const,
    formField: 'customSuccessWrapper',
    switchField: 'isCustomSuccess',
    placeholder: '{"code": 200, "data": "$"}',
    extraHint: (
      <span style={{ color: '#8c8c8c', fontSize: 12, lineHeight: 1.8 }}>
        使用 JSONPath（以 <code style={CODE_TAG_STYLE}>$</code> 开头）提取底层结果。
        例如: <code style={CODE_TAG_STYLE}>{'{"code": 200, "data": "$"}'}</code>
      </span>
    ),
  },
  page: {
    label: '分页包装体',
    badgeStatus: 'processing' as const,
    templateField: 'pageWrapper' as const,
    formField: 'customPageWrapper',
    switchField: 'isCustomPage',
    placeholder: '{"code": 200, "data": {"list": "$.items", "total": "$.total"}}',
    extraHint: (
      <span style={{ color: '#8c8c8c', fontSize: 12, lineHeight: 1.8 }}>
        底层分页结构重点包含 <code style={CODE_TAG_STYLE}>items</code>、
        <code style={CODE_TAG_STYLE}>total</code> 字段。
        示例: <code style={CODE_TAG_STYLE}>{'{"code": 200, "data": {"list": "$.items", "total": "$.total"}}'}</code>
      </span>
    ),
  },
  fail: {
    label: '失败包装体',
    badgeStatus: 'error' as const,
    templateField: 'failWrapper' as const,
    formField: 'customFailWrapper',
    switchField: 'isCustomFail',
    placeholder: '{"code": "$.code", "message": "$.msg", "data": null}',
    extraHint: (
      <span style={{ color: '#8c8c8c', fontSize: 12, lineHeight: 1.8 }}>
        底层异常结构包含 <code style={CODE_TAG_STYLE}>ok</code>, <code style={CODE_TAG_STYLE}>code</code>, <code style={CODE_TAG_STYLE}>msg</code>, <code style={CODE_TAG_STYLE}>data</code>。
        示例: <code style={CODE_TAG_STYLE}>{'{"code": "$.code", "message": "$.msg", "data": null}'}</code>
      </span>
    ),
  },
} as const;

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const ResponseWrapperSection: React.FC = () => {
  const [templates, setTemplates] = useState<TemplateOption[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchTemplates();
  }, []);

  const fetchTemplates = async () => {
    setLoading(true);
    try {
      const res = await request('/flow-api/response-templates/list', { method: 'GET' });
      setTemplates(Array.isArray(res?.data || res) ? (res?.data || res) : []);
    } catch {
      setTemplates([]);
    } finally {
      setLoading(false);
    }
  };

  // 生成下拉选项
  const templateOptions = useMemo(() => {
    return templates.map((t) => ({
      value: t.id,
      label: (
        <Space>
          {t.templateName}
          {t.isDefault === 1 && (
            <Tag icon={<StarOutlined />} color="gold" style={{ marginRight: 0 }}>默认</Tag>
          )}
        </Space>
      ),
    }));
  }, [templates]);

  return (
    <div>
      {/* ── 顶部：基座模板下拉选择 ── */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <FileTextOutlined style={{ color: '#8c8c8c' }} />
          <Text strong style={{ fontSize: 13 }}>基座响应模板</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>— 所有包装体默认继承此模板的配置</Text>
        </div>
        <Spin spinning={loading}>
          <ProFormSelect
            name="templateId"
            options={templateOptions}
            placeholder="请选择基座响应模板，自动继承模板内容"
            fieldProps={{
              allowClear: true,
              showSearch: true,
              optionFilterProp: 'label',
            }}
          />
        </Spin>
      </div>

      {/* ── 下方：三大包装体区域 ── */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {(['success', 'page', 'fail'] as const).map((key) => {
          const config = WRAPPER_CONFIG[key];

          return (
            <ProFormDependency key={key} name={['templateId', config.switchField]}>
              {(deps) => {
                const templateId = deps.templateId;
                const isCustom = !!deps[config.switchField];

                // 获取当前选中基座模板的值
                const selectedTpl = templates.find((t) => t.id === templateId);
                const baseValue = selectedTpl?.[config.templateField] || '';

                return (
                  <div
                    style={{
                      borderRadius: 6,
                      border: '1px solid #e8e8e8',
                      transition: 'all 0.25s ease',
                      background: '#fff',
                      overflow: 'hidden',
                    }}
                  >
                    {/* ── 区块 Header ── */}
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '10px 16px',
                      borderBottom: '1px solid #f0f0f0',
                    }}>
                      <Space size={8} align="center">
                        <Badge status={config.badgeStatus} />
                        <Text strong style={{ fontSize: 13, color: '#1d2129' }}>
                          {config.label}
                        </Text>
                        {isCustom && (
                          <Tag
                            style={{
                              fontSize: 11,
                              lineHeight: '18px',
                              padding: '0 6px',
                              borderRadius: 4,
                              margin: 0,
                              color: '#595959',
                              background: '#f0f0f0',
                              border: '1px solid #d9d9d9',
                            }}
                          >
                            已重载
                          </Tag>
                        )}
                        {!isCustom && baseValue && (
                          <Tag
                            style={{
                              fontSize: 11,
                              lineHeight: '18px',
                              padding: '0 6px',
                              borderRadius: 4,
                              margin: 0,
                              color: '#8c8c8c',
                              background: '#fafafa',
                              border: '1px solid #e8e8e8',
                            }}
                          >
                            继承模板
                          </Tag>
                        )}
                      </Space>

                      {/* 局部重载 Switch，这里使用 ProFormSwitch 以参与表单联动 */}
                      <Space size={8}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {isCustom ? '自定义' : '继承'}
                        </Text>
                        <ProFormSwitch
                          name={config.switchField}
                          noStyle
                          fieldProps={{ size: 'small' }}
                        />
                      </Space>
                    </div>

                    {/* ── 区块 Content (TextArea) ── */}
                    <div style={{ padding: '16px 16px 4px 16px' }}>
                      <ProFormTextArea
                        name={config.formField}
                        placeholder={isCustom ? config.placeholder : (baseValue || '（未选择基座模板）')}
                        disabled={!isCustom}
                        fieldProps={{
                          rows: 3,
                          style: {
                            ...MONO_STYLE,
                            backgroundColor: isCustom ? '#fff' : '#f5f5f5',
                            borderColor: isCustom ? '#d9d9d9' : 'transparent',
                            borderStyle: isCustom ? 'solid' : 'dashed',
                            borderWidth: 1,
                            color: isCustom ? '#1d2129' : '#8c8c8c',
                            boxShadow: 'none',
                            transition: 'all 0.25s ease',
                          },
                        }}
                        extra={
                          isCustom
                            ? <div style={{ marginTop: 4, marginBottom: 8 }}>{config.extraHint}</div>
                            : undefined
                        }
                      />

                      {/* 继承状态下，展示原模板内容 */}
                      {!isCustom && baseValue && (
                        <div style={{
                          marginTop: -8,
                          marginBottom: 12,
                          padding: '8px 12px',
                          background: '#fafafa',
                          borderRadius: 4,
                          border: '1px dashed #e8e8e8',
                        }}>
                          <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 4 }}>
                            当前继承模板值：
                          </Text>
                          <code style={{
                            ...MONO_STYLE,
                            fontSize: 11,
                            color: '#595959',
                            wordBreak: 'break-all',
                            whiteSpace: 'pre-wrap',
                          }}>
                            {baseValue}
                          </code>
                        </div>
                      )}
                    </div>
                  </div>
                );
              }}
            </ProFormDependency>
          );
        })}
      </div>
    </div>
  );
};

export default React.memo(ResponseWrapperSection);
