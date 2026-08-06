/**
 * SectionCard.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「基础信息」面板通用卡片容器 — 统一各区块的标题栏 / 描述 / 边框样式
 *
 * 支持在标题栏右侧放置操作区（extra，如启用开关），并可折叠正文内容
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React from 'react';

export type SectionCardTone = 'default' | 'primary';

export interface SectionCardProps {
  /** 锚点定位用 id */
  id?: string;
  icon: React.ReactNode;
  title: React.ReactNode;
  /** 标题下方或右侧的补充说明 */
  description?: React.ReactNode;
  /** 标题栏右侧操作区，如启用开关 */
  extra?: React.ReactNode;
  /** @deprecated 已统一为统一精美白底卡片 */
  tone?: SectionCardTone;
  /** 为 false 时隐藏正文，仅保留标题栏 */
  bodyVisible?: boolean;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

const SectionCard: React.FC<SectionCardProps> = ({
  id,
  icon,
  title,
  description,
  extra,
  bodyVisible = true,
  children,
  style,
}) => {
  return (
    <div
      id={id}
      style={{
        background: '#ffffff',
        border: '1px solid #f0f0f0',
        borderRadius: 8,
        marginBottom: 16,
        scrollMarginTop: 12,
        boxShadow: '0 1px 3px rgba(0, 0, 0, 0.02)',
        overflow: 'hidden',
        ...style,
      }}
    >
      {/* ── 标题栏 ── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          padding: '12px 18px',
          background: '#fafafa',
          borderBottom: bodyVisible ? '1px solid #f0f0f0' : 'none',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
          <span style={{ fontSize: 15, color: '#1677ff', display: 'flex', alignItems: 'center' }}>
            {icon}
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', minWidth: 0 }}>
            <span style={{ fontSize: 14, fontWeight: 600, color: '#1f1f1f', lineHeight: 1.4 }}>
              {title}
            </span>
            {description && (
              <span style={{ fontSize: 12, color: '#8c8c8c', lineHeight: 1.4 }}>
                {description}
              </span>
            )}
          </div>
        </div>

        {extra && (
          <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center' }}>
            {extra}
          </div>
        )}
      </div>

      {/* ── 正文 ── */}
      {bodyVisible && <div style={{ padding: '20px 20px 8px' }}>{children}</div>}
    </div>
  );
};

export default React.memo(SectionCard);
