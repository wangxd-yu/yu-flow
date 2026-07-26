/**
 * FlowEditorLazy
 * ─────────────────────────────────────────────────────────────
 * FlowEditor 的懒加载壳：X6 / Debugger 体积大，列表与表单路由不应同步打包。
 * 调用方保持与 FlowEditor 完全相同的 props，替换 import 路径即可。
 */
import React, { Suspense } from 'react';
import { Spin } from 'antd';
import type { ExtendedFlowEditorProps } from './flow-editor/types-editor';

const FlowEditorAsync = React.lazy(() => import('./FlowEditor'));

const fallback = (
    <div
        style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
            minHeight: 320,
        }}
    >
        <Spin tip="加载流程编辑器..." />
    </div>
);

export default function FlowEditorLazy(props: ExtendedFlowEditorProps) {
    return (
        <Suspense fallback={fallback}>
            <FlowEditorAsync {...props} />
        </Suspense>
    );
}
