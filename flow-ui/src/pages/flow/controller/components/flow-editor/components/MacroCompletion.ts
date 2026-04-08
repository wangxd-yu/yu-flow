// ============================================================================
// MacroCompletion.ts — 系统宏字典自动补全（基于 CodeMirror 6）
//
// 功能概述:
//   从后端 GET /flow-api/sys-macro/dictionary 拉取全局宏字典，
//   为 CodeEditor 提供智能补全能力。
//   当用户在 SQL 编辑器中输入 `$` 或 `{` 时触发宏变量替换补全；
//   当用户在 JavaScript 编辑器中输入 `s`/`y` 时触发宏变量/函数补全。
//
// 技术方案:
//   CodeEditor 底层为 CodeMirror 6 (@uiw/react-codemirror)，
//   本文件使用 @codemirror/autocomplete 的 autocompletion() 扩展实现补全。
//   通过 CodeEditor 的 `extensions` prop 注入。
//
// 接入方式:
//   调用 createMacroCompletionExtension('sql' | 'javascript') 获取 Extension，
//   传入 <CodeEditor extensions={[ext]} /> 即可。
//
// ============================================================================

import { CompletionContext, Completion } from '@codemirror/autocomplete';
import { EditorState, type Extension } from '@codemirror/state';
import { request } from '@umijs/max';

// ── 类型定义 ────────────────────────────────────────────────────────────────────

/** 后端宏字典 VO 结构 */
export interface SysMacroDictVO {
  /** 宏编码，如 sys_user_id */
  macroCode: string;
  /** 宏名称，如 当前登录用户 ID */
  macroName: string;
  /** 宏类型: VARIABLE | FUNCTION */
  macroType: 'VARIABLE' | 'FUNCTION';
  /** 返回值类型，如 Number、String */
  returnType: string;
}

// ── 单例缓存 ────────────────────────────────────────────────────────────────────

/**
 * 文件级缓存：避免多个编辑器实例同时拉取接口。
 * - macros: 成功拉取后的宏列表
 * - promise: 正在拉取中的 Promise（去重并发请求）
 * - loaded: 是否已成功加载过
 */
let macroCache: SysMacroDictVO[] = [];
let fetchPromise: Promise<SysMacroDictVO[]> | null = null;
let cacheLoaded = false;

/**
 * 拉取宏字典数据（带缓存 + 并发去重）
 *
 * @description
 * - 首次调用时发起 HTTP 请求并缓存结果
 * - 并发调用共享同一个 Promise，避免重复请求
 * - 加载失败后允许下次重试
 */
async function fetchMacros(): Promise<SysMacroDictVO[]> {
  if (cacheLoaded) return macroCache;

  // 并发去重: 如果已有一个 fetch 在进行中，直接复用它
  if (fetchPromise) return fetchPromise;

  fetchPromise = (async () => {
    try {
      // ────────────────────────────────
      // 真实接口调用
      // GET /flow-api/sys-macro/dictionary
      // 返回结构: { code: 200, data: SysMacroDictVO[] }
      // 项目使用 @umijs/max 的 request，默认已拆解 data 层
      // ────────────────────────────────
      const res: any = await request('/flow-api/sys-macros/dictionary', {
        method: 'GET',
      });

      // 兼容后端自动包装 { code: ..., data: [...] } 与直接返回数组的情况
      const list = res?.data || res;
      macroCache = Array.isArray(list) ? list : [];
      cacheLoaded = true;
      return macroCache;
    } catch (err) {
      console.error('[MacroCompletion] 拉取宏字典失败，补全功能暂不可用:', err);
      return [];
    } finally {
      fetchPromise = null;
    }
  })();

  return fetchPromise;
}

/**
 * 手动清除宏字典缓存。
 * 适用场景：管理员在「全局参数管理」页面编辑了宏定义后，
 * 可调用此函数让下一次打开编辑器时重新拉取最新数据。
 */
export function invalidateMacroCache(): void {
  macroCache = [];
  fetchPromise = null;
  cacheLoaded = false;
}

// ── SQL 补全逻辑 ────────────────────────────────────────────────────────────────

/**
 * SQL 语言的宏补全源 (CompletionSource)
 *
 * @description
 * 触发时机：
 *   - 用户输入 `$` 或 `{` 时触发 (通过 activateOnTyping / override 的匹配逻辑)
 *   - 也支持用户手动触发 (Ctrl+Space)
 *
 * 插入规则：
 *   - SQL 中宏变量格式为 `${macro_code}`
 *   - 如果用户已输入 `$` 或 `${`，需要正确计算替换起始位置，
 *     避免产生 `$${...}` 的双 `$` 问题
 *
 * Range 计算说明：
 *   光标位置 pos，向前扫描寻找最近的 `$` 或 `${` 前缀。
 *   from: 前缀起始位置（含 `$`），to: 当前光标位置。
 *   insertText 从 `${` 开始书写，替换用户已有的前缀。
 */
async function sqlCompletionSource(context: CompletionContext) {
  const macros = await fetchMacros();
  if (!macros.length) return null;

  // ── 向前扫描，检测用户已输入的 `$` / `${` / `${xxx` 前缀 ──
  // 在光标位置前最多回溯 50 个字符来寻找 `$` 起始
  const pos = context.pos;
  const lineText = context.state.doc.lineAt(pos);
  const lineStart = lineText.from;
  const textBeforeCursor = context.state.sliceDoc(lineStart, pos);

  // 从右向左找到最近的 `$` 的位置（在当前行内）
  let dollarIdx = -1;
  for (let i = textBeforeCursor.length - 1; i >= 0; i--) {
    const ch = textBeforeCursor[i];
    if (ch === '$') {
      dollarIdx = i;
      break;
    }
    // 如果遇到空格、换行或其他非标识符字符（且不是 `{`、`#`、`.`）则停止
    if (ch !== '{' && !/[a-zA-Z0-9_#.]/.test(ch)) break;
  }

  // 判断前缀
  let dollarPos: number; // $ 的绝对位置
  let filterStart: number; // 实际单词开始位置（用于 CodeMirror 过滤）
  let prefix: string; // 实际输入的单词

  if (dollarIdx >= 0) {
    dollarPos = lineStart + dollarIdx;
    const afterDollar = textBeforeCursor.slice(dollarIdx + 1);

    // 如果有 {，过滤起点在 { 之后；否则在 $ 之后
    const hasBrace = afterDollar.startsWith('{');
    filterStart = dollarPos + 1 + (hasBrace ? 1 : 0);
    prefix = hasBrace ? afterDollar.slice(1) : afterDollar;
  } else {
    // 没有 $ 前缀 —— 需要手动触发或通过 activateOnTyping
    if (!context.explicit) return null;
    dollarPos = pos;
    filterStart = pos;
    prefix = '';
  }

  // ── 构建补全项 ──
  const options: Completion[] = macros.map((macro) => {
    const macroCodeWithPrefix = `#${macro.macroCode}`;
    return {
      // label: 在补全弹窗里显示的文本
      label: `${macroCodeWithPrefix} - ${macro.macroName}`,
      // detail: 右侧辅助文本
      detail: `返回类型: ${macro.returnType}`,
      // type: CodeMirror 补全项类型，用于图标显示
      type: 'variable',
      // apply 回调：替换从 $ 到光标的所有内容，输入完整的 ${#macroCode}
      apply: (view: any, completion: any, _from: number, to: number) => {
        const insertText = `\${${macroCodeWithPrefix}}`;
        view.dispatch({
          changes: { from: dollarPos, to, insert: insertText },
          // 光标移动到插入的内容后面
          selection: { anchor: dollarPos + insertText.length },
        });
      },
      // boost: 提升排序权重，让精确前缀匹配靠前
      boost:
        prefix &&
        (macroCodeWithPrefix.startsWith(prefix) || macro.macroCode.startsWith(prefix))
          ? 10
          : 0,
    };
  });

  return {
    from: filterStart, // 从实际单词开始匹配，CodeMirror 便能正确模糊过滤
    options,
    validFor: /^[a-zA-Z0-9_#.]*$/, // 过滤起点后仅允许标识符及 #, . 字符
  };
}

// ── JavaScript 补全逻辑 ─────────────────────────────────────────────────────────

/**
 * JavaScript 语言的宏补全源 (CompletionSource)
 *
 * @description
 * 触发时机：
 *   - 用户输入标识符字符时触发（通过 activateOnTypingDelay）
 *   - 也支持手动触发 (Ctrl+Space)
 *
 * 插入规则：
 *   - JS 中宏变量直接以标识符形式引用: `sys_user_id`
 *   - 宏函数以 Snippet 形式插入: `sys_get_value(#{1})` —— 光标停在括号内
 *     注：CodeMirror 6 的 Snippet 语法为 `#{n}` (与 Monaco 的 `$n` 不同)
 *
 * 匹配策略：
 *   - 当用户输入任意标识符（如 `sys`）时进行前缀匹配
 *   - 至少需要输入 1 个字符才触发，避免空输入时弹出
 */
async function jsCompletionSource(context: CompletionContext) {
  const macros = await fetchMacros();
  if (!macros.length) return null;

  // ── 提取光标前的标识符前缀 ──
  // matchBefore 返回光标前匹配正则的文本范围，如果没有匹配则返回 null
  const word = context.matchBefore(/[a-zA-Z_][a-zA-Z0-9_]*/);

  if (!word && !context.explicit) {
    // 没有标识符前缀且不是手动触发 → 不弹补全
    return null;
  }

  const from = word ? word.from : context.pos;

  // ── 构建补全项 ──
  const options: Completion[] = macros.map((macro) => {
    const isFunction = macro.macroType === 'FUNCTION';

    return {
      // label: 补全弹窗展示文本
      label: `${macro.macroCode} - ${macro.macroName}`,
      // detail: 右侧辅助信息
      detail: `返回类型: ${macro.returnType}`,
      // type: 决定补全项左侧的图标样式
      type: isFunction ? 'function' : 'variable',
      // apply: 实际插入到编辑器的文本
      // 函数类型 → 插入 macroCode() 并将光标放在括号内（通过 apply 回调实现）
      // 变量类型 → 直接插入 macroCode
      ...(isFunction
        ? {
          // CodeMirror 6 的 snippet 补全：
          // 使用 apply 回调函数来实现函数括号 + 光标定位
          apply: (view: any, completion: any, from: number, to: number) => {
            const insertText = `${macro.macroCode}()`;
            // 替换 [from, to) 并将光标放在括号之间
            view.dispatch({
              changes: { from, to, insert: insertText },
              // 光标位置 = from + macroCode长度 + 1（即 `(` 之后）
              selection: { anchor: from + macro.macroCode.length + 1 },
            });
          },
        }
        : {
          apply: macro.macroCode,
        }),
    };
  });

  return {
    from,
    options,
    // validFor: 标识符字符继续输入时保持补全会话
    validFor: /^[a-zA-Z0-9_]*$/,
  };
}

// ── 公开 API ────────────────────────────────────────────────────────────────────

/**
 * 创建宏补全的 CodeMirror 6 扩展
 *
 * @param language - 当前编辑器语言：'sql' | 'javascript'
 * @returns CodeMirror Extension，直接传入 <CodeEditor extensions={[ext]} />
 *
 * @description
 * 不同语言使用不同的补全策略：
 * - **SQL**: 以 `$` / `{` 为触发字符，插入 `${macroCode}` 格式
 * - **JavaScript**: 以标识符输入为触发，变量插入 macroCode，函数插入 macroCode() 并定位光标
 *
 * @example
 * ```tsx
 * import { createMacroCompletionExtension } from './MacroCompletion';
 *
 * const macroExt = createMacroCompletionExtension('sql');
 *
 * <CodeEditor
 *   value={script}
 *   onChange={setScript}
 *   language="sql"
 *   extensions={[macroExt]}
 * />
 * ```
 */
export function createMacroCompletionExtension(language: 'sql' | 'javascript'): Extension {
  const completionSource = language === 'sql' ? sqlCompletionSource : jsCompletionSource;

  // 使用 languageData 的合并机制，将我们的提示源追加进去，
  // 这样既能实现宏补全，又不会挤占原本 sql/javascript 关键字自带的补全提示。
  return EditorState.languageData.of(() => [{
    autocomplete: completionSource
  }]);
}

// ============================================================================
// React 接入示例 (CodeEditor 组件)
// ============================================================================
//
// 本项目的 CodeEditor 底层为 CodeMirror 6 (@uiw/react-codemirror)，
// 通过 `extensions` prop 注入自定义扩展。
//
// ── 示例 1: 在 ServiceImplementationTab 中为 SQL 编辑器添加宏补全 ──
//
// ```tsx
// import React, { useMemo } from 'react';
// import CodeEditor from './flow-editor/components/CodeEditor';
// import { createMacroCompletionExtension } from './flow-editor/components/MacroCompletion';
//
// const ServiceImplementationTab: React.FC<Props> = ({ script, onScriptChange }) => {
//   // 使用 useMemo 确保扩展引用稳定，避免不必要的编辑器重建
//   const sqlMacroExt = useMemo(() => [createMacroCompletionExtension('sql')], []);
//
//   return (
//     <CodeEditor
//       value={script}
//       onChange={onScriptChange}
//       language="sql"
//       height="100%"
//       extensions={sqlMacroExt}   // ← 注入宏补全扩展
//     />
//   );
// };
// ```
//
// ── 示例 2: 在 BaseExpressionNode 中为 JS/SQL 动态语言添加宏补全 ──
//
// ```tsx
// import React, { useMemo } from 'react';
// import CodeEditor from '../../components/CodeEditor';
// import { createMacroCompletionExtension } from '../../components/MacroCompletion';
//
// const ExpressionEditor: React.FC<{ language: 'sql' | 'javascript'; ... }> = ({
//   language, value, onChange,
// }) => {
//   // language 变化时自动切换补全策略
//   const macroExt = useMemo(
//     () => [createMacroCompletionExtension(language)],
//     [language],
//   );
//
//   return (
//     <CodeEditor
//       value={value}
//       onChange={onChange}
//       language={language}
//       extensions={macroExt}   // ← 补全扩展随语言动态切换
//     />
//   );
// };
// ```
//
// ── 示例 3: 管理页面编辑宏后刷新缓存 ──
//
// ```tsx
// import { invalidateMacroCache } from '@/pages/flow/controller/components/flow-editor/components/MacroCompletion';
//
// const handleSaveMacro = async (values: any) => {
//   await saveMacroApi(values);
//   // 宏定义更新后，清除前端缓存，下次打开编辑器时会重新拉取
//   invalidateMacroCache();
//   message.success('保存成功');
// };
// ```
//
// ============================================================================
