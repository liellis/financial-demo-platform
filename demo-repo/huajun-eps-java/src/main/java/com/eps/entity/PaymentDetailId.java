package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for {@link PaymentDetail} entity.
 * Consists of the parent payment reference and the line number within that payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailId implements Serializable {

    /** Parent payment instruction reference (maps to EPSPOH.paymentRef) */
    private String paymentRef;

    /** Sequential line number within the payment */
    private Integer lineNo;
}
