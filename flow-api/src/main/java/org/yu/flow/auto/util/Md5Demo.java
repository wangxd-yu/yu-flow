package org.yu.flow.auto.util;

import cn.hutool.crypto.SecureUtil;

public class Md5Demo {
    public static void main(String[] args) {
        // 对字符串进行MD5加密（返回32位小写字符串）
        String md5Str = SecureUtil.md5("hello world");
        // [DEBUG] removed println // 输出：5eb63bbbe01eeed093cb22bb8f5acdc3

        // 对文件进行MD5加密
        // String fileMd5 = SecureUtil.md5(new File("test.txt"));
    }
}
