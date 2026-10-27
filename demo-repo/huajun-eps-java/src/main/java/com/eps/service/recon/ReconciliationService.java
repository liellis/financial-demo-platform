package com.eps.service.recon;

import com.eps.config.EpsConfig;
import com.eps.exception.ChannelException;
import com.eps.exception.ReconciliationException;
import com.eps.model.entity.*;
import com.eps.model.enums.*;
import com.eps.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对账核心服务 - 银行流水与内部支付的多层匹配引擎
 * Reconciliation Core Service - Multi-layer matching engine for bank statements vs. internal payments.
 * <p>
 * 对应原AS/400 COBOL程序 / Corresponds to original AS/400 COBOL programs:
 * <ul>
 *   <li>EPSR100 - SFTP账单下载编排 / SFTP statement download orchestration</li>
 *   <li>EPSR200 - 银行账单文件解析 / Bank statement file parsing</li>
 *   <li>EPSR300 - 四轮递进匹配引擎 / 4-pass progressive matching engine</li>
 *   <li>EPSR900 - 未匹配项报告 / Unmatched items exception report</li>
 * </ul>
 *
 * @author EPS Migration Team
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional
public class ReconciliationService {

    // ──────────────────────────── 依赖注入 Dependencies ────────────────────────────

    private final StatementRepository statementRepository;
    private final StatementEntryRepository statementEntryRepository;
    private final PaymentRepository paymentRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final BankChannelRepository bankChannelRepository;
    private final AuditLogRepository auditLogRepository;
    private final SftpService sftpService;
    private final EpsConfig epsConfig;

    // ──────────────────────────── 匹配引擎常量 / Match Engine Constants ────────────────────────────

    /** 第1轮: 精确匹配阈值 / Pass 1: Exact match confidence */
    private static final int CONFIDENCE_EXACT = 100;

    /** 第2轮: 日期+金额匹配 / Pass 2: Date + Amount match confidence */
    private static final int CONFIDENCE_DATE_AMOUNT = 90;

    /** 第3轮: 金额+对手方匹配 / Pass 3: Amount + Counterparty match confidence */
    private static final int CONFIDENCE_AMOUNT_COUNTERPARTY = 70;

    /** 第4轮: 模糊叙述匹配 / Pass 4: Fuzzy narrative match confidence */
    private static final int CONFIDENCE_FUZZY = 50;

    /** 第4轮: Levenshtein相似度阈值 / Pass 4: Levenshtein similarity threshold */
    private static final double FUZZY_SIMILARITY_THRESHOLD = 0.75;

    /** 默认容差金额 / Default tolerance amount */
    private static final BigDecimal DEFAULT_TOLERANCE = BigDecimal.valueOf(0.01);

    public ReconciliationService(StatementRepository statementRepository,
                                  StatementEntryRepository statementEntryRepository,
                                  PaymentRepository paymentRepository,
                                  ReconciliationResultRepository reconciliationResultRepository,
                                  BankChannelRepository bankChannelRepository,
                                  AuditLogRepository auditLogRepository,
                                  SftpService sftpService,
                                  EpsConfig epsConfig) {
        this.statementRepository = statementRepository;
        this.statementEntryRepository = statementEntryRepository;
        this.paymentRepository = paymentRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.bankChannelRepository = bankChannelRepository;
        this.auditLogRepository = auditLogRepository;
        this.sftpService = sftpService;
        this.epsConfig = epsConfig;
    }

    // ──────────────────────────── EPSR100: SFTP账单下载 ────────────────────────────

    /**
     * 编排SFTP账单下载流程 (对应 COBOL EPSR100)
     * Orchestrate SFTP bank statement download.
     * <p>
     * 原AS/400定时任务: 每个工作日 08:00, 12:00, 17:00 / Original AS/400 schedule: business days 08:00, 12:00, 17:00
     * <p>
     * 处理步骤 / Process steps:
     * <ol>
     *   <li>解析银行SFTP连接配置 / Resolve bank SFTP connection config</li>
     *   <li>连接到SFTP服务器 / Connect to SFTP server</li>
     *   <li>下载 TXT/CSV/CAMT.053 格式账单文件 / Download statement files</li>
     *   <li>保存到本地临时目录 / Save to local temp directory</li>
     *   <li>记录下载日志 / Record download log</li>
     * </ol>
     *
     * @param bankCode 银行代码 / Bank code
     * @return 下载的文件路径列表 / List of downloaded file paths
     */
    @Scheduled(cron = "0 0 8,12,17 * * MON-FRI")
    public List<String> downloadStatements(String bankCode) {
        log.info("EPSR100: 开始下载银行账单 / Starting statement download - bank: {}", bankCode);

        List<String> downloadedFiles = new ArrayList<>();

        // 获取银行渠道配置 / Get bank channel configuration
        BankChannel channel = bankChannelRepository.findByChannelCode(bankCode)
                .orElseThrow(() -> new ChannelException("渠道未配置 / Channel not configured: " + bankCode));

        if (!channel.isActive()) {
            throw new ChannelException("渠道未激活 / Channel not active: " + bankCode);
        }

        String remoteDir = channel.getSftpRemoteDir() != null
                ? channel.getSftpRemoteDir()
                : epsConfig.getDefaultSftpDirectory();

        try {
            // 连接SFTP / Connect to SFTP
            sftpService.connect(channel.getSftpHost(), channel.getSftpPort(),
                    channel.getSftpUsername(), channel.getSftpPassword());

            // 列出远程文件 / List remote files
            LocalDate today = LocalDate.now();
            String datePattern = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<String> remoteFiles = sftpService.listFiles(remoteDir, "*" + datePattern + "*");

            for (String remoteFile : remoteFiles) {
                String localPath = epsConfig.getStatementDownloadDirectory()
                        + File.separator + bankCode + "_" + Paths.get(remoteFile).getFileName();
                sftpService.download(remoteFile, localPath);
                downloadedFiles.add(localPath);
                log.info("EPSR100: 下载成功 / Downloaded - file: {} -> {}", remoteFile, localPath);
            }

            sftpService.disconnect();
        } catch (Exception e) {
            log.error("EPSR100: SFTP下载失败 / SFTP download failed - bank: {}", bankCode, e);
            throw new ReconciliationException("账单下载失败 / Statement download failed for bank: " + bankCode, e);
        }

        log.info("EPSR100: 账单下载完成 / Statement download completed - bank: {}, files: {}",
                bankCode, downloadedFiles.size());
        return downloadedFiles;
    }

    // ──────────────────────────── EPSR200: 银行账单解析 ────────────────────────────

    /**
     * 解析银行账单文件 (对应 COBOL EPSR200)
     * Parse bank statement files into structured statement entries.
     * <p>
     * 支持格式 / Supported formats:
     * <ul>
     *   <li>固定长度(Fixed-length) - 传统格式 / Legacy format</li>
     *   <li>CSV - 逗号分隔 / Comma separated</li>
     *   <li>CAMT.053 - ISO 20022 XML格式 / ISO 20022 XML format</li>
     * </ul>
     *
     * @param bankCode 银行代码 / Bank code
     * @param date     账单日期 / Statement date
     * @return 解析出的流水条目数量 / Number of entries parsed
     */
    public int processStatementFiles(String bankCode, LocalDate date) {
        log.info("EPSR200: 开始解析账单文件 / Starting statement parsing - bank: {}, date: {}", bankCode, date);

        // 创建账单主记录 / Create statement header record
        StatementEntity statement = new StatementEntity();
        statement.setBankCode(bankCode);
        statement.setStatementDate(date);
        statement.setStatementRef(bankCode + "_" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        statement.setProcessedTime(LocalDateTime.now());
        statement.setStatus(StatementStatus.PROCESSING);
        statementRepository.save(statement);

        // 查找待解析文件 / Find files to parse
        String downloadDir = epsConfig.getStatementDownloadDirectory();
        String filePattern = bankCode + "_*" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "*";

        int totalEntries = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(downloadDir), filePattern)) {
            for (Path path : stream) {
                List<StatementEntryEntity> entries = parseFile(path, bankCode, date);
                for (StatementEntryEntity entry : entries) {
                    entry.setStatementId(statement.getId());
                    statementEntryRepository.save(entry);
                    totalEntries++;
                }
            }
        } catch (IOException e) {
            statement.setStatus(StatementStatus.FAILED);
            statementRepository.save(statement);
            throw new ReconciliationException("账单文件解析失败 / Statement file parsing failed: " + bankCode, e);
        }

        statement.setTotalEntries(totalEntries);
        statement.setStatus(StatementStatus.PARSED);
        statementRepository.save(statement);

        log.info("EPSR200: 账单解析完成 / Statement parsing completed - bank: {}, entries: {}", bankCode, totalEntries);
        return totalEntries;
    }

    /**
     * 根据文件格式解析 / Parse a single file based on its format.
     */
    private List<StatementEntryEntity> parseFile(Path filePath, String bankCode, LocalDate date)
            throws IOException {
        String fileName = filePath.getFileName().toString().toUpperCase();

        if (fileName.endsWith(".XML") || fileName.contains("CAMT")) {
            return parseCamt053(filePath, bankCode, date);
        } else if (fileName.endsWith(".CSV")) {
            return parseCsv(filePath, bankCode, date);
        } else {
            return parseFixedLength(filePath, bankCode, date);
        }
    }

    /**
     * 解析CSV格式账单 / Parse CSV format statement.
     * CSV格式: bankRef,amount,currency,debitCredit,entryDate,narrative,counterparty,account
     */
    private List<StatementEntryEntity> parseCsv(Path filePath, String bankCode, LocalDate date)
            throws IOException {
        List<StatementEntryEntity> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            // 跳过表头 / Skip header
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] fields = line.split(",", -1);
                if (fields.length < 7) {
                    log.warn("EPSR200: CSV行字段不足 / Insufficient CSV fields - file: {}, line: {}", filePath, line);
                    continue;
                }

                StatementEntryEntity entry = new StatementEntryEntity();
                entry.setBankRef(fields[0].trim());
                entry.setAmount(new BigDecimal(fields[1].trim()).setScale(2, RoundingMode.HALF_UP));
                entry.setCurrency(fields[2].trim());
                entry.setDebitCredit(DebitCredit.valueOf(fields[3].trim().toUpperCase()));
                entry.setEntryDate(LocalDate.parse(fields[4].trim(), DateTimeFormatter.ISO_DATE));
                entry.setNarrative(fields[5].trim());
                entry.setCounterparty(fields[6].trim());
                entry.setAccount(fields.length > 7 ? fields[7].trim() : "");
                entry.setBankCode(bankCode);
                entry.setMatchStatus(MatchStatus.UNMATCHED);
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * 解析固定长度账单 / Parse fixed-length format statement.
     * 格式: bankRef(1-20), amount(21-35), currency(36-38), dc(39), date(40-47), narrative(48-97), counterparty(98-147)
     */
    private List<StatementEntryEntity> parseFixedLength(Path filePath, String bankCode, LocalDate date)
            throws IOException {
        List<StatementEntryEntity> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (line.length() < 147) {
                    log.warn("EPSR200: 固定长度行太短 / Fixed-length line too short - file: {}", filePath);
                    continue;
                }

                StatementEntryEntity entry = new StatementEntryEntity();
                entry.setBankRef(line.substring(0, 20).trim());
                entry.setAmount(new BigDecimal(line.substring(20, 35).trim()).setScale(2, RoundingMode.HALF_UP));
                entry.setCurrency(line.substring(35, 38).trim());
                entry.setDebitCredit(DebitCredit.valueOf(line.substring(38, 39).trim().toUpperCase()));
                entry.setEntryDate(LocalDate.parse(line.substring(39, 47).trim(), DateTimeFormatter.BASIC_ISO_DATE));
                entry.setNarrative(line.substring(47, 97).trim());
                entry.setCounterparty(line.substring(97, 147).trim());
                entry.setBankCode(bankCode);
                entry.setMatchStatus(MatchStatus.UNMATCHED);
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * 解析CAMT.053 XML格式账单 / Parse CAMT.053 XML format statement.
     */
    private List<StatementEntryEntity> parseCamt053(Path filePath, String bankCode, LocalDate date)
            throws IOException {
        List<StatementEntryEntity> entries = new ArrayList<>();
        // 简化解析 - 实际项目应使用完整的CAMT.053 XML解析器
        // Simplified parsing - actual project should use a full CAMT.053 XML parser
        String content = Files.readString(filePath, StandardCharsets.UTF_8);

        // 简易XML提取 / Simple XML extraction (production should use JAXB/XmlMapper)
        String[] txBlocks = content.split("<Ntry>");
        for (int i = 1; i < txBlocks.length; i++) {
            StatementEntryEntity entry = new StatementEntryEntity();
            entry.setBankRef(extractXmlValue(txBlocks[i], "AcctSvcrRef", ""));
            entry.setAmount(new BigDecimal(
                    extractXmlValue(txBlocks[i], "Amt", "0")
                            .replaceAll("[^0-9.]", "")
            ).setScale(2, RoundingMode.HALF_UP));
            entry.setCurrency(extractXmlValue(txBlocks[i], "Amt", "").replaceAll("[0-9.]", "").substring(0, Math.min(3, extractXmlValue(txBlocks[i], "Amt", "").replaceAll("[0-9.]", "").length())));
            String cdtDbt = extractXmlValue(txBlocks[i], "CdtDbtInd", "CRDT");
            entry.setDebitCredit("DBIT".equalsIgnoreCase(cdtDbt) ? DebitCredit.DEBIT : DebitCredit.CREDIT);
            entry.setNarrative(extractXmlValue(txBlocks[i], "AddtlNtryInf", ""));
            entry.setCounterparty(extractXmlValue(txBlocks[i], "Nm", ""));
            entry.setBankCode(bankCode);
            entry.setMatchStatus(MatchStatus.UNMATCHED);
            entry.setEntryDate(date);
            entries.add(entry);
        }
        return entries;
    }

    private String extractXmlValue(String xml, String tag, String defaultValue) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = xml.indexOf(startTag);
        int end = xml.indexOf(endTag);
        if (start >= 0 && end > start) {
            return xml.substring(start + startTag.length(), end).trim();
        }
        // Also try attributes / Try self-closing tags
        return defaultValue;
    }

    // ──────────────────────────── EPSR300: 四轮递进匹配引擎 ────────────────────────────

    /**
     * 执行四轮递进自动对账 (对应 COBOL EPSR300)
     * Execute 4-pass progressive auto-reconciliation matching engine.
     * <p>
     * 匹配策略 / Matching strategy:
     * <table>
     *   <tr><th>轮次</th><th>匹配键</th><th>置信度</th></tr>
     *   <tr><td>Pass 1</td><td>bankRef + amount + currency</td><td>100%</td></tr>
     *   <tr><td>Pass 2</td><td>date + amount (with tolerance)</td><td>90%</td></tr>
     *   <tr><td>Pass 3</td><td>amount + counterparty</td><td>70%</td></tr>
     *   <tr><td>Pass 4</td><td>fuzzy narrative match (Levenshtein)</td><td>50%</td></tr>
     * </table>
     * <p>
     * 每一轮只处理上一轮未匹配的条目 / Each pass only processes unmatched items from previous passes.
     * 高置信度匹配自动确认,低置信度匹配标记为待人工审核 / High confidence auto-confirmed, low confidence flagged for review.
     *
     * @param bankCode  银行代码 / Bank code
     * @param tolerance 匹配容差金额(null使用默认0.01) / Match tolerance amount (null = 0.01 default)
     * @return 各轮次匹配结果汇总 / Match result summary per pass
     */
    public ReconciliationSummary autoReconcile(String bankCode, BigDecimal tolerance) {
        log.info("EPSR300: 开始自动对账 / Starting auto-reconciliation - bank: {}, tolerance: {}",
                bankCode, tolerance);

        BigDecimal effectiveTolerance = tolerance != null ? tolerance : DEFAULT_TOLERANCE;
        LocalDate today = LocalDate.now();

        // 加载待对账银行流水 / Load unmatched bank statement entries
        List<StatementEntryEntity> unmatchedEntries = statementEntryRepository
                .findByBankCodeAndMatchStatus(bankCode, MatchStatus.UNMATCHED);

        // 加载已回执待对账的内部支付 / Load ack-received internal payments pending reconciliation
        List<PaymentEntity> pendingReconPayments = paymentRepository
                .findByStatus(PaymentStatus.ACK_RECEIVED);

        log.info("EPSR300: 待匹配流水 {} 笔, 待对账支付 {} 笔 / Unmatched entries: {}, pending payments: {}",
                unmatchedEntries.size(), pendingReconPayments.size());

        ReconciliationSummary summary = new ReconciliationSummary();
        summary.setBankCode(bankCode);
        summary.setStatementDate(today);

        List<StatementEntryEntity> remainingEntries = new ArrayList<>(unmatchedEntries);
        List<PaymentEntity> remainingPayments = new ArrayList<>(pendingReconPayments);

        // ──────────── Pass 1: 精确匹配 (bankRef + amount + currency) → 100% ────────────
        PassResult pass1Result = performExactMatch(remainingEntries, remainingPayments);
        summary.setPass1(pass1Result);
        saveReconciliationResults(pass1Result.getMatches(), ReconciliationPass.EXACT);
        remainingEntries = pass1Result.getUnmatchedEntries();
        remainingPayments = pass1Result.getUnmatchedPayments();

        // ──────────── Pass 2: 日期 + 金额(含容差) → 90% ────────────
        PassResult pass2Result = performDateAmountMatch(remainingEntries, remainingPayments, effectiveTolerance);
        summary.setPass2(pass2Result);
        saveReconciliationResults(pass2Result.getMatches(), ReconciliationPass.DATE_AMOUNT);
        remainingEntries = pass2Result.getUnmatchedEntries();
        remainingPayments = pass2Result.getUnmatchedPayments();

        // ──────────── Pass 3: 金额 + 对手方 → 70% ────────────
        PassResult pass3Result = performAmountCounterpartyMatch(remainingEntries, remainingPayments);
        summary.setPass3(pass3Result);
        saveReconciliationResults(pass3Result.getMatches(), ReconciliationPass.AMOUNT_COUNTERPARTY);
        remainingEntries = pass3Result.getUnmatchedEntries();
        remainingPayments = pass3Result.getUnmatchedPayments();

        // ──────────── Pass 4: 模糊叙述匹配 (Levenshtein) → 50% ────────────
        PassResult pass4Result = performFuzzyNarrativeMatch(remainingEntries, remainingPayments);
        summary.setPass4(pass4Result);
        saveReconciliationResults(pass4Result.getMatches(), ReconciliationPass.FUZZY_NARRATIVE);

        // 更新最终未匹配条目 / Mark remaining as permanently unmatched
        for (StatementEntryEntity entry : unmatchedEntries) {
            if (entry.getMatchStatus() == MatchStatus.UNMATCHED) {
                entry.setMatchStatus(MatchStatus.EXCEPTION);
                statementEntryRepository.save(entry);
            }
        }

        log.info("EPSR300: 自动对账完成 / Auto-reconciliation completed - "
                + "Pass1: {}, Pass2: {}, Pass3: {}, Pass4: {}",
                pass1Result.getMatchCount(), pass2Result.getMatchCount(),
                pass3Result.getMatchCount(), pass4Result.getMatchCount());

        return summary;
    }

    /**
     * Pass 1: 精确匹配 / Exact match.
     * 匹配键: bankRef == payment bankRef AND amount == payment amount AND currency == payment currency
     */
    private PassResult performExactMatch(List<StatementEntryEntity> entries, List<PaymentEntity> payments) {
        List<ReconciliationMatch> matches = new ArrayList<>();
        Map<String, PaymentEntity> paymentByBankRef = payments.stream()
                .filter(p -> p.getBankRef() != null)
                .collect(Collectors.toMap(PaymentEntity::getBankRef, p -> p, (a, b) -> a));

        List<StatementEntryEntity> unmatched = new ArrayList<>();

        for (StatementEntryEntity entry : entries) {
            PaymentEntity payment = paymentByBankRef.get(entry.getBankRef());
            if (payment != null
                    && entry.getAmount().compareTo(payment.getAmount()) == 0
                    && entry.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
                ReconciliationMatch match = new ReconciliationMatch();
                match.setStatementEntry(entry);
                match.setPayment(payment);
                match.setConfidence(CONFIDENCE_EXACT);
                match.setPass(ReconciliationPass.EXACT);
                match.setMatchKey("bankRef:" + entry.getBankRef());
                matches.add(match);

                entry.setMatchStatus(MatchStatus.MATCHED);
                payment.setStatus(PaymentStatus.RECONCILED);

                payments.remove(payment);
            } else {
                unmatched.add(entry);
            }
        }

        return new PassResult(ReconciliationPass.EXACT, matches, unmatched, new ArrayList<>(payments));
    }

    /**
     * Pass 2: 日期 + 金额(含容差) / Date + Amount (with tolerance).
     * 匹配键: entry date == payment value date AND abs(entry amount - payment amount) <= tolerance
     */
    private PassResult performDateAmountMatch(List<StatementEntryEntity> entries,
                                               List<PaymentEntity> payments,
                                               BigDecimal tolerance) {
        List<ReconciliationMatch> matches = new ArrayList<>();
        Map<LocalDate, List<PaymentEntity>> paymentsByDate = payments.stream()
                .collect(Collectors.groupingBy(PaymentEntity::getValueDate));

        List<StatementEntryEntity> unmatched = new ArrayList<>();

        for (StatementEntryEntity entry : entries) {
            List<PaymentEntity> sameDatePayments = paymentsByDate.getOrDefault(entry.getEntryDate(), Collections.emptyList());
            if (sameDatePayments.isEmpty()) {
                unmatched.add(entry);
                continue;
            }

            PaymentEntity bestMatch = null;
            BigDecimal minDiff = null;

            for (PaymentEntity payment : sameDatePayments) {
                BigDecimal diff = entry.getAmount().subtract(payment.getAmount()).abs();
                if (diff.compareTo(tolerance) <= 0) {
                    if (minDiff == null || diff.compareTo(minDiff) < 0) {
                        minDiff = diff;
                        bestMatch = payment;
                    }
                }
            }

            if (bestMatch != null) {
                ReconciliationMatch match = new ReconciliationMatch();
                match.setStatementEntry(entry);
                match.setPayment(bestMatch);
                match.setConfidence(CONFIDENCE_DATE_AMOUNT);
                match.setPass(ReconciliationPass.DATE_AMOUNT);
                match.setMatchKey("date:" + entry.getEntryDate() + "+amount:" + entry.getAmount());
                matches.add(match);

                entry.setMatchStatus(MatchStatus.MATCHED);
                bestMatch.setStatus(PaymentStatus.RECONCILED);
                payments.remove(bestMatch);
                paymentsByDate.get(entry.getEntryDate()).remove(bestMatch);
            } else {
                unmatched.add(entry);
            }
        }

        return new PassResult(ReconciliationPass.DATE_AMOUNT, matches, unmatched, new ArrayList<>(payments));
    }

    /**
     * Pass 3: 金额 + 对手方 / Amount + Counterparty.
     * 匹配键: entry amount == payment amount AND entry counterparty ~= payment payee/payer name
     */
    private PassResult performAmountCounterpartyMatch(List<StatementEntryEntity> entries,
                                                       List<PaymentEntity> payments) {
        List<ReconciliationMatch> matches = new ArrayList<>();
        List<StatementEntryEntity> unmatched = new ArrayList<>();

        for (StatementEntryEntity entry : entries) {
            PaymentEntity bestMatch = null;
            for (PaymentEntity payment : payments) {
                if (entry.getAmount().compareTo(payment.getAmount()) == 0
                        && isCounterpartyMatch(entry.getCounterparty(),
                        payment.getPayeeName(), payment.getPayerName())) {
                    bestMatch = payment;
                    break;
                }
            }

            if (bestMatch != null) {
                ReconciliationMatch match = new ReconciliationMatch();
                match.setStatementEntry(entry);
                match.setPayment(bestMatch);
                match.setConfidence(CONFIDENCE_AMOUNT_COUNTERPARTY);
                match.setPass(ReconciliationPass.AMOUNT_COUNTERPARTY);
                match.setMatchKey("amount:" + entry.getAmount() + "+cp:" + entry.getCounterparty());
                matches.add(match);

                entry.setMatchStatus(MatchStatus.MATCHED);
                bestMatch.setStatus(PaymentStatus.RECONCILED);
                payments.remove(bestMatch);
            } else {
                unmatched.add(entry);
            }
        }

        return new PassResult(ReconciliationPass.AMOUNT_COUNTERPARTY, matches, unmatched, new ArrayList<>(payments));
    }

    /**
     * 检查对手方名称是否匹配 / Check if counterparty names match.
     */
    private boolean isCounterpartyMatch(String counterparty, String payeeName, String payerName) {
        if (counterparty == null) return false;
        String cpLower = counterparty.toLowerCase().trim();
        return (payeeName != null && payeeName.toLowerCase().trim().contains(cpLower))
                || (payerName != null && payerName.toLowerCase().trim().contains(cpLower))
                || (cpLower.contains(payeeName != null ? payeeName.toLowerCase().trim() : ""))
                || (cpLower.contains(payerName != null ? payerName.toLowerCase().trim() : ""));
    }

    /**
     * Pass 4: 模糊叙述匹配 (Levenshtein) / Fuzzy narrative match.
     * 匹配键: Levenshtein distance between statement narrative and payment narrative >= 75% similarity
     */
    private PassResult performFuzzyNarrativeMatch(List<StatementEntryEntity> entries,
                                                   List<PaymentEntity> payments) {
        List<ReconciliationMatch> matches = new ArrayList<>();
        List<StatementEntryEntity> unmatched = new ArrayList<>();

        for (StatementEntryEntity entry : entries) {
            if (entry.getNarrative() == null || entry.getNarrative().isBlank()) {
                unmatched.add(entry);
                continue;
            }

            PaymentEntity bestMatch = null;
            double bestSimilarity = 0.0;

            for (PaymentEntity payment : payments) {
                if (payment.getNarrative() == null || payment.getNarrative().isBlank()) continue;

                double similarity = calculateLevenshteinSimilarity(entry.getNarrative(), payment.getNarrative());
                if (similarity >= FUZZY_SIMILARITY_THRESHOLD && similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = payment;
                }
            }

            if (bestMatch != null) {
                ReconciliationMatch match = new ReconciliationMatch();
                match.setStatementEntry(entry);
                match.setPayment(bestMatch);
                match.setConfidence((int) (CONFIDENCE_FUZZY * bestSimilarity));
                match.setPass(ReconciliationPass.FUZZY_NARRATIVE);
                match.setMatchKey("fuzzy:" + String.format("%.2f", bestSimilarity));
                matches.add(match);

                entry.setMatchStatus(MatchStatus.MATCHED);
                bestMatch.setStatus(PaymentStatus.RECONCILED);
                payments.remove(bestMatch);
            } else {
                unmatched.add(entry);
            }
        }

        return new PassResult(ReconciliationPass.FUZZY_NARRATIVE, matches, unmatched, new ArrayList<>(payments));
    }

    /**
     * 计算Levenshtein相似度 / Calculate Levenshtein similarity.
     *
     * @param s1 字符串1 / String 1
     * @param s2 字符串2 / String 2
     * @return 相似度 0.0-1.0 / Similarity 0.0-1.0
     */
    private double calculateLevenshteinSimilarity(String s1, String s2) {
        String a = s1.toLowerCase().trim();
        String b = s2.toLowerCase().trim();

        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        int distance = dp[a.length()][b.length()];
        int maxLen = Math.max(a.length(), b.length());
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * 保存对账匹配结果 / Persist reconciliation match results.
     */
    private void saveReconciliationResults(List<ReconciliationMatch> matches, ReconciliationPass pass) {
        for (ReconciliationMatch m : matches) {
            ReconciliationResultEntity result = new ReconciliationResultEntity();
            result.setBankCode(m.getStatementEntry().getBankCode());
            result.setStatementEntryId(m.getStatementEntry().getId());
            result.setPaymentRef(m.getPayment().getPaymentRef());
            result.setPass(pass);
            result.setConfidence(m.getConfidence());
            result.setMatchKey(m.getMatchKey());
            result.setAmount(m.getStatementEntry().getAmount());
            result.setCurrency(m.getStatementEntry().getCurrency());
            result.setMatchTime(LocalDateTime.now());
            result.setAutoMatched(true);
            reconciliationResultRepository.save(result);

            // Update both entities
            statementEntryRepository.save(m.getStatementEntry());
            paymentRepository.save(m.getPayment());
        }
    }

    // ──────────────────────────── EPSR900: 异常报告 ────────────────────────────

    /**
     * 生成未匹配异常报告 (对应 COBOL EPSR900)
     * Generate exception report for unmatched items.
     * <p>
     * 报告内容 / Report contents:
     * <ul>
     *   <li>仅银行端存在(长款) / Bank-only items (unmatched credits at bank)</li>
     *   <li>仅我方存在(短款) / EPS-only items (unmatched payments)</li>
     *   <li>金额差异项 / Amount discrepancy items</li>
     * </ul>
     *
     * @return 异常报告 / Exception report
     */
    @Transactional(readOnly = true)
    public ExceptionReport generateExceptionReport() {
        log.info("EPSR900: 生成异常对账报告 / Generating exception report");

        ExceptionReport report = new ExceptionReport();
        report.setGeneratedTime(LocalDateTime.now());

        // 银行端未匹配(长款) / Bank-side unmatched (long positions)
        List<StatementEntryEntity> bankUnmatched = statementEntryRepository
                .findByMatchStatus(MatchStatus.EXCEPTION);
        report.setBankOnlyItems(bankUnmatched.stream()
                .map(e -> new ExceptionReport.ExceptionItem(
                        e.getBankRef(), e.getBankCode(), e.getAmount(), e.getCurrency(),
                        e.getEntryDate(), "银行端未匹配 / Bank unmatched: " + e.getNarrative()))
                .collect(Collectors.toList()));

        // 我方未匹配(短款) / EPS-side unmatched (short positions)
        List<PaymentEntity> epsUnmatched = paymentRepository
                .findByStatus(PaymentStatus.ACK_RECEIVED);
        report.setEpsOnlyItems(epsUnmatched.stream()
                .map(p -> new ExceptionReport.ExceptionItem(
                        p.getPaymentRef(), p.getChannel(), p.getAmount(), p.getCurrency(),
                        p.getValueDate(), "我方未匹配 / EPS unmatched: " + p.getNarrative()))
                .collect(Collectors.toList()));

        // 低置信度匹配项(需人工复核) / Low-confidence matches (need manual review)
        List<ReconciliationResultEntity> lowConfResults = reconciliationResultRepository
                .findByConfidenceLessThanAndAutoMatched(70, true);
        report.setPendingManualReview(lowConfResults.stream()
                .map(r -> new ExceptionReport.ExceptionItem(
                        r.getPaymentRef(), r.getBankCode(), r.getAmount(), r.getCurrency(),
                        r.getMatchTime().toLocalDate(),
                        "置信度低需复核 / Low confidence: " + r.getConfidence() + "%"))
                .collect(Collectors.toList()));

        log.info("EPSR900: 异常报告生成完成 - 银行端:{}, 我方:{}, 待复核:{}",
                report.getBankOnlyItems().size(), report.getEpsOnlyItems().size(),
                report.getPendingManualReview().size());
        return report;
    }

    // ──────────────────────────── 人工匹配 ────────────────────────────

    /**
     * 人工对账匹配 / Manual reconciliation match.
     * <p>
     * 用于处理自动对账引擎无法匹配的条目 / Used for items the auto-recon engine cannot match.
     *
     * @param paymentRef 支付引用号 / Payment reference number
     * @param entryRef   银行流水条目ID / Bank statement entry ID
     * @param operator   操作员ID / Operator ID
     */
    public void manualMatch(String paymentRef, String entryRef, String operator) {
        log.info("人工对账匹配 / Manual reconciliation - paymentRef: {}, entryRef: {}, operator: {}",
                paymentRef, entryRef, operator);

        PaymentEntity payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new ReconciliationException("支付未找到 / Payment not found: " + paymentRef));

        StatementEntryEntity entry = statementEntryRepository.findByBankRef(entryRef)
                .orElseThrow(() -> new ReconciliationException("流水条目未找到 / Statement entry not found: " + entryRef));

        // 创建人工匹配记录 / Create manual match record
        ReconciliationResultEntity result = new ReconciliationResultEntity();
        result.setBankCode(entry.getBankCode());
        result.setStatementEntryId(entry.getId());
        result.setPaymentRef(paymentRef);
        result.setPass(ReconciliationPass.MANUAL);
        result.setConfidence(100); // 人工确认 = 100% / Manual confirmation = 100%
        result.setMatchKey("manual:" + operator);
        result.setAmount(entry.getAmount());
        result.setCurrency(entry.getCurrency());
        result.setMatchTime(LocalDateTime.now());
        result.setAutoMatched(false);
        result.setOperator(operator);
        reconciliationResultRepository.save(result);

        // 更新状态 / Update statuses
        entry.setMatchStatus(MatchStatus.MANUALLY_MATCHED);
        statementEntryRepository.save(entry);

        payment.setStatus(PaymentStatus.RECONCILED);
        paymentRepository.save(payment);

        log.info("人工匹配完成 / Manual match completed - paymentRef: {}, entryRef: {}", paymentRef, entryRef);
    }

    // ──────────────────────────── 内部类 / Inner Classes ────────────────────────────

    /**
     * 单次对账匹配结果 / Single reconciliation match result.
     */
    @lombok.Data
    public static class ReconciliationMatch {
        private StatementEntryEntity statementEntry;
        private PaymentEntity payment;
        private int confidence;
        private ReconciliationPass pass;
        private String matchKey;
    }

    /**
     * 单轮匹配结果 / Single pass result.
     */
    @lombok.Value
    public static class PassResult {
        ReconciliationPass pass;
        List<ReconciliationMatch> matches;
        List<StatementEntryEntity> unmatchedEntries;
        List<PaymentEntity> unmatchedPayments;

        public int getMatchCount() {
            return matches.size();
        }
    }

    /**
     * 对账汇总 / Reconciliation summary.
     */
    @lombok.Data
    public static class ReconciliationSummary {
        private String bankCode;
        private LocalDate statementDate;
        private PassResult pass1;
        private PassResult pass2;
        private PassResult pass3;
        private PassResult pass4;

        public int getTotalMatched() {
            return (pass1 != null ? pass1.getMatchCount() : 0)
                    + (pass2 != null ? pass2.getMatchCount() : 0)
                    + (pass3 != null ? pass3.getMatchCount() : 0)
                    + (pass4 != null ? pass4.getMatchCount() : 0);
        }
    }

    /**
     * 异常报告 / Exception report.
     */
    @lombok.Data
    public static class ExceptionReport {
        private LocalDateTime generatedTime;
        private List<ExceptionItem> bankOnlyItems = new ArrayList<>();
        private List<ExceptionItem> epsOnlyItems = new ArrayList<>();
        private List<ExceptionItem> pendingManualReview = new ArrayList<>();

        public int getTotalExceptions() {
            return bankOnlyItems.size() + epsOnlyItems.size() + pendingManualReview.size();
        }

        @lombok.Value
        public static class ExceptionItem {
            String reference;
            String source;
            BigDecimal amount;
            String currency;
            LocalDate date;
            String reason;
        }
    }
}
