/**
 * 模板选择弹窗 (Template Selector)
 *
 * 用户点击「生成页面」后首先弹出此弹窗，选择一种生成模板。
 * 选择后关闭弹窗，并通过 onSelect 回调把 templateId 传给父组件，
 * 由父组件带着模板参数唤起 GenerateWizardDrawer。
 */

import React from 'react';
import { Modal, Row, Col, Typography } from 'antd';
import {
  TableOutlined,
  EyeOutlined,
  AppstoreAddOutlined,
} from '@ant-design/icons';

const { Title, Paragraph } = Typography;

// ================================================================
// 模板数据定义
// ================================================================

export type TemplateId = 'crud' | 'readonly' | 'custom';

interface TemplateCard {
  id: TemplateId;
  icon: React.ReactNode;
  title: string;
  description: string;
  /** 渐变背景色 (icon 背景) */
  gradient: string;
  /** 边框高亮色 */
  accent: string;
  /** 推荐标签 */
  badge?: string;
}

const TEMPLATE_CARDS: TemplateCard[] = [
  {
    id: 'crud',
    icon: <TableOutlined />,
    title: '经典增删改查',
    description: '包含标准的分页查询、详情查看、新增、修改、删除，适用于绝大多数业务场景。',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    accent: '#667eea',
    badge: '推荐',
  },
  {
    id: 'readonly',
    icon: <EyeOutlined />,
    title: '只读数据表格',
    description: '仅提供分页查询和详情查看，无新增 / 修改 / 删除操作权限，适用于数据展示型页面。',
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    accent: '#43e97b',
  },
  {
    id: 'custom',
    icon: <AppstoreAddOutlined />,
    title: '自定义 / 大而全',
    description: '完全自定义能力，可按需勾选审核流、上下架、导入导出等高阶特性插件，灵活组合。',
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    accent: '#f093fb',
  },
];

// ================================================================
// 组件
// ================================================================

export interface TemplateSelectorModalProps {
  open: boolean;
  onOpenChange: (visible: boolean) => void;
  onSelect: (templateId: TemplateId) => void;
}

const TemplateSelectorModal: React.FC<TemplateSelectorModalProps> = ({
  open,
  onOpenChange,
  onSelect,
}) => {
  const handleCardClick = (templateId: TemplateId) => {
    onOpenChange(false);
    onSelect(templateId);
  };

  return (
    <Modal
      open={open}
      onCancel={() => onOpenChange(false)}
      footer={null}
      centered
      width={780}
      title={
        <div style={{ textAlign: 'center', padding: '4px 0' }}>
          <Title level={4} style={{ margin: 0, fontWeight: 700 }}>
            ⚡ 选择生成模板
          </Title>
          <Paragraph
            type="secondary"
            style={{ margin: '6px 0 0', fontSize: 13 }}
          >
            请选择一种模板开始配置，后续仍可在向导中自由调整
          </Paragraph>
        </div>
      }
      destroyOnClose
      bodyStyle={{ padding: '12px 24px 24px' }}
    >
      <Row gutter={[20, 20]}>
        {TEMPLATE_CARDS.map((card) => (
          <Col span={8} key={card.id}>
            <div
              onClick={() => handleCardClick(card.id)}
              style={{
                position: 'relative',
                borderRadius: 12,
                border: '1.5px solid #f0f0f0',
                padding: '28px 20px 24px',
                cursor: 'pointer',
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                background: '#fff',
                textAlign: 'center',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
              }}
              onMouseEnter={(e) => {
                const el = e.currentTarget;
                el.style.transform = 'translateY(-6px)';
                el.style.boxShadow = `0 12px 32px ${card.accent}30`;
                el.style.borderColor = card.accent;
              }}
              onMouseLeave={(e) => {
                const el = e.currentTarget;
                el.style.transform = 'translateY(0)';
                el.style.boxShadow = 'none';
                el.style.borderColor = '#f0f0f0';
              }}
            >
              {/* 推荐标签 */}
              {card.badge && (
                <span
                  style={{
                    position: 'absolute',
                    top: -1,
                    right: -1,
                    background: card.gradient,
                    color: '#fff',
                    fontSize: 11,
                    fontWeight: 600,
                    padding: '2px 12px',
                    borderRadius: '0 11px 0 8px',
                    letterSpacing: 1,
                  }}
                >
                  {card.badge}
                </span>
              )}

              {/* 图标 */}
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: 14,
                  background: card.gradient,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 26,
                  color: '#fff',
                  marginBottom: 16,
                  flexShrink: 0,
                }}
              >
                {card.icon}
              </div>

              {/* 标题 */}
              <div
                style={{
                  fontSize: 15,
                  fontWeight: 600,
                  color: '#1a1a2e',
                  marginBottom: 8,
                }}
              >
                {card.title}
              </div>

              {/* 描述 */}
              <Paragraph
                type="secondary"
                style={{
                  fontSize: 12.5,
                  lineHeight: 1.7,
                  margin: 0,
                  flex: 1,
                }}
              >
                {card.description}
              </Paragraph>
            </div>
          </Col>
        ))}
      </Row>
    </Modal>
  );
};

export default TemplateSelectorModal;
