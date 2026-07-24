import React from 'react';
import CodeEditor, {
  CodeEditorLanguage,
} from '@/components/flow/flow-editor/components/CodeEditor';
import { LOG_WORDWRAP_LIMIT } from './logFormat';

export interface LogCodePanelProps {
  content?: string;
  language?: CodeEditorLanguage;
  emptyText?: string;
  /**
   * 默认 100% 填满父容器（父级需有明确高度）。
   * 也可传固定值如 300px。
   */
  height?: string;
}

/**
 * 日志详情只读代码面板 — 统一 CodeEditor，默认自适应父容器高度
 */
const LogCodePanel: React.FC<LogCodePanelProps> = ({
  content,
  language = 'json',
  emptyText = '暂无内容',
  height = '100%',
}) => {
  const text = content || '';
  const fill = height === '100%' || height === 'auto';

  if (!text) {
    return (
      <div
        style={{
          flex: fill ? 1 : undefined,
          height: fill ? '100%' : height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#8c8c8c',
          fontSize: 13,
        }}
      >
        {emptyText}
      </div>
    );
  }

  return (
    <div
      style={{
        flex: fill ? 1 : undefined,
        height: fill ? '100%' : height,
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <CodeEditor
        value={text}
        onChange={() => {}}
        language={language}
        readOnly
        height="100%"
        wordWrap={text.length < LOG_WORDWRAP_LIMIT}
        showFormat={false}
      />
    </div>
  );
};

export default React.memo(LogCodePanel);
