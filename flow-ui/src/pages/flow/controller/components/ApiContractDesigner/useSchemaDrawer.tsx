/**
 * useSchemaDrawer.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 可复用的 Schema 预览 / 导入 Drawer Hook
 *
 * 提供：
 *  - previewBtn / importBtn — 触发按钮（可直接放入任意工具栏）
 *  - drawers              — 两个 Drawer 的 JSX（放在组件树末尾即可）
 *
 * 使用方只需传入 { nodes, onNodesChange } 即可获得完整功能，
 * 无需关心 CodeMirror / 转换逻辑 / 状态管理。
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useCallback, useMemo, useState } from 'react';
import { Button, Drawer, Space, message } from 'antd';
import {
  EyeOutlined, ImportOutlined, CopyOutlined, CheckOutlined,
} from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { EditorView } from '@codemirror/view';
import type { SchemaNode } from './types';
import { treeToSchema, schemaToTree } from './schemaUtils';

// ─── Hook 参数 ──────────────────────────────────────────────

export interface UseSchemaDrawerOptions {
  /** 当前树形节点数据 */
  nodes: SchemaNode[];
  /** 节点变更回调（导入成功后触发） */
  onNodesChange: (nodes: SchemaNode[]) => void;
}

// ─── Hook 返回值 ────────────────────────────────────────────

export interface UseSchemaDrawerReturn {
  /** 预览 Schema 按钮 */
  previewBtn: React.ReactNode;
  /** 导入 Schema 按钮 */
  importBtn: React.ReactNode;
  /** 两个 Drawer 的 JSX，需要放在组件 return 中 */
  drawers: React.ReactNode;
}

// ─── Hook 实现 ──────────────────────────────────────────────

const useSchemaDrawer = ({
  nodes,
  onNodesChange,
}: UseSchemaDrawerOptions): UseSchemaDrawerReturn => {
  const [drawerType, setDrawerType] = useState<'preview' | 'import' | null>(null);
  const [importText, setImportText] = useState('');
  const [copied, setCopied] = useState(false);

  // ── 预览 JSON ──────────────────────────────────────────────
  const previewSchema = useMemo(() => {
    try {
      return JSON.stringify(treeToSchema(nodes), null, 2);
    } catch {
      return '{}';
    }
  }, [nodes]);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(previewSchema).then(() => {
      setCopied(true);
      message.success('已复制到剪贴板');
      setTimeout(() => setCopied(false), 2000);
    });
  }, [previewSchema]);

  // ── 导入逻辑 ──────────────────────────────────────────────
  const handleImport = useCallback(() => {
    if (!importText.trim()) {
      message.warning('请粘贴 JSON Schema 内容后再导入');
      return;
    }
    try {
      onNodesChange(schemaToTree(importText));
      setDrawerType(null);
      setImportText('');
      message.success('导入成功');
    } catch (e: any) {
      message.error(e.message || 'JSON Schema 解析失败');
    }
  }, [importText, onNodesChange]);

  // ── 关闭 ──────────────────────────────────────────────────
  const closeDrawer = useCallback(() => {
    setDrawerType(null);
    setCopied(false);
  }, []);

  // ── 按钮 ──────────────────────────────────────────────────
  const previewBtn = (
    <Button
      size="small"
      type="text"
      icon={<EyeOutlined />}
      onClick={() => setDrawerType('preview')}
    >
      预览 Schema
    </Button>
  );

  const importBtn = (
    <Button
      size="small"
      type="text"
      icon={<ImportOutlined />}
      onClick={() => { setImportText(''); setDrawerType('import'); }}
    >
      导入 Schema
    </Button>
  );

  // ── Drawers JSX ───────────────────────────────────────────
  const drawers = (
    <>
      {/* ── 预览 Drawer ── */}
      <Drawer
        title="JSON Schema 预览"
        placement="right"
        width={560}
        open={drawerType === 'preview'}
        onClose={closeDrawer}
        extra={
          <Button
            type="primary"
            icon={copied ? <CheckOutlined /> : <CopyOutlined />}
            onClick={handleCopy}
          >
            {copied ? '已复制' : '复制'}
          </Button>
        }
      >
        <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden' }}>
          <CodeMirror
            value={previewSchema}
            readOnly
            extensions={[json(), EditorView.lineWrapping]}
            basicSetup={{ foldGutter: true, lineNumbers: true, highlightActiveLine: false }}
            style={{ fontSize: 12 }}
            minHeight="300px"
            maxHeight="calc(100vh - 160px)"
          />
        </div>
      </Drawer>

      {/* ── 导入 Drawer ── */}
      <Drawer
        title="导入 JSON Schema"
        placement="right"
        width={560}
        open={drawerType === 'import'}
        onClose={() => setDrawerType(null)}
        extra={
          <Space>
            <Button onClick={() => setDrawerType(null)}>取消</Button>
            <Button type="primary" onClick={handleImport}>确认导入</Button>
          </Space>
        }
      >
        <div style={{ marginBottom: 8, color: '#8c8c8c', fontSize: 12 }}>
          粘贴标准 JSON Schema (Draft 7) 格式内容，确认后将覆盖当前表格数据。
        </div>
        <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden' }}>
          <CodeMirror
            value={importText}
            onChange={setImportText}
            extensions={[json(), EditorView.lineWrapping]}
            basicSetup={{ foldGutter: true, lineNumbers: true }}
            placeholder={'{\n  "type": "object",\n  "properties": {\n    "name": { "type": "string" }\n  }\n}'}
            style={{ fontSize: 12 }}
            minHeight="300px"
            maxHeight="calc(100vh - 200px)"
          />
        </div>
      </Drawer>
    </>
  );

  return { previewBtn, importBtn, drawers };
};

export default useSchemaDrawer;
