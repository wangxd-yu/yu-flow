/**
 * 创建节点内唯一 ID
 * 格式: {prefix}_{6位随机} ，例如 v_p6jqjt
 * - 去掉时间戳（Date.now），避免生成如 var_1772470819834_p6jqjti 这样过长的 ID
 * - 端口 ID 只需同一节点内唯一，6位随机 (36^6 ≈ 21亿) 已经足够
 */
export function createId(prefix: string) {
  return `${prefix}_${Math.random().toString(36).slice(2, 8)}`;
}

