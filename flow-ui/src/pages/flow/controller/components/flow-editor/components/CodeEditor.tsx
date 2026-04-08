// ============================================================================
// CodeEditor.tsx — 通用代码编辑器（基于 CodeMirror 6）
// 用于替换 Monaco Editor，体积更小、画布内嵌性能优异
// ============================================================================

import React, { useMemo } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { json } from '@codemirror/lang-json';
import { javascript } from '@codemirror/lang-javascript';
import { EditorView } from '@codemirror/view';
import type { Extension } from '@codemirror/state';
import { createMacroCompletionExtension } from './MacroCompletion';

export interface CodeEditorProps {
    /** 编辑器内容 */
    value: string;
    /** 内容变化回调 */
    onChange: (val: string) => void;
    /** 语言类型，决定语法高亮扩展 */
    language?: 'json' | 'sql' | 'javascript' | 'text';
    /** 固定高度，例如 '200px'、'100%' */
    height?: string;
    /** 最大高度（内嵌画布时使用），例如 '300px' */
    maxHeight?: string;
    /** 是否只读 */
    readOnly?: boolean;
    /** 主题：'light' | 'dark' */
    theme?: 'light' | 'dark';
    /** 是否显示行号，默认 true */
    lineNumbers?: boolean;
    /** 是否自动换行，默认 true */
    wordWrap?: boolean;
    /** 字号，默认 12 */
    fontSize?: number;
    /** 额外的 CodeMirror 扩展 */
    extensions?: Extension[];
    /** 外层容器样式覆盖 */
    style?: React.CSSProperties;
    /** 外层容器 className */
    className?: string;
    /** 编辑器挂载回调（可用于拿到 EditorView 实例） */
    onMount?: (view: EditorView) => void;
}

/**
 * 根据 language 返回对应的 CodeMirror 6 语言扩展
 */
function getLanguageExtension(language?: string): Extension[] {
    switch (language) {
        case 'sql':
            return [sql()];
        case 'json':
            return [json()];
        case 'javascript':
            return [javascript()];
        case 'text':
        default:
            return [];
    }
}

/**
 * 通用 CodeEditor 组件
 *
 * 功能：
 * - 动态语言高亮 (sql / json / javascript / text)
 * - 亮/暗主题切换
 * - 边框样式与 Ant Design Input 对齐
 * - 支持固定高度或 maxHeight 滚动
 * - 支持只读模式
 */
const CodeEditor: React.FC<CodeEditorProps> = ({
    value,
    onChange,
    language = 'text',
    height = '200px',
    maxHeight,
    readOnly = false,
    theme = 'light',
    lineNumbers = true,
    wordWrap = true,
    fontSize = 12,
    extensions: extraExtensions,
    style,
    className,
    onMount,
}) => {
    const isFlexHeight = height === '100%' || height === 'auto';

    // 合并扩展
    const extensions = useMemo(() => {
        const themeRules: Record<string, any> = {
            '&': {
                fontSize: `${fontSize}px`,
            },
            '.cm-scroller': {
                fontFamily: "'JetBrains Mono', 'Fira Code', 'Consolas', 'Monaco', monospace",
                overflow: 'auto',
                ...(maxHeight ? { maxHeight } : {}),
            },
            '.cm-gutters': {
                backgroundColor: 'transparent',
                border: 'none',
            },
            '.cm-activeLineGutter': {
                backgroundColor: 'transparent',
            },
        };

        // 当使用 flex 布局填充高度时，让 .cm-editor 也变成 flex:1
        if (isFlexHeight) {
            themeRules['&'] = {
                ...themeRules['&'],
                flex: '1',
                display: 'flex',
                flexDirection: 'column',
                height: '100%',
            };
            themeRules['.cm-scroller'] = {
                ...themeRules['.cm-scroller'],
                flex: '1',
                height: '100%',
            };
        }

        const exts: Extension[] = [
            ...getLanguageExtension(language),
            EditorView.theme(themeRules),
        ];

        if (language === 'sql' || language === 'javascript') {
            exts.push(createMacroCompletionExtension(language));
        }

        if (wordWrap) {
            exts.push(EditorView.lineWrapping);
        }

        if (extraExtensions) {
            exts.push(...extraExtensions);
        }

        return exts;
    }, [language, fontSize, maxHeight, wordWrap, extraExtensions, isFlexHeight]);

    // 容器样式 — 模仿 Ant Design Input 风格
    const wrapperStyle: React.CSSProperties = {
        border: '1px solid #d9d9d9',
        borderRadius: 6,
        overflow: 'hidden',
        transition: 'border-color 0.2s',
        // 当 flex 布局时，wrapper 也需要参与 flex
        ...(isFlexHeight ? { flex: 1, display: 'flex', flexDirection: 'column' as const, height: '100%', minHeight: 0 } : {}),
        ...style,
    };

    return (
        <div
            className={className}
            style={wrapperStyle}
            onFocus={(e) => {
                (e.currentTarget as HTMLDivElement).style.borderColor = '#4096ff';
            }}
            onBlur={(e) => {
                (e.currentTarget as HTMLDivElement).style.borderColor = '#d9d9d9';
            }}
        >
            <CodeMirror
                value={value}
                height={isFlexHeight ? '100%' : height}
                theme={theme}
                readOnly={readOnly}
                basicSetup={{
                    lineNumbers,
                    foldGutter: true,
                    highlightActiveLine: !readOnly,
                    highlightActiveLineGutter: !readOnly,
                    autocompletion: true,
                    bracketMatching: true,
                    closeBrackets: true,
                    indentOnInput: true,
                    tabSize: 2,
                }}
                extensions={extensions}
                onChange={(val: string) => onChange(val)}
                onCreateEditor={(view: EditorView) => {
                    if (onMount) onMount(view);
                }}
                style={isFlexHeight ? { flex: 1, display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 } : undefined}
            />
        </div>
    );
};

export default CodeEditor;
