// ============================================================================
// useResizablePanelWidth — 右侧属性面板拖拽调宽（localStorage 持久化）
// ============================================================================

import React from 'react';

const STORAGE_KEY = 'yu-flow.flowEditor.propertyPanelWidth';

export const PANEL_WIDTH = {
    default: 420,
    min: 320,
    max: 720,
} as const;

function clamp(w: number): number {
    return Math.min(PANEL_WIDTH.max, Math.max(PANEL_WIDTH.min, Math.round(w)));
}

function readStored(): number {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return PANEL_WIDTH.default;
        const n = Number(raw);
        if (!Number.isFinite(n)) return PANEL_WIDTH.default;
        return clamp(n);
    } catch {
        return PANEL_WIDTH.default;
    }
}

/**
 * 拖拽左边框调整宽度。向左拖加宽，向右拖变窄。
 */
export function useResizablePanelWidth(collapsed: boolean) {
    const [width, setWidth] = React.useState(readStored);
    const dragging = React.useRef(false);
    const startX = React.useRef(0);
    const startW = React.useRef(0);

    const onResizeStart = React.useCallback(
        (e: React.MouseEvent) => {
            if (collapsed) return;
            e.preventDefault();
            e.stopPropagation();
            dragging.current = true;
            startX.current = e.clientX;
            startW.current = width;
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        },
        [collapsed, width],
    );

    React.useEffect(() => {
        const onMove = (e: MouseEvent) => {
            if (!dragging.current) return;
            // 向左拖 → clientX 减小 → 面板变宽
            const next = clamp(startW.current + (startX.current - e.clientX));
            setWidth(next);
        };
        const onUp = () => {
            if (!dragging.current) return;
            dragging.current = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            setWidth((w) => {
                try {
                    localStorage.setItem(STORAGE_KEY, String(w));
                } catch {
                    /* ignore */
                }
                return w;
            });
        };
        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
        return () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
        };
    }, []);

    return { width, onResizeStart };
}
