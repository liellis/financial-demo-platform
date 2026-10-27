package com.eps.controller;

import com.eps.entity.PaymentInstruction;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST controller for reporting operations in the EPS.
 * Provides endpoints for generating daily payment summaries, channel SLA reports,
 * cash flow reports, and MAS regulatory reports (CTR and STR).
 *
 * <p>All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Report Controller", description = "Payment reporting and regulatory report APIs")
public class ReportController {

    private final PaymentInstructionRepository paymentInstructionRepository;

    /**
     * Generates a daily payment summary report grouped by channel and status.
     * Counts payments and sums amounts for the specified date.
     *
     * @param date the report date
     * @return 200 OK with summary data
     */
    @GetMapping("/daily-payment-summary")
    @Operation(summary = "Generate daily payment summary",
            description = "Generates a summary of payments for the specified date, grouped by channel and status, with counts and total amounts.")
    public ResponseEntity<?> dailyPaymentSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            if (date == null) {
                date = LocalDate.now();
            }

            // Get all payments and filter by value date in memory
            LocalDate startDate = date;
            LocalDate endDate = date;
            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findPaymentsByDateRange(startDate, endDate);

            // Filter to only payments with matching valueDate
            List<PaymentInstruction> datePayments = new ArrayList<>();
            for (PaymentInstruction p : payments) {
                if (p.getValueDate() != null && p.getValueDate().equals(date)) {
                    datePayments.add(p);
                }
            }

            // Group by channel and status
            Map<String, Map<String, BigDecimal>> channelStatusAmounts = new LinkedHashMap<>();
            Map<String, Map<String, Integer>> channelStatusCounts = new LinkedHashMap<>();
            Map<String, BigDecimal> channelTotals = new LinkedHashMap<>();
            Map<String, Integer> channelCounts = new LinkedHashMap<>();

            for (PaymentInstruction p : datePayments) {
                String channel = p.getChannel() != null ? p.getChannel() : "UNKNOWN";
                String status = p.getStatus() != null ? p.getStatus() : "UNKNOWN";

                channelStatusAmounts
                        .computeIfAbsent(channel, k -> new LinkedHashMap<>())
                        .merge(status, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);

                channelStatusCounts
                        .computeIfAbsent(channel, k -> new LinkedHashMap<>())
                        .merge(status, 1, Integer::sum);

                channelTotals.merge(channel, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO,
                        BigDecimal::add);
                channelCounts.merge(channel, 1, Integer::sum);
            }

            BigDecimal grandTotal = channelTotals.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> response = new HashMap<>();
            response.put("reportDate", date.toString());
            response.put("totalPayments", datePayments.size());
            response.put("grandTotal", grandTotal);
            response.put("channelSummary", buildChannelSummary(channelStatusAmounts, channelStatusCounts,
                    channelTotals, channelCounts));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating daily payment summary: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate daily payment summary");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Builds a structured channel summary from the grouped data.
     */
    private List<Map<String, Object>> buildChannelSummary(
            Map<String, Map<String, BigDecimal>> channelStatusAmounts,
            Map<String, Map<String, Integer>> channelStatusCounts,
            Map<String, BigDecimal> channelTotals,
            Map<String, Integer> channelCounts) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (String channel : channelTotals.keySet()) {
            Map<String, Object> channelData = new HashMap<>();
            channelData.put("channel", channel);
            channelData.put("totalCount", channelCounts.getOrDefault(channel, 0));
            channelData.put("totalAmount", channelTotals.getOrDefault(channel, BigDecimal.ZERO));

            Map<String, Map<String, Object>> statusBreakdown = new LinkedHashMap<>();
            Map<String, BigDecimal> statusAmounts = channelStatusAmounts.getOrDefault(channel, Collections.emptyMap());
            Map<String, Integer> statusCountsMap = channelStatusCounts.getOrDefault(channel, Collections.emptyMap());

            for (String status : statusCountsMap.keySet()) {
                Map<String, Object> statusData = new HashMap<>();
                statusData.put("count", statusCountsMap.get(status));
                statusData.put("amount", statusAmounts.getOrDefault(status, BigDecimal.ZERO));
                statusBreakdown.put(status, statusData);
            }
            channelData.put("statusBreakdown", statusBreakdown);
            summary.add(channelData);
        }
        return summary;
    }

    /**
     * Generates a channel SLA report showing average processing time from creation to settlement.
     *
     * @param startDate the start of the reporting period
     * @param endDate   the end of the reporting period
     * @return 200 OK with SLA data per channel
     */
    @GetMapping("/channel-sla")
    @Operation(summary = "Generate channel SLA report",
            description = "Calculates the average processing time (from createTime to settleDate) for each payment channel within the specified date range.")
    public ResponseEntity<?> channelSlaReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findPaymentsByDateRange(startDate, endDate);

            // Group by channel and calculate average processing time
            Map<String, List<Long>> channelDurations = new LinkedHashMap<>();
            Map<String, Integer> channelSettledCounts = new LinkedHashMap<>();

            for (PaymentInstruction p : payments) {
                if (p.getCreateTime() != null && p.getSettleDate() != null && p.getChannel() != null) {
                    long durationHours = Duration.between(p.getCreateTime(),
                            p.getSettleDate().atStartOfDay()).toHours();
                    if (durationHours >= 0) {
                        channelDurations
                                .computeIfAbsent(p.getChannel(), k -> new ArrayList<>())
                                .add(durationHours);
                        channelSettledCounts.merge(p.getChannel(), 1, Integer::sum);
                    }
                }
            }

            List<Map<String, Object>> slaData = new ArrayList<>();
            for (Map.Entry<String, List<Long>> entry : channelDurations.entrySet()) {
                String channel = entry.getKey();
                List<Long> durations = entry.getValue();

                double avgHours = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
                long maxHours = durations.stream().mapToLong(Long::longValue).max().orElse(0);
                long minHours = durations.stream().mapToLong(Long::longValue).min().orElse(0);

                Map<String, Object> channelSla = new HashMap<>();
                channelSla.put("channel", channel);
                channelSla.put("settledCount", channelSettledCounts.getOrDefault(channel, 0));
                channelSla.put("avgProcessingHours", Math.round(avgHours * 100.0) / 100.0);
                channelSla.put("minProcessingHours", minHours);
                channelSla.put("maxProcessingHours", maxHours);
                slaData.add(channelSla);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("reportType", "CHANNEL_SLA");
            response.put("startDate", startDate.toString());
            response.put("endDate", endDate.toString());
            response.put("channels", slaData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating channel SLA report: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate channel SLA report");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Generates a cash flow report showing net cash flow per account for a given date.
     *
     * @param date the report date
     * @return 200 OK with cash flow data per account
     */
    @GetMapping("/cash-flow")
    @Operation(summary = "Generate cash flow report",
            description = "Calculates net cash flow per account for the specified date based on payment inflows and outflows.")
    public ResponseEntity<?> cashFlowReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findPaymentsByDateRange(date, date);

            Map<String, BigDecimal> outflows = new LinkedHashMap<>();
            Map<String, BigDecimal> inflows = new LinkedHashMap<>();

            for (PaymentInstruction p : payments) {
                if (p.getValueDate() != null && p.getValueDate().equals(date)
                        && "SETT".equals(p.getStatus())) {
                    BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;

                    // Outflow from payer account
                    if (p.getPayerAccount() != null) {
                        outflows.merge(p.getPayerAccount(), amount, BigDecimal::add);
                    }

                    // Inflow to payee account (internal accounts only, approximated)
                    if (p.getPayeeAccount() != null) {
                        inflows.merge(p.getPayeeAccount(), amount, BigDecimal::add);
                    }
                }
            }

            // Combine accounts
            Set<String> allAccounts = new LinkedHashSet<>();
            allAccounts.addAll(outflows.keySet());
            allAccounts.addAll(inflows.keySet());

            List<Map<String, Object>> cashFlowData = new ArrayList<>();
            BigDecimal netTotal = BigDecimal.ZERO;
            for (String account : allAccounts) {
                BigDecimal outflow = outflows.getOrDefault(account, BigDecimal.ZERO);
                BigDecimal inflow = inflows.getOrDefault(account, BigDecimal.ZERO);
                BigDecimal netFlow = inflow.subtract(outflow);
                netTotal = netTotal.add(netFlow);

                Map<String, Object> accountData = new HashMap<>();
                accountData.put("account", account);
                accountData.put("outflow", outflow);
                accountData.put("inflow", inflow);
                accountData.put("netFlow", netFlow);
                cashFlowData.add(accountData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("reportType", "CASH_FLOW");
            response.put("reportDate", date.toString());
            response.put("netTotal", netTotal);
            response.put("accounts", cashFlowData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating cash flow report: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate cash flow report");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Generates a MAS CTR (Cash Transaction Report) showing accounts with daily
     * accumulated amounts exceeding the threshold.
     *
     * @param date the report date
     * @return 200 OK with CTR report data
     */
    @GetMapping("/mas-ctr")
    @Operation(summary = "Generate MAS CTR report",
            description = "Generates a Cash Transaction Report (CTR) for accounts whose daily accumulated transaction amount exceeds the regulatory threshold (default 10000 SGD).")
    public ResponseEntity<?> masCtrReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            BigDecimal threshold = new BigDecimal("10000");

            List<Object[]> accumulatedAmounts = paymentInstructionRepository
                    .findDailyAccumulatedAmountByAccount(date);

            List<Map<String, Object>> ctrEntries = new ArrayList<>();
            for (Object[] row : accumulatedAmounts) {
                String accountNo = (String) row[0];
                BigDecimal totalAmount = row[1] instanceof BigDecimal
                        ? (BigDecimal) row[1]
                        : new BigDecimal(row[1].toString());

                if (totalAmount.compareTo(threshold) >= 0) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("accountNo", accountNo);
                    entry.put("reportDate", date.toString());
                    entry.put("totalAmount", totalAmount);
                    entry.put("threshold", threshold);
                    entry.put("exceedsThreshold", true);
                    ctrEntries.add(entry);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("reportType", "MAS_CTR");
            response.put("reportDate", date.toString());
            response.put("threshold", threshold);
            response.put("currency", "SGD");
            response.put("entryCount", ctrEntries.size());
            response.put("entries", ctrEntries);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating MAS CTR report: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate MAS CTR report");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Generates a MAS STR (Suspicious Transaction Report) by analyzing payment patterns
     * for suspicious activity within the date range.
     *
     * @param startDate the start of the reporting period
     * @param endDate   the end of the reporting period
     * @param threshold optional reporting threshold (default 5000 SGD)
     * @return 200 OK with STR report data
     */
    @GetMapping("/mas-str")
    @Operation(summary = "Generate MAS STR report",
            description = "Generates a Suspicious Transaction Report (STR) by analyzing payment patterns for suspicious activity including round amounts, structured transactions, and multiple payments to the same beneficiary.")
    public ResponseEntity<?> masStrReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal threshold) {
        try {
            if (threshold == null) {
                threshold = new BigDecimal("5000");
            }

            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findPaymentsByDateRange(startDate, endDate);

            List<Map<String, Object>> suspiciousItems = new ArrayList<>();

            // Check for round amounts above threshold
            for (PaymentInstruction p : payments) {
                if (p.getAmount() != null
                        && p.getAmount().compareTo(threshold) >= 0
                        && p.getAmount().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertType", "ROUND_AMOUNT");
                    item.put("paymentRef", p.getPaymentRef());
                    item.put("amount", p.getAmount());
                    item.put("valueDate", p.getValueDate() != null ? p.getValueDate().toString() : null);
                    item.put("payerName", p.getPayerName());
                    item.put("payeeName", p.getPayeeName());
                    item.put("channel", p.getChannel());
                    item.put("reason", "Round amount at or above threshold");
                    suspiciousItems.add(item);
                }
            }

            // Check for structured amounts (just below threshold with odd decimals)
            BigDecimal lowerBound = threshold.multiply(new BigDecimal("0.90"));
            for (PaymentInstruction p : payments) {
                if (p.getAmount() != null
                        && p.getAmount().compareTo(lowerBound) >= 0
                        && p.getAmount().compareTo(threshold) < 0) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertType", "STRUCTURED_AMOUNT");
                    item.put("paymentRef", p.getPaymentRef());
                    item.put("amount", p.getAmount());
                    item.put("valueDate", p.getValueDate() != null ? p.getValueDate().toString() : null);
                    item.put("payerName", p.getPayerName());
                    item.put("payeeName", p.getPayeeName());
                    item.put("channel", p.getChannel());
                    item.put("reason", "Amount structured just below reporting threshold of " + threshold);
                    suspiciousItems.add(item);
                }
            }

            // Check for multiple payments to same beneficiary
            Map<String, List<PaymentInstruction>> beneficiaryPayments = new LinkedHashMap<>();
            for (PaymentInstruction p : payments) {
                if (p.getPayeeName() != null) {
                    beneficiaryPayments.computeIfAbsent(p.getPayeeName(), k -> new ArrayList<>()).add(p);
                }
            }
            for (Map.Entry<String, List<PaymentInstruction>> entry : beneficiaryPayments.entrySet()) {
                if (entry.getValue().size() > 5) {
                    BigDecimal total = entry.getValue().stream()
                            .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertType", "MULTIPLE_PAYMENTS");
                    item.put("beneficiary", entry.getKey());
                    item.put("paymentCount", entry.getValue().size());
                    item.put("totalAmount", total);
                    item.put("reason", "More than 5 payments to same beneficiary");
                    suspiciousItems.add(item);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("reportType", "MAS_STR");
            response.put("startDate", startDate.toString());
            response.put("endDate", endDate.toString());
            response.put("threshold", threshold);
            response.put("currency", "SGD");
            response.put("totalPaymentsAnalyzed", payments.size());
            response.put("suspiciousCount", suspiciousItems.size());
            response.put("suspiciousItems", suspiciousItems);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating MAS STR report: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate MAS STR report");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
