package com.eps.util;

import com.eps.entity.PaymentInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds ISO 20022 XML messages for payment initiation and processing,
 * modeled after AS/400 EPSISO20022 generation.
 *
 * <p>ISO 20022 is the global standard for financial messaging, using XML
 * syntax. This builder supports the following message types:</p>
 * <ul>
 *   <li><strong>pacs.008</strong> — FIToFICustomerCreditTransfer (single payment)</li>
 *   <li><strong>pacs.004</strong> — PaymentReturn (return/refund)</li>
 *   <li><strong>pain.001</strong> — CustomerCreditTransferInitiation (bulk)</li>
 * </ul>
 *
 * <p>All dates are formatted as ISO 8601. Amounts include proper currency
 * attributes. The XML uses the standard ISO 20022 namespace declarations.</p>
 */
public final class Iso20022MessageBuilder {

    private static final Logger log = LoggerFactory.getLogger(Iso20022MessageBuilder.class);

    /** ISO 8601 date format. */
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** ISO 8601 datetime format. */
    private static final DateTimeFormatter ISO_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** XML namespace for ISO 20022 pain.001 messages. */
    private static final String NS_PAIN = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";

    /** XML namespace for ISO 20022 pacs.008 messages. */
    private static final String NS_PACS = "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08";

    /** XML namespace for ISO 20022 pacs.004 messages. */
    private static final String NS_PACS_004 = "urn:iso:std:iso:20022:tech:xsd:pacs.004.001.09";

    /** Generic XML namespace. */
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    /** XML declaration. */
    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /** Private constructor to prevent instantiation. */
    private Iso20022MessageBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================================================
    // pacs.008 — FIToFICustomerCreditTransfer
    // ========================================================================

    /**
     * Builds an ISO 20022 pacs.008 (FIToFICustomerCreditTransfer) XML message
     * for a single payment instruction.
     *
     * <p>This message type is used for customer credit transfers between
     * financial institutions. Example output structure:</p>
     * <pre>
     * &lt;FIToFICstmrCdtTrf&gt;
     *   &lt;GrpHdr&gt;...&lt;/GrpHdr&gt;
     *   &lt;CdtTrfTxInf&gt;...&lt;/CdtTrfTxInf&gt;
     * &lt;/FIToFICstmrCdtTrf&gt;
     * </pre>
     *
     * @param payment the payment instruction
     * @return the complete pacs.008 XML as a string
     */
    public static String buildPacs008(PaymentInstruction payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment instruction must not be null");
        }

        log.debug("Building pacs.008 for payment ref: {}", payment.getPaymentRef());

        StringBuilder xml = new StringBuilder();
        xml.append(XML_DECL).append("\n");
        xml.append("<FIToFICstmrCdtTrf xmlns=\"").append(NS_PACS)
                .append("\" xmlns:xsi=\"").append(NS_XSI).append("\">\n");

        // Group Header
        xml.append(buildGroupHeader(payment, "pacs.008"));

        // Credit Transfer Transaction Information
        xml.append(buildCdtTrfTxInf(payment));

        xml.append("</FIToFICstmrCdtTrf>\n");

        return xml.toString();
    }

    /**
     * Builds the Group Header element for any ISO 20022 message.
     */
    private static String buildGroupHeader(PaymentInstruction payment, String messageType) {
        StringBuilder xml = new StringBuilder();
        xml.append("  <GrpHdr>\n");
        xml.append("    <MsgId>").append(xmlEscape(payment.getPaymentRef())).append("</MsgId>\n");
        xml.append("    <CreDtTm>")
                .append(LocalDateTime.now().format(ISO_DATETIME_FORMAT))
                .append("</CreDtTm>\n");
        xml.append("    <NbOfTxs>1</NbOfTxs>\n");
        // Control sum: total amount
        xml.append("    <CtrlSum>")
                .append(formatAmount(payment.getAmount()))
                .append("</CtrlSum>\n");
        xml.append("    <InitgPty>\n");
        xml.append("      <Nm>EPS Payment System</Nm>\n");
        xml.append("    </InitgPty>\n");
        xml.append("  </GrpHdr>\n");
        return xml.toString();
    }

    /**
     * Builds the Credit Transfer Transaction Information element.
     */
    private static String buildCdtTrfTxInf(PaymentInstruction payment) {
        StringBuilder xml = new StringBuilder();
        xml.append("  <CdtTrfTxInf>\n");

        // Payment Identification
        xml.append("    <PmtId>\n");
        xml.append("      <InstrId>").append(xmlEscape(payment.getPaymentRef())).append("</InstrId>\n");
        xml.append("      <EndToEndId>").append(xmlEscape(payment.getPaymentRef())).append("</EndToEndId>\n");
        xml.append("    </PmtId>\n");

        // Amount
        xml.append("    <IntrBkSttlmAmt Ccy=\"")
                .append(currencyCode(payment.getCurrency())).append("\">")
                .append(formatAmount(payment.getAmount()))
                .append("</IntrBkSttlmAmt>\n");

        // Value Date / Interbank Settlement Date
        if (payment.getValueDate() != null) {
            xml.append("    <IntrBkSttlmDt>")
                    .append(formatIsoDate(payment.getValueDate()))
                    .append("</IntrBkSttlmDt>\n");
        }

        // Charges — SHAR (shared)
        xml.append("    <ChrgBr>SLEV</ChrgBr>\n");

        // Debtor (ordering customer / payer)
        xml.append("    <Dbtr>\n");
        xml.append("      <Nm>").append(xmlEscape(payment.getPayerName(), "Payer")).append("</Nm>\n");
        if (payment.getPayerAccount() != null && !payment.getPayerAccount().isEmpty()) {
            xml.append("      <Id>\n");
            xml.append("        <PrvtId>\n");
            xml.append("          <Othr>\n");
            xml.append("            <Id>").append(xmlEscape(payment.getPayerAccount())).append("</Id>\n");
            xml.append("          </Othr>\n");
            xml.append("        </PrvtId>\n");
            xml.append("      </Id>\n");
        }
        xml.append("    </Dbtr>\n");

        // Debtor Account
        if (payment.getPayerAccount() != null && !payment.getPayerAccount().isEmpty()) {
            xml.append("    <DbtrAcct>\n");
            xml.append("      <Id>\n");
            xml.append("        <Othr>\n");
            xml.append("          <Id>").append(xmlEscape(payment.getPayerAccount())).append("</Id>\n");
            xml.append("        </Othr>\n");
            xml.append("      </Id>\n");
            xml.append("    </DbtrAcct>\n");
        }

        // Creditor Agent (payee's bank)
        if (payment.getPayeeBic() != null && !payment.getPayeeBic().isEmpty()) {
            xml.append("    <CdtrAgt>\n");
            xml.append("      <FinInstnId>\n");
            xml.append("        <BICFI>").append(xmlEscape(payment.getPayeeBic().toUpperCase().trim()))
                    .append("</BICFI>\n");
            xml.append("      </FinInstnId>\n");
            xml.append("    </CdtrAgt>\n");
        }

        // Creditor (beneficiary)
        xml.append("    <Cdtr>\n");
        xml.append("      <Nm>").append(xmlEscape(payment.getPayeeName(), "Beneficiary")).append("</Nm>\n");
        xml.append("    </Cdtr>\n");

        // Creditor Account
        if (payment.getPayeeAccount() != null && !payment.getPayeeAccount().isEmpty()) {
            xml.append("    <CdtrAcct>\n");
            xml.append("      <Id>\n");
            xml.append("        <Othr>\n");
            xml.append("          <Id>").append(xmlEscape(payment.getPayeeAccount())).append("</Id>\n");
            xml.append("        </Othr>\n");
            xml.append("      </Id>\n");
            xml.append("    </CdtrAcct>\n");
        }

        // Remittance Information
        if (payment.getRemittance() != null && !payment.getRemittance().isEmpty()) {
            xml.append("    <RmtInf>\n");
            xml.append("      <Ustrd>").append(xmlEscape(payment.getRemittance())).append("</Ustrd>\n");
            xml.append("    </RmtInf>\n");
        }

        xml.append("  </CdtTrfTxInf>\n");
        return xml.toString();
    }

    // ========================================================================
    // pacs.004 — PaymentReturn
    // ========================================================================

    /**
     * Builds an ISO 20022 pacs.004 (PaymentReturn) XML message for returning
     * a payment that could not be processed.
     *
     * @param payment      the original payment instruction being returned
     * @param returnReason the return reason code (e.g. "AC01" for incorrect account)
     * @return the complete pacs.004 XML as a string
     */
    public static String buildPacs004(PaymentInstruction payment, String returnReason) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment instruction must not be null");
        }

        log.debug("Building pacs.004 for payment ref: {}, reason: {}",
                payment.getPaymentRef(), returnReason);

        StringBuilder xml = new StringBuilder();
        xml.append(XML_DECL).append("\n");
        xml.append("<PmtRtr xmlns=\"").append(NS_PACS_004)
                .append("\" xmlns:xsi=\"").append(NS_XSI).append("\">\n");

        // Group Header
        xml.append("  <GrpHdr>\n");
        xml.append("    <MsgId>RTR").append(xmlEscape(payment.getPaymentRef())).append("</MsgId>\n");
        xml.append("    <CreDtTm>")
                .append(LocalDateTime.now().format(ISO_DATETIME_FORMAT))
                .append("</CreDtTm>\n");
        xml.append("    <NbOfTxs>1</NbOfTxs>\n");
        xml.append("    <CtrlSum>")
                .append(formatAmount(payment.getAmount()))
                .append("</CtrlSum>\n");
        xml.append("  </GrpHdr>\n");

        // Transaction Information
        xml.append("  <TxInf>\n");
        xml.append("    <RtrId>RTR").append(xmlEscape(payment.getPaymentRef())).append("</RtrId>\n");
        xml.append("    <OrgnlInstrId>").append(xmlEscape(payment.getPaymentRef()))
                .append("</OrgnlInstrId>\n");
        xml.append("    <OrgnlEndToEndId>").append(xmlEscape(payment.getPaymentRef()))
                .append("</OrgnlEndToEndId>\n");

        // Returned amount
        xml.append("    <RtrdIntrBkSttlmAmt Ccy=\"")
                .append(currencyCode(payment.getCurrency())).append("\">")
                .append(formatAmount(payment.getAmount()))
                .append("</RtrdIntrBkSttlmAmt>\n");

        // Return reason
        if (returnReason != null && !returnReason.isEmpty()) {
            xml.append("    <RtrRsnInf>\n");
            xml.append("      <Rsn>\n");
            xml.append("        <Cd>").append(xmlEscape(returnReason)).append("</Cd>\n");
            xml.append("      </Rsn>\n");
            xml.append("    </RtrRsnInf>\n");
        }

        xml.append("  </TxInf>\n");
        xml.append("</PmtRtr>\n");

        return xml.toString();
    }

    // ========================================================================
    // pain.001 — CustomerCreditTransferInitiation (bulk)
    // ========================================================================

    /**
     * Builds an ISO 20022 pain.001 (CustomerCreditTransferInitiation) XML
     * message for a batch of payment instructions.
     *
     * <p>This message is typically used to submit bulk payment files to a
     * bank for processing. Each payment in the list becomes a separate
     * transaction information block.</p>
     *
     * @param payments the list of payment instructions (must not be null or empty)
     * @return the complete pain.001 XML as a string
     */
    public static String buildPain001(List<PaymentInstruction> payments) {
        if (payments == null || payments.isEmpty()) {
            throw new IllegalArgumentException("Payment list must not be null or empty");
        }

        log.debug("Building pain.001 for {} payments", payments.size());

        StringBuilder xml = new StringBuilder();
        xml.append(XML_DECL).append("\n");
        xml.append("<CstmrCdtTrfInitn xmlns=\"").append(NS_PAIN)
                .append("\" xmlns:xsi=\"").append(NS_XSI).append("\">\n");

        // Group Header
        xml.append(buildPain001GroupHeader(payments));

        // Payment Information
        xml.append(buildPain001PaymentInfo(payments));

        xml.append("</CstmrCdtTrfInitn>\n");

        return xml.toString();
    }

    /**
     * Builds the Group Header for a pain.001 message.
     */
    private static String buildPain001GroupHeader(List<PaymentInstruction> payments) {
        // Calculate total amount sum
        BigDecimal totalSum = payments.stream()
                .map(PaymentInstruction::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder xml = new StringBuilder();
        xml.append("  <GrpHdr>\n");
        PaymentInstruction first = payments.get(0);
        String batchRef = first.getBatchNo() != null ? first.getBatchNo() : first.getPaymentRef();
        xml.append("    <MsgId>").append(xmlEscape(batchRef)).append("</MsgId>\n");
        xml.append("    <CreDtTm>")
                .append(LocalDateTime.now().format(ISO_DATETIME_FORMAT))
                .append("</CreDtTm>\n");
        xml.append("    <NbOfTxs>").append(payments.size()).append("</NbOfTxs>\n");
        xml.append("    <CtrlSum>").append(formatAmount(totalSum)).append("</CtrlSum>\n");
        xml.append("    <InitgPty>\n");
        xml.append("      <Nm>EPS Payment System</Nm>\n");
        xml.append("    </InitgPty>\n");
        xml.append("  </GrpHdr>\n");
        return xml.toString();
    }

    /**
     * Builds the Payment Information block for pain.001.
     */
    private static String buildPain001PaymentInfo(List<PaymentInstruction> payments) {
        StringBuilder xml = new StringBuilder();
        xml.append("  <PmtInf>\n");

        PaymentInstruction first = payments.get(0);
        String batchRef = first.getBatchNo() != null ? first.getBatchNo() : "BATCH";
        xml.append("    <PmtInfId>").append(xmlEscape(batchRef)).append("</PmtInfId>\n");
        xml.append("    <PmtMtd>TRF</PmtMtd>\n");
        xml.append("    <NbOfTxs>").append(payments.size()).append("</NbOfTxs>\n");
        xml.append("    <ReqdExctnDt>")
                .append(first.getValueDate() != null
                        ? formatIsoDate(first.getValueDate())
                        : formatIsoDate(LocalDate.now()))
                .append("</ReqdExctnDt>\n");

        // Debtor information from first payment
        xml.append("    <Dbtr>\n");
        xml.append("      <Nm>").append(xmlEscape(first.getPayerName(), "Payer")).append("</Nm>\n");
        xml.append("    </Dbtr>\n");

        if (first.getPayerAccount() != null && !first.getPayerAccount().isEmpty()) {
            xml.append("    <DbtrAcct>\n");
            xml.append("      <Id>\n");
            xml.append("        <Othr>\n");
            xml.append("          <Id>").append(xmlEscape(first.getPayerAccount())).append("</Id>\n");
            xml.append("        </Othr>\n");
            xml.append("      </Id>\n");
            xml.append("    </DbtrAcct>\n");
        }

        // Individual transaction entries
        for (PaymentInstruction payment : payments) {
            xml.append(buildPain001CdtTrfTxInf(payment));
        }

        xml.append("  </PmtInf>\n");
        return xml.toString();
    }

    /**
     * Builds an individual Credit Transfer Transaction for pain.001.
     */
    private static String buildPain001CdtTrfTxInf(PaymentInstruction payment) {
        StringBuilder xml = new StringBuilder();
        xml.append("    <CdtTrfTxInf>\n");

        // Payment ID
        xml.append("      <PmtId>\n");
        xml.append("        <InstrId>").append(xmlEscape(payment.getPaymentRef())).append("</InstrId>\n");
        xml.append("        <EndToEndId>").append(xmlEscape(payment.getPaymentRef())).append("</EndToEndId>\n");
        xml.append("      </PmtId>\n");

        // Amount
        xml.append("      <Amt>\n");
        xml.append("        <InstdAmt Ccy=\"")
                .append(currencyCode(payment.getCurrency())).append("\">")
                .append(formatAmount(payment.getAmount()))
                .append("</InstdAmt>\n");
        xml.append("      </Amt>\n");

        // Creditor Agent
        if (payment.getPayeeBic() != null && !payment.getPayeeBic().isEmpty()) {
            xml.append("      <CdtrAgt>\n");
            xml.append("        <FinInstnId>\n");
            xml.append("          <BICFI>").append(xmlEscape(payment.getPayeeBic().toUpperCase().trim()))
                    .append("</BICFI>\n");
            xml.append("        </FinInstnId>\n");
            xml.append("      </CdtrAgt>\n");
        }

        // Creditor
        xml.append("      <Cdtr>\n");
        xml.append("        <Nm>").append(xmlEscape(payment.getPayeeName(), "Beneficiary")).append("</Nm>\n");
        xml.append("      </Cdtr>\n");

        // Creditor Account
        if (payment.getPayeeAccount() != null && !payment.getPayeeAccount().isEmpty()) {
            xml.append("      <CdtrAcct>\n");
            xml.append("        <Id>\n");
            xml.append("          <Othr>\n");
            xml.append("            <Id>").append(xmlEscape(payment.getPayeeAccount())).append("</Id>\n");
            xml.append("          </Othr>\n");
            xml.append("        </Id>\n");
            xml.append("      </CdtrAcct>\n");
        }

        // Remittance
        if (payment.getRemittance() != null && !payment.getRemittance().isEmpty()) {
            xml.append("      <RmtInf>\n");
            xml.append("        <Ustrd>").append(xmlEscape(payment.getRemittance())).append("</Ustrd>\n");
            xml.append("      </RmtInf>\n");
        }

        xml.append("    </CdtTrfTxInf>\n");
        return xml.toString();
    }

    // ========================================================================
    // Formatting helpers
    // ========================================================================

    /**
     * Formats a {@link LocalDate} as an ISO 8601 date string (YYYY-MM-DD).
     *
     * @param date the date to format; if null, today is used
     * @return the ISO 8601 date string
     */
    public static String formatIsoDate(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return d.format(ISO_DATE_FORMAT);
    }

    /**
     * Formats a {@link BigDecimal} amount for ISO 20022 messages.
     * Uses a period (.) as the decimal separator with appropriate scale
     * for the currency.
     *
     * @param amount the amount to format; if null, "0.00" is returned
     * @return the formatted amount string
     */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        // ISO 20022 uses period as decimal separator
        return amount.toPlainString();
    }

    /**
     * Returns a safe currency code string, defaulting to "SGD" if null.
     *
     * @param currency the currency code, may be null
     * @return the uppercase currency code
     */
    private static String currencyCode(String currency) {
        return currency != null ? currency.toUpperCase().trim() : "SGD";
    }

    /**
     * Escapes special XML characters in a string value.
     *
     * @param value the raw value; null returns empty string
     * @return the XML-escaped string
     */
    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Escapes XML characters and returns a default if the result is empty.
     *
     * @param value        the raw value
     * @param defaultValue the default if null or empty after escaping
     * @return the escaped or default string
     */
    private static String xmlEscape(String value, String defaultValue) {
        String escaped = xmlEscape(value);
        if (escaped.isEmpty() && defaultValue != null) {
            return xmlEscape(defaultValue);
        }
        return escaped;
    }
}
