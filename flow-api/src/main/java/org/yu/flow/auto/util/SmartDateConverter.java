package org.yu.flow.auto.util;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 智能日期转换器（自动识别日期格式）
 * 使用 DateTimeFormatter 替代 ThreadLocal<SimpleDateFormat>，
 * DateTimeFormatter 是不可变且线程安全的，无需 ThreadLocal 包装。
 */
public class SmartDateConverter implements Converter<Date> {
    // DateTimeFormatter 是不可变的、线程安全的，可直接声明为 static final
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Class<Date> supportJavaTypeKey() {
        return Date.class;
    }

    @Override
    public WriteCellData<?> convertToExcelData(Date value,
                                             ExcelContentProperty contentProperty,
                                             GlobalConfiguration globalConfiguration) {
        if (value == null) {
            return new WriteCellData<>("");
        }

        // 将 Date 转换为 LocalDateTime 进行格式化
        LocalDateTime ldt = Instant.ofEpochMilli(value.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // 检查是否包含时间部分
        boolean hasTime = (value.getTime() % (24 * 60 * 60 * 1000) != 0);

        return new WriteCellData<>(hasTime ?
            DATETIME_FORMAT.format(ldt) :
            DATE_FORMAT.format(ldt));
    }
}
