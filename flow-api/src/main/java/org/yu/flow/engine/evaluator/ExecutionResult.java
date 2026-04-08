package org.yu.flow.engine.evaluator;

public class ExecutionResult {
    private boolean success;      // 执行是否成功
    private int code;            // 状态码（成功时为200，失败时为错误码）
    private String message;      // 结果描述信息
    private Object data;       // 执行输出的数据

    // 静态工厂方法：成功结果
    public static ExecutionResult success(Object output) {
        ExecutionResult result = new ExecutionResult();
        result.success = true;
        result.code = 200;
        result.message = "success";
        result.data = output;
        return result;
    }

    // 静态工厂方法：失败结果
    public static ExecutionResult failure(int code, String message) {
        ExecutionResult result = new ExecutionResult();
        result.success = false;
        result.code = code;
        result.message = message;
        result.data = null;
        return result;
    }

    // Getter方法（省略Setter以保持不可变性）
    public boolean isSuccess() { return success; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
}
