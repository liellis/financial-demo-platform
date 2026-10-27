package com.eps.controller;

import com.eps.entity.GlVoucher;
import com.eps.entity.PaymentInstruction;
import com.eps.repository.GlVoucherRepository;
import com.eps.repository.PaymentInstructionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST controller for General Ledger (GL) operations in the EPS.
 * Provides endpoints for posting settled payments to the general ledger
 * and generating GL interface files in various formats.
 *
 * <p>All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/gl")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GL Controller", description = "General Ledger posting and file generation APIs")
public class GlController {

    private final PaymentInstructionRepository paymentInstructionRepository;
    private final GlVoucherRepository glVoucherRepository;

    /**
     * Posts settled payments to the general ledger by creating GL vouchers.
     * Finds all payments with status SETT (optionally filtered by date range),
     * creates a GL voucher for each with debit and credit accounts.
     *
     * @param requestBody optional map with startDate and endDate filters
     * @return 200 OK with the count of vouchers created
     */
    @PostMapping("/post")
    @Operation(summary = "Post settled payments to GL",
            description = "Creates GL vouchers for all settled payments (status SETT) within the optional date range. Each voucher records the debit and credit entries.")
    public ResponseEntity<?> postToGl(@RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            LocalDate startDate = null;
            LocalDate endDate = null;

            if (requestBody != null) {
                if (requestBody.get("startDate") != null) {
                    startDate = LocalDate.parse(requestBody.get("startDate").toString(),
                            DateTimeFormatter.ISO_DATE);
                }
                if (requestBody.get("endDate") != null) {
                    endDate = LocalDate.parse(requestBody.get("endDate").toString(),
                            DateTimeFormatter.ISO_DATE);
                }
            }

            if (startDate == null) startDate = LocalDate.now().minusDays(1);
            if (endDate == null) endDate = LocalDate.now();

            List<PaymentInstruction> settledPayments = paymentInstructionRepository
                    .findPaymentsByDateRange(startDate, endDate);

            int voucherCount = 0;
            for (PaymentInstruction payment : settledPayments) {
                if (!"SETT".equals(payment.getStatus())) {
                    continue;
                }

                GlVoucher voucher = new GlVoucher();
                voucher.setVoucherNo("GLV" + System.currentTimeMillis()
                        + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
                voucher.setPaymentRef(payment.getPaymentRef());
                voucher.setPostDate(LocalDate.now());
                voucher.setCurrency(payment.getCurrency());
                voucher.setAmount(payment.getAmount() != null ? payment.getAmount().abs() : BigDecimal.ZERO);

                // Debit: payer's account, Credit: EPS clearing account
                voucher.setDebitAccount(payment.getPayerAccount());
                voucher.setCreditAccount("EPS_CLR_" + payment.getChannel());

                voucher.setNarrative("Payment " + payment.getPaymentRef()
                        + " from " + payment.getPayerName()
                        + " to " + payment.getPayeeName());
                voucher.setPostStatus("S");
                voucher.setPostTime(LocalDateTime.now());

                glVoucherRepository.save(voucher);
                voucherCount++;
            }

            log.info("GL posting completed: {} vouchers created for period {} to {}",
                    voucherCount, startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "GL posting completed successfully");
            response.put("voucherCount", voucherCount);
            response.put("startDate", startDate.toString());
            response.put("endDate", endDate.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error posting to GL: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "GL posting failed");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Generates a GL interface file for posted vouchers on a given date.
     * Supports CSV and FIXED (fixed-width) formats.
     *
     * @param requestBody map containing date and format (CSV or FIXED)
     * @return 200 OK with the generated file content and filename
     */
    @PostMapping("/generate-file")
    @Operation(summary = "Generate GL interface file",
            description = "Generates a GL interface file (CSV or fixed-width format) for all posted vouchers on the specified date.")
    public ResponseEntity<?> generateFile(@RequestBody Map<String, Object> requestBody) {
        try {
            String dateStr = (String) requestBody.get("date");
            String format = (String) requestBody.get("format");

            if (dateStr == null || format == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "date and format are required");
                return ResponseEntity.badRequest().body(error);
            }

            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            String upperFormat = format.toUpperCase();

            if (!"CSV".equals(upperFormat) && !"FIXED".equals(upperFormat)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Format must be CSV or FIXED");
                return ResponseEntity.badRequest().body(error);
            }

            // Find vouchers for the date with post status 'S'
            List<GlVoucher> vouchers = glVoucherRepository.findByPostStatus("S");
            List<GlVoucher> dateVouchers = new ArrayList<>();
            for (GlVoucher v : vouchers) {
                if (v.getPostDate() != null && v.getPostDate().equals(date)) {
                    dateVouchers.add(v);
                }
            }

            StringBuilder fileContent = new StringBuilder();
            String filename;

            if ("CSV".equals(upperFormat)) {
                filename = "GL_INTERFACE_" + dateStr + ".csv";
                // CSV header
                fileContent.append("VoucherNo,PaymentRef,PostDate,Currency,Amount,DebitAccount,CreditAccount,Narrative,PostStatus\n");
                for (GlVoucher v : dateVouchers) {
                    fileContent.append(escapeCsv(v.getVoucherNo())).append(",");
                    fileContent.append(escapeCsv(v.getPaymentRef())).append(",");
                    fileContent.append(v.getPostDate()).append(",");
                    fileContent.append(escapeCsv(v.getCurrency())).append(",");
                    fileContent.append(v.getAmount()).append(",");
                    fileContent.append(escapeCsv(v.getDebitAccount())).append(",");
                    fileContent.append(escapeCsv(v.getCreditAccount())).append(",");
                    fileContent.append(escapeCsv(v.getNarrative())).append(",");
                    fileContent.append(escapeCsv(v.getPostStatus())).append("\n");
                }
            } else {
                filename = "GL_INTERFACE_" + dateStr + ".txt";
                // Fixed-width format: each field padded to fixed column widths
                for (GlVoucher v : dateVouchers) {
                    fileContent.append(padRight(nullSafe(v.getVoucherNo()), 25));
                    fileContent.append(padRight(nullSafe(v.getPaymentRef()), 25));
                    fileContent.append(padRight(v.getPostDate() != null ? v.getPostDate().toString() : "", 10));
                    fileContent.append(padRight(nullSafe(v.getCurrency()), 3));
                    fileContent.append(padLeft(nullSafe(v.getAmount() != null ? v.getAmount().toString() : "0"), 20));
                    fileContent.append(padRight(nullSafe(v.getDebitAccount()), 20));
                    fileContent.append(padRight(nullSafe(v.getCreditAccount()), 20));
                    fileContent.append(padRight(nullSafe(v.getNarrative()), 100));
                    fileContent.append(padRight(nullSafe(v.getPostStatus()), 1));
                    fileContent.append("\n");
                }
            }

            log.info("Generated GL interface file: {} with {} vouchers", filename, dateVouchers.size());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "GL interface file generated successfully");
            response.put("filename", filename);
            response.put("format", upperFormat);
            response.put("voucherCount", dateVouchers.size());
            response.put("fileContent", fileContent.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating GL file: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "GL file generation failed");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Escapes a CSV field value by wrapping in double quotes if it contains special characters.
     *
     * @param value the value to escape
     * @return the escaped CSV value
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Pads a string on the right to the specified length.
     *
     * @param value  the string to pad
     * @param length the target length
     * @return the right-padded string
     */
    private String padRight(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }

    /**
     * Pads a string on the left to the specified length.
     *
     * @param value  the string to pad
     * @param length the target length
     * @return the left-padded string
     */
    private String padLeft(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return " ".repeat(length - value.length()) + value;
    }

    /**
     * Returns an empty string if the value is null, otherwise the value itself.
     *
     * @param value the string to check
     * @return the original string or empty string if null
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
