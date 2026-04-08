/**
 * useLatestRef — 闭包陷阱终结者
 * ─────────────────────────────────────────────────────────────────────────────
 * 将一个会变化的值存入 Ref，并在每次渲染后同步更新。
 * 闭包中通过 ref.current 永远可以安全读取到最新值，
 * 无需手写 useRef + useEffect 同步流水线。
 *
 * 用法：
 *   const onChangeRef = useLatestRef(onChange);
 *   // 在事件回调中安全使用：
 *   graph.on('xxx', () => onChangeRef.current(data));
 */
import { useRef } from 'react';

export function useLatestRef<T>(value: T): React.MutableRefObject<T> {
  const ref = useRef(value);
  // 直接在 render 阶段同步，保证闭包中读取到的总是最新值。
  // 这比 useEffect 更早更新，避免了 useEffect 异步更新的时序间隙问题。
  ref.current = value;
  return ref;
}

export default useLatestRef;
