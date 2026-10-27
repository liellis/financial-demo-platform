package com.eps.controller;

import com.eps.entity.InternalAccount;
import com.eps.repository.InternalAccountRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * REST controller for liquidity management operations in the EPS.
 * Provides endpoints for updating account positions, performing fund sweeps
 * between accounts, and calculating interest on account balances.
 *
 * <p>All monetary calculations use {@link BigDecimal} for precision.
 * All endpoints return {@link ResponseEntity} with appropriate HTTP status codes
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/liquidity")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Liquidity Controller", description = "Liquidity and account position management APIs")
public class LiquidityController {

    private final InternalAccountRepository internalAccountRepository;

    /**
     * Updates the balance position for an internal account.
     * Takes the current balance and available balance and updates the account record.
     *
     * @param requestBody map containing accountNo, currentBalance, and availableBalance
     * @return 200 OK with updated account details, 404 if account not found
     */
    @PostMapping("/update-position")
    @Operation(summary = "Update account position",
            description = "Updates the current and available balance for a specified internal account.")
    public ResponseEntity<?> updatePosition(@RequestBody Map<String, Object> requestBody) {
        try {
            String accountNo = (String) requestBody.get("accountNo");
            Object currentBalanceObj = requestBody.get("currentBalance");
            Object availableBalanceObj = requestBody.get("availableBalance");

            if (accountNo == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "accountNo is required");
                return ResponseEntity.badRequest().body(error);
            }

            Optional<InternalAccount> optAccount = internalAccountRepository.findById(accountNo);
            if (optAccount.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Internal account not found");
                error.put("accountNo", accountNo);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            InternalAccount account = optAccount.get();

            if (currentBalanceObj != null) {
                BigDecimal currentBalance = currentBalanceObj instanceof BigDecimal
                        ? (BigDecimal) currentBalanceObj
                        : new BigDecimal(currentBalanceObj.toString());
                account.setCurrentBalance(currentBalance);
            }

            if (availableBalanceObj != null) {
                BigDecimal availableBalance = availableBalanceObj instanceof BigDecimal
                        ? (BigDecimal) availableBalanceObj
                        : new BigDecimal(availableBalanceObj.toString());
                account.setAvailableBalance(availableBalance);
            }

            account.setLastUpdateTime(LocalDateTime.now());
            internalAccountRepository.save(account);

            log.info("Updated position for account {}: currentBalance={}, availableBalance={}",
                    accountNo, account.getCurrentBalance(), account.getAvailableBalance());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Account position updated successfully");
            response.put("accountNo", account.getAccountNo());
            response.put("currentBalance", account.getCurrentBalance());
            response.put("availableBalance", account.getAvailableBalance());
            response.put("lastUpdateTime", account.getLastUpdateTime().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating account position: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to update account position");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Performs a fund sweep (transfer) between two internal accounts.
     * Validates that the source account has sufficient funds before transferring.
     *
     * @param requestBody map containing fromAccount, toAccount, amount, and currency
     * @return 200 OK with transfer details, 400 if insufficient funds, 404 if account not found
     */
    @PostMapping("/sweep")
    @Operation(summary = "Perform fund sweep between accounts",
            description = "Transfers a specified amount from one internal account to another. Validates sufficient funds in the source account.")
    public ResponseEntity<?> sweepFunds(@RequestBody Map<String, Object> requestBody) {
        try {
            String fromAccount = (String) requestBody.get("fromAccount");
            String toAccount = (String) requestBody.get("toAccount");
            Object amountObj = requestBody.get("amount");
            String currency = (String) requestBody.get("currency");

            if (fromAccount == null || toAccount == null || amountObj == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "fromAccount, toAccount, and amount are required");
                return ResponseEntity.badRequest().body(error);
            }

            BigDecimal amount = amountObj instanceof BigDecimal
                    ? (BigDecimal) amountObj
                    : new BigDecimal(amountObj.toString());

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Amount must be greater than zero");
                return ResponseEntity.badRequest().body(error);
            }

            Optional<InternalAccount> optFrom = internalAccountRepository.findById(fromAccount);
            Optional<InternalAccount> optTo = internalAccountRepository.findById(toAccount);

            if (optFrom.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Source account not found");
                error.put("accountNo", fromAccount);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            if (optTo.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Destination account not found");
                error.put("accountNo", toAccount);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            InternalAccount from = optFrom.get();
            InternalAccount to = optTo.get();

            // Validate sufficient funds
            if (from.getAvailableBalance() == null
                    || from.getAvailableBalance().compareTo(amount) < 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Insufficient funds in source account");
                error.put("accountNo", fromAccount);
                error.put("availableBalance", from.getAvailableBalance());
                error.put("requestedAmount", amount);
                return ResponseEntity.badRequest().body(error);
            }

            // Perform transfer
            from.setCurrentBalance(from.getCurrentBalance().subtract(amount));
            from.setAvailableBalance(from.getAvailableBalance().subtract(amount));
            from.setLastUpdateTime(LocalDateTime.now());

            to.setCurrentBalance(to.getCurrentBalance().add(amount));
            to.setAvailableBalance(to.getAvailableBalance().add(amount));
            to.setLastUpdateTime(LocalDateTime.now());

            internalAccountRepository.save(from);
            internalAccountRepository.save(to);

            log.info("Fund sweep completed: {} {} from {} to {}", amount, currency, fromAccount, toAccount);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Fund sweep completed successfully");
            response.put("fromAccount", fromAccount);
            response.put("toAccount", toAccount);
            response.put("amount", amount);
            response.put("currency", currency);
            response.put("fromNewBalance", from.getCurrentBalance());
            response.put("toNewBalance", to.getCurrentBalance());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error performing fund sweep: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Fund sweep failed");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Calculates interest for an account over a date range at a given rate.
     * Uses the formula: interest = balance * ratePercent * days / 36500.
     *
     * @param requestBody map containing accountNo, fromDate, toDate, and ratePercent
     * @return 200 OK with interest calculation details, 404 if account not found
     */
    @PostMapping("/calculate-interest")
    @Operation(summary = "Calculate interest on account balance",
            description = "Calculates interest for a specified account over a date range using the formula: interest = balance * ratePercent * days / 36500.")
    public ResponseEntity<?> calculateInterest(@RequestBody Map<String, Object> requestBody) {
        try {
            String accountNo = (String) requestBody.get("accountNo");
            String fromDateStr = (String) requestBody.get("fromDate");
            String toDateStr = (String) requestBody.get("toDate");
            Object ratePercentObj = requestBody.get("ratePercent");

            if (accountNo == null || fromDateStr == null || toDateStr == null || ratePercentObj == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "accountNo, fromDate, toDate, and ratePercent are required");
                return ResponseEntity.badRequest().body(error);
            }

            Optional<InternalAccount> optAccount = internalAccountRepository.findById(accountNo);
            if (optAccount.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Internal account not found");
                error.put("accountNo", accountNo);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            InternalAccount account = optAccount.get();
            LocalDate fromDate = LocalDate.parse(fromDateStr);
            LocalDate toDate = LocalDate.parse(toDateStr);

            if (toDate.isBefore(fromDate)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "toDate must be on or after fromDate");
                return ResponseEntity.badRequest().body(error);
            }

            BigDecimal ratePercent = ratePercentObj instanceof BigDecimal
                    ? (BigDecimal) ratePercentObj
                    : new BigDecimal(ratePercentObj.toString());

            long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            BigDecimal balance = account.getCurrentBalance() != null
                    ? account.getCurrentBalance() : BigDecimal.ZERO;

            // interest = balance * ratePercent * days / 36500
            BigDecimal interest = balance.multiply(ratePercent)
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(36500), 4, RoundingMode.HALF_UP);

            log.info("Interest calculated for account {}: balance={}, rate={}, days={}, interest={}",
                    accountNo, balance, ratePercent, days, interest);

            Map<String, Object> response = new HashMap<>();
            response.put("accountNo", accountNo);
            response.put("currency", account.getCurrency());
            response.put("balance", balance);
            response.put("ratePercent", ratePercent);
            response.put("fromDate", fromDateStr);
            response.put("toDate", toDateStr);
            response.put("days", days);
            response.put("interest", interest);
            response.put("formula", "balance * ratePercent * days / 36500");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error calculating interest: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Interest calculation failed");
            error.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
