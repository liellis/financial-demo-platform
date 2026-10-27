package com.eps.service.payment;

import com.eps.config.EpsConfig;
import com.eps.exception.*;
import com.eps.model.entity.*;
import com.eps.model.enums.*;
import com.eps.model.request.PaymentRequest;
import com.eps.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付核心服务 - 支付指令全生命周期管理
 * Payment Core Service - Full lifecycle management of payment instructions.
 * <p>
 * 对应原AS/400 COBOL程序 / Corresponds to original AS/400 COBOL programs:
 * <ul>
 *   <li>EPSP100 - 单笔支付创建 / Single payment creation</li>
 *   <li>EPSP110 - 批量支付导入 / Batch payment import</li>
 *   <li>EPSP200 - 支付审批(双人复核) / Payment approval (maker-checker)</li>
 *   <li>EPSP300 - 支付派发到银行渠道 / Payment dispatch to bank channels</li>
 *   <li>EPSP400 - 银行回执处理(ACK/NAK) / Bank acknowledgement processing</li>
 *   <li>EPSPVAL - 多维度支付校验 / Multi-dimension payment validation</li>
 *   <li>EPSP900EOD - 日终滞留支付检查 / End-of-day stale payment check</li>
 * </ul>
 *
 * @author EPS Migration Team
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional
public class PaymentService {

    // ──────────────────────────── 依赖注入 Dependencies ────────────────────────────

    private final PaymentRepository paymentRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final ChannelLimitRepository channelLimitRepository;
    private final DailyAccumulationRepository dailyAccumulationRepository;
    private final AuditLogRepository auditLogRepository;
    private final BankChannelRepository bankChannelRepository;
    private final EpsConfig epsConfig;

    /**
     * 构造器注入 / Constructor injection.
     */
    public PaymentService(PaymentRepository paymentRepository,
                          PaymentRequestRepository paymentRequestRepository,
                          ChannelLimitRepository channelLimitRepository,
                          DailyAccumulationRepository dailyAccumulationRepository,
                          AuditLogRepository auditLogRepository,
                          BankChannelRepository bankChannelRepository,
                          EpsConfig epsConfig) {
        this.paymentRepository = paymentRepository;
        this.paymentRequestRepository = paymentRequestRepository;
        this.channelLimitRepository = channelLimitRepository;
        this.dailyAccumulationRepository = dailyAccumulationRepository;
        this.auditLogRepository = auditLogRepository;
        this.bankChannelRepository = bankChannelRepository;
        this.epsConfig = epsConfig;
    }

    // ──────────────────────────── EPSP100: 单笔支付创建 ────────────────────────────

    /**
     * 创建单笔支付指令 (对应 COBOL EPSP100)
     * Create a single payment instruction.
     * <p>
     * 处理流程 / Process flow:
     * <ol>
     *   <li>接收并解析支付请求 / Receive and parse payment request</li>
     *   <li>执行多维度校验 / Execute multi-dimension validation</li>
     *   <li>生成支付引用号(18位) / Generate payment reference number (18 digits)</li>
     *   <li>持久化支付实体,状态为 PENDING_APPROVAL / Persist entity with PENDING_APPROVAL status</li>
     *   <li>记录审计日志 / Record audit log</li>
     * </ol>
     *
     * @param request 支付请求对象 / Payment request DTO
     * @return 生成的支付引用号 / Generated payment reference number
     * @throws PaymentValidationException 校验失败时抛出 / Thrown when validation fails
     */
    public String createPayment(PaymentRequest request) {
        log.info("EPSP100: 开始创建单笔支付 / Starting single payment creation - payer: {}, currency: {}, amount: {}",
                request.getPayerAccount(), request.getCurrency(), request.getAmount());

        // Step 1: 多维度校验 / Multi-dimension validation
        List<String> violations = validatePayment(request);
        if (!violations.isEmpty()) {
            log.warn("EPSP100: 支付校验失败 / Payment validation failed - violations: {}", violations);
            throw new PaymentValidationException("支付校验未通过 / Payment validation failed", violations);
        }

        // Step 2: 生成支付引用号 / Generate payment reference number
        String paymentRef = generatePaymentReference();

        // Step 3: 构建支付实体 / Build payment entity
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentRef(paymentRef);
        payment.setChannel(request.getChannel());
        payment.setPayerAccount(request.getPayerAccount());
        payment.setPayerName(request.getPayerName());
        payment.setPayeeAccount(request.getPayeeAccount());
        payment.setPayeeName(request.getPayeeName());
        payment.setPayeeBankCode(request.getPayeeBankCode());
        payment.setCurrency(request.getCurrency());
        payment.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        payment.setNarrative(request.getNarrative());
        payment.setValueDate(request.getValueDate());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPriority(request.getPriority() != null ? request.getPriority() : PaymentPriority.NORMAL);
        payment.setStatus(PaymentStatus.PENDING_APPROVAL);
        payment.setCreator(request.getCreator());
        payment.setCreatedTime(LocalDateTime.now());
        payment.setUpdatedTime(LocalDateTime.now());

        // Step 4: 持久化 / Persist
        paymentRepository.save(payment);

        // Step 5: 审计日志 / Audit log
        writeAuditLog(paymentRef, AuditAction.PAYMENT_CREATED, request.getCreator(),
                "单笔支付创建 / Single payment created - amount: " + request.getAmount() + " " + request.getCurrency());

        log.info("EPSP100: 支付创建成功 / Payment created successfully - ref: {}", paymentRef);
        return paymentRef;
    }

    // ──────────────────────────── EPSP110: 批量支付导入 ────────────────────────────

    /**
     * 批量导入支付文件 (对应 COBOL EPSP110)
     * Import batch payments from a CSV file.
     * <p>
     * CSV格式 / CSV format: payerAccount,payerName,payeeAccount,payeeName,payeeBankCode,
     *                       currency,amount,narrative,valueDate,paymentMethod,priority,channel
     * <p>
     * 导入策略 / Import strategy:
     * <ul>
     *   <li>逐行解析,跳过空行和注释行(#开头) / Parse line-by-line, skip blanks and comments (#)</li>
     *   <li>单行校验失败不阻塞整批 / Single line failure does not block the entire batch</li>
     *   <li>返回详细导入报告 / Return a detailed import report</li>
     * </ul>
     *
     * @param filePath CSV文件完整路径 / Full path to the CSV file
     * @return 导入结果报告 / Import result report (total, success, failed, details)
     */
    public BatchImportResult batchImport(String filePath) {
        log.info("EPSP110: 开始批量导入 / Starting batch import - file: {}", filePath);

        BatchImportResult result = new BatchImportResult();
        result.setFileName(filePath);
        result.setStartTime(LocalDateTime.now());
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // 跳过空行和注释行 / Skip blank and comment lines
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    PaymentRequest request = parseCsvLine(line);
                    String paymentRef = createPayment(request);
                    result.addSuccess(lineNumber, paymentRef);
                } catch (PaymentValidationException e) {
                    result.addFailure(lineNumber, line, e.getViolations());
                    log.warn("EPSP110: 行 {} 校验失败 / Line {} validation failed - {}", lineNumber, lineNumber, e.getViolations());
                } catch (Exception e) {
                    result.addFailure(lineNumber, line, Collections.singletonList(e.getMessage()));
                    log.error("EPSP110: 行 {} 处理异常 / Line {} processing error", lineNumber, lineNumber, e);
                }
            }
        } catch (IOException e) {
            log.error("EPSP110: 文件读取失败 / File read error - file: {}", filePath, e);
            throw new BatchImportException("批量导入文件读取失败 / Batch import file read failed: " + filePath, e);
        }

        result.setEndTime(LocalDateTime.now());
        log.info("EPSP110: 批量导入完成 / Batch import completed - total: {}, success: {}, failed: {}",
                result.getTotalCount(), result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    /**
     * 解析CSV行 / Parse a CSV line into a PaymentRequest.
     */
    private PaymentRequest parseCsvLine(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length < 11) {
            throw new BatchImportException("CSV字段不足,需要至少11列 / Insufficient CSV fields, need at least 11 columns");
        }
        PaymentRequest req = new PaymentRequest();
        req.setPayerAccount(fields[0].trim());
        req.setPayerName(fields[1].trim());
        req.setPayeeAccount(fields[2].trim());
        req.setPayeeName(fields[3].trim());
        req.setPayeeBankCode(fields[4].trim());
        req.setCurrency(fields[5].trim());
        req.setAmount(new BigDecimal(fields[6].trim()));
        req.setNarrative(fields[7].trim());
        req.setValueDate(LocalDate.parse(fields[8].trim(), DateTimeFormatter.ISO_DATE));
        req.setPaymentMethod(fields[9].trim());
        req.setPriority(fields.length > 10 && !fields[10].trim().isEmpty()
                ? PaymentPriority.valueOf(fields[10].trim()) : PaymentPriority.NORMAL);
        req.setChannel(fields.length > 11 && !fields[11].trim().isEmpty() ? fields[11].trim() : "DEFAULT");
        req.setCreator("BATCH_IMPORT");
        return req;
    }

    // ──────────────────────────── EPSP200: 支付审批(双人复核) ────────────────────────────

    /**
     * 审批通过支付指令 (对应 COBOL EPSP200 APPROVE)
     * Approve a payment instruction (maker-checker control).
     * <p>
     * 约束 / Constraints:
     * <ul>
     *   <li>审批人不能是创建人 / Approver must not be the creator</li>
     *   <li>仅 PENDING_APPROVAL 状态可审批 / Only PENDING_APPROVAL status is approvable</li>
     *   <li>审批后状态变为 APPROVED / Status becomes APPROVED after approval</li>
     * </ul>
     *
     * @param paymentRef 支付引用号 / Payment reference number
     * @param approver   审批人ID / Approver ID
     * @throws PaymentNotFoundException 支付不存在 / Payment not found
     * @throws ApprovalException       审批约束违反 / Approval constraint violated
     */
    public void approvePayment(String paymentRef, String approver) {
        log.info("EPSP200: 审批支付 / Approving payment - ref: {}, approver: {}", paymentRef, approver);

        PaymentEntity payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new PaymentNotFoundException("支付未找到 / Payment not found: " + paymentRef));

        // 校验状态 / Validate status
        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new ApprovalException("支付状态不允许审批,当前状态: " + payment.getStatus()
                    + " / Payment status not approvable, current: " + payment.getStatus());
        }

        // 校验双人复核 / Maker-checker: approver cannot be creator
        if (approver.equals(payment.getCreator())) {
            throw new ApprovalException("审批人不能与创建人相同 / Approver cannot be the same as creator");
        }

        // 更新状态 / Update status
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setApprover(approver);
        payment.setApprovedTime(LocalDateTime.now());
        payment.setUpdatedTime(LocalDateTime.now());
        paymentRepository.save(payment);

        writeAuditLog(paymentRef, AuditAction.PAYMENT_APPROVED, approver,
                "支付审批通过 / Payment approved - amount: " + payment.getAmount());

        log.info("EPSP200: 支付审批成功 / Payment approved successfully - ref: {}", paymentRef);
    }

    /**
     * 驳回支付指令 (对应 COBOL EPSP200 REJECT)
     * Reject a payment instruction.
     *
     * @param paymentRef 支付引用号 / Payment reference number
     * @param approver   审批人ID / Approver ID
     * @param reason     驳回原因 / Rejection reason
     */
    public void rejectPayment(String paymentRef, String approver, String reason) {
        log.info("EPSP200: 驳回支付 / Rejecting payment - ref: {}, approver: {}, reason: {}", paymentRef, approver, reason);

        PaymentEntity payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new PaymentNotFoundException("支付未找到 / Payment not found: " + paymentRef));

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new ApprovalException("支付状态不允许驳回,当前状态: " + payment.getStatus()
                    + " / Payment status not rejectable, current: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REJECTED);
        payment.setApprover(approver);
        payment.setRejectionReason(reason);
        payment.setApprovedTime(LocalDateTime.now());
        payment.setUpdatedTime(LocalDateTime.now());
        paymentRepository.save(payment);

        writeAuditLog(paymentRef, AuditAction.PAYMENT_REJECTED, approver,
                "支付驳回 / Payment rejected - reason: " + reason);

        log.info("EPSP200: 支付驳回成功 / Payment rejected successfully - ref: {}", paymentRef);
    }

    // ──────────────────────────── EPSP300: 支付派发到银行渠道 ────────────────────────────

    /**
     * 派发已审批支付到银行渠道 (对应 COBOL EPSP300)
     * Dispatch approved payments to bank channels.
     * <p>
     * 派发逻辑 / Dispatch logic:
     * <ol>
     *   <li>查询指定渠道所有 APPROVED 状态的支付 / Query all APPROVED payments for the channel</li>
     *   <li>按优先级排序 (URGENT > HIGH > NORMAL) / Sort by priority</li>
     *   <li>生成银行报文(如SWIFT MT103/PACS.008) / Generate bank message</li>
     *   <li>更新状态为 DISPATCHED / Update status to DISPATCHED</li>
     *   <li>记录派发审计日志 / Record dispatch audit log</li>
     * </ol>
     *
     * @param channel 银行渠道代码 / Bank channel code (e.g., "SWIFT", "RTGS", "ACH")
     * @return 派发成功的支付数量 / Count of successfully dispatched payments
     */
    public int dispatchPayments(String channel) {
        log.info("EPSP300: 开始派发支付 / Starting payment dispatch - channel: {}", channel);

        List<PaymentEntity> approvedPayments = paymentRepository
                .findByChannelAndStatusOrderByPriorityAsc(channel, PaymentStatus.APPROVED);

        if (approvedPayments.isEmpty()) {
            log.info("EPSP300: 无待派发支付 / No payments to dispatch for channel: {}", channel);
            return 0;
        }

        // 校验渠道是否可用 / Check if channel is available
        BankChannel bankChannel = bankChannelRepository.findByChannelCode(channel)
                .orElseThrow(() -> new ChannelException("渠道未配置 / Channel not configured: " + channel));

        if (!bankChannel.isActive()) {
            throw new ChannelException("渠道未激活 / Channel not active: " + channel);
        }

        int dispatchedCount = 0;
        for (PaymentEntity payment : approvedPayments) {
            try {
                // 生成银行报文 / Generate bank message (simplified - actual implementation would call bank adapter)
                String bankMessage = generateBankMessage(payment, bankChannel);

                // 更新状态 / Update status
                payment.setStatus(PaymentStatus.DISPATCHED);
                payment.setDispatchedTime(LocalDateTime.now());
                payment.setChannelRef(bankMessage);
                payment.setUpdatedTime(LocalDateTime.now());
                paymentRepository.save(payment);

                writeAuditLog(payment.getPaymentRef(), AuditAction.PAYMENT_DISPATCHED, "SYSTEM",
                        "支付派发到 " + channel + " / Payment dispatched to " + channel);

                dispatchedCount++;
            } catch (Exception e) {
                log.error("EPSP300: 支付派发失败 / Payment dispatch failed - ref: {}", payment.getPaymentRef(), e);
                payment.setStatus(PaymentStatus.DISPATCH_FAILED);
                payment.setUpdatedTime(LocalDateTime.now());
                paymentRepository.save(payment);
            }
        }

        log.info("EPSP300: 支付派发完成 / Payment dispatch completed - channel: {}, dispatched: {}/{}",
                channel, dispatchedCount, approvedPayments.size());
        return dispatchedCount;
    }

    /**
     * 生成银行报文 / Generate bank message.
     * 简化实现 - 实际项目中应调用银行适配器 / Simplified - actual projects should call bank adapter.
     */
    private String generateBankMessage(PaymentEntity payment, BankChannel bankChannel) {
        return String.format("MSG-%s-%s-%d",
                bankChannel.getChannelCode(),
                payment.getPaymentRef(),
                System.currentTimeMillis());
    }

    // ──────────────────────────── EPSP400: 银行回执处理 ────────────────────────────

    /**
     * 处理银行回执 ACK/NAK (对应 COBOL EPSP400)
     * Process bank acknowledgement (ACK/NAK).
     * <p>
     * ACK流程 / ACK flow: DISPATCHED → ACK_RECEIVED → SETTLED (后续对账确认后)
     * NAK流程 / NAK flow: DISPATCHED → NAK_RECEIVED → 需人工处理 / Requires manual handling
     *
     * @param paymentRef 支付引用号 / Payment reference number
     * @param ackStatus  回执状态 / Ack status ("ACK" or "NAK")
     * @param bankRef    银行端交易参考号 / Bank-side transaction reference
     */
    public void processAck(String paymentRef, String ackStatus, String bankRef) {
        log.info("EPSP400: 处理银行回执 / Processing bank ack - ref: {}, status: {}, bankRef: {}",
                paymentRef, ackStatus, bankRef);

        PaymentEntity payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new PaymentNotFoundException("支付未找到 / Payment not found: " + paymentRef));

        if (payment.getStatus() != PaymentStatus.DISPATCHED) {
            throw new AckProcessingException("支付状态不是 DISPATCHED,无法处理回执 / "
                    + "Payment not in DISPATCHED status: " + payment.getStatus());
        }

        if ("ACK".equalsIgnoreCase(ackStatus)) {
            payment.setStatus(PaymentStatus.ACK_RECEIVED);
            payment.setBankRef(bankRef);
            payment.setAckTime(LocalDateTime.now());
        } else if ("NAK".equalsIgnoreCase(ackStatus)) {
            payment.setStatus(PaymentStatus.NAK_RECEIVED);
            payment.setBankRef(bankRef);
            payment.setAckTime(LocalDateTime.now());
            log.warn("EPSP400: 银行拒绝支付 / Bank rejected payment - ref: {}", paymentRef);
        } else {
            throw new AckProcessingException("无效的回执状态 / Invalid ack status: " + ackStatus);
        }

        payment.setUpdatedTime(LocalDateTime.now());
        paymentRepository.save(payment);

        writeAuditLog(paymentRef, AuditAction.ACK_PROCESSED, "SYSTEM",
                "银行回执处理 / Bank ack processed - status: " + ackStatus + ", bankRef: " + bankRef);

        log.info("EPSP400: 回执处理完成 / Ack processed successfully - ref: {}", paymentRef);
    }

    // ──────────────────────────── EPSPVAL: 多维度支付校验 ────────────────────────────

    /**
     * 多维度支付校验 (对应 COBOL EPSPVAL)
     * Multi-dimension payment validation.
     * <p>
     * 校验清单 / Validation checklist:
     * <ol>
     *   <li>渠道限额检查 / Channel limit check</li>
     *   <li>当日累计金额检查 / Daily accumulated amount check</li>
     *   <li>重复支付检测 / Duplicate payment detection</li>
     *   <li>制裁名单筛查 / Sanctions screening check</li>
     *   <li>必填字段检查 / Required field check</li>
     * </ol>
     *
     * @param request 支付请求 / Payment request
     * @return 违规项列表(空列表表示校验通过) / List of violation messages (empty means passed)
     */
    public List<String> validatePayment(PaymentRequest request) {
        log.debug("EPSPVAL: 开始多维度校验 / Starting multi-dimension validation");
        List<String> violations = new ArrayList<>();

        // 1. 必填字段校验 / Required fields
        if (request.getPayerAccount() == null || request.getPayerAccount().isBlank()) {
            violations.add("付款账号必填 / Payer account is required");
        }
        if (request.getPayeeAccount() == null || request.getPayeeAccount().isBlank()) {
            violations.add("收款账号必填 / Payee account is required");
        }
        if (request.getCurrency() == null || request.getCurrency().isBlank()) {
            violations.add("币种必填 / Currency is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            violations.add("金额必须大于0 / Amount must be greater than zero");
        }
        if (request.getChannel() == null || request.getChannel().isBlank()) {
            violations.add("渠道必填 / Channel is required");
        }

        // 2. 渠道限额检查 / Channel limit check
        if (request.getAmount() != null && request.getChannel() != null) {
            ChannelLimit channelLimit = channelLimitRepository
                    .findByChannelAndCurrency(request.getChannel(), request.getCurrency())
                    .orElse(null);
            if (channelLimit != null) {
                if (request.getAmount().compareTo(channelLimit.getMaxSingleAmount()) > 0) {
                    violations.add(String.format("单笔金额超过渠道[%s]限额 %s %s / "
                                    + "Amount exceeds channel [%s] single limit of %s %s",
                            request.getChannel(), channelLimit.getMaxSingleAmount(), request.getCurrency(),
                            request.getChannel(), channelLimit.getMaxSingleAmount(), request.getCurrency()));
                }
            }
        }

        // 3. 当日累计金额检查 / Daily accumulated amount check
        if (request.getAmount() != null && request.getPayerAccount() != null) {
            LocalDate today = LocalDate.now();
            DailyAccumulation accumulation = dailyAccumulationRepository
                    .findByAccountAndDate(request.getPayerAccount(), today)
                    .orElse(null);
            BigDecimal currentAccumulated = accumulation != null
                    ? accumulation.getTotalAmount() : BigDecimal.ZERO;

            BigDecimal dailyLimit = epsConfig.getDefaultDailyLimit();
            if (currentAccumulated.add(request.getAmount()).compareTo(dailyLimit) > 0) {
                violations.add(String.format("当日累计金额[%s]加上当前金额[%s]将超出日限额[%s] / "
                                + "Daily accumulated [%s] + current [%s] will exceed daily limit [%s]",
                        currentAccumulated, request.getAmount(), dailyLimit));
            }
        }

        // 4. 重复支付检测 / Duplicate payment detection
        if (request.getPayerAccount() != null && request.getPayeeAccount() != null
                && request.getAmount() != null && request.getCurrency() != null) {
            List<PaymentEntity> duplicates = paymentRepository
                    .findPotentialDuplicates(request.getPayerAccount(), request.getPayeeAccount(),
                            request.getAmount(), request.getCurrency(),
                            LocalDateTime.now().minusMinutes(epsConfig.getDuplicateCheckWindowMinutes()));
            if (!duplicates.isEmpty()) {
                violations.add(String.format("检测到疑似重复支付,笔数: %d, 窗口: %d分钟 / "
                                + "Potential duplicate payments detected, count: %d, window: %d minutes",
                        duplicates.size(), epsConfig.getDuplicateCheckWindowMinutes()));
            }
        }

        // 5. 制裁名单筛查 / Sanctions screening check
        if (request.getPayeeName() != null || request.getPayerName() != null) {
            // 此处在实际项目中调用合规服务 / In real project, delegates to ComplianceService.screenPayment()
            // For now we check if payee bank code is in a high-risk jurisdiction list
            if (request.getPayeeBankCode() != null && epsConfig.getHighRiskBankCodes().contains(request.getPayeeBankCode())) {
                violations.add(String.format("收款银行[%s]在高风险地区列表中 / "
                        + "Payee bank [%s] is in high-risk jurisdiction list", request.getPayeeBankCode()));
            }
        }

        log.debug("EPSPVAL: 校验完成,违规项数量: {}", violations.size());
        return violations;
    }

    // ──────────────────────────── 支付状态查询 ────────────────────────────

    /**
     * 查询支付状态 / Query payment status.
     *
     * @param paymentRef 支付引用号 / Payment reference number
     * @return 支付实体 / Payment entity
     * @throws PaymentNotFoundException 支付不存在 / Payment not found
     */
    @Transactional(readOnly = true)
    public PaymentEntity getPaymentStatus(String paymentRef) {
        log.debug("查询支付状态 / Querying payment status - ref: {}", paymentRef);
        return paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new PaymentNotFoundException("支付未找到 / Payment not found: " + paymentRef));
    }

    // ──────────────────────────── 取消支付 ────────────────────────────

    /**
     * 取消支付 / Cancel a payment.
     * <p>
     * 仅 PENDING_APPROVAL 和 APPROVED 状态可取消 / Only PENDING_APPROVAL and APPROVED can be cancelled.
     * 仅创建人可取消 / Only the creator can cancel.
     *
     * @param paymentRef 支付引用号 / Payment reference number
     * @param creator    创建人ID(用于权限校验) / Creator ID for authorization
     */
    public void cancelPayment(String paymentRef, String creator) {
        log.info("取消支付 / Cancelling payment - ref: {}, creator: {}", paymentRef, creator);

        PaymentEntity payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new PaymentNotFoundException("支付未找到 / Payment not found: " + paymentRef));

        if (!payment.getCreator().equals(creator)) {
            throw new AuthorizationException("仅创建人可取消支付 / Only the creator can cancel the payment");
        }

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL
                && payment.getStatus() != PaymentStatus.APPROVED) {
            throw new PaymentStatusException("当前状态不允许取消: " + payment.getStatus()
                    + " / Current status does not allow cancellation: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setUpdatedTime(LocalDateTime.now());
        paymentRepository.save(payment);

        writeAuditLog(paymentRef, AuditAction.PAYMENT_CANCELLED, creator, "支付取消 / Payment cancelled");
        log.info("支付取消成功 / Payment cancelled successfully - ref: {}", paymentRef);
    }

    // ──────────────────────────── EPSP900EOD: 日终滞留支付检查 ────────────────────────────

    /**
     * 日终滞留支付检查 (对应 COBOL EPSP900EOD)
     * End-of-day stale payment check.
     * <p>
     * 原AS/400定时任务调度: 每日 19:00 / Original AS/400 job schedule: daily at 19:00
     * <p>
     * 检查逻辑 / Check logic:
     * <ul>
     *   <li>支付创建超过 N 小时未被审批 → 报警 / Payment older than N hours not approved → alert</li>
     *   <li>支付 DISPATCHED 超过 N 小时未收到 ACK → 报警 / Payment DISPATCHED older than N hours without ACK → alert</li>
     *   <li>生成滞留报告 / Generate stagnation report</li>
     * </ul>
     *
     * @param date 对账日 / Reconciliation date
     * @return 滞留支付列表 / List of stale payments
     */
    @Transactional(readOnly = true)
    @Scheduled(cron = "0 0 19 * * *") // 每日19:00 / Daily at 19:00, matching AS/400 EPSP900EOD
    public List<StalePaymentReport> endOfDayReconciliation(LocalDate date) {
        log.info("EPSP900EOD: 开始日终滞留支付检查 / Starting EOD stale payment check - date: {}", date);

        List<StalePaymentReport> stalePayments = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int pendingHours = epsConfig.getStalePendingApprovalHours();
        int dispatchHours = epsConfig.getStaleDispatchHours();

        // 检查滞留的待审批支付 / Check stale pending approval payments
        LocalDateTime pendingThreshold = now.minusHours(pendingHours);
        List<PaymentEntity> stalePendingPayments = paymentRepository
                .findByStatusAndCreatedTimeBefore(PaymentStatus.PENDING_APPROVAL, pendingThreshold);
        for (PaymentEntity p : stalePendingPayments) {
            stalePayments.add(new StalePaymentReport(p.getPaymentRef(), "PENDING_APPROVAL",
                    p.getCreatedTime(), "超过" + pendingHours + "小时未审批 / Not approved within " + pendingHours + " hours"));
        }

        // 检查滞留的已派发但未收到回执的支付 / Check stale dispatched payments without ACK
        LocalDateTime dispatchThreshold = now.minusHours(dispatchHours);
        List<PaymentEntity> staleDispatchedPayments = paymentRepository
                .findByStatusAndDispatchedTimeBefore(PaymentStatus.DISPATCHED, dispatchThreshold);
        for (PaymentEntity p : staleDispatchedPayments) {
            stalePayments.add(new StalePaymentReport(p.getPaymentRef(), "DISPATCHED",
                    p.getDispatchedTime(), "超过" + dispatchHours + "小时未收到回执 / No ACK within " + dispatchHours + " hours"));
        }

        if (!stalePayments.isEmpty()) {
            log.warn("EPSP900EOD: 发现滞留支付: {} 笔 / Stale payments found: {}", stalePayments.size(), stalePayments);
        } else {
            log.info("EPSP900EOD: 无滞留支付 / No stale payments found");
        }

        return stalePayments;
    }

    // ──────────────────────────── 辅助方法 / Helper Methods ────────────────────────────

    /**
     * 生成18位支付引用号 / Generate 18-digit payment reference number.
     * 格式: EPSP + yyyyMMddHHmmss + 4位随机数 / Format: EPSP + yyyyMMddHHmmss + 4 random digits
     */
    private String generatePaymentReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "EPSP" + timestamp + random;
    }

    /**
     * 写入审计日志 / Write audit log entry.
     */
    private void writeAuditLog(String paymentRef, AuditAction action, String operator, String detail) {
        AuditLogEntity logEntry = new AuditLogEntity();
        logEntry.setReferenceId(paymentRef);
        logEntry.setAction(action);
        logEntry.setOperator(operator);
        logEntry.setDetail(detail);
        logEntry.setOperateTime(LocalDateTime.now());
        auditLogRepository.save(logEntry);
    }

    /**
     * 批量导入结果内部类 / Batch import result container.
     */
    @lombok.Data
    public static class BatchImportResult {
        private String fileName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private final List<ImportSuccess> successes = new ArrayList<>();
        private final List<ImportFailure> failures = new ArrayList<>();

        public int getTotalCount() { return successes.size() + failures.size(); }
        public int getSuccessCount() { return successes.size(); }
        public int getFailureCount() { return failures.size(); }

        public void addSuccess(int line, String ref) {
            successes.add(new ImportSuccess(line, ref));
        }

        public void addFailure(int line, String rawData, List<String> reasons) {
            failures.add(new ImportFailure(line, rawData, reasons));
        }

        @lombok.Value
        public static class ImportSuccess {
            int lineNumber;
            String paymentRef;
        }

        @lombok.Value
        public static class ImportFailure {
            int lineNumber;
            String rawData;
            List<String> reasons;
        }
    }

    /**
     * 滞留支付报告 / Stale payment report DTO.
     */
    @lombok.Value
    public static class StalePaymentReport {
        String paymentRef;
        String status;
        LocalDateTime since;
        String reason;
    }
}
