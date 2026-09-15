package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.energy.mapper.ReadingMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 批量导入抄表数据。每行以表计编号定位已有表计，不会新增或修改表计档案。 */
@Service
@RequiredArgsConstructor
public class ReadingImportService {
    private final MeterMapper meterMapper;
    private final ReadingMapper readingMapper;

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择抄表 Excel 或 CSV 文件");
        }
        List<List<String>> rows = readRows(file);
        int headerRow = findHeader(rows);
        if (headerRow < 0) {
            throw new BizException("没找到表头，需包含“表计编号”和“本次读数”");
        }
        Map<String, Integer> columns = columns(rows.get(headerRow));
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int rowNo = headerRow + 1; rowNo < rows.size(); rowNo++) {
            List<String> row = rows.get(rowNo);
            if (row.stream().allMatch(v -> v == null || v.isBlank())) {
                continue;
            }
            try {
                String code = value(row, columns, "表计编号", "表号", "表计号");
                if (code.isBlank()) {
                    throw new BizException("表计编号为空");
                }
                Meter meter = meterMapper.selectOne(new LambdaQueryWrapper<Meter>()
                        .eq(Meter::getCode, code).last("LIMIT 1"));
                if (meter == null) {
                    throw new BizException("找不到表计“" + code + "”");
                }
                BigDecimal current = decimal(value(row, columns, "本次读数", "当前读数", "读数"), "本次读数");
                LocalDateTime readTime = dateTime(value(row, columns, "抄表时间", "抄表日期", "读取时间"));
                if (readTime == null) {
                    throw new BizException("抄表时间为空");
                }
                Reading duplicate = readingMapper.selectOne(new LambdaQueryWrapper<Reading>()
                        .eq(Reading::getMeterId, meter.getId())
                        .eq(Reading::getReadTime, readTime).last("LIMIT 1"));
                if (duplicate != null) {
                    skipped++;
                    continue;
                }
                Reading reading = new Reading();
                reading.setMeterId(meter.getId());
                reading.setPrevReading(decimalNullable(value(row, columns, "上次读数", "起始读数"), "上次读数"));
                if (reading.getPrevReading() == null) {
                    reading.setPrevReading(meter.getLastReading() == null ? BigDecimal.ZERO : meter.getLastReading());
                }
                reading.setCurrReading(current);
                reading.setReadTime(readTime);
                reading.setPeriod(nonBlank(value(row, columns, "账期", "月份"), YearMonth.from(readTime).toString()));
                reading.setReadSource(nonBlank(value(row, columns, "抄表方式", "来源"), "导入"));
                BigDecimal fee = decimalNullable(value(row, columns, "费用", "金额"), "费用");
                reading.setFee(fee == null ? BigDecimal.ZERO : fee);
                reading.setUsageAmount(usage(reading, meter));
                readingMapper.insert(reading);
                meterMapper.update(null, new LambdaUpdateWrapper<Meter>()
                        .eq(Meter::getId, meter.getId()).set(Meter::getLastReading, current));
                meter.setLastReading(current);
                imported++;
            } catch (Exception e) {
                errors.add("第 " + (rowNo + 1) + " 行: " + (e instanceof BizException ? e.getMessage() : "字段格式不正确"));
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    private BigDecimal usage(Reading reading, Meter meter) {
        BigDecimal previous = reading.getPrevReading() == null ? BigDecimal.ZERO : reading.getPrevReading();
        BigDecimal current = reading.getCurrReading() == null ? BigDecimal.ZERO : reading.getCurrReading();
        BigDecimal ratio = meter.getRatio() == null || meter.getRatio().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE : meter.getRatio();
        return current.subtract(previous).max(BigDecimal.ZERO).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, Integer> columns(List<String> header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            map.putIfAbsent(norm(header.get(i)), i);
        }
        return map;
    }

    private static String value(List<String> row, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(norm(name));
            if (index != null && index < row.size()) {
                return Optional.ofNullable(row.get(index)).orElse("").trim();
            }
        }
        return "";
    }

    private static String norm(String value) {
        return value == null ? "" : value.replaceAll("[\\s()（）]", "").toLowerCase(Locale.ROOT);
    }

    private static int findHeader(List<List<String>> rows) {
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String row = String.join("", rows.get(i));
            if (row.contains("表计编号") && (row.contains("本次读数") || row.contains("当前读数"))) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal decimal(String value, String label) {
        BigDecimal result = decimalNullable(value, label);
        if (result == null) {
            throw new BizException(label + "为空");
        }
        return result;
    }

    private static BigDecimal decimalNullable(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BizException(label + "不是有效数字");
        }
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/M/d H:m"),
                DateTimeFormatter.ofPattern("yyyy年M月d日 H:m"))) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一个常见格式
            }
        }
        throw new BizException("抄表时间格式不正确");
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<List<String>> readRows(MultipartFile file) {
        try {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return Arrays.stream(new String(file.getBytes(), StandardCharsets.UTF_8).split("\\R", -1))
                        .map(line -> Arrays.asList(line.split(",", -1))).toList();
            }
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
                List<List<String>> result = new ArrayList<>();
                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int column = 0; column < row.getLastCellNum(); column++) {
                        cells.add(formatter.formatCellValue(row.getCell(column)));
                    }
                    result.add(cells);
                }
                return result;
            }
        } catch (Exception e) {
            throw new BizException("文件解析失败，仅支持 .xlsx、.xls、.csv");
        }
    }
}
