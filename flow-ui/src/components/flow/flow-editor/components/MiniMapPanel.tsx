import React from 'react';

export type MiniMapPanelProps = {
  visible: boolean;
  containerRef: React.RefObject<HTMLDivElement>;
};

export default function MiniMapPanel(props: MiniMapPanelProps) {
  const { visible, containerRef } = props;

  return (
    <div
      style={{
        position: 'absolute',
        right: 12,
        bottom: 12,
        width: 220,
        height: 140,
        border: '1px solid #e5e6eb',
        borderRadius: 8,
        background: '#ffffff',
        overflow: 'hidden',
        display: visible ? 'block' : 'none',
        boxShadow: '0 6px 18px rgba(0,0,0,0.08)',
      }}
    >
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
    </div>
  );
}

