package com.eps.service.refund;

import com.eps.config.EpsConfig;
import com.eps.exception.*;
import com.eps.model.entity.*;
import com.eps.model.enums.*;
import com.eps.model.request.RefundRequest;
import com.eps.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 退款管理服务 - 支付退款的全生命周期管理
 * Refund Management Service - Full lifecycle management of payment refunds.
 * <p>
 * 对应原AS/400 COBOL程序 / Corresponds to original AS/400 COBOL programs:
 * <ul>
 *   <li>EPSREF100 - 退款创建(含校验) / Refund creation with validation</li>
 *   <li>EPSREF200 - 退款审批(双人复核) / Refund approval (maker-checker)</li>
 *   <li>EPSREF400 - 退款会计过账(冲销分录) / Refund accounting posting (reversal entries)</li>
 * </ul>
 * <p>
 * 退款业务规则 / Refund business rules:
 * <ul>
 *   <li>仅已结算(RECONCILED/SETTLED)的支付可退款 / Only RECONCILED/SETTLED payments can be refunded</li>
 *   <li>退款金额不能超过原支付金额 / Refund amount cannot exceed original payment amount</li>
 *   <li>支持部分退款和全额退款 / Supports partial and full refunds</li>
 *   <li>退款需经过与支付相同的审批流程 / Refunds go through same approval workflow as payments</li>
 *   <li>退款审批通过后生成冲销会计分录 / Approved refunds generate reversal accounting entries</li>
 *   <li>退款期限: 原支付日+90天 / Refund window: original payment date + 90 days</li>
 * </ul>
 *
 * @author EPS Migration Team
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional
public class RefundService {

    // ──────────────────────────── 依赖注入 Dependencies ────────────────────────────

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final GlVoucherRepository glVoucherRepository;
    private final GlVoucherLineRepository glVoucherLineRepository;
    private final GlConfigurationRepository glConfigurationRepository;
    private final AuditLogRepository auditLogRepository;
    private final EpsConfig epsConfig;

    /** 退款期限(天) / Refund window in days */
    private static final int MAX_REFUND_WINDOW_DAYS = 90;

    /** 借贷方向常量 / Debit/Credit indicator constants */
    private static final String DR = "D";
    private static final String CR = "C";

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         GlVoucherRepository glVoucherRepository,
                         GlVoucherLineRepository glVoucherLineRepository,
                         GlConfigurationRepository glConfigurationRepository,
                         AuditLogRepository auditLogRepository,
                         EpsConfig epsConfig) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.glVoucherRepository = glVoucherRepository;
        this.glVoucherLineRepository = glVoucherLineRepository;
        this.glConfigurationRepository = glConfigurationRepository;
        this.auditLogRepository = auditLogRepository;
        this.epsConfig = epsConfig;
    }

    // ──────────────────────────── EPSREF100: 退款创建 ────────────────────────────

    /**
     * 创建退款指令 (对应 COBOL EPSREF100)
     * Create a refund instruction for an existing payment.
     * <p>
     * 校验规则 / Validation rules:
     * <ol>
     *   <li>原支付必须存在 / Original payment must exist</li>
     *   <li>原支付状态必须为已结算(RECONCILED/SETTLED) / Original payment must be RECONCILED or SETTLED</li>
     *   <li>退款金额不能超过原支付金额 / Refund amount cannot exceed original amount</li>
     *   <li>退款期限: 原支付日 + 90天 / Refund window: original value date + 90 days</li>
     *   <li>同一笔支付不能重复退款 / Cannot refund the same payment multiple times</li>
     *   <li>币种必须与原支付一致 / Currency must match original payment</li>
     * </ol>
     *
     * @param originalPaymentRef 原支付引用号 / Original payment reference number
     * @param request            退款请求 / Refund request DTO
     * @return 生成的退款引用号 / Generated refund reference number
     * @throws PaymentNotFoundException  原支付不存在 / Original payment not found
     * @throws RefundValidationException 退款校验失败 / Refund validation failed
     */
    public String createRefund(String originalPaymentRef, RefundRequest request) {
        log.info("EPSREF100: 开始创建退款 / Starting refund creation - original: {}, amount: {}",
                originalPaymentRef, request.getAmount());

        // Step 1: 查询原支付 / Find original payment
        PaymentEntity originalPayment = paymentRepository.findByPaymentRef(originalPaymentRef)
                .orElseThrow(() -> new PaymentNotFoundException("原支付未找到 / Original payment not found: " + originalPaymentRef));

        // Step 2: 多维度校验 / Multi-dimension validation
        List<String> violations = validateRefundRequest(originalPayment, request);
        if (!violations.isEmpty()) {
            log.warn("EPSREF100: 退款校验失败 / Refund validation failed - violations: {}", violations);
            throw new RefundValidationException("退款校验未通过 / Refund validation failed", violations);
        }

        // Step 3: 计算实际退款金额 / Calculate actual refund amount
        BigDecimal refundAmount = request.getAmount();
        boolean isFullRefund = refundAmount.compareTo(originalPayment.getAmount()) == 0;

        // Step 4: 计算已退款总额 / Calculate already refunded total
        BigDecimal alreadyRefunded = calculateRefundedTotal(originalPaymentRef);
        BigDecimal remainingRefundable = originalPayment.getAmount().subtract(alreadyRefunded);
        if (refundAmount.compareTo(remainingRefundable) > 0) {
            throw new RefundValidationException("退款金额超过可退余额 / Refund amount exceeds refundable balance",
                    Collections.singletonList(String.format("退款: %s, 已退: %s, 可退: %s / Refund: %s, Refunded: %s, Refundable: %s",
                            refundAmount, alreadyRefunded, remainingRefundable)));
        }

        // Step 5: 生成退款引用号 / Generate refund reference number
        String refundRef = generateRefundReference();

        // Step 6: 构建退款实体 / Build refund entity
        RefundEntity refund = new RefundEntity();
        refund.setRefundRef(refundRef);
        refund.setOriginalPaymentRef(originalPaymentRef);
        refund.setPayerAccount(originalPayment.getPayeeAccount()); // 退款方向: 原收款方退还给原付款方
        refund.setPayerName(originalPayment.getPayeeName());       // Refund direction: original payee → original payer
        refund.setPayeeAccount(originalPayment.getPayerAccount());
        refund.setPayeeName(originalPayment.getPayerName());
        refund.setCurrency(originalPayment.getCurrency());
        refund.setAmount(refundAmount.setScale(2, RoundingMode.HALF_UP));
        refund.setOriginalAmount(originalPayment.getAmount());
        refund.setChannel(originalPayment.getChannel());
        refund.setRefundReason(request.getReason());
        refund.setRefundType(isFullRefund ? RefundType.FULL : RefundType.PARTIAL);
        refund.setStatus(RefundStatus.PENDING_APPROVAL);
        refund.setCreator(request.getCreator());
        refund.setCreatedTime(LocalDateTime.now());
        refund.setUpdatedTime(LocalDateTime.now());
        refundRepository.save(refund);

        // Step 7: 审计日志 / Audit log
        writeAuditLog(refundRef, AuditAction.REFUND_CREATED, request.getCreator(),
                String.format("退款创建 / Refund created - original: %s, amount: %s %s, type: %s, reason: %s",
                        originalPaymentRef, refundAmount, originalPayment.getCurrency(),
                        isFullRefund ? "全额/Full" : "部分/Partial", request.getReason()));

        log.info("EPSREF100: 退款创建成功 / Refund created successfully - ref: {}, type: {}",
                refundRef, isFullRefund ? "FULL" : "PARTIAL");
        return refundRef;
    }

    /**
     * 退款请求多维度校验 / Multi-dimension validation for refund request.
     */
    private List<String> validateRefundRequest(PaymentEntity originalPayment, RefundRequest request) {
        List<String> violations = new ArrayList<>();

        // 1. 原支付状态检查 / Original payment status check
        if (originalPayment.getStatus() != PaymentStatus.RECONCILED
                && originalPayment.getStatus() != PaymentStatus.SETTLED) {
            violations.add(String.format("原支付状态[%s]不允许退款,需为已结算状态 / "
                            + "Original payment status [%s] not eligible for refund, must be settled",
                    originalPayment.getStatus(), originalPayment.getStatus()));
        }

        // 2. 退款金额校验 / Refund amount validation
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            violations.add("退款金额必须大于0 / Refund amount must be greater than zero");
        } else if (request.getAmount().compareTo(originalPayment.getAmount()) > 0) {
            violations.add(String.format("退款金额[%s]超过原支付金额[%s] / "
                            + "Refund amount [%s] exceeds original payment amount [%s]",
                    request.getAmount(), originalPayment.getAmount()));
        }

        // 3. 退款期限检查 / Refund window check (90 days from value date)
        if (originalPayment.getValueDate() != null) {
            long daysSincePayment = ChronoUnit.DAYS.between(originalPayment.getValueDate(), LocalDate.now());
            if (daysSincePayment > MAX_REFUND_WINDOW_DAYS) {
                violations.add(String.format("超过退款期限(%d天),已过%d天 / "
                                + "Exceeded refund window (%d days), %d days passed",
                        MAX_REFUND_WINDOW_DAYS, daysSincePayment));
            }
        }

        // 4. 币种一致性校验 / Currency consistency check
        if (request.getCurrency() != null && !request.getCurrency().equalsIgnoreCase(originalPayment.getCurrency())) {
            violations.add(String.format("退款币种[%s]与原支付币种[%s]不一致 / "
                            + "Refund currency [%s] does not match original payment currency [%s]",
                    request.getCurrency(), originalPayment.getCurrency()));
        }

        // 5. 退款原因必填 / Refund reason is required
        if (request.getReason() == null || request.getReason().isBlank()) {
            violations.add("退款原因必填 / Refund reason is required");
        }

        // 6. 重复退款检查 / Duplicate refund check
        List<RefundEntity> existingRefunds = refundRepository
                .findByOriginalPaymentRefAndStatusNot(originalPayment.getPaymentRef(), RefundStatus.REJECTED);
        if (!existingRefunds.isEmpty()) {
            BigDecimal approvedTotal = existingRefunds.stream()
                    .filter(r -> r.getStatus() == RefundStatus.APPROVED || r.getStatus() == RefundStatus.PROCESSING)
                    .map(RefundEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (approvedTotal.add(request.getAmount()).compareTo(originalPayment.getAmount()) > 0) {
                violations.add(String.format("累计退款金额将超过原支付金额 / "
                                + "Cumulative refund amount would exceed original payment amount. Pending: %s",
                        existingRefunds.size()));
            }
        }

        return violations;
    }

    /**
     * 计算已退款总额 / Calculate total already refunded amount for an original payment.
     */
    private BigDecimal calculateRefundedTotal(String originalPaymentRef) {
        List<RefundEntity> approvedRefunds = refundRepository
                .findByOriginalPaymentRefAndStatusIn(originalPaymentRef,
                        Arrays.asList(RefundStatus.APPROVED, RefundStatus.PROCESSING, RefundStatus.COMPLETED));
        return approvedRefunds.stream()
                .map(RefundEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ──────────────────────────── EPSREF200: 退款审批 ────────────────────────────

    /**
     * 审批通过退款指令 (对应 COBOL EPSREF200 APPROVE)
     * Approve a refund instruction (maker-checker control).
     * <p>
     * 审批约束 / Approval constraints:
     * <ul>
     *   <li>审批人不能是创建人 / Approver must not be the creator</li>
     *   <li>仅 PENDING_APPROVAL 状态可审批 / Only PENDING_APPROVAL status is approvable</li>
     *   <li>审批后状态变为 APPROVED / Status becomes APPROVED after approval</li>
     * </ul>
     *
     * @param refundRef 退款引用号 / Refund reference number
     * @param approver  审批人ID / Approver ID
     * @throws RefundNotFoundException 退款不存在 / Refund not found
     * @throws ApprovalException      审批约束违反 / Approval constraint violated
     */
    public void approveRefund(String refundRef, String approver) {
        log.info("EPSREF200: 审批退款 / Approving refund - ref: {}, approver: {}", refundRef, approver);

        RefundEntity refund = refundRepository.findByRefundRef(refundRef)
                .orElseThrow(() -> new RefundNotFoundException("退款未找到 / Refund not found: " + refundRef));

        // 状态校验 / Status validation
        if (refund.getStatus() != RefundStatus.PENDING_APPROVAL) {
            throw new ApprovalException("退款状态不允许审批,当前状态: " + refund.getStatus()
                    + " / Refund status not approvable, current: " + refund.getStatus());
        }

        // 双人复核校验 / Maker-checker validation
        if (approver.equals(refund.getCreator())) {
            throw new ApprovalException("审批人不能与创建人相同 / Approver cannot be the same as creator");
        }

        // 更新状态 / Update status
        refund.setStatus(RefundStatus.APPROVED);
        refund.setApprover(approver);
        refund.setApprovedTime(LocalDateTime.now());
        refund.setUpdatedTime(LocalDateTime.now());
        refundRepository.save(refund);

        // 审计日志 / Audit log
        writeAuditLog(refundRef, AuditAction.REFUND_APPROVED, approver,
                "退款审批通过 / Refund approved - amount: " + refund.getAmount() + " " + refund.getCurrency());

        log.info("EPSREF200: 退款审批成功 / Refund approved successfully - ref: {}", refundRef);
    }

    /**
     * 驳回退款指令 (对应 COBOL EPSREF200 REJECT)
     * Reject a refund instruction.
     *
     * @param refundRef 退款引用号 / Refund reference number
     * @param approver  审批人ID / Approver ID
     * @param reason    驳回原因 / Rejection reason
     */
    public void rejectRefund(String refundRef, String approver, String reason) {
        log.info("EPSREF200: 驳回退款 / Rejecting refund - ref: {}, approver: {}, reason: {}", refundRef, approver, reason);

        RefundEntity refund = refundRepository.findByRefundRef(refundRef)
                .orElseThrow(() -> new RefundNotFoundException("退款未找到 / Refund not found: " + refundRef));

        if (refund.getStatus() != RefundStatus.PENDING_APPROVAL) {
            throw new ApprovalException("退款状态不允许驳回,当前状态: " + refund.getStatus()
                    + " / Refund status not rejectable, current: " + refund.getStatus());
        }

        refund.setStatus(RefundStatus.REJECTED);
        refund.setApprover(approver);
        refund.setRejectionReason(reason);
        refund.setApprovedTime(LocalDateTime.now());
        refund.setUpdatedTime(LocalDateTime.now());
        refundRepository.save(refund);

        writeAuditLog(refundRef, AuditAction.REFUND_REJECTED, approver,
                "退款驳回 / Refund rejected - reason: " + reason);

        log.info("EPSREF200: 退款驳回成功 / Refund rejected successfully - ref: {}", refundRef);
    }

    // ──────────────────────────── EPSREF400: 退款会计过账 ────────────────────────────

    /**
     * 为已审批退款生成冲销会计分录 (对应 COBOL EPSREF400)
     * Generate reversal accounting entries for approved refunds.
     * <p>
     * 冲销分录规则 / Reversal entry rules:
     * <pre>
     * 退款冲销分录 (与原支付分录方向相反) / Refund reversal (opposite of original payment):
     *   DR  银行往来账户 (Bank nostro account)            XXX.XX
     *   DR  手续费冲回 (Fee reversal)                       X.XX
     *     CR  客户付款账户 (Customer payment account)        XXX.XX
     *
     * 与原支付分录对比 / Compared with original payment entry:
     *   原支付: DR 客户付款账户 / CR 银行往来账户
     *   退款:   DR 银行往来账户 / CR 客户付款账户 (冲销/Reversal)
     * </pre>
     *
     * @param refundRef 退款引用号 / Refund reference number
     * @return 生成的冲销凭证 / Generated reversal voucher
     */
    public GlVoucherEntity postRefundAccounting(String refundRef) {
        log.info("EPSREF400: 开始退款会计过账 / Starting refund accounting posting - ref: {}", refundRef);

        RefundEntity refund = refundRepository.findByRefundRef(refundRef)
                .orElseThrow(() -> new RefundNotFoundException("退款未找到 / Refund not found: " + refundRef));

        if (refund.getStatus() != RefundStatus.APPROVED) {
            throw new RefundStatusException("退款状态不是已审批,无法过账 / Refund not in APPROVED status: " + refund.getStatus());
        }

        // 查找原支付 / Find original payment
        PaymentEntity originalPayment = paymentRepository.findByPaymentRef(refund.getOriginalPaymentRef())
                .orElseThrow(() -> new PaymentNotFoundException("原支付未找到: " + refund.getOriginalPaymentRef()));

        // 获取GL科目配置 / Get GL account mapping
        GlConfiguration glConfig = glConfigurationRepository
                .findByCurrencyAndChannel(refund.getCurrency(), refund.getChannel())
                .orElse(glConfigurationRepository.findByCurrencyAndChannel("DEFAULT", "DEFAULT")
                        .orElseThrow(() -> new GlPostingException("GL科目配置未找到 / GL configuration not found")));

        // 生成冲销凭证 / Generate reversal voucher
        String voucherRef = "REV" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%08d", glVoucherRepository.count() + 1);

        GlVoucherEntity voucher = new GlVoucherEntity();
        voucher.setVoucherRef(voucherRef);
        voucher.setPaymentRef(refundRef);  // 引用退款号 / Linked to refund
        voucher.setCurrency(refund.getCurrency());
        voucher.setAmount(refund.getAmount());
        voucher.setVoucherDate(LocalDate.now());
        voucher.setVoucherType(VoucherType.REFUND_REVERSAL);
        voucher.setStatus(VoucherStatus.PENDING);
        voucher.setCreatedTime(LocalDateTime.now());
        voucher.setUpdatedTime(LocalDateTime.now());
        glVoucherRepository.save(voucher);

        // 生成冲销分录行 / Generate reversal entry lines
        // 与原支付分录方向完全相反 / Fully reversed direction from original payment
        List<GlVoucherLineEntity> lines = new ArrayList<>();
        int lineSeq = 0;

        // Line 1: DR 银行往来账户 / Debit bank nostro account (was CR in original)
        GlVoucherLineEntity line1 = new GlVoucherLineEntity();
        line1.setVoucherId(voucher.getId());
        line1.setLineSequence(++lineSeq);
        line1.setDebitCredit(DR);
        line1.setGlAccount(glConfig.getBankNostroAccount());
        line1.setAmount(refund.getAmount());
        line1.setCurrency(refund.getCurrency());
        line1.setNarrative("退款冲销-" + refundRef + " (原支付:" + originalPayment.getPaymentRef() + ")"
                + " / Refund reversal-" + refundRef + " (Original:" + originalPayment.getPaymentRef() + ")");
        line1.setCounterparty(refund.getPayerName());
        line1.setCreatedTime(LocalDateTime.now());
        lines.add(line1);

        // Line 2: CR 客户付款账户 / Credit customer payment account (was DR in original)
        GlVoucherLineEntity line2 = new GlVoucherLineEntity();
        line2.setVoucherId(voucher.getId());
        line2.setLineSequence(++lineSeq);
        line2.setDebitCredit(CR);
        line2.setGlAccount(glConfig.getCustomerPayableAccount());
        line2.setAmount(refund.getAmount());
        line2.setCurrency(refund.getCurrency());
        line2.setNarrative("退款冲销-" + refundRef + " / Refund reversal-" + refundRef);
        line2.setCounterparty(refund.getPayeeName());
        line2.setCreatedTime(LocalDateTime.now());
        lines.add(line2);

        // 保存分录行 / Save lines
        for (GlVoucherLineEntity line : lines) {
            glVoucherLineRepository.save(line);
        }

        // 更新退款状态 / Update refund status
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setGlVoucherRef(voucherRef);
        refund.setUpdatedTime(LocalDateTime.now());
        refundRepository.save(refund);

        // 审计日志 / Audit log
        writeAuditLog(refundRef, AuditAction.REFUND_POSTED, "SYSTEM_EPSREF400",
                String.format("退款会计过账完成/Refund accounting posted - voucher: %s, amount: %s %s",
                        voucherRef, refund.getAmount(), refund.getCurrency()));

        log.info("EPSREF400: 退款会计过账完成 / Refund accounting posted - voucher: {}, amount: {} {}",
                voucherRef, refund.getAmount(), refund.getCurrency());
        return voucher;
    }

    // ──────────────────────────── 查询方法 / Query Methods ────────────────────────────

    /**
     * 查询退款状态 / Query refund status.
     *
     * @param refundRef 退款引用号 / Refund reference number
     * @return 退款实体 / Refund entity
     * @throws RefundNotFoundException 退款不存在 / Refund not found
     */
    @Transactional(readOnly = true)
    public RefundEntity getRefundStatus(String refundRef) {
        return refundRepository.findByRefundRef(refundRef)
                .orElseThrow(() -> new RefundNotFoundException("退款未找到 / Refund not found: " + refundRef));
    }

    /**
     * 查询某笔原支付的所有退款 / Query all refunds for an original payment.
     *
     * @param originalPaymentRef 原支付引用号 / Original payment reference
     * @return 退款列表 / List of refunds
     */
    @Transactional(readOnly = true)
    public List<RefundEntity> getRefundsByOriginalPayment(String originalPaymentRef) {
        return refundRepository.findByOriginalPaymentRef(originalPaymentRef);
    }

    // ──────────────────────────── 辅助方法 / Helper Methods ────────────────────────────

    /**
     * 生成退款引用号 / Generate refund reference number.
     * 格式: EPSR + yyyyMMddHHmmss + 4位随机数 / Format: EPSR + yyyyMMddHHmmss + 4 random digits
     */
    private String generateRefundReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "EPSR" + timestamp + random;
    }

    /**
     * 写入审计日志 / Write audit log entry.
     */
    private void writeAuditLog(String referenceId, AuditAction action, String operator, String detail) {
        AuditLogEntity logEntry = new AuditLogEntity();
        logEntry.setReferenceId(referenceId);
        logEntry.setAction(action);
        logEntry.setOperator(operator);
        logEntry.setDetail(detail);
        logEntry.setOperateTime(LocalDateTime.now());
        auditLogRepository.save(logEntry);
    }
}
