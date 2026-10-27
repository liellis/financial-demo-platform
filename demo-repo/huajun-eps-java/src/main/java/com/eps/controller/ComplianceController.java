package com.eps.controller;

import com.eps.entity.PaymentInstruction;
import com.eps.entity.SanctionsEntry;
import com.eps.repository.PaymentInstructionRepository;
import com.eps.repository.SanctionsEntryRepository;

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
import java.time.LocalDate;
import java.util.*;

/**
 * REST controller for compliance and sanctions screening operations in the EPS.
 * Provides endpoints for loading sanctions lists, screening payments against
 * sanctions entries, and analyzing payment patterns for suspicious activity.
 *
 * <p>All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance Controller", description = "Compliance and sanctions screening APIs")
public class ComplianceController {

    private final SanctionsEntryRepository sanctionsEntryRepository;
    private final PaymentInstructionRepository paymentInstructionRepository;

    /**
     * Loads a batch of sanctions entries into the system.
     *
     * @param entries list of sanctions entries to load
     * @return 201 Created with the count of entries loaded
     */
    @PostMapping("/sanctions/load")
    @Operation(summary = "Load sanctions entries",
            description = "Accepts a list of sanctions entries and saves them to the system. Used for bulk loading of sanctions lists.")
    public ResponseEntity<?> loadSanctions(@RequestBody List<SanctionsEntry> entries) {
        try {
            if (entries == null || entries.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Sanctions entries list must not be empty");
                return ResponseEntity.badRequest().body(error);
            }

            // Generate entry IDs and set load date for entries without them
            for (SanctionsEntry entry : entries) {
                if (entry.getEntryId() == null || entry.getEntryId().isEmpty()) {
                    entry.setEntryId("SANC" + System.currentTimeMillis()
                            + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
                }
                if (entry.getLoadDate() == null) {
                    entry.setLoadDate(LocalDate.now());
                }
                if (entry.getLoadUser() == null) {
                    entry.setLoadUser("SYSTEM");
                }
            }

            List<SanctionsEntry> saved = sanctionsEntryRepository.saveAll(entries);

            log.info("Loaded {} sanctions entries", saved.size());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Sanctions entries loaded successfully");
            response.put("count", saved.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error loading sanctions entries: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load sanctions entries");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Screens a payment against the sanctions list by checking payer and payee names.
     * Uses fuzzy matching to find potential matches in the sanctions database.
     *
     * @param paymentRef the payment reference to screen
     * @return 200 OK with screening result including matches and pass/fail status
     */
    @PostMapping("/screen/{paymentRef}")
    @Operation(summary = "Screen a payment against sanctions list",
            description = "Checks the payer and payee names of a payment against the sanctions database using fuzzy name matching.")
    public ResponseEntity<?> screenPayment(@PathVariable String paymentRef) {
        try {
            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(paymentRef);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", paymentRef);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();
            List<Map<String, Object>> allMatches = new ArrayList<>();

            // Screen payer name
            if (payment.getPayerName() != null && !payment.getPayerName().isEmpty()) {
                List<SanctionsEntry> payerMatches = sanctionsEntryRepository
                        .fuzzyMatchByName(payment.getPayerName());
                if (payerMatches != null) {
                    for (SanctionsEntry entry : payerMatches) {
                        Map<String, Object> match = new HashMap<>();
                        match.put("screenedField", "payerName");
                        match.put("screenedValue", payment.getPayerName());
                        match.put("matchedEntity", entry.getEntityName());
                        match.put("riskLevel", entry.getRiskLevel());
                        match.put("listSource", entry.getListSource());
                        match.put("entryId", entry.getEntryId());
                        allMatches.add(match);
                    }
                }
            }

            // Screen payee name
            if (payment.getPayeeName() != null && !payment.getPayeeName().isEmpty()) {
                List<SanctionsEntry> payeeMatches = sanctionsEntryRepository
                        .fuzzyMatchByName(payment.getPayeeName());
                if (payeeMatches != null) {
                    for (SanctionsEntry entry : payeeMatches) {
                        // Avoid duplicate entries
                        boolean duplicate = allMatches.stream()
                                .anyMatch(m -> entry.getEntryId().equals(m.get("entryId")));
                        if (!duplicate) {
                            Map<String, Object> match = new HashMap<>();
                            match.put("screenedField", "payeeName");
                            match.put("screenedValue", payment.getPayeeName());
                            match.put("matchedEntity", entry.getEntityName());
                            match.put("riskLevel", entry.getRiskLevel());
                            match.put("listSource", entry.getListSource());
                            match.put("entryId", entry.getEntryId());
                            allMatches.add(match);
                        }
                    }
                }
            }

            boolean passed = allMatches.isEmpty();

            log.info("Screened payment {}: matches={}, passed={}", paymentRef, allMatches.size(), passed);

            Map<String, Object> response = new HashMap<>();
            response.put("screened", true);
            response.put("paymentRef", paymentRef);
            response.put("payerName", payment.getPayerName());
            response.put("payeeName", payment.getPayeeName());
            response.put("matches", allMatches);
            response.put("matchCount", allMatches.size());
            response.put("passed", passed);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error screening payment {}: {}", paymentRef, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to screen payment");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Analyzes payments in a date range for suspicious patterns.
     * Detects: same beneficiary receiving multiple payments, round amounts,
     * and structured amounts just below reporting thresholds.
     *
     * @param requestBody optional map with startDate, endDate, and threshold
     * @return 200 OK with analysis results
     */
    @PostMapping("/analyze")
    @Operation(summary = "Analyze payments for suspicious patterns",
            description = "Analyzes payment instructions within a date range for suspicious patterns including duplicate beneficiaries, round amounts, and structured amounts below reporting thresholds.")
    public ResponseEntity<?> analyzePayments(@RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            LocalDate startDate = LocalDate.now().minusDays(30);
            LocalDate endDate = LocalDate.now();
            BigDecimal threshold = new BigDecimal("10000");

            if (requestBody != null) {
                if (requestBody.get("startDate") != null) {
                    startDate = LocalDate.parse(requestBody.get("startDate").toString());
                }
                if (requestBody.get("endDate") != null) {
                    endDate = LocalDate.parse(requestBody.get("endDate").toString());
                }
                if (requestBody.get("threshold") != null) {
                    threshold = new BigDecimal(requestBody.get("threshold").toString());
                }
            }

            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findPaymentsByDateRange(startDate, endDate);

            // Pattern 1: Same beneficiary receiving multiple payments
            Map<String, List<PaymentInstruction>> beneficiaryPayments = new LinkedHashMap<>();
            for (PaymentInstruction p : payments) {
                if (p.getPayeeName() != null) {
                    beneficiaryPayments.computeIfAbsent(p.getPayeeName(), k -> new ArrayList<>()).add(p);
                }
            }

            List<Map<String, Object>> beneficiaryAlerts = new ArrayList<>();
            for (Map.Entry<String, List<PaymentInstruction>> entry : beneficiaryPayments.entrySet()) {
                if (entry.getValue().size() > 3) {
                    BigDecimal total = BigDecimal.ZERO;
                    for (PaymentInstruction p : entry.getValue()) {
                        if (p.getAmount() != null) {
                            total = total.add(p.getAmount());
                        }
                    }
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("pattern", "MULTIPLE_PAYMENTS_SAME_BENEFICIARY");
                    alert.put("beneficiary", entry.getKey());
                    alert.put("paymentCount", entry.getValue().size());
                    alert.put("totalAmount", total);
                    beneficiaryAlerts.add(alert);
                }
            }

            // Pattern 2: Round amount payments (no cents)
            List<Map<String, Object>> roundAmountAlerts = new ArrayList<>();
            for (PaymentInstruction p : payments) {
                if (p.getAmount() != null
                        && p.getAmount().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
                        && p.getAmount().compareTo(threshold) >= 0) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("pattern", "ROUND_AMOUNT");
                    alert.put("paymentRef", p.getPaymentRef());
                    alert.put("amount", p.getAmount());
                    alert.put("payeeName", p.getPayeeName());
                    roundAmountAlerts.add(alert);
                }
            }

            // Pattern 3: Structured amounts just below reporting threshold
            BigDecimal lowerBound = threshold.multiply(new BigDecimal("0.90"));
            List<Map<String, Object>> structuredAlerts = new ArrayList<>();
            for (PaymentInstruction p : payments) {
                if (p.getAmount() != null
                        && p.getAmount().compareTo(lowerBound) >= 0
                        && p.getAmount().compareTo(threshold) < 0) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("pattern", "STRUCTURED_BELOW_THRESHOLD");
                    alert.put("paymentRef", p.getPaymentRef());
                    alert.put("amount", p.getAmount());
                    alert.put("payeeName", p.getPayeeName());
                    alert.put("threshold", threshold);
                    structuredAlerts.add(alert);
                }
            }

            int totalAlerts = beneficiaryAlerts.size() + roundAmountAlerts.size() + structuredAlerts.size();

            log.info("Compliance analysis completed: {} alerts found for period {} to {}",
                    totalAlerts, startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Compliance analysis completed");
            response.put("startDate", startDate.toString());
            response.put("endDate", endDate.toString());
            response.put("threshold", threshold);
            response.put("totalPaymentsAnalyzed", payments.size());
            response.put("totalAlerts", totalAlerts);
            response.put("beneficiaryAlerts", beneficiaryAlerts);
            response.put("roundAmountAlerts", roundAmountAlerts);
            response.put("structuredAlerts", structuredAlerts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error analyzing payments: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Compliance analysis failed");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
