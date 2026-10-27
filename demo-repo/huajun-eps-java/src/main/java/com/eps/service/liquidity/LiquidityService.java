package com.eps.service.liquidity;

import com.eps.config.EpsConfig;
import com.eps.exception.AccountNotFoundException;
import com.eps.exception.LiquidityException;
import com.eps.model.entity.*;
import com.eps.model.enums.*;
import com.eps.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 流动性管理服务 - 账户余额监控、资金归集与内部计息
 * Liquidity Management Service - Account balance monitoring, fund sweeping, and internal interest calculation.
 * <p>
 * 对应原AS/400 COBOL程序 / Corresponds to original AS/400 COBOL programs:
 * <ul>
 *   <li>EPSL100 - CAMT.052日间头寸更新 / Intraday position update from CAMT.052</li>
 *   <li>EPSL200 - 零余额/目标余额归集 / Zero-balance / target-balance sweeping</li>
 *   <li>EPSL210 - 跨币种归集(含FX) / Cross-currency sweeping with FX</li>
 *   <li>EPSL300 - 内部计息计算 / Internal interest calculation</li>
 * </ul>
 *
 * @author EPS Migration Team
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional
public class LiquidityService {

    // ──────────────────────────── 依赖注入 Dependencies ────────────────────────────

    private final EpsAccountRepository epsAccountRepository;
    private final SweepInstructionRepository sweepInstructionRepository;
    private final SweepExecutionRepository sweepExecutionRepository;
    private final InterestCalculationRepository interestCalculationRepository;
    private final FxRateRepository fxRateRepository;
    private final AuditLogRepository auditLogRepository;
    private final EpsConfig epsConfig;

    /** 一年天数基准 / Day count basis for interest calculation */
    private static final int DAYS_PER_YEAR = 360;

    /** 计息精度 / Interest calculation precision */
    private static final int INTEREST_SCALE = 4;

    public LiquidityService(EpsAccountRepository epsAccountRepository,
                             SweepInstructionRepository sweepInstructionRepository,
                             SweepExecutionRepository sweepExecutionRepository,
                             InterestCalculationRepository interestCalculationRepository,
                             FxRateRepository fxRateRepository,
                             AuditLogRepository auditLogRepository,
                             EpsConfig epsConfig) {
        this.epsAccountRepository = epsAccountRepository;
        this.sweepInstructionRepository = sweepInstructionRepository;
        this.sweepExecutionRepository = sweepExecutionRepository;
        this.interestCalculationRepository = interestCalculationRepository;
        this.fxRateRepository = fxRateRepository;
        this.auditLogRepository = auditLogRepository;
        this.epsConfig = epsConfig;
    }

    // ──────────────────────────── EPSL100: 日间头寸更新 ────────────────────────────

    /**
     * 根据CAMT.052/MT942更新EPS账户余额和头寸 (对应 COBOL EPSL100)
     * Update EPS account balances and positions from CAMT.052 / MT942 intraday reports.
     * <p>
     * CAMT.052 提供日内账户报告,包含:
     * <ul>
     *   <li>开户余额 (Opening booked balance)</li>
     *   <li>当前余额 (Current balance)</li>
     *   <li>可用余额 (Available balance)</li>
     *   <li>日内交易条目 (Intraday transaction entries)</li>
     * </ul>
     * <p>
     * 原AS/400调度: 每小时运行一次,工作日 09:00-18:00 / Original AS/400 schedule: every hour, 09:00-18:00 business days
     * <p>
     * 更新逻辑 / Update logic:
     * <ol>
     *   <li>解析CAMT.052报文 / Parse CAMT.052 message</li>
     *   <li>更新 EPS_ACCOUNT 表: 账面余额, 可用余额, 冻结余额 / Update balances in EPS_ACCOUNT</li>
     *   <li>记录头寸快照 / Record position snapshot</li>
     *   <li>检查是否触发归集条件 / Check if sweep trigger conditions met</li>
     * </ol>
     *
     * @param camt052Content CAMT.052 XML报文内容 / CAMT.052 XML message content
     * @return 更新的账户列表 / List of updated accounts
     */
    @Scheduled(cron = "0 0 9-18 * * MON-FRI")
    public List<EpsAccountEntity> updatePosition(String camt052Content) {
        log.info("EPSL100: 开始头寸更新 / Starting position update");

        // 解析CAMT.052报文 / Parse CAMT.052 message
        List<Camt052Entry> entries = parseCamt052(camt052Content);
        List<EpsAccountEntity> updatedAccounts = new ArrayList<>();

        for (Camt052Entry entry : entries) {
            EpsAccountEntity account = epsAccountRepository.findByAccountNumber(entry.getAccountNumber())
                    .orElse(null);

            if (account == null) {
                log.warn("EPSL100: 账户未在EPS系统中维护 / Account not maintained in EPS: {}",
                        entry.getAccountNumber());
                continue;
            }

            // 更新余额 / Update balances
            BigDecimal previousBalance = account.getBookedBalance();
            account.setBookedBalance(entry.getBookedBalance().setScale(2, RoundingMode.HALF_UP));
            account.setAvailableBalance(entry.getAvailableBalance().setScale(2, RoundingMode.HALF_UP));
            account.setIntradayCreditAmount(entry.getIntradayCreditAmount());
            account.setIntradayDebitAmount(entry.getIntradayDebitAmount());
            account.setLastPositionUpdate(LocalDateTime.now());
            account.setUpdatedTime(LocalDateTime.now());
            epsAccountRepository.save(account);

            updatedAccounts.add(account);

            // 记录头寸快照 / Record position snapshot
            recordPositionSnapshot(account, previousBalance, entry);

            log.info("EPSL100: 账户余额更新 / Account balance updated - account: {}, balance: {} -> {} {}",
                    entry.getAccountNumber(), previousBalance, account.getBookedBalance(), account.getCurrency());
        }

        // 检查归集触发条件 / Check sweep triggers
        checkSweepTriggers(updatedAccounts);

        log.info("EPSL100: 头寸更新完成 / Position update completed - accounts: {}", updatedAccounts.size());
        return updatedAccounts;
    }

    /**
     * 简化版CAMT.052解析器 / Simplified CAMT.052 parser.
     * 实际项目应使用ISO 20022标准解析器 / Production should use ISO 20022 standard parser.
     */
    private List<Camt052Entry> parseCamt052(String camt052Content) {
        List<Camt052Entry> entries = new ArrayList<>();

        if (camt052Content == null || camt052Content.isBlank()) {
            log.warn("EPSL100: CAMT.052内容为空 / CAMT.052 content is empty");
            return entries;
        }

        // 简易XML提取 / Simple XML extraction
        String[] acctBlocks = camt052Content.split("<Rpt>");
        for (int i = 1; i < acctBlocks.length; i++) {
            Camt052Entry entry = new Camt052Entry();
            entry.setAccountNumber(extractXmlValue(acctBlocks[i], "Id", "IBAN", ""));
            String balText = extractXmlValue(acctBlocks[i], "Bal", "Amt", "0");
            entry.setBookedBalance(parseAmount(balText));
            entry.setAvailableBalance(parseAmount(extractXmlValue(acctBlocks[i], "Avlbl", "Amt", "0")));
            entry.setIntradayCreditAmount(parseAmount(extractXmlValue(acctBlocks[i], "CdtDbtInd", "CRDT", "0")));
            entry.setIntradayDebitAmount(parseAmount(extractXmlValue(acctBlocks[i], "CdtDbtInd", "DBIT", "0")));
            entries.add(entry);
        }

        return entries;
    }

    private String extractXmlValue(String xml, String tag, String attrTag, String defaultValue) {
        int start = xml.indexOf("<" + tag + ">");
        int end = xml.indexOf("</" + tag + ">");
        if (attrTag != null && !attrTag.isEmpty()) {
            // Try with attribute
            String pattern = "<" + tag + " " + attrTag + "=\"";
            start = xml.indexOf(pattern);
            if (start >= 0) {
                start = xml.indexOf(">", start) + 1;
                end = xml.indexOf("</" + tag + ">", start);
            }
        }
        if (start >= 0 && end > start) {
            return xml.substring(start, end).trim();
        }
        return defaultValue;
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(text.replaceAll("[^0-9.]", ""));
    }

    /**
     * 记录头寸快照 / Record position snapshot for audit trail.
     */
    private void recordPositionSnapshot(EpsAccountEntity account, BigDecimal previousBalance, Camt052Entry entry) {
        AuditLogEntity logEntry = new AuditLogEntity();
        logEntry.setReferenceId(account.getAccountNumber());
        logEntry.setAction(AuditAction.POSITION_UPDATED);
        logEntry.setOperator("SYSTEM_EPSL100");
        logEntry.setDetail(String.format("头寸更新: %s -> %s %s / Position updated: %s -> %s %s",
                previousBalance, account.getBookedBalance(), account.getCurrency(),
                previousBalance, account.getBookedBalance(), account.getCurrency()));
        logEntry.setOperateTime(LocalDateTime.now());
        auditLogRepository.save(logEntry);
    }

    /**
     * 检查归集触发条件 / Check if sweep triggers are met for any accounts.
     */
    private void checkSweepTriggers(List<EpsAccountEntity> accounts) {
        for (EpsAccountEntity account : accounts) {
            List<SweepInstructionEntity> instructions = sweepInstructionRepository
                    .findBySourceAccountAndActive(account.getAccountNumber(), true);

            for (SweepInstructionEntity instruction : instructions) {
                if (shouldTriggerSweep(account, instruction)) {
                    log.info("EPSL100: 触发归集条件 / Sweep trigger met - account: {}, instruction: {}",
                            account.getAccountNumber(), instruction.getId());
                    // 触发异步归集 或 标记为待处理 / Trigger async sweep or mark as pending
                }
            }
        }
    }

    private boolean shouldTriggerSweep(EpsAccountEntity account, SweepInstructionEntity instruction) {
        switch (instruction.getSweepType()) {
            case ZERO_BALANCE:
                return account.getBookedBalance().abs().compareTo(epsConfig.getSweepMinimumAmount()) > 0;
            case TARGET_BALANCE:
                return account.getBookedBalance().compareTo(instruction.getTargetBalance()) > 0
                        || instruction.getTargetBalance().compareTo(account.getBookedBalance()) > 0;
            case THRESHOLD:
                return account.getBookedBalance().compareTo(instruction.getThresholdAmount()) > 0;
            default:
                return false;
        }
    }

    // ──────────────────────────── EPSL200: 余额归集 ────────────────────────────

    /**
     * 执行零余额归集或目标余额归集 (对应 COBOL EPSL200)
     * Execute zero-balance or target-balance cash sweep.
     * <p>
     * 归集类型 / Sweep types:
     * <ul>
     *   <li>ZERO_BALANCE - 将源账户余额归零,资金转至目标账户 / Zero out source account, move to target</li>
     *   <li>TARGET_BALANCE - 将源账户余额调整至目标余额 / Adjust source account to target balance</li>
     *   <li>THRESHOLD - 余额超过阈值时归集超出部分 / Sweep excess when balance exceeds threshold</li>
     * </ul>
     * <p>
     * 原AS/400调度: 每日 16:30 执行 / Original AS/400 schedule: daily at 16:30
     *
     * @param instructionId 归集指令ID / Sweep instruction ID
     * @return 归集执行结果 / Sweep execution result
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI")
    public SweepExecutionResult executeSweep(Long instructionId) {
        log.info("EPSL200: 开始执行余额归集 / Starting sweep execution - instructionId: {}", instructionId);

        SweepInstructionEntity instruction = sweepInstructionRepository.findById(instructionId)
                .orElseThrow(() -> new LiquidityException("归集指令未找到 / Sweep instruction not found: " + instructionId));

        if (!instruction.isActive()) {
            throw new LiquidityException("归集指令未激活 / Sweep instruction is not active: " + instructionId);
        }

        // 获取源账户和目标账户 / Get source and target accounts
        EpsAccountEntity source = epsAccountRepository.findByAccountNumber(instruction.getSourceAccount())
                .orElseThrow(() -> new AccountNotFoundException("源账户未找到 / Source account not found: " + instruction.getSourceAccount()));
        EpsAccountEntity target = epsAccountRepository.findByAccountNumber(instruction.getTargetAccount())
                .orElseThrow(() -> new AccountNotFoundException("目标账户未找到 / Target account not found: " + instruction.getTargetAccount()));

        // 计算归集金额 / Calculate sweep amount
        BigDecimal sweepAmount = calculateSweepAmount(source, instruction);

        if (sweepAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("EPSL200: 无归集需求 / No sweep needed - source: {}, balance: {}",
                    source.getAccountNumber(), source.getBookedBalance());
            return SweepExecutionResult.noSweepNeeded(instructionId, source.getAccountNumber());
        }

        // 校验归集最小金额 / Validate minimum sweep amount
        if (sweepAmount.compareTo(epsConfig.getSweepMinimumAmount()) < 0) {
            log.info("EPSL200: 归集金额低于最小阈值 / Sweep amount below minimum threshold - amount: {}", sweepAmount);
            return SweepExecutionResult.belowThreshold(instructionId, sweepAmount, epsConfig.getSweepMinimumAmount());
        }

        // 执行转账归集 / Execute transfer sweep
        source.setBookedBalance(source.getBookedBalance().subtract(sweepAmount));
        source.setUpdatedTime(LocalDateTime.now());
        epsAccountRepository.save(source);

        target.setBookedBalance(target.getBookedBalance().add(sweepAmount));
        target.setUpdatedTime(LocalDateTime.now());
        epsAccountRepository.save(target);

        // 记录归集执行 / Record sweep execution
        SweepExecutionEntity execution = new SweepExecutionEntity();
        execution.setInstructionId(instructionId);
        execution.setSourceAccount(source.getAccountNumber());
        execution.setTargetAccount(target.getAccountNumber());
        execution.setAmount(sweepAmount);
        execution.setCurrency(source.getCurrency());
        execution.setSweepType(instruction.getSweepType());
        execution.setExecutionTime(LocalDateTime.now());
        execution.setStatus(SweepStatus.COMPLETED);
        sweepExecutionRepository.save(execution);

        log.info("EPSL200: 归集完成 / Sweep completed - source: {}, target: {}, amount: {} {}",
                source.getAccountNumber(), target.getAccountNumber(), sweepAmount, source.getCurrency());

        return SweepExecutionResult.success(instructionId, source.getAccountNumber(),
                target.getAccountNumber(), sweepAmount, source.getCurrency());
    }

    /**
     * 计算归集金额 / Calculate the sweep amount based on sweep type.
     */
    private BigDecimal calculateSweepAmount(EpsAccountEntity source, SweepInstructionEntity instruction) {
        switch (instruction.getSweepType()) {
            case ZERO_BALANCE:
                // 归集全部正余额 / Sweep entire positive balance
                return source.getBookedBalance().max(BigDecimal.ZERO);
            case TARGET_BALANCE:
                // 归集超出目标余额的部分 / Sweep excess above target balance
                BigDecimal excess = source.getBookedBalance().subtract(instruction.getTargetBalance());
                return excess.max(BigDecimal.ZERO);
            case THRESHOLD:
                // 归集超出阈值的部分 / Sweep excess above threshold
                BigDecimal aboveThreshold = source.getBookedBalance().subtract(instruction.getThresholdAmount());
                return aboveThreshold.max(BigDecimal.ZERO);
            default:
                return BigDecimal.ZERO;
        }
    }

    // ──────────────────────────── EPSL210: 跨币种归集 ────────────────────────────

    /**
     * 执行跨币种归集(含自动换汇) (对应 COBOL EPSL210)
     * Execute cross-currency sweep with automatic FX conversion.
     * <p>
     * 归集流程 / Sweep process:
     * <ol>
     *   <li>查询适用汇率 (mid-rate +/- spread) / Query applicable FX rate (mid-rate +/- spread)</li>
     *   <li>按汇率换算归集金额 / Convert sweep amount at the FX rate</li>
     *   <li>执行源币种借记 + 目标币种贷记 / Execute source currency debit + target currency credit</li>
     *   <li>产生FX头寸记录 / Generate FX position record</li>
     * </ol>
     * <p>
     * 原AS/400调度: 每日 17:00 / Original AS/400 schedule: daily at 17:00
     *
     * @param instructionId 归集指令ID / Sweep instruction ID
     * @return 跨币种归集结果 / Cross-currency sweep result
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI")
    public CrossCurrencySweepResult executeCrossCurrencySweep(Long instructionId) {
        log.info("EPSL210: 开始跨币种归集 / Starting cross-currency sweep - instructionId: {}", instructionId);

        SweepInstructionEntity instruction = sweepInstructionRepository.findById(instructionId)
                .orElseThrow(() -> new LiquidityException("归集指令未找到 / Sweep instruction not found: " + instructionId));

        if (!instruction.isActive()) {
            throw new LiquidityException("归集指令未激活 / Sweep instruction is not active");
        }

        // 获取源账户和目标账户 / Get source and target accounts
        EpsAccountEntity source = epsAccountRepository.findByAccountNumber(instruction.getSourceAccount())
                .orElseThrow(() -> new AccountNotFoundException("源账户未找到: " + instruction.getSourceAccount()));
        EpsAccountEntity target = epsAccountRepository.findByAccountNumber(instruction.getTargetAccount())
                .orElseThrow(() -> new AccountNotFoundException("目标账户未找到: " + instruction.getTargetAccount()));

        // 检查是否为同币种 / Check if same currency
        if (source.getCurrency().equalsIgnoreCase(target.getCurrency())) {
            log.info("EPSL210: 同币种,回退到EPSL200 / Same currency, falling back to EPSL200");
            SweepExecutionResult simpleResult = executeSweep(instructionId);
            return CrossCurrencySweepResult.fromSimpleSweep(simpleResult);
        }

        // 计算源币种归集金额 / Calculate source currency sweep amount
        BigDecimal sourceAmount = calculateSweepAmount(source, instruction);
        if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return CrossCurrencySweepResult.noSweepNeeded(instructionId, source.getAccountNumber());
        }

        // 查询汇率 / Query FX rate
        String currencyPair = source.getCurrency() + "/" + target.getCurrency();
        FxRateEntity fxRate = fxRateRepository
                .findByCurrencyPairAndDate(currencyPair, LocalDate.now())
                .orElseThrow(() -> new LiquidityException("汇率未找到 / FX rate not found: " + currencyPair));

        // 计算价差调整 / Apply spread
        BigDecimal effectiveRate;
        if ("SELL".equalsIgnoreCase(fxRate.getDirection())) {
            // 卖出源币种 = 买入价 / Selling source currency = bid rate
            effectiveRate = fxRate.getBidRate();
        } else {
            // 买入目标币种 = 卖出价 / Buying target currency = ask rate
            effectiveRate = fxRate.getAskRate();
        }

        // 换算目标币种金额 / Convert to target currency amount
        BigDecimal targetAmount = sourceAmount.multiply(effectiveRate).setScale(2, RoundingMode.HALF_UP);

        // 执行转账 / Execute transfers
        source.setBookedBalance(source.getBookedBalance().subtract(sourceAmount));
        source.setUpdatedTime(LocalDateTime.now());
        epsAccountRepository.save(source);

        target.setBookedBalance(target.getBookedBalance().add(targetAmount));
        target.setUpdatedTime(LocalDateTime.now());
        epsAccountRepository.save(target);

        // 记录跨币种归集执行 / Record cross-currency sweep execution
        SweepExecutionEntity execution = new SweepExecutionEntity();
        execution.setInstructionId(instructionId);
        execution.setSourceAccount(source.getAccountNumber());
        execution.setTargetAccount(target.getAccountNumber());
        execution.setSourceAmount(sourceAmount);
        execution.setAmount(targetAmount); // target amount
        execution.setSourceCurrency(source.getCurrency());
        execution.setCurrency(target.getCurrency());
        execution.setFxRate(effectiveRate);
        execution.setFxRateRef(fxRate.getRateRef());
        execution.setSweepType(SweepType.CROSS_CURRENCY);
        execution.setExecutionTime(LocalDateTime.now());
        execution.setStatus(SweepStatus.COMPLETED);
        sweepExecutionRepository.save(execution);

        log.info("EPSL210: 跨币种归集完成 / Cross-currency sweep completed - {} {} -> {} {} at rate {}",
                sourceAmount, source.getCurrency(), targetAmount, target.getCurrency(), effectiveRate);

        return CrossCurrencySweepResult.success(instructionId, source.getAccountNumber(),
                target.getAccountNumber(), sourceAmount, source.getCurrency(),
                targetAmount, target.getCurrency(), effectiveRate);
    }

    // ──────────────────────────── EPSL300: 内部计息 ────────────────────────────

    /**
     * 内部计息计算 (对应 COBOL EPSL300)
     * Calculate internal interest on EPS account balances.
     * <p>
     * 计算逻辑 / Calculation logic:
     * <ul>
     *   <li>使用 360天/年 计息基准 / 360-day year day count convention</li>
     *   <li>按账户每日日终余额逐日计算 / Daily accrual based on end-of-day balance</li>
     *   <li>支持单一利率和阶梯利率 / Supports flat rate and tiered rates</li>
     *   <li>贷方余额计利息收入,借方余额计利息支出 / Credit balances earn interest income, debit pay interest expense</li>
     * </ul>
     * <p>
     * 原AS/400调度: 每月最后日 18:00 / Original AS/400 schedule: last day of month at 18:00
     *
     * @param accountNumber 账户号(为空计算所有账户) / Account number (empty = all accounts)
     * @param startDate     计息起始日 / Interest calculation start date
     * @param endDate       计息截止日 / Interest calculation end date
     * @return 计息结果列表 / Interest calculation results
     */
    @Scheduled(cron = "0 0 18 L * ?")
    public List<InterestResult> calculateInterest(String accountNumber, LocalDate startDate, LocalDate endDate) {
        log.info("EPSL300: 开始计息计算 / Starting interest calculation - account: {}, period: {} to {}",
                accountNumber, startDate, endDate);

        List<EpsAccountEntity> accounts;
        if (accountNumber != null && !accountNumber.isBlank()) {
            accounts = epsAccountRepository.findByAccountNumber(accountNumber)
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        } else {
            accounts = epsAccountRepository.findByActiveTrue();
        }

        List<InterestResult> results = new ArrayList<>();

        for (EpsAccountEntity account : accounts) {
            // 获取计息期每日余额快照 / Get daily balance snapshots for the period
            List<DailyBalanceSnapshot> snapshots = epsAccountRepository
                    .findDailyBalances(account.getAccountNumber(), startDate, endDate);

            if (snapshots.isEmpty()) {
                log.debug("EPSL300: 无余额快照 / No balance snapshots - account: {}", account.getAccountNumber());
                continue;
            }

            // 获取适用利率 / Get applicable interest rate
            BigDecimal flatRate = account.getInterestRateFlat();
            List<InterestRateTier> rateTiers = account.getInterestRateTiers();

            BigDecimal totalInterest = BigDecimal.ZERO;
            BigDecimal totalBalance = BigDecimal.ZERO;
            int totalDays = 0;

            for (DailyBalanceSnapshot snapshot : snapshots) {
                BigDecimal balance = snapshot.getEndOfDayBalance();
                totalBalance = totalBalance.add(balance);
                totalDays++;

                // 确定适用利率 / Determine applicable rate
                BigDecimal applicableRate;
                if (rateTiers != null && !rateTiers.isEmpty()) {
                    applicableRate = resolveTieredRate(balance, rateTiers);
                } else {
                    applicableRate = flatRate != null ? flatRate : epsConfig.getDefaultInterestRate();
                }

                // 日利息 = 余额 × 年利率(%) / 100 / 360
                // Daily interest = Balance × Annual Rate(%) / 100 / 360
                if (applicableRate != null && applicableRate.compareTo(BigDecimal.ZERO) != 0
                        && balance.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal dailyInterest = balance
                            .multiply(applicableRate)
                            .divide(BigDecimal.valueOf(100), INTEREST_SCALE + 2, RoundingMode.HALF_UP)
                            .divide(BigDecimal.valueOf(DAYS_PER_YEAR), INTEREST_SCALE, RoundingMode.HALF_UP);
                    totalInterest = totalInterest.add(dailyInterest);
                }
            }

            if (totalInterest.compareTo(BigDecimal.ZERO) != 0) {
                // 保存计息结果 / Save interest calculation result
                InterestCalculationEntity calc = new InterestCalculationEntity();
                calc.setAccountNumber(account.getAccountNumber());
                calc.setCurrency(account.getCurrency());
                calc.setStartDate(startDate);
                calc.setEndDate(endDate);
                calc.setTotalDays(totalDays);
                calc.setAverageBalance(totalBalance.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP));
                calc.setInterestAmount(totalInterest.setScale(2, RoundingMode.HALF_UP));
                calc.setInterestDirection(totalInterest.compareTo(BigDecimal.ZERO) > 0
                        ? InterestDirection.CREDIT : InterestDirection.DEBIT);
                calc.setCalculationTime(LocalDateTime.now());
                interestCalculationRepository.save(calc);

                results.add(new InterestResult(account.getAccountNumber(), account.getCurrency(),
                        startDate, endDate, totalDays, totalInterest.setScale(2, RoundingMode.HALF_UP),
                        calc.getInterestDirection()));

                log.info("EPSL300: 计息完成 / Interest calculated - account: {}, interest: {} {}",
                        account.getAccountNumber(), totalInterest.setScale(2, RoundingMode.HALF_UP), account.getCurrency());
            }
        }

        log.info("EPSL300: 计息计算全部完成 / All interest calculations completed - accounts: {}, results: {}",
                accounts.size(), results.size());
        return results;
    }

    /**
     * 阶梯利率解析 / Resolve applicable rate from tiered structure.
     * <p>
     * 示例 / Example:
     * Tier 1: 0 - 100,000 at 1.5%
     * Tier 2: 100,001 - 1,000,000 at 2.0%
     * Tier 3: 1,000,001+ at 2.5%
     */
    private BigDecimal resolveTieredRate(BigDecimal balance, List<InterestRateTier> tiers) {
        for (InterestRateTier tier : tiers) {
            if (balance.compareTo(tier.getMinBalance()) >= 0
                    && (tier.getMaxBalance() == null || balance.compareTo(tier.getMaxBalance()) < 0)) {
                return tier.getRate();
            }
        }
        // 返回最后一档的利率 / Return the highest tier rate as fallback
        if (!tiers.isEmpty()) {
            return tiers.get(tiers.size() - 1).getRate();
        }
        return epsConfig.getDefaultInterestRate();
    }

    // ──────────────────────────── 内部类 / Inner Classes ────────────────────────────

    /**
     * CAMT.052 解析条目 / Parsed CAMT.052 entry.
     */
    @lombok.Data
    private static class Camt052Entry {
        private String accountNumber;
        private BigDecimal bookedBalance;
        private BigDecimal availableBalance;
        private BigDecimal intradayCreditAmount;
        private BigDecimal intradayDebitAmount;
    }

    /**
     * 归集执行结果 / Sweep execution result DTO.
     */
    @lombok.Value
    public static class SweepExecutionResult {
        Long instructionId;
        String sourceAccount;
        String targetAccount;
        BigDecimal sweepAmount;
        String currency;
        SweepResultStatus status;
        String message;

        public static SweepExecutionResult success(Long instructionId, String source, String target,
                                                    BigDecimal amount, String currency) {
            return new SweepExecutionResult(instructionId, source, target, amount, currency,
                    SweepResultStatus.SUCCESS, "归集成功 / Sweep successful");
        }

        public static SweepExecutionResult noSweepNeeded(Long instructionId, String source) {
            return new SweepExecutionResult(instructionId, source, null, BigDecimal.ZERO, null,
                    SweepResultStatus.NO_ACTION, "无归集需求 / No sweep needed");
        }

        public static SweepExecutionResult belowThreshold(Long instructionId, BigDecimal amount, BigDecimal min) {
            return new SweepExecutionResult(instructionId, null, null, amount, null,
                    SweepResultStatus.BELOW_THRESHOLD,
                    "归集金额低于最小阈值 / Sweep amount below minimum: " + amount + " < " + min);
        }
    }

    /**
     * 跨币种归集结果 / Cross-currency sweep result DTO.
     */
    @lombok.Value
    public static class CrossCurrencySweepResult {
        Long instructionId;
        String sourceAccount;
        String targetAccount;
        BigDecimal sourceAmount;
        String sourceCurrency;
        BigDecimal targetAmount;
        String targetCurrency;
        BigDecimal fxRate;
        SweepResultStatus status;
        String message;

        public static CrossCurrencySweepResult success(Long instructionId, String source, String target,
                                                        BigDecimal srcAmt, String srcCcy,
                                                        BigDecimal tgtAmt, String tgtCcy,
                                                        BigDecimal rate) {
            return new CrossCurrencySweepResult(instructionId, source, target, srcAmt, srcCcy, tgtAmt, tgtCcy,
                    rate, SweepResultStatus.SUCCESS, "跨币种归集成功 / Cross-currency sweep successful");
        }

        public static CrossCurrencySweepResult noSweepNeeded(Long instructionId, String source) {
            return new CrossCurrencySweepResult(instructionId, source, null,
                    BigDecimal.ZERO, null, BigDecimal.ZERO, null, null,
                    SweepResultStatus.NO_ACTION, "无归集需求 / No sweep needed");
        }

        public static CrossCurrencySweepResult fromSimpleSweep(SweepExecutionResult simple) {
            return new CrossCurrencySweepResult(simple.getInstructionId(), simple.getSourceAccount(),
                    simple.getTargetAccount(), simple.getSweepAmount(), simple.getCurrency(),
                    simple.getSweepAmount(), simple.getCurrency(), BigDecimal.ONE,
                    simple.getStatus(), simple.getMessage());
        }
    }

    /**
     * 计息结果 / Interest calculation result DTO.
     */
    @lombok.Value
    public static class InterestResult {
        String accountNumber;
        String currency;
        LocalDate startDate;
        LocalDate endDate;
        int totalDays;
        BigDecimal interestAmount;
        InterestDirection direction;
    }

    /**
     * 归集结果状态枚举 / Sweep result status enum.
     */
    public enum SweepResultStatus {
        SUCCESS, NO_ACTION, BELOW_THRESHOLD, FAILED
    }
}
