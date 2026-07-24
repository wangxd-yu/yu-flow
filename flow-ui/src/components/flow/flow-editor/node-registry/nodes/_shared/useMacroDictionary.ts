// ============================================================================
// useMacroDictionary.ts — 共享宏字典数据拉取 Hook
//
// 功能：
//   从后端 GET /flow-api/sys-macros/dictionary 拉取全量宏字典，
//   供 systemVar / systemMethod 节点组件统一消费。
//   内置模块级缓存，多个节点实例共享同一份数据，不会重复请求。
//
// 安全说明：
//   后端接口 SysMacroDictVO 仅暴露 macroCode / macroName / macroType /
//   returnType / macroParams 五个字段，**不包含任何 SpEL 表达式**，
//   因此前端拿不到也无需关心底层 expression。
// ============================================================================

import { useState, useEffect } from 'react';
import { request } from '@umijs/max';

// ── 类型定义 ────────────────────────────────────────────────────────────────────

/** 后端宏字典 VO（与 SysMacroDictVO.java 完全对应） */
export interface MacroDictItem {
  /** 宏编码，前端调用唯一凭证（如 UUID、FORMAT_DATE） */
  macroCode: string;
  /** 宏名称，用于 UI 显示（如 UUID 生成、日期格式化） */
  macroName: string;
  /** 宏类型: VARIABLE 变量 / FUNCTION 方法 */
  macroType: 'VARIABLE' | 'FUNCTION';
  /** 返回值类型，用于前端类型推导（如 String, Number） */
  returnType: string;
  /** 入参列表，仅 FUNCTION 类型有效（逗号分隔，如 "date, format"） */
  macroParams?: string;
}

// ── 模块级缓存 ──────────────────────────────────────────────────────────────────
// 所有节点实例共享同一份缓存，避免画布上多个节点同时发出 N 个请求

let cachedList: MacroDictItem[] = [];
let fetchPromise: Promise<MacroDictItem[]> | null = null;
let loaded = false;

/**
 * 拉取宏字典（带缓存 + 并发去重）
 *
 * - 首次调用发起 HTTP 请求并缓存
 * - 并发调用复用同一个 Promise
 * - 失败后允许重试
 */
async function fetchDictionary(): Promise<MacroDictItem[]> {
  if (loaded) return cachedList;
  if (fetchPromise) return fetchPromise;

  fetchPromise = (async () => {
    try {
      const res: any = await request('/flow-api/sys-macros/dictionary', { method: 'GET' });
      // 兼容 { code, data: [...] } 与直接返回数组
      const list = res?.data || res;
      cachedList = Array.isArray(list) ? list : [];
      loaded = true;
      return cachedList;
    } catch (err) {
      console.error('[useMacroDictionary] 拉取宏字典失败:', err);
      return [];
    } finally {
      fetchPromise = null;
    }
  })();

  return fetchPromise;
}

/**
 * 手动清除缓存（供管理页面编辑宏后调用）
 */
export function invalidateNodeMacroCache(): void {
  cachedList = [];
  fetchPromise = null;
  loaded = false;
}

// ── React Hook ──────────────────────────────────────────────────────────────────

/**
 * 获取指定类型的宏字典列表
 *
 * @param macroType - 筛选类型：'VARIABLE' 仅变量 / 'FUNCTION' 仅方法
 * @returns 已过滤的宏字典条目数组
 *
 * @example
 * ```tsx
 * const variables = useMacroDictionary('VARIABLE');
 * const methods   = useMacroDictionary('FUNCTION');
 * ```
 */
export function useMacroDictionary(macroType: 'VARIABLE' | 'FUNCTION'): MacroDictItem[] {
  const [items, setItems] = useState<MacroDictItem[]>([]);

  useEffect(() => {
    let cancelled = false;

    fetchDictionary().then((all) => {
      if (!cancelled) {
        // 按 macroType 过滤
        setItems(all.filter((m) => m.macroType === macroType));
      }
    });

    return () => {
      cancelled = true;
    };
  }, [macroType]);

  return items;
}

/**
 * 工具函数：将逗号分隔的 macroParams 字符串解析为参数名数组
 *
 * @example
 * parseMacroParams('date, format') → ['date', 'format']
 * parseMacroParams(undefined)      → []
 * parseMacroParams('')             → []
 */
export function parseMacroParams(macroParams?: string): string[] {
  if (!macroParams) return [];
  return macroParams
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}
