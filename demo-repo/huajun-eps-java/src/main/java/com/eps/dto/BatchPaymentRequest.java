package com.eps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * DTO for batch payment import.
 * Wraps a list of {@link PaymentRequest} objects under a single batch number
 * for processing multiple payments in a single API call or file upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPaymentRequest {

    /** Batch identifier — if not supplied the system generates one */
    @Size(max = 20, message = "Batch number must not exceed 20 characters")
    private String batchNo;

    /** Source system originating this batch */
    @Size(max = 10, message = "Source system must not exceed 10 characters")
    private String sourceSystem;

    /** List of payment requests in this batch, at least one required */
    @NotEmpty(message = "Batch must contain at least one payment")
    @Valid
    private List<PaymentRequest> payments;
}
