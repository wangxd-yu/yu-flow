package org.yu.flow.auto.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExcelExportUtil {

    /**
     * 通用Excel导出方法
     * @param data 数据列表（List<Map>结构）
     * @param fileName 导出文件名（不含扩展名）
     * @param response HttpServletResponse
     */
    public static void export(List<Map<String, Object>> data,
                              String fileName,
                              HttpServletResponse response) throws IOException {
        // 1. 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx");

        // 2. 设置单元格样式（居中对齐）
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        HorizontalCellStyleStrategy styleStrategy = new HorizontalCellStyleStrategy(null, contentStyle);

        // 3. 执行导出
        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream())
                .registerConverter(new SmartDateConverter()) // 注册智能日期转换器
                .registerWriteHandler(styleStrategy)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .build()) {

            WriteSheet sheet = EasyExcel.writerSheet("Sheet1")
                    .head(generateHeaders(data))
                    .build();

            writer.write(generateData(data), sheet);
        }
    }

    // 生成表头
    private static List<List<String>> generateHeaders(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) return Collections.emptyList();
        return data.get(0).keySet().stream()
                .map(Collections::singletonList)
                .collect(Collectors.toList());
    }

    // 生成数据行
    private static List<List<Object>> generateData(List<Map<String, Object>> data) {
        return data.stream()
                .map(row -> new ArrayList<>(row.values()))
                .collect(Collectors.toList());
    }
}
