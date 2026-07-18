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
  /** default: 灰白底；primary: 蓝色强调底 */
  tone?: SectionCardTone;
  /** 为 false 时隐藏正文，仅保留标题栏 */
  bodyVisible?: boolean;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

const TONE_STYLE: Record<SectionCardTone, { bg: string; border: string; divider: string }> = {
  default: { bg: '#fff', border: '#ebeef5', divider: '#ebeef5' },
  primary: { bg: '#f0f7ff', border: '#91caff', divider: '#bae0ff' },
};

const SectionCard: React.FC<SectionCardProps> = ({
  id,
  icon,
  title,
  description,
  extra,
  tone = 'default',
  bodyVisible = true,
  children,
  style,
}) => {
  const t = TONE_STYLE[tone];

  return (
    <div
      id={id}
      style={{
        background: t.bg,
        border: `1px solid ${t.border}`,
        borderRadius: 8,
        marginBottom: 16,
        scrollMarginTop: 12,
        ...style,
      }}
    >
      {/* ── 标题栏 ── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 16,
          padding: '12px 20px',
          borderBottom: bodyVisible ? `1px solid ${t.divider}` : 'none',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
          <span style={{ fontSize: 16, color: '#1677ff', lineHeight: 1, display: 'flex' }}>
            {icon}
          </span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
            <span style={{ fontSize: 15, fontWeight: 600, color: '#1d2129', lineHeight: 1.4 }}>
              {title}
            </span>
            {description && (
              <span style={{ fontSize: 12, color: '#8c8c8c', lineHeight: 1.5 }}>
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
      {bodyVisible && <div style={{ padding: '16px 20px' }}>{children}</div>}
    </div>
  );
};

export default React.memo(SectionCard);
