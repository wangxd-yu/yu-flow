package org.yu.flow.engine.model;

/**
 * 端口名称常量池 — 消除魔法字符串的唯一真相源
 *
 * <p>所有 Step / Executor 中引用端口名必须通过此类引用，禁止硬编码。
 */
public final class PortNames {

    private PortNames() { /* 防止实例化 */ }

    // ========== 通用端口 ==========
    /** 通用输入端口 */
    public static final String IN = "in";
    /** 通用输出端口 */
    public static final String OUT = "out";

    // ========== 控制流端口 ==========
    /** 条件 true 分支 (If 节点) */
    public static final String TRUE = "true";
    /** 条件 false 分支 (If 节点) */
    public static final String FALSE = "false";
    /** Switch 节点 case 前缀，完整端口名为 case_VALUE */
    public static final String CASE_PREFIX = "case_";
    /** Switch 节点默认分支 */
    public static final String DEFAULT = "default";
    /** 控制流起始触发 (For 节点) */
    public static final String START = "start";
    /** 控制流完成信号 (Collect / ForEach 节点) */
    public static final String FINISH = "finish";
    /** HTTP 请求成功分支 */
    public static final String SUCCESS = "success";
    /** HTTP 请求失败分支 */
    public static final String FAIL = "fail";

    // ========== 数据流端口 ==========
    /** 单条元素流转 (For/ForEach/Collect) */
    public static final String ITEM = "item";
    /** 集合/数组 (Collect 输出) */
    public static final String LIST = "list";
    /** HTTP Headers */
    public static final String HEADERS = "headers";
    /** HTTP Query Params */
    public static final String PARAMS = "params";
    /** HTTP Body */
    public static final String BODY = "body";
}
