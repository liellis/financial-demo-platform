package com.eps.service.gl;

import com.eps.config.EpsConfig;
import com.eps.exception.GlPostingException;
import com.eps.exception.PaymentStatusException;
import com.eps.model.entity.*;
import com.eps.model.enums.*;
import com.eps.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 总账过账服务 - 支付结算后的复式记账分录生成与GL接口文件输出
 * General Ledger Posting Service - Double-entry voucher generation after payment settlement
 * and GL interface file output.
 * <p>
 * 对应原AS/400 COBOL程序 / Corresponds to original AS/400 COBOL programs:
 * <ul>
 *   <li>EPSL400 - 已结算支付过账(生成复式分录) / Settled payment posting (double-entry generation)</li>
 *   <li>EPSL410 - GL接口文件生成(固定长度) / GL interface file generation (fixed-length)</li>
 *   <li>EPSL900EOD - 日终批量过账 / End-of-day batch posting</li>
 * </ul>
 * <p>
 * 会计分录规则 / Accounting Entry Rules:
 * <pre>
 * 向外支付 / Outbound Payment:
 *   DR  客户付款账户 (Customer payment account)    XXX.XX
 *   DR  手续费暂挂 (Fee suspense)                     X.XX
 *     CR  银行往来账户 (Bank nostro account)            XXX.XX
 *
 * 向内收款 / Inbound Receipt:
 *   DR  银行往来账户 (Bank nostro account)            XXX.XX
 *     CR  客户收款账户 (Customer receipt account)       XXX.XX
 * </pre>
 *
 * @author EPS Migration Team
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional
public class GlPostingService {

    // ──────────────────────────── 依赖注入 Dependencies ────────────────────────────

    private final PaymentRepository paymentRepository;
    private final GlVoucherRepository glVoucherRepository;
    private final GlVoucherLineRepository glVoucherLineRepository;
    private final EpsAccountRepository epsAccountRepository;
    private final GlConfigurationRepository glConfigurationRepository;
    private final AuditLogRepository auditLogRepository;
    private final EpsConfig epsConfig;

    /** GL接口文件固定长度记录宽度 / GL interface file fixed-length record width */
    private static final int GL_RECORD_LENGTH = 256;

    /** 借贷方向常量 / Debit/Credit indicator constants */
    private static final String DR = "D";
    private static final String CR = "C";

    public GlPostingService(PaymentRepository paymentRepository,
                            GlVoucherRepository glVoucherRepository,
                            GlVoucherLineRepository glVoucherLineRepository,
                            EpsAccountRepository epsAccountRepository,
                            GlConfigurationRepository glConfigurationRepository,
                            AuditLogRepository auditLogRepository,
                            EpsConfig epsConfig) {
        this.paymentRepository = paymentRepository;
        this.glVoucherRepository = glVoucherRepository;
        this.glVoucherLineRepository = glVoucherLineRepository;
        this.epsAccountRepository = epsAccountRepository;
        this.glConfigurationRepository = glConfigurationRepository;
        this.auditLogRepository = auditLogRepository;
        this.epsConfig = epsConfig;
    }

    // ──────────────────────────── EPSL400: 已结算支付过账 ────────────────────────────

    /**
     * 选取已结算支付,生成复式记账凭证 (对应 COBOL EPSL400)
     * Select settled payments and generate double-entry accounting vouchers.
     * <p>
     * 已结算(SETTLED)状态的支付确认资金已到账,此时需:
     * <ul>
     *   <li>生成标准复式记账分录 / Generate standard double-entry journal entries</li>
     *   <li>分录需借贷平衡 / Debit and credit must balance</li>
     *   <li>每条分录关联到对应的总账科目 / Each line linked to the corresponding GL account</li>
     *   <li>凭证状态设为 PENDING (待发送至GL系统) / Voucher status = PENDING (ready for GL system)</li>
     * </ul>
     * <p>
     * 原AS/400调度: 每日 18:30 / Original AS/400 schedule: daily at 18:30
     *
     * @return 生成的凭证列表 / List of generated vouchers
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI")
    public List<GlVoucherEntity> postSettledPayments() {
        log.info("EPSL400: 开始已结算支付过账 / Starting settled payment posting");

        // 查询所有已对账但未过账的支付 / Find all reconciled but not yet posted payments
        List<PaymentEntity> settledPayments = paymentRepository
                .findByStatusAndGlPosted(PaymentStatus.RECONCILED, false);

        if (settledPayments.isEmpty()) {
            log.info("EPSL400: 无待过账支付 / No payments to post");
            return Collections.emptyList();
        }

        log.info("EPSL400: 待过账支付: {} 笔 / Payments to post: {}", settledPayments.size());

        List<GlVoucherEntity> generatedVouchers = new ArrayList<>();

        for (PaymentEntity payment : settledPayments) {
            try {
                GlVoucherEntity voucher = generateVoucher(payment);
                generatedVouchers.add(voucher);

                // 标记支付为已过账 / Mark payment as posted
                payment.setGlPosted(true);
                payment.setGlVoucherRef(voucher.getVoucherRef());
                payment.setUpdatedTime(LocalDateTime.now());
                paymentRepository.save(payment);
            } catch (Exception e) {
                log.error("EPSL400: 支付过账失败 / Payment posting failed - ref: {}",
                        payment.getPaymentRef(), e);
            }
        }

        log.info("EPSL400: 过账完成 - 生成凭证: {} 笔 / Posting completed - vouchers: {}",
                generatedVouchers.size(), generatedVouchers.size());
        return generatedVouchers;
    }

    /**
     * 为单笔支付生成复式记账凭证 / Generate double-entry voucher for a single payment.
     */
    private GlVoucherEntity generateVoucher(PaymentEntity payment) {
        // 生成凭证号 / Generate voucher reference
        String voucherRef = "GLV" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%08d", glVoucherRepository.count() + 1);

        // 构建凭证头 / Build voucher header
        GlVoucherEntity voucher = new GlVoucherEntity();
        voucher.setVoucherRef(voucherRef);
        voucher.setPaymentRef(payment.getPaymentRef());
        voucher.setCurrency(payment.getCurrency());
        voucher.setAmount(payment.getAmount());
        voucher.setVoucherDate(payment.getValueDate());
        voucher.setVoucherType(determineVoucherType(payment));
        voucher.setStatus(VoucherStatus.PENDING);
        voucher.setCreatedTime(LocalDateTime.now());
        voucher.setUpdatedTime(LocalDateTime.now());
        glVoucherRepository.save(voucher);

        // 获取科目映射配置 / Get GL account mapping configuration
        GlConfiguration glConfig = glConfigurationRepository
                .findByCurrencyAndChannel(payment.getCurrency(), payment.getChannel())
                .orElse(glConfigurationRepository.findByCurrencyAndChannel("DEFAULT", "DEFAULT")
                        .orElseThrow(() -> new GlPostingException("GL科目配置未找到 / GL configuration not found")));

        // 生成分录行 / Generate voucher lines
        List<GlVoucherLineEntity> lines = new ArrayList<>();

        // 判断支付方向 / Determine payment direction (outbound / inbound)
        boolean isOutbound = isOutboundPayment(payment);

        if (isOutbound) {
            lines.addAll(generateOutboundLines(voucher, payment, glConfig));
        } else {
            lines.addAll(generateInboundLines(voucher, payment, glConfig));
        }

        // 借贷平衡校验 / Balance check
        BigDecimal totalDr = lines.stream()
                .filter(l -> DR.equals(l.getDebitCredit()))
                .map(GlVoucherLineEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCr = lines.stream()
                .filter(l -> CR.equals(l.getDebitCredit()))
                .map(GlVoucherLineEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDr.compareTo(totalCr) != 0) {
            log.error("EPSL400: 借贷不平衡! / Debit/Credit imbalance! DR: {}, CR: {}, payment: {}",
                    totalDr, totalCr, payment.getPaymentRef());
            // 生成差额挂账分录 / Generate difference suspense entry
            BigDecimal diff = totalDr.subtract(totalCr);
            lines.add(createSuspenseLine(voucher, glConfig, diff));
        }

        // 保存分录行 / Save voucher lines
        for (GlVoucherLineEntity line : lines) {
            line.setVoucherId(voucher.getId());
            glVoucherLineRepository.save(line);
        }

        writeAuditLog(voucherRef, AuditAction.GL_POSTED, "SYSTEM_EPSL400",
                "支付过账,金额: " + payment.getAmount() + " " + payment.getCurrency()
                        + " / Payment posted, amount: " + payment.getAmount() + " " + payment.getCurrency());

        return voucher;
    }

    /**
     * 判断是否为对外支付 / Determine if this is an outbound payment.
     */
    private boolean isOutboundPayment(PaymentEntity payment) {
        // 根据支付方法判断 / Based on payment method
        return payment.getPaymentMethod() == null
                || !"INWARD".equalsIgnoreCase(payment.getPaymentMethod());
    }

    /**
     * 生成对外支付分录 / Generate outbound payment journal entries.
     * <pre>
     * DR 客户付款账户 / Customer payment account
     * DR 手续费暂挂 / Fee suspense
     *   CR 银行往来账户 / Bank nostro account
     * </pre>
     */
    private List<GlVoucherLineEntity> generateOutboundLines(GlVoucherEntity voucher,
                                                             PaymentEntity payment,
                                                             GlConfiguration glConfig) {
        List<GlVoucherLineEntity> lines = new ArrayList<>();
        int lineSeq = 0;

        BigDecimal totalAmount = payment.getAmount();
        BigDecimal feeAmount = calculateFee(payment);
        BigDecimal bankAmount = totalAmount.subtract(feeAmount);

        // Line 1: DR 客户付款账户 / Debit customer payment account
        GlVoucherLineEntity line1 = new GlVoucherLineEntity();
        line1.setVoucherId(voucher.getId());
        line1.setLineSequence(++lineSeq);
        line1.setDebitCredit(DR);
        line1.setGlAccount(glConfig.getCustomerPayableAccount());
        line1.setAmount(totalAmount);
        line1.setCurrency(payment.getCurrency());
        line1.setNarrative("支付-" + payment.getPaymentRef() + "-" + payment.getPayeeName()
                + " / Payment-" + payment.getPaymentRef() + "-" + payment.getPayeeName());
        line1.setCounterparty(payment.getPayeeName());
        line1.setCreatedTime(LocalDateTime.now());
        lines.add(line1);

        // Line 2: DR 手续费暂挂 / Debit fee suspense (if applicable)
        if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            GlVoucherLineEntity feeLine = new GlVoucherLineEntity();
            feeLine.setVoucherId(voucher.getId());
            feeLine.setLineSequence(++lineSeq);
            feeLine.setDebitCredit(DR);
            feeLine.setGlAccount(glConfig.getFeeReceivableAccount());
            feeLine.setAmount(feeAmount);
            feeLine.setCurrency(payment.getCurrency());
            feeLine.setNarrative("手续费-" + payment.getPaymentRef() + " / Fee-" + payment.getPaymentRef());
            feeLine.setCreatedTime(LocalDateTime.now());
            lines.add(feeLine);
        }

        // Line 3: CR 银行往来账户 / Credit bank nostro account
        GlVoucherLineEntity line3 = new GlVoucherLineEntity();
        line3.setVoucherId(voucher.getId());
        line3.setLineSequence(++lineSeq);
        line3.setDebitCredit(CR);
        line3.setGlAccount(glConfig.getBankNostroAccount());
        line3.setAmount(bankAmount);
        line3.setCurrency(payment.getCurrency());
        line3.setNarrative("银行支付-" + payment.getBankRef() + " / Bank payment-" + payment.getBankRef());
        line3.setCounterparty(payment.getPayeeBankCode());
        line3.setCreatedTime(LocalDateTime.now());
        lines.add(line3);

        return lines;
    }

    /**
     * 生成对内收款分录 / Generate inbound receipt journal entries.
     * <pre>
     * DR 银行往来账户 / Bank nostro account
     *   CR 客户收款账户 / Customer receipt account
     * </pre>
     */
    private List<GlVoucherLineEntity> generateInboundLines(GlVoucherEntity voucher,
                                                            PaymentEntity payment,
                                                            GlConfiguration glConfig) {
        List<GlVoucherLineEntity> lines = new ArrayList<>();
        int lineSeq = 0;

        // Line 1: DR 银行往来账户 / Debit bank nostro account
        GlVoucherLineEntity line1 = new GlVoucherLineEntity();
        line1.setVoucherId(voucher.getId());
        line1.setLineSequence(++lineSeq);
        line1.setDebitCredit(DR);
        line1.setGlAccount(glConfig.getBankNostroAccount());
        line1.setAmount(payment.getAmount());
        line1.setCurrency(payment.getCurrency());
        line1.setNarrative("收款-" + payment.getPaymentRef() + "-" + payment.getPayerName()
                + " / Receipt-" + payment.getPaymentRef() + "-" + payment.getPayerName());
        line1.setCounterparty(payment.getPayerName());
        line1.setCreatedTime(LocalDateTime.now());
        lines.add(line1);

        // Line 2: CR 客户收款账户 / Credit customer receipt account
        GlVoucherLineEntity line2 = new GlVoucherLineEntity();
        line2.setVoucherId(voucher.getId());
        line2.setLineSequence(++lineSeq);
        line2.setDebitCredit(CR);
        line2.setGlAccount(glConfig.getCustomerReceivableAccount());
        line2.setAmount(payment.getAmount());
        line2.setCurrency(payment.getCurrency());
        line2.setNarrative("收款-" + payment.getPaymentRef() + " / Receipt-" + payment.getPaymentRef());
        line2.setCounterparty(payment.getPayerName());
        line2.setCreatedTime(LocalDateTime.now());
        lines.add(line2);

        return lines;
    }

    /**
     * 创建差额暂挂分录 / Create a difference suspense line.
     */
    private GlVoucherLineEntity createSuspenseLine(GlVoucherEntity voucher,
                                                    GlConfiguration glConfig,
                                                    BigDecimal diff) {
        GlVoucherLineEntity line = new GlVoucherLineEntity();
        line.setVoucherId(voucher.getId());
        line.setLineSequence(99);
        line.setDebitCredit(diff.compareTo(BigDecimal.ZERO) > 0 ? CR : DR);
        line.setGlAccount(glConfig.getSuspenseAccount());
        line.setAmount(diff.abs());
        line.setCurrency(voucher.getCurrency());
        line.setNarrative("借贷差额暂挂-" + voucher.getVoucherRef()
                + " / Balance difference suspense-" + voucher.getVoucherRef());
        line.setCreatedTime(LocalDateTime.now());
        return line;
    }

    /**
     * 计算手续费 / Calculate transaction fee.
     */
    private BigDecimal calculateFee(PaymentEntity payment) {
        // 简化实现: 从配置读取费率 / Simplified: read fee from configuration
        BigDecimal feeRate = epsConfig.getTransactionFeeRate(payment.getChannel());
        if (feeRate == null || feeRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = payment.getAmount().multiply(feeRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal minFee = epsConfig.getMinimumTransactionFee();
        BigDecimal maxFee = epsConfig.getMaximumTransactionFee();

        if (minFee != null && fee.compareTo(minFee) < 0) fee = minFee;
        if (maxFee != null && fee.compareTo(maxFee) > 0) fee = maxFee;

        return fee;
    }

    /**
     * 确定凭证类型 / Determine voucher type.
     */
    private VoucherType determineVoucherType(PaymentEntity payment) {
        if (isOutboundPayment(payment)) {
            return VoucherType.PAYMENT_OUTBOUND;
        } else {
            return VoucherType.PAYMENT_INBOUND;
        }
    }

    // ──────────────────────────── EPSL410: GL接口文件生成 ────────────────────────────

    /**
     * 生成待发送GL凭证的固定长度接口文件 (对应 COBOL EPSL410)
     * Generate fixed-length GL interface file for pending vouchers.
     * <p>
     * 文件格式规范 / File format specification:
     * <pre>
     * 记录格式 (256字节固定长度) / Record format (256 bytes fixed-length):
     * ┌─────────────────────────────────────────────────────────────────────┐
     * │ Field         │ Position │ Length │ Description                     │
     * ├─────────────────────────────────────────────────────────────────────┤
     * │ RecordType    │ 1-1      │ 1      │ 'H'=Header, 'D'=Detail, 'T'=Trailer │
     * │ CompanyCode   │ 2-5      │ 4      │ 公司代码 / Company code          │
     * │ VoucherRef    │ 6-25     │ 20     │ 凭证参考号 / Voucher reference   │
     * │ VoucherDate   │ 26-33    │ 8      │ 凭证日期 YYYYMMDD                │
     * │ GlAccount     │ 34-53    │ 20     │ GL科目代码 / GL account code     │
     * │ DebitCredit   │ 54-54    │ 1      │ 'D'=借方, 'C'=贷方               │
     * │ Amount        │ 55-69    │ 15     │ 金额(含2位小数,右对齐补零)        │
     * │ Currency      │ 70-72    │ 3      │ 币种代码 ISO 4217                │
     * │ Narrative     │ 73-172   │ 100    │ 摘要/叙述                        │
     * │ Filler        │ 173-256  │ 84     │ 填充空格                        │
     * └─────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param outputDir 输出目录 / Output directory
     * @param batchDate 批次日期 / Batch date
     * @return 生成的GL接口文件路径 / Path to the generated GL interface file
     */
    public String generateGlInterfaceFile(String outputDir, LocalDate batchDate) {
        log.info("EPSL410: 开始生成GL接口文件 / Starting GL interface file generation - date: {}", batchDate);

        // 查询待发送的PENDING状态凭证 / Query PENDING vouchers ready for GL system
        List<GlVoucherEntity> pendingVouchers = glVoucherRepository.findByStatus(VoucherStatus.PENDING);

        if (pendingVouchers.isEmpty()) {
            log.info("EPSL410: 无待发送凭证 / No pending vouchers to send");
            return null;
        }

        // 确保输出目录存在 / Ensure output directory exists
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成文件名 / Generate file name
        String companyCode = epsConfig.getCompanyCode();
        String fileName = String.format("EPS_GL_%s_%s_%s.txt",
                companyCode,
                batchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
        String filePath = outputDir + File.separator + fileName;

        int detailCount = 0;
        BigDecimal totalDr = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // 写入文件头 / Write file header
            writer.write(formatGlHeader(companyCode, batchDate, GL_RECORD_LENGTH));
            writer.newLine();

            // 写入明细行 / Write detail lines
            for (GlVoucherEntity voucher : pendingVouchers) {
                List<GlVoucherLineEntity> lines = glVoucherLineRepository
                        .findByVoucherId(voucher.getId());

                for (GlVoucherLineEntity line : lines) {
                    String detailRecord = formatGlDetail(voucher, line, companyCode, GL_RECORD_LENGTH);
                    writer.write(detailRecord);
                    writer.newLine();
                    detailCount++;

                    // 累计借贷金额 / Accumulate debit/credit totals
                    if (DR.equals(line.getDebitCredit())) {
                        totalDr = totalDr.add(line.getAmount());
                    } else {
                        totalCr = totalCr.add(line.getAmount());
                    }
                }

                // 标记凭证为已发送 / Mark voucher as sent
                voucher.setStatus(VoucherStatus.SENT);
                voucher.setSentTime(LocalDateTime.now());
                voucher.setUpdatedTime(LocalDateTime.now());
                glVoucherRepository.save(voucher);
            }

            // 写入文件尾 / Write file trailer
            writer.write(formatGlTrailer(companyCode, batchDate, detailCount,
                    totalDr, totalCr, GL_RECORD_LENGTH));
            writer.newLine();

        } catch (IOException e) {
            log.error("EPSL410: GL接口文件写入失败 / GL interface file write failed - path: {}", filePath, e);
            throw new GlPostingException("GL接口文件写入失败 / GL interface file write failed: " + filePath, e);
        }

        writeAuditLog("GL_FILE_" + batchDate, AuditAction.GL_FILE_GENERATED, "SYSTEM_EPSL410",
                "GL接口文件生成: " + filePath + ", 明细: " + detailCount + " 行"
                        + " / GL file generated: " + filePath + ", details: " + detailCount + " lines");

        log.info("EPSL410: GL接口文件生成完成 / GL interface file generated - path: {}, records: {}",
                filePath, detailCount);
        return filePath;
    }

    /**
     * 格式化GL文件头记录 / Format GL file header record.
     */
    private String formatGlHeader(String companyCode, LocalDate batchDate, int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append(padRight("H", 1));                                           // RecordType: H
        sb.append(padRight(companyCode, 4));                                    // CompanyCode: 4
        sb.append(padRight(batchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 8)); // BatchDate: 8
        sb.append(padRight("EPS-GL-INTERFACE-V1.0", 30));                       // FileDescription: 30
        sb.append(padRight("", length - 43));                                   // Filler
        return sb.substring(0, length);
    }

    /**
     * 格式化GL明细记录 / Format GL detail record.
     */
    private String formatGlDetail(GlVoucherEntity voucher, GlVoucherLineEntity line,
                                   String companyCode, int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append(padRight("D", 1));                                            // RecordType: D
        sb.append(padRight(companyCode, 4));                                    // CompanyCode: 4
        sb.append(padRight(voucher.getVoucherRef(), 20));                       // VoucherRef: 20
        sb.append(padRight(voucher.getVoucherDate().format(
                DateTimeFormatter.ofPattern("yyyyMMdd")), 8));                  // VoucherDate: 8
        sb.append(padRight(line.getGlAccount(), 20));                           // GlAccount: 20
        sb.append(padRight(line.getDebitCredit(), 1));                          // DebitCredit: 1
        sb.append(padLeft(line.getAmount().multiply(BigDecimal.valueOf(100))
                .toBigInteger().toString(), 15, '0'));                          // Amount: 15 (cents, 0-padded)
        sb.append(padRight(line.getCurrency(), 3));                             // Currency: 3
        sb.append(padRight(truncate(line.getNarrative(), 100), 100));           // Narrative: 100
        sb.append(padRight("", length - 172));                                  // Filler
        return sb.substring(0, length);
    }

    /**
     * 格式化GL文件尾记录 / Format GL file trailer record.
     */
    private String formatGlTrailer(String companyCode, LocalDate batchDate,
                                    int detailCount, BigDecimal totalDr,
                                    BigDecimal totalCr, int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append(padRight("T", 1));                                            // RecordType: T
        sb.append(padRight(companyCode, 4));                                     // CompanyCode: 4
        sb.append(padRight(batchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 8)); // BatchDate: 8
        sb.append(padLeft(String.valueOf(detailCount), 10, '0'));               // TotalRecords: 10
        sb.append(padLeft(totalDr.multiply(BigDecimal.valueOf(100))
                .toBigInteger().toString(), 18, '0'));                          // TotalDr: 18 (cents)
        sb.append(padLeft(totalCr.multiply(BigDecimal.valueOf(100))
                .toBigInteger().toString(), 18, '0'));                          // TotalCr: 18 (cents)
        sb.append(padRight("", length - 59));                                   // Filler
        return sb.substring(0, length);
    }

    // ──────────────────────────── EPSL900EOD: 日终批量过账 ────────────────────────────

    /**
     * 日终批量过账: 过账 + GL文件生成 (对应 COBOL EPSL900EOD)
     * End-of-day batch posting: post settled payments + generate GL interface file.
     * <p>
     * 原AS/400调度: 每日 20:00 / Original AS/400 schedule: daily at 20:00
     * <p>
     * 完整的日终处理流程 / Full end-of-day process:
     * <ol>
     *   <li>执行已结算支付过账 (EPSL400) / Post settled payments</li>
     *   <li>验证所有凭证借贷平衡 / Validate all vouchers are balanced</li>
     *   <li>生成GL接口文件 (EPSL410) / Generate GL interface file</li>
     *   <li>生成日终报表 / Generate EOD report</li>
     * </ol>
     *
     * @return 日终处理结果 / EOD processing result
     */
    @Scheduled(cron = "0 0 20 * * MON-FRI")
    public EndOfDayResult endOfDayGlPosting() {
        log.info("EPSL900EOD: 开始日终批量过账 / Starting end-of-day batch posting");
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = LocalDateTime.now();

        EndOfDayResult result = new EndOfDayResult();
        result.setProcessingDate(today);
        result.setStartTime(startTime);

        // Step 1: 执行已结算支付过账 / Post settled payments
        log.info("EPSL900EOD: Step 1/3 - 支付过账 / Payment posting");
        List<GlVoucherEntity> vouchers = postSettledPayments();
        result.setVouchersGenerated(vouchers.size());

        // Step 2: 验证借贷平衡 / Validate balance
        log.info("EPSL900EOD: Step 2/3 - 借贷平衡验证 / Balance validation");
        List<String> imbalancedVouchers = validateVoucherBalance(vouchers);
        result.setImbalancedVouchers(imbalancedVouchers);
        if (!imbalancedVouchers.isEmpty()) {
            log.error("EPSL900EOD: 发现借贷不平衡凭证: {} / Imbalanced vouchers found: {}",
                    imbalancedVouchers.size(), imbalancedVouchers);
        }

        // Step 3: 生成GL接口文件 / Generate GL interface file
        log.info("EPSL900EOD: Step 3/3 - GL接口文件生成 / GL file generation");
        String outputDir = epsConfig.getGlOutputDirectory();
        String glFilePath = generateGlInterfaceFile(outputDir, today);
        result.setGlFilePath(glFilePath);

        result.setEndTime(LocalDateTime.now());
        result.setSuccess(result.getImbalancedVouchers().isEmpty() && glFilePath != null);

        log.info("EPSL900EOD: 日终批量过账完成 / EOD batch posting completed - success: {}, vouchers: {}",
                result.isSuccess(), result.getVouchersGenerated());
        return result;
    }

    /**
     * 验证所有凭证借贷平衡 / Validate that all vouchers are balanced.
     *
     * @param vouchers 凭证列表 / List of vouchers
     * @return 不平衡凭证引用号列表 / List of imbalanced voucher references
     */
    private List<String> validateVoucherBalance(List<GlVoucherEntity> vouchers) {
        List<String> imbalanced = new ArrayList<>();

        for (GlVoucherEntity voucher : vouchers) {
            List<GlVoucherLineEntity> lines = glVoucherLineRepository
                    .findByVoucherId(voucher.getId());

            BigDecimal drSum = lines.stream()
                    .filter(l -> DR.equals(l.getDebitCredit()))
                    .map(GlVoucherLineEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal crSum = lines.stream()
                    .filter(l -> CR.equals(l.getDebitCredit()))
                    .map(GlVoucherLineEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (drSum.compareTo(crSum) != 0) {
                imbalanced.add(voucher.getVoucherRef() + " (DR: " + drSum + ", CR: " + crSum + ")");
            }
        }

        return imbalanced;
    }

    // ──────────────────────────── 辅助方法 / Helper Methods ────────────────────────────

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

    /**
     * 右填充 / Pad right with spaces to specified length.
     */
    private String padRight(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }

    /**
     * 左填充 / Pad left with the given character to specified length.
     */
    private String padLeft(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length - value.length(); i++) {
            sb.append(padChar);
        }
        sb.append(value);
        return sb.toString();
    }

    /**
     * 截断至最大长度 / Truncate to max length.
     */
    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // ──────────────────────────── 内部类 / Inner Classes ────────────────────────────

    /**
     * 日终批量过账结果 / End-of-day batch posting result DTO.
     */
    @lombok.Data
    public static class EndOfDayResult {
        private LocalDate processingDate;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int vouchersGenerated;
        private List<String> imbalancedVouchers = new ArrayList<>();
        private String glFilePath;
        private boolean success;
    }
}
