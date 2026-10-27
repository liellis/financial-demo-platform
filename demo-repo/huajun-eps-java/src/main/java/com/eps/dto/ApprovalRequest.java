package com.eps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * DTO for payment approval (maker-checker) operations.
 * Supports single and batch approval with an optional rejection reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    /** Payment reference(s) to approve, comma-separated for batch approval */
    @NotBlank(message = "Payment reference(s) is required")
    @Size(max = 500, message = "Payment reference list too long")
    private String paymentRefs;

    /** Approval action: APPROVE or REJECT */
    @NotBlank(message = "Action is required")
    @Size(max = 10)
    private String action;

    /** Reason for rejection (required when action is REJECT) */
    @Size(max = 200, message = "Reason must not exceed 200 characters")
    private String reason;

    /** Name or ID of the approver */
    @NotBlank(message = "Approver is required")
    @Size(max = 20, message = "Approver must not exceed 20 characters")
    private String approver;
}
