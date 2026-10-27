package com.eps.controller;

import com.eps.entity.PaymentInstruction;
import com.eps.repository.PaymentInstructionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST controller for refund operations in the EPS.
 * Provides endpoints for creating refund payment instructions based on
 * original settled payments and approving refund payments.
 *
 * <p>A refund creates a new payment instruction with the payer and payee swapped.
 * All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Refund Controller", description = "Refund payment processing APIs")
public class RefundController {

    private final PaymentInstructionRepository paymentInstructionRepository;

    /**
     * Creates a refund payment instruction based on an original settled payment.
     * Validates that the original payment is in SETT status, then creates a new
     * payment instruction with swapped payer/payee and negative amount (or specified amount).
     *
     * @param requestBody map containing originalPaymentRef, reason, and optional amount
     * @return 201 Created with the refund payment reference, 404 if original not found,
     *         400 if original is not in SETT status
     */
    @PostMapping
    @Operation(summary = "Create a refund payment",
            description = "Creates a refund payment instruction for a previously settled payment. Swaps payer and payee, sets the amount as negative (or uses the specified amount).")
    public ResponseEntity<?> createRefund(@RequestBody Map<String, Object> requestBody) {
        try {
            String originalPaymentRef = (String) requestBody.get("originalPaymentRef");
            String reason = (String) requestBody.get("reason");
            Object amountObj = requestBody.get("amount");

            if (originalPaymentRef == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "originalPaymentRef is required");
                return ResponseEntity.badRequest().body(error);
            }

            Optional<PaymentInstruction> optOriginal = paymentInstructionRepository.findById(originalPaymentRef);
            if (optOriginal.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Original payment instruction not found");
                error.put("originalPaymentRef", originalPaymentRef);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction original = optOriginal.get();

            if (!"SETT".equals(original.getStatus())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Original payment is not in SETT status; cannot refund");
                error.put("currentStatus", original.getStatus());
                return ResponseEntity.badRequest().body(error);
            }

            // Determine refund amount (negative or as specified)
            BigDecimal refundAmount;
            if (amountObj != null) {
                refundAmount = amountObj instanceof BigDecimal
                        ? (BigDecimal) amountObj
                        : new BigDecimal(amountObj.toString());
            } else {
                refundAmount = original.getAmount() != null
                        ? original.getAmount().negate() : BigDecimal.ZERO;
            }

            // Create refund payment with swapped payer/payee
            PaymentInstruction refund = new PaymentInstruction();
            refund.setPaymentRef("EPS" + System.currentTimeMillis()
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            refund.setChannel(original.getChannel());
            refund.setSourceSystem(original.getSourceSystem());

            // Swap payer and payee
            refund.setPayerAccount(original.getPayeeAccount());
            refund.setPayerName(original.getPayeeName());
            refund.setPayeeAccount(original.getPayerAccount());
            refund.setPayeeName(original.getPayerName());

            refund.setPayeeBank(original.getPayeeBank());
            refund.setPayeeBranch(original.getPayeeBranch());
            refund.setPayeeBic(original.getPayeeBic());
            refund.setCurrency(original.getCurrency());
            refund.setAmount(refundAmount);
            refund.setRemittance("REFUND for " + originalPaymentRef
                    + (reason != null ? ": " + reason : ""));
            refund.setValueDate(LocalDate.now());
            refund.setStatus("INIT");
            refund.setCreateTime(LocalDateTime.now());
            refund.setCreator("REFUND_SYSTEM");

            PaymentInstruction saved = paymentInstructionRepository.save(refund);

            log.info("Created refund payment {} for original payment {} (amount: {}, reason: {})",
                    saved.getPaymentRef(), originalPaymentRef, refundAmount, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Refund payment created successfully");
            response.put("refundPaymentRef", saved.getPaymentRef());
            response.put("originalPaymentRef", originalPaymentRef);
            response.put("amount", saved.getAmount());
            response.put("status", saved.getStatus());
            response.put("reason", reason);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating refund: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to create refund");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Approves a refund payment instruction by changing its status from INIT to APPR.
     *
     * @param ref the refund payment reference
     * @return 200 OK, 404 if not found, 400 if not in INIT status
     */
    @PutMapping("/{ref}/approve")
    @Operation(summary = "Approve a refund payment",
            description = "Changes the status of a refund payment from INIT to APPR (approved).")
    public ResponseEntity<?> approveRefund(@PathVariable String ref) {
        try {
            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(ref);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Refund payment instruction not found");
                error.put("paymentRef", ref);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();

            if (!"INIT".equals(payment.getStatus())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Refund payment is not in INIT status; cannot approve");
                error.put("currentStatus", payment.getStatus());
                return ResponseEntity.badRequest().body(error);
            }

            payment.setStatus("APPR");
            payment.setApprover("REFUND_APPROVER");
            payment.setApprovalTime(LocalDateTime.now());
            paymentInstructionRepository.save(payment);

            log.info("Approved refund payment: {}", ref);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Refund payment approved successfully");
            response.put("paymentRef", ref);
            response.put("status", payment.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving refund {}: {}", ref, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to approve refund");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
