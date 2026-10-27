package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * Payment detail / line-item entity, mapped to table EPSPOD.
 * Each payment instruction (EPSPOH) can have multiple detail lines
 * representing individual invoices, cost-center splits, or tax line items.
 *
 * Uses {@link PaymentDetailId} as the composite primary key.
 */
@Entity
@Table(name = "EPSPOD")
@IdClass(PaymentDetailId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetail {

    /** Parent payment reference — part of composite key */
    @Id
    @Column(name = "PAYMENT_REF", length = 20, nullable = false)
    private String paymentRef;

    /** Sequential line number within the payment — part of composite key */
    @Id
    @Column(name = "LINE_NO", nullable = false)
    private Integer lineNo;

    /** Invoice reference number being settled, length 50 */
    @Column(name = "INVOICE_REF", length = 50)
    private String invoiceRef;

    /** Cost center code for accounting allocation, length 20 */
    @Column(name = "COST_CENTER", length = 20)
    private String costCenter;

    /** Tax amount component of this line (if applicable) */
    @Column(name = "TAX_AMOUNT", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    /** Free-text description of the line item, length 200 */
    @Column(name = "DESCRIPTION", length = 200)
    private String description;
}
