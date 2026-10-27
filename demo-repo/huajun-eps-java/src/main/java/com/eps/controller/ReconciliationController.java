package com.eps.controller;

import com.eps.entity.PaymentInstruction;
import com.eps.entity.ReconMatch;
import com.eps.entity.StatementLine;
import com.eps.repository.PaymentInstructionRepository;
import com.eps.repository.ReconMatchRepository;
import com.eps.repository.StatementLineRepository;

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
import java.util.*;

/**
 * REST controller for bank statement reconciliation operations in the EPS.
 * Provides endpoints for downloading and processing bank statements,
 * performing automatic and manual matching between statements and payments,
 * and viewing reconciliation exceptions and matches.
 *
 * <p>All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/recon")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reconciliation Controller", description = "Bank statement reconciliation APIs")
public class ReconciliationController {

    private final StatementLineRepository statementLineRepository;
    private final PaymentInstructionRepository paymentInstructionRepository;
    private final ReconMatchRepository reconMatchRepository;

    /**
     * Initiates download of bank statements for a given bank code.
     * This is a stub implementation that logs the request and returns an accepted response.
     *
     * @param bankCode the bank code to download statements for
     * @return 202 Accepted with an acknowledgement message
     */
    @PostMapping("/download/{bankCode}")
    @Operation(summary = "Initiate statement download",
            description = "Initiates the download of bank statement files for the specified bank code. Stub implementation.")
    public ResponseEntity<?> downloadStatements(@PathVariable String bankCode) {
        log.info("Statement download initiated for bank code: {}", bankCode);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Statement download initiated for " + bankCode);
        response.put("bankCode", bankCode);
        response.put("status", "ACCEPTED");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Initiates processing of downloaded bank statements for a given bank code.
     * This is a stub implementation that logs the request and returns an accepted response.
     *
     * @param bankCode the bank code to process statements for
     * @return 202 Accepted with an acknowledgement message
     */
    @PostMapping("/process/{bankCode}")
    @Operation(summary = "Initiate statement processing",
            description = "Initiates the processing of downloaded bank statement files for the specified bank code. Stub implementation.")
    public ResponseEntity<?> processStatements(@PathVariable String bankCode) {
        log.info("Statement processing initiated for bank code: {}", bankCode);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Statement processing initiated for " + bankCode);
        response.put("bankCode", bankCode);
        response.put("status", "ACCEPTED");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Performs automatic reconciliation between unreconciled statement lines and settled payments.
     * Matches statement lines (reconStatus not in MA -- i.e., matched) with payment instructions
     * (status SETT) by comparing bank references. Creates {@link ReconMatch} records for successful matches.
     *
     * @param bankCode the bank code to reconcile
     * @return 200 OK with the number of matches created
     */
    @PostMapping("/auto/{bankCode}")
    @Operation(summary = "Perform automatic reconciliation",
            description = "Automatically matches unreconciled statement lines with settled payments by bank reference for the given bank code.")
    public ResponseEntity<?> autoReconcile(@PathVariable String bankCode) {
        try {
            // Find unreconciled statement lines (those not yet matched)
            List<StatementLine> stmtLines = statementLineRepository.findByBankCodeAndValueDate(bankCode, null);

            // Filter to unreconciled (reconStatus is null or not 'MA')
            List<StatementLine> unreconciled = new ArrayList<>();
            if (stmtLines != null) {
                for (StatementLine line : stmtLines) {
                    if (line.getReconStatus() == null || !"MA".equals(line.getReconStatus())) {
                        unreconciled.add(line);
                    }
                }
            }

            // Find settled payments
            List<PaymentInstruction> settledPayments = paymentInstructionRepository.findByStatus("SETT");

            int matchCount = 0;
            for (StatementLine line : unreconciled) {
                if (line.getTransactionRef() == null) continue;

                for (PaymentInstruction payment : settledPayments) {
                    if (line.getTransactionRef().equals(payment.getBankRef())) {
                        ReconMatch match = new ReconMatch();
                        match.setMatchId(UUID.randomUUID().toString().substring(0, 20));
                        match.setStatementNo(line.getStatementNo());
                        match.setEntryRef(line.getEntryRef());
                        match.setLineNo(line.getLineNo());
                        match.setPaymentRef(payment.getPaymentRef());
                        match.setAmount(payment.getAmount());
                        match.setStmtAmount(line.getAmount());
                        if (payment.getAmount() != null && line.getAmount() != null) {
                            match.setDifference(payment.getAmount().subtract(line.getAmount()));
                        } else {
                            match.setDifference(BigDecimal.ZERO);
                        }
                        match.setMatchDate(LocalDate.now());
                        match.setMatchStatus("MA");
                        match.setMatchedBy("AUTO");

                        reconMatchRepository.save(match);

                        // Mark statement line as matched
                        line.setReconStatus("MA");
                        line.setReconDate(LocalDate.now());
                        line.setMatchedPaymentRef(payment.getPaymentRef());
                        statementLineRepository.save(line);

                        matchCount++;
                        break;
                    }
                }
            }

            log.info("Auto reconciliation completed for bank {}: {} matches created", bankCode, matchCount);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Auto reconciliation completed");
            response.put("bankCode", bankCode);
            response.put("matchCount", matchCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in auto reconciliation for bank {}: {}", bankCode, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Auto reconciliation failed");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Retrieves reconciliation exception reports grouped by status and date.
     *
     * @param startDate optional start date filter
     * @param endDate   optional end date filter
     * @return 200 OK with exception report data
     */
    @GetMapping("/exceptions")
    @Operation(summary = "Get reconciliation exceptions",
            description = "Retrieves reconciliation exception reports grouped by status and date, with optional date range filters.")
    public ResponseEntity<?> getExceptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.now().minusDays(30);
            LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

            List<Object[]> exceptionReports = reconMatchRepository
                    .findExceptionReportGroupByStatusAndDate(effectiveStartDate, effectiveEndDate);

            Map<String, Object> response = new HashMap<>();
            response.put("exceptionReports", exceptionReports);
            response.put("startDate", effectiveStartDate.toString());
            response.put("endDate", effectiveEndDate.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving exception reports: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve exception reports");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Creates a manual reconciliation match between a payment and a statement line.
     *
     * @param requestBody map containing paymentRef, statementNo, entryRef, lineNo, and matchedBy
     * @return 201 Created with the match details
     */
    @PostMapping("/manual-match")
    @Operation(summary = "Create a manual reconciliation match",
            description = "Manually matches a payment instruction with a statement line and creates a reconciliation match record.")
    public ResponseEntity<?> createManualMatch(@RequestBody Map<String, Object> requestBody) {
        try {
            String paymentRef = (String) requestBody.get("paymentRef");
            String statementNo = (String) requestBody.get("statementNo");
            String entryRef = (String) requestBody.get("entryRef");
            Object lineNoObj = requestBody.get("lineNo");
            String matchedBy = (String) requestBody.get("matchedBy");

            if (paymentRef == null || statementNo == null || entryRef == null || lineNoObj == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "paymentRef, statementNo, entryRef, and lineNo are required");
                return ResponseEntity.badRequest().body(error);
            }

            Integer lineNo = lineNoObj instanceof Integer ? (Integer) lineNoObj
                    : Integer.parseInt(lineNoObj.toString());

            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(paymentRef);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", paymentRef);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();

            ReconMatch match = new ReconMatch();
            match.setMatchId(UUID.randomUUID().toString().substring(0, 20));
            match.setStatementNo(statementNo);
            match.setEntryRef(entryRef);
            match.setLineNo(lineNo);
            match.setPaymentRef(paymentRef);
            match.setAmount(payment.getAmount());
            match.setStmtAmount(payment.getAmount());
            match.setDifference(BigDecimal.ZERO);
            match.setMatchDate(LocalDate.now());
            match.setMatchStatus("MA");
            match.setMatchedBy(matchedBy != null ? matchedBy : "MANUAL");

            reconMatchRepository.save(match);

            // Update the statement line if found
            List<StatementLine> stmtLines = statementLineRepository.findByBankRef(payment.getBankRef());
            for (StatementLine line : stmtLines) {
                line.setReconStatus("MA");
                line.setReconDate(LocalDate.now());
                line.setMatchedPaymentRef(paymentRef);
                statementLineRepository.save(line);
            }

            log.info("Manual match created: paymentRef={}, statementNo={}, matchedBy={}",
                    paymentRef, statementNo, matchedBy);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Manual match created successfully");
            response.put("matchId", match.getMatchId());
            response.put("paymentRef", paymentRef);
            response.put("statementNo", statementNo);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating manual match: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to create manual match");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Retrieves reconciliation matches with optional filters.
     *
     * @param matchStatus optional match status filter
     * @param startDate   optional start date filter
     * @param endDate     optional end date filter
     * @return 200 OK with matching reconciliation records
     */
    @GetMapping("/matches")
    @Operation(summary = "Get reconciliation matches",
            description = "Retrieves reconciliation match records with optional filters for match status and date range.")
    public ResponseEntity<?> getMatches(
            @RequestParam(required = false) String matchStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            List<ReconMatch> matches;
            if (matchStatus != null && !matchStatus.isEmpty()) {
                matches = reconMatchRepository.findByMatchStatus(matchStatus);
            } else {
                // Fallback: find all and filter by date in memory
                matches = reconMatchRepository.findAll();
            }

            // Apply date range filtering
            if (startDate != null || endDate != null) {
                LocalDate effectiveStart = startDate != null ? startDate : LocalDate.of(2000, 1, 1);
                LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now().plusDays(1);
                List<ReconMatch> filtered = new ArrayList<>();
                for (ReconMatch match : matches) {
                    if (match.getMatchDate() != null
                            && !match.getMatchDate().isBefore(effectiveStart)
                            && !match.getMatchDate().isAfter(effectiveEnd)) {
                        filtered.add(match);
                    }
                }
                matches = filtered;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("matches", matches);
            response.put("count", matches.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving matches: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve matches");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
