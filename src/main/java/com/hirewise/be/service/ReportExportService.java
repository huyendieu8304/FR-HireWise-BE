package com.hirewise.be.service;

import com.hirewise.be.dto.response.PipelineVelocityReportResponseDto;
import com.hirewise.be.dto.response.SourceRoiReportResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * BR-RPT-03: renders the two Reporting dashboards as .xlsx workbooks (UC-42
 * REF 3 "Export Excel", and the same for UC-43).
 *
 * <p>Each sheet opens with the filter it was produced under. A spreadsheet gets
 * mailed around and quoted in meetings long after the screen that produced it
 * is closed, and numbers with no stated date range are numbers nobody can check.
 * For the same reason the two lifetime columns of UC-42 carry that word in
 * their header rather than being silently mixed in with the windowed ones.</p>
 *
 * <p>Written with {@link XSSFWorkbook} rather than the streaming variant: a
 * report has one row per source or per stage, so it is tens of rows, and the
 * in-memory model keeps auto-sized columns available.</p>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * UC-42 normal flow step 4: the Source ROI table as a workbook.
     *
     * @param report the dashboard exactly as it was rendered on screen
     * @return the .xlsx bytes
     */
    public byte[] toSourceRoiWorkbook(SourceRoiReportResponseDto report) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Hiệu quả nguồn tuyển dụng");
            Styles styles = new Styles(workbook);

            int rowIndex = 0;
            rowIndex = writeTitle(sheet, styles, rowIndex,
                    "UC-42 - Báo cáo hiệu quả nguồn tuyển dụng (Source ROI)",
                    report.getFromDate(), report.getToDate());

            Row summary = sheet.createRow(rowIndex++);
            text(summary, 0, styles.label, "Tổng ứng viên");
            number(summary, 1, styles.integer, report.getTotalApplications());
            text(summary, 2, styles.label, "Tuyển thành công");
            number(summary, 3, styles.integer, report.getTotalHires());
            text(summary, 4, styles.label, "Tỷ lệ trúng tuyển (%)");
            decimal(summary, 5, styles.decimal, report.getOverallHireRate());
            rowIndex++;

            List<String> headers = List.of(
                    "Nguồn", "Ứng viên", "Tỷ trọng (%)", "Trúng tuyển", "Tỷ lệ trúng tuyển (%)",
                    "Lượt chia sẻ (trọn đời)", "Lượt click (trọn đời)", "Click sang ứng tuyển (%)",
                    "Thời gian tuyển TB (ngày)");
            writeHeader(sheet, styles, rowIndex++, headers);

            for (SourceRoiReportResponseDto.SourceRow row : report.getRows()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                text(sheetRow, 0, styles.body, row.getLabel());
                number(sheetRow, 1, styles.integer, row.getApplicationCount());
                decimal(sheetRow, 2, styles.decimal, row.getApplicationShare());
                number(sheetRow, 3, styles.integer, row.getHireCount());
                decimal(sheetRow, 4, styles.decimal, row.getHireRate());
                number(sheetRow, 5, styles.integer, row.getShareCount());
                number(sheetRow, 6, styles.integer, row.getClickCount());
                decimal(sheetRow, 7, styles.decimal, row.getClickToApplyRate());
                decimal(sheetRow, 8, styles.decimal, row.getAvgDaysToHire());
            }

            autoSize(sheet, headers.size());
            return toBytes(workbook);
        } catch (IOException e) {
            throw new BadRequestException(ErrorCode.REPORT_EXPORT_FAILED);
        }
    }

    /**
     * UC-43: the Pipeline Velocity table as a workbook.
     *
     * @param report the dashboard exactly as it was rendered on screen
     * @return the .xlsx bytes
     */
    public byte[] toPipelineVelocityWorkbook(PipelineVelocityReportResponseDto report) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Tốc độ pipeline");
            Styles styles = new Styles(workbook);

            int rowIndex = 0;
            rowIndex = writeTitle(sheet, styles, rowIndex,
                    "UC-43 - Báo cáo tốc độ chuyển đổi giữa các Stage (Pipeline Velocity)",
                    report.getFromDate(), report.getToDate());

            Row summary = sheet.createRow(rowIndex++);
            text(summary, 0, styles.label, "Tuyển thành công");
            number(summary, 1, styles.integer, report.getHiredCount());
            text(summary, 2, styles.label, "Time-to-Hire TB (ngày)");
            decimal(summary, 3, styles.decimal, report.getAvgTimeToHireDays());
            text(summary, 4, styles.label, "Stage nghẽn");
            text(summary, 5, styles.body, report.getBottleneckStageCode());
            rowIndex++;

            List<String> headers = List.of(
                    "Stage", "TB (ngày)", "Trung vị (ngày)", "P90 (ngày)", "Số lượt đã hoàn tất",
                    "Ngưỡng SLA (ngày)", "Vượt SLA", "Đang chờ", "Chờ lâu nhất (ngày)",
                    "Vào", "Đi tiếp", "Bị loại", "Tỷ lệ qua vòng (%)");
            writeHeader(sheet, styles, rowIndex++, headers);

            for (PipelineVelocityReportResponseDto.StageRow row : report.getStages()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                text(sheetRow, 0, styles.body, row.getStageName());
                decimal(sheetRow, 1, styles.decimal, row.getAvgDays());
                decimal(sheetRow, 2, styles.decimal, row.getMedianDays());
                decimal(sheetRow, 3, styles.decimal, row.getP90Days());
                number(sheetRow, 4, styles.integer, row.getCompletedCount());
                decimal(sheetRow, 5, styles.decimal, row.getSlaDays());
                text(sheetRow, 6, styles.body, row.isSlaBreached() ? "Có" : "");
                number(sheetRow, 7, styles.integer, row.getWaitingCount());
                decimal(sheetRow, 8, styles.decimal, row.getMaxWaitingDays());
                number(sheetRow, 9, styles.integer, row.getEnteredCount());
                number(sheetRow, 10, styles.integer, row.getAdvancedCount());
                number(sheetRow, 11, styles.integer, row.getRejectedCount());
                decimal(sheetRow, 12, styles.decimal, row.getPassThroughRate());
            }

            autoSize(sheet, headers.size());
            return toBytes(workbook);
        } catch (IOException e) {
            throw new BadRequestException(ErrorCode.REPORT_EXPORT_FAILED);
        }
    }

    /**
     * @param prefix short report name, e.g. {@code source-roi}
     * @param toDate last day of the exported range
     * @return the download file name the browser saves
     */
    public String fileName(String prefix, LocalDate toDate) {
        LocalDate stamp = toDate != null ? toDate : LocalDate.now();
        return prefix + "-" + stamp.format(FILE_DATE) + ".xlsx";
    }

    /**
     * Writes the title and the filter the export was taken under.
     *
     * @param sheet    target sheet
     * @param styles   shared cell styles
     * @param rowIndex first free row
     * @param title    report title including its UC number
     * @param fromDate inclusive first day of the range
     * @param toDate   inclusive last day of the range
     * @return the next free row index
     */
    private int writeTitle(Sheet sheet, Styles styles, int rowIndex, String title,
                           LocalDate fromDate, LocalDate toDate) {
        Row titleRow = sheet.createRow(rowIndex++);
        text(titleRow, 0, styles.title, title);

        Row rangeRow = sheet.createRow(rowIndex++);
        text(rangeRow, 0, styles.label, "Khoảng thời gian");
        text(rangeRow, 1, styles.body, formatRange(fromDate, toDate));
        rowIndex++;
        return rowIndex;
    }

    /**
     * @param fromDate inclusive first day
     * @param toDate   inclusive last day
     * @return the range as the report header prints it
     */
    private String formatRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            return "Toàn bộ dữ liệu";
        }
        return fromDate.format(DATE) + " - " + toDate.format(DATE);
    }

    /**
     * @param sheet    target sheet
     * @param styles   shared cell styles
     * @param rowIndex row to write into
     * @param headers  column titles, left to right
     */
    private void writeHeader(Sheet sheet, Styles styles, int rowIndex, List<String> headers) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < headers.size(); i++) {
            text(row, i, styles.header, headers.get(i));
        }
    }

    private void text(Row row, int column, CellStyle style, String value) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        cell.setCellValue(value == null ? "" : value);
    }

    private void number(Row row, int column, CellStyle style, long value) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }

    /**
     * Writes a decimal, leaving the cell <b>blank</b> when the value is
     * {@code null}. A missing ratio is not a zero - printing 0.0 would tell the
     * reader a source converted nobody when in fact it was never measured.
     *
     * @param row    target row
     * @param column zero-based column index
     * @param style  numeric style
     * @param value  the value, or {@code null} when undefined
     */
    private void decimal(Row row, int column, CellStyle style, BigDecimal value) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * The four cell styles both sheets share. POI caches styles per workbook and
     * a workbook has a hard cap on them, so they are created once here rather
     * than per cell.
     */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle label;
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle integer;
        private final CellStyle decimal;

        private Styles(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            this.title = workbook.createCellStyle();
            this.title.setFont(titleFont);

            this.label = workbook.createCellStyle();
            this.label.setFont(boldFont);

            this.header = workbook.createCellStyle();
            this.header.setFont(boldFont);
            this.header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            this.header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.header.setBorderBottom(BorderStyle.THIN);
            this.header.setAlignment(HorizontalAlignment.CENTER);
            this.header.setWrapText(true);

            this.body = workbook.createCellStyle();

            this.integer = workbook.createCellStyle();
            this.integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            this.decimal = workbook.createCellStyle();
            this.decimal.setDataFormat(workbook.createDataFormat().getFormat("#,##0.0"));
        }
    }
}
