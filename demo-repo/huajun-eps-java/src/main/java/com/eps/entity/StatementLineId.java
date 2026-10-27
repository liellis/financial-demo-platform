package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for {@link StatementLine} entity.
 * Uniquely identifies a line within a bank statement entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementLineId implements Serializable {

    /** Statement identifier from the bank file */
    private String statementNo;

    /** Bank reference for this entry */
    private String entryRef;

    /** Sequential line number within the statement entry */
    private Integer lineNo;
}
