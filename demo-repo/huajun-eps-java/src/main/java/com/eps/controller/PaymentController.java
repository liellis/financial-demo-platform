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

import javax.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST controller for payment instruction operations in the EPS (Electronic Payment System).
 * Provides endpoints for creating, approving, rejecting, cancelling, dispatching,
 * acknowledging, and searching payment instructions.
 *
 * <p>All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Controller", description = "Payment instruction management APIs")
public class PaymentController {

    private final PaymentInstructionRepository paymentInstructionRepository;

    private static final Set<String> VALID_CHANNELS = Set.of("FAST", "MEPS", "PAYNOW", "GIRO", "SWIFT");

    /**
     * Generates a unique payment reference string.
     *
     * @return a unique payment reference prefixed with "EPS"
     */
    private String generatePaymentRef() {
        return "EPS" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * Validates that the given channel is one of the supported payment channels.
     *
     * @param channel the channel to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidChannel(String channel) {
        return channel != null && VALID_CHANNELS.contains(channel.toUpperCase());
    }

    /**
     * Creates a single payment instruction.
     * Validates the channel and generates a unique payment reference.
     * Sets the initial status to "INIT" and records the creation timestamp.
     *
     * @param payment the payment instruction to create
     * @return 201 Created with the saved payment instruction
     */
    @PostMapping
    @Operation(summary = "Create a single payment instruction",
            description = "Creates a new payment instruction with status INIT. Validates that the channel is one of FAST, MEPS, PAYNOW, GIRO, or SWIFT.")
    public ResponseEntity<?> createPayment(@Valid @RequestBody PaymentInstruction payment) {
        try {
            if (payment.getChannel() == null || !isValidChannel(payment.getChannel())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid channel. Must be one of: " + String.join(", ", VALID_CHANNELS));
                error.put("providedChannel", payment.getChannel());
                return ResponseEntity.badRequest().body(error);
            }

            payment.setPaymentRef(generatePaymentRef());
            payment.setStatus("INIT");
            payment.setCreateTime(LocalDateTime.now());

            PaymentInstruction saved = paymentInstructionRepository.save(payment);
            log.info("Created payment instruction: {}", saved.getPaymentRef());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment instruction created successfully");
            response.put("paymentRef", saved.getPaymentRef());
            response.put("status", saved.getStatus());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating payment instruction: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to create payment instruction");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Creates multiple payment instructions in a batch.
     * Each payment is assigned a unique reference, status "INIT", and creation timestamp.
     *
     * @param payments the list of payment instructions to create
     * @return 201 Created with the count of saved payments
     */
    @PostMapping("/batch")
    @Operation(summary = "Create payment instructions in batch",
            description = "Accepts a list of payment instructions, generates references for each, and saves them all with INIT status.")
    public ResponseEntity<?> createBatchPayments(@Valid @RequestBody List<PaymentInstruction> payments) {
        try {
            List<PaymentInstruction> toSave = new ArrayList<>();
            for (PaymentInstruction payment : payments) {
                if (payment.getChannel() != null && !isValidChannel(payment.getChannel())) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Invalid channel for payment: " + payment.getChannel());
                    return ResponseEntity.badRequest().body(error);
                }
                payment.setPaymentRef(generatePaymentRef());
                payment.setStatus("INIT");
                payment.setCreateTime(LocalDateTime.now());
                toSave.add(payment);
            }

            List<PaymentInstruction> saved = paymentInstructionRepository.saveAll(toSave);
            log.info("Created {} payment instructions in batch", saved.size());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Batch payment instructions created successfully");
            response.put("count", saved.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating batch payment instructions: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to create batch payment instructions");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Approves a payment instruction by changing its status from INIT to APPR.
     *
     * @param ref        the payment reference
     * @param approvedBy the user who approved the payment
     * @return 200 OK with the updated payment, 404 if not found, 400 if not in INIT status
     */
    @PutMapping("/{ref}/approve")
    @Operation(summary = "Approve a payment instruction",
            description = "Changes the status of a payment from INIT to APPR. Records the approver and approval time.")
    public ResponseEntity<?> approvePayment(@PathVariable String ref, @RequestParam String approvedBy) {
        try {
            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(ref);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", ref);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();
            if (!"INIT".equals(payment.getStatus())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment is not in INIT status; cannot approve");
                error.put("currentStatus", payment.getStatus());
                return ResponseEntity.badRequest().body(error);
            }

            payment.setStatus("APPR");
            payment.setApprover(approvedBy);
            payment.setApprovalTime(LocalDateTime.now());
            paymentInstructionRepository.save(payment);

            log.info("Approved payment instruction: {} by {}", ref, approvedBy);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment approved successfully");
            response.put("paymentRef", ref);
            response.put("status", payment.getStatus());
            response.put("approvedBy", approvedBy);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving payment {}: {}", ref, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to approve payment");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Rejects a payment instruction by changing its status to RJCT.
     *
     * @param ref the payment reference
     * @return 200 OK, 404 if not found
     */
    @PutMapping("/{ref}/reject")
    @Operation(summary = "Reject a payment instruction",
            description = "Changes the status of a payment to RJCT (rejected).")
    public ResponseEntity<?> rejectPayment(@PathVariable String ref) {
        try {
            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(ref);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", ref);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();
            payment.setStatus("RJCT");
            paymentInstructionRepository.save(payment);

            log.info("Rejected payment instruction: {}", ref);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment rejected successfully");
            response.put("paymentRef", ref);
            response.put("status", payment.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error rejecting payment {}: {}", ref, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to reject payment");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Cancels a payment instruction by changing its status to CANC.
     *
     * @param ref the payment reference
     * @return 200 OK, 404 if not found
     */
    @PutMapping("/{ref}/cancel")
    @Operation(summary = "Cancel a payment instruction",
            description = "Changes the status of a payment to CANC (cancelled).")
    public ResponseEntity<?> cancelPayment(@PathVariable String ref) {
        try {
            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(ref);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", ref);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();
            payment.setStatus("CANC");
            paymentInstructionRepository.save(payment);

            log.info("Cancelled payment instruction: {}", ref);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment cancelled successfully");
            response.put("paymentRef", ref);
            response.put("status", payment.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error cancelling payment {}: {}", ref, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to cancel payment");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Dispatches all approved payments for a given channel by changing their status to WAIT.
     *
     * @param channel the payment channel
     * @return 200 OK with count of dispatched payments, 400 if channel invalid
     */
    @PostMapping("/dispatch/{channel}")
    @Operation(summary = "Dispatch approved payments for a channel",
            description = "Finds all payments with status APPR for the specified channel and changes them to WAIT.")
    public ResponseEntity<?> dispatchPayments(@PathVariable String channel) {
        try {
            if (!isValidChannel(channel)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid channel. Must be one of: " + String.join(", ", VALID_CHANNELS));
                return ResponseEntity.badRequest().body(error);
            }

            String upperChannel = channel.toUpperCase();
            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findByChannelAndStatus(upperChannel, "APPR");

            int count = 0;
            for (PaymentInstruction payment : payments) {
                payment.setStatus("WAIT");
                paymentInstructionRepository.save(payment);
                count++;
            }

            log.info("Dispatched {} payments for channel {}", count, upperChannel);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payments dispatched successfully");
            response.put("channel", upperChannel);
            response.put("dispatchedCount", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error dispatching payments for channel {}: {}", channel, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to dispatch payments");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Retrieves a payment instruction by its reference.
     *
     * @param ref the payment reference
     * @return 200 OK with the payment, 404 if not found
     */
    @GetMapping("/{ref}")
    @Operation(summary = "Get a payment instruction by reference",
            description = "Retrieves a single payment instruction by its unique payment reference.")
    public ResponseEntity<?> getPayment(@PathVariable String ref) {
        try {
            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(ref);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", ref);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("payment", optPayment.get());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving payment {}: {}", ref, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve payment");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Searches for payment instructions with optional filters.
     * Defaults to the last 7 days if no date range is specified.
     *
     * @param channel   optional channel filter
     * @param status    optional status filter
     * @param currency  optional currency filter
     * @param startDate optional start date (defaults to 7 days ago)
     * @param endDate   optional end date (defaults to today)
     * @return 200 OK with matching payment instructions
     */
    @GetMapping
    @Operation(summary = "Search payment instructions",
            description = "Searches for payment instructions with optional filters for channel, status, currency, and date range. Defaults to the last 7 days.")
    public ResponseEntity<?> searchPayments(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
            LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

            List<PaymentInstruction> payments = paymentInstructionRepository
                    .findPaymentsByDateRange(effectiveStartDate, effectiveEndDate);

            // Apply additional filters in-memory
            List<PaymentInstruction> filtered = new ArrayList<>();
            for (PaymentInstruction payment : payments) {
                boolean matches = true;
                if (channel != null && !channel.equalsIgnoreCase(payment.getChannel())) {
                    matches = false;
                }
                if (status != null && !status.equalsIgnoreCase(payment.getStatus())) {
                    matches = false;
                }
                if (currency != null && !currency.equalsIgnoreCase(payment.getCurrency())) {
                    matches = false;
                }
                if (matches) {
                    filtered.add(payment);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("payments", filtered);
            response.put("count", filtered.size());
            response.put("startDate", effectiveStartDate.toString());
            response.put("endDate", effectiveEndDate.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching payments: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to search payments");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Acknowledges a payment with bank reference and settlement date.
     * Updates the payment status to ACK and then SETT.
     *
     * @param ackData map containing paymentRef, bankRef, and settleDate
     * @return 200 OK, 404 if payment not found
     */
    @PostMapping("/ack")
    @Operation(summary = "Acknowledge a payment",
            description = "Acknowledges a payment by updating its bank reference, settlement date, and changing status through ACK to SETT.")
    public ResponseEntity<?> acknowledgePayment(@RequestBody Map<String, Object> ackData) {
        try {
            String paymentRef = (String) ackData.get("paymentRef");
            String bankRef = (String) ackData.get("bankRef");
            String settleDateStr = (String) ackData.get("settleDate");

            if (paymentRef == null || bankRef == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "paymentRef and bankRef are required");
                return ResponseEntity.badRequest().body(error);
            }

            Optional<PaymentInstruction> optPayment = paymentInstructionRepository.findById(paymentRef);
            if (optPayment.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Payment instruction not found");
                error.put("paymentRef", paymentRef);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            PaymentInstruction payment = optPayment.get();
            payment.setBankRef(bankRef);
            if (settleDateStr != null && !settleDateStr.isEmpty()) {
                payment.setSettleDate(LocalDate.parse(settleDateStr, DateTimeFormatter.ISO_DATE));
            }
            payment.setStatus("ACK");
            paymentInstructionRepository.save(payment);

            // Move to SETT after ACK
            payment.setStatus("SETT");
            paymentInstructionRepository.save(payment);

            log.info("Acknowledged payment {} with bank ref {} and settled", paymentRef, bankRef);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment acknowledged and settled successfully");
            response.put("paymentRef", paymentRef);
            response.put("bankRef", bankRef);
            response.put("status", payment.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error acknowledging payment: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to acknowledge payment");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
