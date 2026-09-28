package org.yu.flow.module.envvar.dto;

/**
 * 变量名字典（不含值），供编排编辑器提示与发布包依赖检查。
 */
public record EnvVariableDictVO(String code, boolean secret, String remark) {
}
