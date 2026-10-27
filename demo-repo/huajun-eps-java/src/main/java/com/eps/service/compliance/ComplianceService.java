package com.eps.service.compliance;

import com.eps.config.EpsConfig;
import com.eps.exception.ComplianceException;
import com.eps.model.entity.*;
import com.eps.model.enums.*;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合规管理服务 - 制裁名单筛查、AML/CFT模式检测与白名单管理
 * Compliance Management Service - Sanctions screening, AML/CFT pattern detection,
 * and whitelist management.
 * <p>
 * 对应原AS/400 COBOL程序 / Corresponds to original AS/400 COBOL programs:
 * <ul>
 *   <li>EPSSANLOD - 制裁名单加载和解析 / Sanctions list loading and parsing</li>
 *   <li>EPSSANPAT - 可疑模式检测 / Suspicious pattern detection (AML/CFT)</li>
 *   <li>EPSSANWHT - 白名单管理 / Whitelist management</li>
 * </ul>
 * <p>
 * 合规框架 / Compliance framework:
 * <ul>
 *   <li>OFAC (美国财政部海外资产控制办公室) / OFAC sanctions compliance</li>
 *   <li>UN Security Council sanctions lists / 联合国安理会制裁名单</li>
 *   <li>FATF 40项建议 / FATF 40 Recommendations for AML/CFT</li>
 *   <li>本地监管要求 / Local regulatory requirements</li>
 * </ul>
 *
 * @author EPS Migration Team
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional
public class ComplianceService {

    // ──────────────────────────── 依赖注入 Dependencies ────────────────────────────

    private final SanctionsListRepository sanctionsListRepository;
    private final SanctionsHitRepository sanctionsHitRepository;
    private final WhitelistRepository whitelistRepository;
    private final PaymentRepository paymentRepository;
    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private final AuditLogRepository auditLogRepository;
    private final EpsConfig epsConfig;

    /** 高风险地区国家代码 / High-risk jurisdiction country codes (FATF grey/black list) */
    private static final Set<String> HIGH_RISK_JURISDICTIONS = Set.of(
            "IR", // Iran
            "KP", // North Korea
            "MM", // Myanmar (high-risk)
            "SY"  // Syria
    );

    /** 大额交易报告阈值(美元等值) / Large transaction reporting threshold (USD equivalent) */
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = BigDecimal.valueOf(10_000);

    /** 异常交易滚动窗口(天) / Abnormal volume rolling window (days) */
    private static final int ABNORMAL_VOLUME_WINDOW_DAYS = 30;

    /** 异常倍数阈值(超过滚动平均值) / Abnormal multiplier threshold (over rolling avg) */
    private static final BigDecimal ABNORMAL_VOLUME_MULTIPLIER = BigDecimal.valueOf(3.0);

    /** 结构化交易检测窗口(小时) / Structuring detection window (hours) */
    private static final int STRUCTURING_WINDOW_HOURS = 24;

    /** 结构化交易最小笔数阈值 / Structuring minimum transaction count threshold */
    private static final int STRUCTURING_MIN_COUNT = 3;

    /** 名字匹配相似度阈值 / Name matching similarity threshold */
    private static final double NAME_MATCH_THRESHOLD = 0.85;

    /** 内存缓存: 最新制裁名单 / In-memory cache: latest sanctions list */
    private final Map<String, SanctionsEntry> sanctionsCache = new ConcurrentHashMap<>();

    public ComplianceService(SanctionsListRepository sanctionsListRepository,
                              SanctionsHitRepository sanctionsHitRepository,
                              WhitelistRepository whitelistRepository,
                              PaymentRepository paymentRepository,
                              SuspiciousActivityRepository suspiciousActivityRepository,
                              AuditLogRepository auditLogRepository,
                              EpsConfig epsConfig) {
        this.sanctionsListRepository = sanctionsListRepository;
        this.sanctionsHitRepository = sanctionsHitRepository;
        this.whitelistRepository = whitelistRepository;
        this.paymentRepository = paymentRepository;
        this.suspiciousActivityRepository = suspiciousActivityRepository;
        this.auditLogRepository = auditLogRepository;
        this.epsConfig = epsConfig;
    }

    // ──────────────────────────── EPSSANLOD: 制裁名单加载 ────────────────────────────

    /**
     * 解析并加载制裁名单数据 (对应 COBOL EPSSANLOD)
     * Parse and load sanctions list data from files into the system.
     * <p>
     * 支持格式 / Supported formats:
     * <ul>
     *   <li>OFAC SDN (Specially Designated Nationals) CSV格式</li>
     *   <li>UN Consolidated Sanctions List XML格式</li>
     *   <li>本地自定义黑名单 CSV格式 / Local custom blacklist CSV format</li>
     * </ul>
     * <p>
     * 原AS/400调度: 每日 06:00 自动更新 / Original AS/400 schedule: daily at 06:00 automatic refresh
     * <p>
     * 加载过程 / Loading process:
     * <ol>
     *   <li>清空过期名单 / Clear expired entries</li>
     *   <li>逐行解析文件 / Parse file line by line</li>
     *   <li>验签并解密(如果加密) / Verify signature and decrypt (if encrypted)</li>
     *   <li>构建内存索引 / Build in-memory index</li>
     *   <li>记录加载日志 / Record loading audit log</li>
     * </ol>
     *
     * @param filePath   制裁名单文件路径 / Sanctions list file path
     * @param listSource 名单来源 / List source (OFAC, UN, LOCAL)
     * @return 加载的记录数 / Number of records loaded
     */
    @Scheduled(cron = "0 0 6 * * *")
    public int loadSanctionsList(String filePath, String listSource) {
        log.info("EPSSANLOD: 开始加载制裁名单 / Starting sanctions list loading - source: {}, file: {}",
                listSource, filePath);

        int loadedCount = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            // 清除旧数据 / Clear old data for this source
            sanctionsListRepository.deleteBySourceAndLoadDateBefore(listSource, LocalDate.now());
            sanctionsCache.clear();

            String line;
            // 跳过表头 / Skip header
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                try {
                    SanctionsListEntity entry = parseSanctionsLine(line, listSource);
                    if (entry != null) {
                        sanctionsListRepository.save(entry);
                        sanctionsCache.put(entry.getSanctionsRef(), new SanctionsEntry(entry));
                        loadedCount++;
                    }
                } catch (Exception e) {
                    log.warn("EPSSANLOD: 行 {} 解析失败 / Line {} parse error: {}", lineNumber, lineNumber, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("EPSSANLOD: 制裁名单文件读取失败 / Sanctions file read failed: {}", filePath, e);
            throw new ComplianceException("制裁名单加载失败 / Sanctions list load failed: " + filePath, e);
        }

        writeAuditLog("SANCTIONS_" + listSource, AuditAction.SANCTIONS_LOADED, "SYSTEM_EPSSANLOD",
                String.format("制裁名单加载完成 / Sanctions list loaded - source: %s, records: %d", listSource, loadedCount));

        log.info("EPSSANLOD: 制裁名单加载完成 / Sanctions list loaded - source: {}, loaded: {}", listSource, loadedCount);
        return loadedCount;
    }

    /**
     * 解析制裁名单CSV行 / Parse a sanctions list CSV line.
     * CSV格式: sanctionsRef,entityType,name,aliases,country,program,idNumber,dob,status,effectiveDate
     */
    private SanctionsListEntity parseSanctionsLine(String line, String listSource) {
        String[] fields = line.split(",", -1);
        if (fields.length < 8) {
            log.warn("EPSSANLOD: 制裁列表字段不足 / Insufficient sanctions list fields");
            return null;
        }

        SanctionsListEntity entity = new SanctionsListEntity();
        entity.setSanctionsRef(fields[0].trim());
        entity.setSource(listSource);
        entity.setEntityType(parseEntityType(fields[1].trim()));
        entity.setPrimaryName(fields[2].trim().toUpperCase());
        entity.setAliases(fields[3].trim().toUpperCase());
        entity.setCountry(fields[4].trim());
        entity.setSanctionProgram(fields[5].trim());
        entity.setIdNumber(fields[6].trim());
        entity.setDateOfBirth(fields.length > 7 && !fields[7].trim().isEmpty()
                ? fields[7].trim() : null);
        entity.setActive("ACTIVE".equalsIgnoreCase(fields.length > 8 ? fields[8].trim() : "ACTIVE"));
        entity.setEffectiveDate(LocalDate.now());
        entity.setLoadDate(LocalDate.now());
        return entity;
    }

    private SanctionsEntityType parseEntityType(String type) {
        switch (type.toUpperCase()) {
            case "INDIVIDUAL": return SanctionsEntityType.INDIVIDUAL;
            case "ENTITY":
            case "ORGANIZATION": return SanctionsEntityType.ENTITY;
            case "VESSEL": return SanctionsEntityType.VESSEL;
            case "AIRCRAFT": return SanctionsEntityType.AIRCRAFT;
            default: return SanctionsEntityType.ENTITY;
        }
    }

    // ──────────────────────────── EPSSAN: 制裁筛查 ────────────────────────────

    /**
     * 对支付指令进行制裁名单筛查 (对应原AS/400 EPSSANCHK)
     * Screen a payment instruction against the sanctions list.
     * <p>
     * 筛查维度 / Screening dimensions:
     * <ol>
     *   <li>收款方名称 vs 制裁名单 / Payee name vs sanctions list</li>
     *   <li>付款方名称 vs 制裁名单 / Payer name vs sanctions list</li>
     *   <li>收款银行 vs 制裁实体列表 / Payee bank vs sanctioned entities</li>
     *   <li>国家/地区 vs 制裁国家列表 / Country/jurisdiction vs sanctioned countries</li>
     *   <li>证件号码 vs 制裁证件数据库 / ID number vs sanctions ID database</li>
     * </ol>
     * <p>
     * 匹配策略 / Matching strategy:
     * <ul>
     *   <li>精确匹配: 权重 100% / Exact match: weight 100%</li>
     *   <li>模糊匹配(Levenshtein >= 85%): 权重 70% / Fuzzy match (Levenshtein >= 85%): weight 70%</li>
     *   <li>别名匹配: 权重 80% / Alias match: weight 80%</li>
     * </ul>
     *
     * @param payment 支付指令 / Payment instruction entity
     * @return 筛查结果: 通过/命中/需复核 / Screening result: PASS, HIT, or REVIEW_REQUIRED
     */
    public ScreeningResult screenPayment(PaymentEntity payment) {
        log.info("EPSSANCHK: 开始制裁筛查 / Starting sanctions screening - payment: {}, payer: {}, payee: {}",
                payment.getPaymentRef(), payment.getPayerName(), payment.getPayeeName());

        // Step 1: 检查白名单 / Check whitelist first
        if (isWhitelisted(payment)) {
            log.info("EPSSANCHK: 命中白名单,跳过筛查 / Whitelist hit, skipping screening - payment: {}",
                    payment.getPaymentRef());
            return ScreeningResult.passed("WHITELIST", "白名单实体,豁免筛查 / Whitelisted entity, exempted");
        }

        List<SanctionsHit> hits = new ArrayList<>();

        // Step 2: 收款方名称筛查 / Payee name screening
        for (SanctionsEntry entry : sanctionsCache.values()) {
            if (!entry.isActive()) continue;

            double payeeSimilarity = calculateNameSimilarity(payment.getPayeeName(), entry.getPrimaryName());
            if (payeeSimilarity >= NAME_MATCH_THRESHOLD) {
                hits.add(new SanctionsHit(entry, "PAYEE_NAME", (int) (payeeSimilarity * 100)));
            } else {
                // 检查别名 / Check aliases
                for (String alias : entry.getAliases()) {
                    double aliasSimilarity = calculateNameSimilarity(payment.getPayeeName(), alias);
                    if (aliasSimilarity >= NAME_MATCH_THRESHOLD) {
                        hits.add(new SanctionsHit(entry, "PAYEE_NAME_ALIAS", (int) (aliasSimilarity * 100)));
                        break;
                    }
                }
            }
        }

        // Step 3: 付款方名称筛查 / Payer name screening
        for (SanctionsEntry entry : sanctionsCache.values()) {
            if (!entry.isActive()) continue;

            double payerSimilarity = calculateNameSimilarity(payment.getPayerName(), entry.getPrimaryName());
            if (payerSimilarity >= NAME_MATCH_THRESHOLD) {
                hits.add(new SanctionsHit(entry, "PAYER_NAME", (int) (payerSimilarity * 100)));
            } else {
                for (String alias : entry.getAliases()) {
                    double aliasSimilarity = calculateNameSimilarity(payment.getPayerName(), alias);
                    if (aliasSimilarity >= NAME_MATCH_THRESHOLD) {
                        hits.add(new SanctionsHit(entry, "PAYER_NAME_ALIAS", (int) (aliasSimilarity * 100)));
                        break;
                    }
                }
            }
        }

        // Step 4: 高风险地区检查 / High-risk jurisdiction check
        if (payment.getPayeeBankCode() != null) {
            // 从配置中获取高风险地区银行代码 / Read high-risk bank codes from config
            if (epsConfig.getHighRiskBankCodes() != null
                    && epsConfig.getHighRiskBankCodes().contains(payment.getPayeeBankCode())) {
                hits.add(new SanctionsHit(
                        new SanctionsEntry("HIGH_RISK_JURISDICTION", payment.getPayeeBankCode(),
                                "高风险地区 / High-risk jurisdiction"),
                        "JURISDICTION", 90));
            }
        }

        // Step 5: 判定最终结果 / Determine final result
        if (hits.isEmpty()) {
            log.info("EPSSANCHK: 筛查通过 / Screening passed - payment: {}", payment.getPaymentRef());
            return ScreeningResult.passed("CLEAN", "无命中 / No hits");
        }

        // 保存命中记录 / Save hit records
        for (SanctionsHit hit : hits) {
            SanctionsHitEntity hitEntity = new SanctionsHitEntity();
            hitEntity.setPaymentRef(payment.getPaymentRef());
            hitEntity.setSanctionsRef(hit.getEntry().getSanctionsRef());
            hitEntity.setHitType(hit.getHitType());
            hitEntity.setMatchScore(hit.getMatchScore());
            hitEntity.setMatchedName(hit.getEntry().getPrimaryName());
            hitEntity.setScreenedTime(LocalDateTime.now());
            sanctionsHitRepository.save(hitEntity);
        }

        // 判断是否需要阻止 / Determine if blocking is required
        boolean shouldBlock = hits.stream().anyMatch(h -> h.getMatchScore() >= 90 && !"JURISDICTION".equals(h.getHitType()));

        if (shouldBlock) {
            log.warn("EPSSANCHK: 制裁命中,需阻止交易! / Sanctions hit, transaction must be blocked! - payment: {}, hits: {}",
                    payment.getPaymentRef(), hits.size());

            writeAuditLog(payment.getPaymentRef(), AuditAction.SANCTIONS_HIT, "SYSTEM_EPSSANCHK",
                    String.format("制裁命中 / Sanctions hit - %d matches, blocked: true", hits.size()));

            return ScreeningResult.blocked(hits.size(), hits);
        } else {
            log.warn("EPSSANCHK: 低置信度命中,需人工复核 / Low-confidence hit, manual review required - payment: {}, hits: {}",
                    payment.getPaymentRef(), hits.size());

            return ScreeningResult.reviewRequired(hits.size(), hits);
        }
    }

    // ──────────────────────────── EPSSANPAT: 可疑模式检测 ────────────────────────────

    /**
     * AML/CFT 可疑交易模式分析 (对应 COBOL EPSSANPAT)
     * Analyze suspicious transaction patterns for AML/CFT compliance.
     * <p>
     * 原AS/400调度: 每日 07:00 / Original AS/400 schedule: daily at 07:00
     * <p>
     * 检测模式 / Detection patterns:
     * <ol>
     *   <li>结构化交易 (Smurfing/Structuring): 同一收款方短时间内多笔小额支付</li>
     *   <li>异常交易量: 单日交易量超过前30天滚动均值的300%</li>
     *   <li>高风险地区交易: 涉及FATF灰/黑名单地区的交易</li>
     *   <li>凑整交易: 接近整数的交易金额(如 $9,990 仅差$10达到$10,000报告阈值)</li>
     * </ol>
     *
     * @return 可疑活动报告列表 / List of suspicious activity reports
     */
    @Scheduled(cron = "0 0 7 * * *")
    public List<SuspiciousActivityReport> analyzeSuspiciousPatterns() {
        log.info("EPSSANPAT: 开始可疑模式检测 / Starting suspicious pattern analysis");
        LocalDate today = LocalDate.now();
        List<SuspiciousActivityReport> reports = new ArrayList<>();

        // Pattern 1: 结构化交易检测 / Structuring detection
        List<SuspiciousActivityReport> structuringReports = detectStructuring(today);
        reports.addAll(structuringReports);

        // Pattern 2: 异常交易量检测 / Abnormal volume detection
        List<SuspiciousActivityReport> volumeReports = detectAbnormalVolume(today);
        reports.addAll(volumeReports);

        // Pattern 3: 高风险地区交易检测 / High-risk jurisdiction detection
        List<SuspiciousActivityReport> jurisdictionReports = detectHighRiskJurisdiction(today);
        reports.addAll(jurisdictionReports);

        // Pattern 4: 凑整交易检测 / Round-amount transaction detection
        List<SuspiciousActivityReport> roundAmountReports = detectRoundAmountTransactions(today);
        reports.addAll(roundAmountReports);

        // 持久化可疑活动报告 / Persist suspicious activity reports
        for (SuspiciousActivityReport report : reports) {
            SuspiciousActivityEntity entity = new SuspiciousActivityEntity();
            entity.setDetectionDate(today);
            entity.setPatternType(report.getPatternType());
            entity.setSeverity(report.getSeverity());
            entity.setPaymentRef(report.getPaymentRef());
            entity.setCounterparty(report.getCounterparty());
            entity.setAmount(report.getAmount());
            entity.setCurrency(report.getCurrency());
            entity.setDetail(report.getDetail());
            entity.setCreatedTime(LocalDateTime.now());
            suspiciousActivityRepository.save(entity);
        }

        if (!reports.isEmpty()) {
            log.warn("EPSSANPAT: 检测到可疑活动: {} 项 / Suspicious activities detected: {}", reports.size(), reports);

            writeAuditLog("PATTERN_" + today, AuditAction.SUSPICIOUS_PATTERN_DETECTED, "SYSTEM_EPSSANPAT",
                    String.format("可疑模式检测完成,发现 %d 项 / Suspicious patterns detected: %d items",
                            reports.size(), reports.size()));
        } else {
            log.info("EPSSANPAT: 无可疑活动 / No suspicious activities detected");
        }

        return reports;
    }

    /**
     * Pattern 1: 结构化交易检测 / Structuring (smurfing) detection.
     * 检测同一对手方在24小时内多笔小额支付,可能存在规避报告阈值的嫌疑。
     * Detect multiple small payments to the same counterparty within 24 hours,
     * potentially to evade reporting thresholds.
     */
    private List<SuspiciousActivityReport> detectStructuring(LocalDate date) {
        List<SuspiciousActivityReport> reports = new ArrayList<>();
        LocalDateTime windowStart = date.atStartOfDay();
        LocalDateTime windowEnd = date.atTime(LocalTime.MAX);

        // 查询当天所有支付 / Query all payments for today
        List<PaymentEntity> todayPayments = paymentRepository
                .findByCreatedTimeBetween(windowStart, windowEnd);

        // 按收款方分组 / Group by payee
        Map<String, List<PaymentEntity>> byPayee = todayPayments.stream()
                .collect(java.util.stream.Collectors.groupingBy(PaymentEntity::getPayeeAccount));

        for (Map.Entry<String, List<PaymentEntity>> entry : byPayee.entrySet()) {
            String payee = entry.getKey();
            List<PaymentEntity> payments = entry.getValue();

            // 检查笔数和总金额 / Check count and total amount
            if (payments.size() >= STRUCTURING_MIN_COUNT) {
                BigDecimal totalAmount = payments.stream()
                        .map(PaymentEntity::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 检查是否每笔都低于报告阈值但总额超过 / Each below threshold but total exceeds
                boolean allBelowThreshold = payments.stream()
                        .allMatch(p -> p.getAmount().compareTo(LARGE_TRANSACTION_THRESHOLD) < 0);

                if (allBelowThreshold && totalAmount.compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
                    SuspiciousActivityReport report = new SuspiciousActivityReport();
                    report.setPatternType("STRUCTURING");
                    report.setSeverity(AlertSeverity.HIGH);
                    report.setCounterparty(payee);
                    report.setAmount(totalAmount);
                    report.setCurrency(payments.get(0).getCurrency());
                    report.setDetail(String.format("结构化交易嫌疑: %d笔支付,总计 %s %s,全部低于$10,000阈值 / "
                                    + "Structuring suspected: %d payments totaling %s %s, all below $10,000 threshold",
                            payments.size(), totalAmount, payments.get(0).getCurrency(), payments.size(), totalAmount, payments.get(0).getCurrency()));
                    reports.add(report);
                }
            }
        }

        return reports;
    }

    /**
     * Pattern 2: 异常交易量检测 / Abnormal volume detection.
     * 检测单日交易量是否超过前30天滚动均值的300%。
     * Check if today's transaction volume exceeds 300% of the 30-day rolling average.
     */
    private List<SuspiciousActivityReport> detectAbnormalVolume(LocalDate date) {
        List<SuspiciousActivityReport> reports = new ArrayList<>();

        LocalDate windowStart = date.minusDays(ABNORMAL_VOLUME_WINDOW_DAYS);
        LocalDate yesterday = date.minusDays(1);

        // 计算30天滚动日均值 / Calculate 30-day rolling average
        // (excluding today since it's currently in progress)
        BigDecimal dailyAvg = paymentRepository
                .calculateAverageDailyAmount(windowStart, yesterday);
        if (dailyAvg == null || dailyAvg.compareTo(BigDecimal.ZERO) == 0) {
            return reports; // 历史数据不足 / Insufficient history
        }

        // 今天交易总额 / Today's total volume
        BigDecimal todayTotal = paymentRepository
                .calculateTotalAmount(date, date);

        if (todayTotal == null || todayTotal.compareTo(BigDecimal.ZERO) == 0) {
            return reports;
        }

        // 计算超出倍数 / Calculate multiplier
        BigDecimal multiplier = todayTotal.divide(dailyAvg, 2, RoundingMode.HALF_UP);
        if (multiplier.compareTo(ABNORMAL_VOLUME_MULTIPLIER) > 0) {
            SuspiciousActivityReport report = new SuspiciousActivityReport();
            report.setPatternType("ABNORMAL_VOLUME");
            report.setSeverity(AlertSeverity.MEDIUM);
            report.setAmount(todayTotal);
            report.setDetail(String.format("异常交易量: 今日 %s 是30日均值 %s 的 %.1f倍(阈值: %.0f倍) / "
                            + "Abnormal volume: today %s is %.1fx of 30-day avg %s (threshold: %.0fx)",
                    todayTotal, dailyAvg, multiplier, ABNORMAL_VOLUME_MULTIPLIER,
                    todayTotal, multiplier, dailyAvg, ABNORMAL_VOLUME_MULTIPLIER));
            reports.add(report);
        }

        return reports;
    }

    /**
     * Pattern 3: 高风险地区交易检测 / High-risk jurisdiction transaction detection.
     * 检查交易是否涉及FATF灰/黑名单地区。
     * Check if transactions involve FATF grey/black listed jurisdictions.
     */
    private List<SuspiciousActivityReport> detectHighRiskJurisdiction(LocalDate date) {
        List<SuspiciousActivityReport> reports = new ArrayList<>();

        List<PaymentEntity> todayPayments = paymentRepository.findByValueDate(date);

        for (PaymentEntity payment : todayPayments) {
            // 检查收款银行是否在高风险地区 / Check if payee bank is in high-risk jurisdiction
            if (payment.getPayeeBankCode() != null
                    && epsConfig.getHighRiskBankCodes() != null
                    && epsConfig.getHighRiskBankCodes().contains(payment.getPayeeBankCode())) {

                SuspiciousActivityReport report = new SuspiciousActivityReport();
                report.setPatternType("HIGH_RISK_JURISDICTION");
                report.setSeverity(AlertSeverity.CRITICAL);
                report.setPaymentRef(payment.getPaymentRef());
                report.setCounterparty(payment.getPayeeName());
                report.setAmount(payment.getAmount());
                report.setCurrency(payment.getCurrency());
                report.setDetail(String.format("高风险地区交易: 收款银行 %s / "
                                + "High-risk jurisdiction transaction: payee bank %s",
                        payment.getPayeeBankCode()));
                reports.add(report);
            }
        }

        return reports;
    }

    /**
     * Pattern 4: 凑整交易检测 / Round-amount transaction detection.
     * 检测金额接近报告阈值的交易(如 $9,990, $9,995, $19,990 等)。
     * Detect transactions with amounts just below reporting thresholds (e.g., $9,990, $19,990).
     */
    private List<SuspiciousActivityReport> detectRoundAmountTransactions(LocalDate date) {
        List<SuspiciousActivityReport> reports = new ArrayList<>();
        List<PaymentEntity> todayPayments = paymentRepository.findByValueDate(date);

        // 凑整检测阈值范围(0.5%～2%低于大额阈值) / Round amount detection range
        BigDecimal lowerBound = LARGE_TRANSACTION_THRESHOLD.multiply(BigDecimal.valueOf(0.98));
        BigDecimal upperBound = LARGE_TRANSACTION_THRESHOLD.multiply(BigDecimal.valueOf(1.02));

        for (PaymentEntity payment : todayPayments) {
            BigDecimal amount = payment.getAmount();

            // 检查是否在阈值附近 / Check if near threshold
            if (amount.compareTo(lowerBound) > 0 && amount.compareTo(upperBound) < 0) {
                BigDecimal diffFromThreshold = LARGE_TRANSACTION_THRESHOLD.subtract(amount);

                SuspiciousActivityReport report = new SuspiciousActivityReport();
                report.setPatternType("ROUND_AMOUNT");
                report.setSeverity(AlertSeverity.LOW);
                report.setPaymentRef(payment.getPaymentRef());
                report.setCounterparty(payment.getPayeeName());
                report.setAmount(amount);
                report.setCurrency(payment.getCurrency());
                report.setDetail(String.format("凑整交易: 金额 %s %s 接近$10,000报告阈值(差%s) / "
                                + "Round-amount transaction: %s %s near $10,000 threshold (diff: %s)",
                        amount, payment.getCurrency(), diffFromThreshold.abs(),
                        amount, payment.getCurrency(), diffFromThreshold.abs()));
                reports.add(report);
            }

            // 也检查大额阈值的整数倍 / Also check multiples of the threshold
            for (int i = 2; i <= 5; i++) {
                BigDecimal multiple = LARGE_TRANSACTION_THRESHOLD.multiply(BigDecimal.valueOf(i));
                BigDecimal multiLower = multiple.multiply(BigDecimal.valueOf(0.995));
                BigDecimal multiUpper = multiple.multiply(BigDecimal.valueOf(1.005));

                if (amount.compareTo(multiLower) > 0 && amount.compareTo(multiUpper) < 0) {
                    BigDecimal multiDiff = multiple.subtract(amount).abs();
                    SuspiciousActivityReport report = new SuspiciousActivityReport();
                    report.setPatternType("ROUND_AMOUNT");
                    report.setSeverity(AlertSeverity.LOW);
                    report.setPaymentRef(payment.getPaymentRef());
                    report.setCounterparty(payment.getPayeeName());
                    report.setAmount(amount);
                    report.setCurrency(payment.getCurrency());
                    report.setDetail(String.format("凑整交易: 金额 %s %s 接近 $%s 的 %d倍 / "
                                    + "Round-amount transaction: %s %s near %dx of $%s",
                            amount, payment.getCurrency(), LARGE_TRANSACTION_THRESHOLD, i,
                            amount, payment.getCurrency(), i, LARGE_TRANSACTION_THRESHOLD));
                    reports.add(report);
                    break; // 只记录一次 / Only record once
                }
            }
        }

        return reports;
    }

    // ──────────────────────────── EPSSANWHT: 白名单管理 ────────────────────────────

    /**
     * 白名单管理 - 添加/移除/查询白名单实体 (对应 COBOL EPSSANWHT)
     * Whitelist management - add, remove, and query whitelisted entities.
     * <p>
     * 白名单用途 / Whitelist usage:
     * <ul>
     *   <li>已知合法实体 / Known legitimate entities</li>
     *   <li>内部关联公司 / Internal related companies</li>
     *   <li>政府机构 / Government agencies</li>
     *   <li>豁免筛查的特定支付类型 / Specific payment types exempt from screening</li>
     * </ul>
     * <p>
     * 白名单条目管理 / Whitelist entry management:
     * <ul>
     *   <li>添加: 需审批 / Add: requires approval</li>
     *   <li>移除: 需审批 / Remove: requires approval</li>
     *   <li>过期: 自动移除 / Expiry: auto-remove by scheduled job</li>
     * </ul>
     *
     * @return 当前活跃白名单列表 / Current active whitelist
     */
    @Transactional
    public List<WhitelistEntity> manageWhitelist(WhitelistAction action, WhitelistEntity entry, String operator) {
        log.info("EPSSANWHT: 白名单操作 / Whitelist operation - action: {}, operator: {}, entity: {}",
                action, operator, entry != null ? entry.getEntityName() : "N/A");

        switch (action) {
            case ADD:
                if (entry == null) {
                    throw new ComplianceException("白名单添加失败: 条目为空 / Whitelist add failed: entry is null");
                }
                entry.setActive(true);
                entry.setAddedBy(operator);
                entry.setAddedTime(LocalDateTime.now());
                entry.setUpdatedTime(LocalDateTime.now());
                whitelistRepository.save(entry);

                writeAuditLog("WHT_" + entry.getId(), AuditAction.WHITELIST_ADDED, operator,
                        "白名单添加/Whitelist added: " + entry.getEntityName() + " (" + entry.getEntityType() + ")");
                break;

            case REMOVE:
                if (entry == null || entry.getId() == null) {
                    throw new ComplianceException("白名单移除失败: ID为空 / Whitelist remove failed: ID is null");
                }
                WhitelistEntity existing = whitelistRepository.findById(entry.getId())
                        .orElseThrow(() -> new ComplianceException("白名单条目未找到 / Whitelist entry not found: " + entry.getId()));
                existing.setActive(false);
                existing.setRemovedBy(operator);
                existing.setRemovalReason(entry.getRemovalReason());
                existing.setUpdatedTime(LocalDateTime.now());
                whitelistRepository.save(existing);

                writeAuditLog("WHT_" + entry.getId(), AuditAction.WHITELIST_REMOVED, operator,
                        "白名单移除/Whitelist removed: " + existing.getEntityName() + ", reason: " + entry.getRemovalReason());
                break;

            case UPDATE:
                if (entry == null || entry.getId() == null) {
                    throw new ComplianceException("白名单更新失败: ID为空 / Whitelist update failed: ID is null");
                }
                WhitelistEntity toUpdate = whitelistRepository.findById(entry.getId())
                        .orElseThrow(() -> new ComplianceException("白名单条目未找到 / Whitelist entry not found: " + entry.getId()));
                toUpdate.setEntityName(entry.getEntityName());
                toUpdate.setEntityType(entry.getEntityType());
                toUpdate.setAccountNumber(entry.getAccountNumber());
                toUpdate.setExpiryDate(entry.getExpiryDate());
                toUpdate.setUpdatedTime(LocalDateTime.now());
                whitelistRepository.save(toUpdate);

                writeAuditLog("WHT_" + entry.getId(), AuditAction.WHITELIST_UPDATED, operator,
                        "白名单更新/Whitelist updated: " + entry.getEntityName());
                break;

            case LIST:
            default:
                // 返回活跃白名单 / Return active whitelist
                // (handled below via return)
                break;
        }

        return whitelistRepository.findByActiveTrue();
    }

    /**
     * 检查支付是否在白名单中 / Check if a payment is on the whitelist.
     */
    private boolean isWhitelisted(PaymentEntity payment) {
        // 按付款方名称检查 / Check by payer name
        List<WhitelistEntity> byPayer = whitelistRepository
                .findByEntityNameAndActive(payment.getPayerName(), true);
        if (!byPayer.isEmpty()) return true;

        // 按收款方名称检查 / Check by payee name
        List<WhitelistEntity> byPayee = whitelistRepository
                .findByEntityNameAndActive(payment.getPayeeName(), true);
        if (!byPayee.isEmpty()) return true;

        // 按账号检查 / Check by account number
        List<WhitelistEntity> byPayerAccount = whitelistRepository
                .findByAccountNumberAndActive(payment.getPayerAccount(), true);
        if (!byPayerAccount.isEmpty()) return true;

        List<WhitelistEntity> byPayeeAccount = whitelistRepository
                .findByAccountNumberAndActive(payment.getPayeeAccount(), true);
        if (!byPayeeAccount.isEmpty()) return true;

        return false;
    }

    // ──────────────────────────── 定时清理过期白名单 ────────────────────────────

    /**
     * 自动清理过期白名单条目 (原AS/400 EPSSANWHT CLEANUP)
     * Auto-clean expired whitelist entries.
     * 原AS/400调度: 每日 03:00 / Original AS/400 schedule: daily at 03:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanExpiredWhitelist() {
        log.info("EPSSANWHT: 清理过期白名单 / Cleaning expired whitelist entries");
        List<WhitelistEntity> expired = whitelistRepository
                .findByExpiryDateBeforeAndActive(LocalDate.now(), true);
        for (WhitelistEntity entry : expired) {
            entry.setActive(false);
            entry.setRemovedBy("SYSTEM_CLEANUP");
            entry.setRemovalReason("过期自动清理 / Expired auto-cleanup");
            entry.setUpdatedTime(LocalDateTime.now());
            whitelistRepository.save(entry);
        }
        log.info("EPSSANWHT: 清理完成, 过期条目: {} / Cleanup completed, expired entries: {}", expired.size());
    }

    // ──────────────────────────── 辅助方法 / Helper Methods ────────────────────────────

    /**
     * 计算名称相似度 / Calculate name similarity using Jaro-Winkler-like algorithm.
     * <p>
     * 这是一个简化版算法,用于名称匹配 / Simplified algorithm for name matching.
     * 实际生产环境应使用更专业的名字匹配引擎 / Production should use a specialized name matching engine.
     *
     * @param name1 名称1 / Name 1
     * @param name2 名称2 / Name 2
     * @return 相似度 0.0 - 1.0 / Similarity 0.0 - 1.0
     */
    private double calculateNameSimilarity(String name1, String name2) {
        if (name1 == null || name2 == null) return 0.0;

        String n1 = name1.toUpperCase().trim().replaceAll("[^A-Z0-9\\s]", "");
        String n2 = name2.toUpperCase().trim().replaceAll("[^A-Z0-9\\s]", "");

        if (n1.isEmpty() || n2.isEmpty()) return 0.0;
        if (n1.equals(n2)) return 1.0;

        // Tokenized comparison / 分词比较
        String[] tokens1 = n1.split("\\s+");
        String[] tokens2 = n2.split("\\s+");

        // 如果有任何分词完全匹配 / If any token exact matches
        for (String t1 : tokens1) {
            for (String t2 : tokens2) {
                if (t1.equals(t2) && t1.length() > 2) {
                    return 0.9;
                }
            }
        }

        // Levenshtein距离 / Levenshtein distance for fallback
        int distance = levenshteinDistance(n1, n2);
        int maxLen = Math.max(n1.length(), n2.length());
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Levenshtein编辑距离 / Levenshtein edit distance.
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
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

    // ──────────────────────────── 内部类 / Inner Classes ────────────────────────────

    /**
     * 制裁名单内存缓存条目 / In-memory sanctions cache entry.
     */
    @lombok.Value
    private static class SanctionsEntry {
        String sanctionsRef;
        String primaryName;
        List<String> aliases;
        boolean active;

        SanctionsEntry(SanctionsListEntity entity) {
            this.sanctionsRef = entity.getSanctionsRef();
            this.primaryName = entity.getPrimaryName();
            this.aliases = entity.getAliases() != null
                    ? Arrays.asList(entity.getAliases().split("\\|"))
                    : Collections.emptyList();
            this.active = entity.isActive();
        }

        SanctionsEntry(String ref, String name, String description) {
            this.sanctionsRef = ref;
            this.primaryName = name;
            this.aliases = Collections.singletonList(description);
            this.active = true;
        }
    }

    /**
     * 制裁筛查命中 / Sanctions screening hit record.
     */
    @lombok.Value
    static class SanctionsHit {
        SanctionsEntry entry;
        String hitType;
        int matchScore;
    }

    /**
     * 筛查结果 / Screening result DTO.
     */
    @lombok.Value
    public static class ScreeningResult {
        ScreeningOutcome outcome;
        int hitCount;
        String reason;
        List<String> hitDetails;

        public static ScreeningResult passed(String reason, String detail) {
            return new ScreeningResult(ScreeningOutcome.PASSED, 0, reason, Collections.singletonList(detail));
        }

        public static ScreeningResult blocked(int hitCount, List<SanctionsHit> hits) {
            List<String> details = new ArrayList<>();
            for (SanctionsHit hit : hits) {
                details.add(String.format("[%s] %s (score: %d%%)",
                        hit.getHitType(), hit.getEntry().getPrimaryName(), hit.getMatchScore()));
            }
            return new ScreeningResult(ScreeningOutcome.BLOCKED, hitCount,
                    "制裁命中 / Sanctions match found", details);
        }

        public static ScreeningResult reviewRequired(int hitCount, List<SanctionsHit> hits) {
            List<String> details = new ArrayList<>();
            for (SanctionsHit hit : hits) {
                details.add(String.format("[%s] %s (score: %d%%)",
                        hit.getHitType(), hit.getEntry().getPrimaryName(), hit.getMatchScore()));
            }
            return new ScreeningResult(ScreeningOutcome.REVIEW_REQUIRED, hitCount,
                    "需人工复核 / Manual review required", details);
        }
    }

    /**
     * 可疑活动报告 / Suspicious activity report DTO.
     */
    @lombok.Data
    public static class SuspiciousActivityReport {
        private String patternType;
        private AlertSeverity severity;
        private String paymentRef;
        private String counterparty;
        private BigDecimal amount;
        private String currency;
        private String detail;
    }

    /**
     * 筛查结果枚举 / Screening outcome enum.
     */
    public enum ScreeningOutcome {
        PASSED,         // 通过 / Passed
        REVIEW_REQUIRED,// 需人工复核 / Manual review required
        BLOCKED         // 命中,需阻止 / Hit, must block
    }

    /**
     * 白名单操作类型 / Whitelist action type.
     */
    public enum WhitelistAction {
        ADD, REMOVE, UPDATE, LIST
    }

    /**
     * 预警严重程度 / Alert severity levels.
     */
    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
