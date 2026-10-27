package com.eps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the EPS (Electronic Payment System) application.
 * <p>
 * This application is a Java re-implementation of the AS/400 EPS system.
 * It handles payment processing across multiple channels (FAST, MEPS,
 * PayNow, GIRO, SWIFT), reconciliation, liquidity management, GL posting,
 * refunds, sanctions screening, and reporting.
 * </p>
 *
 * <p><b>Key capabilities:</b></p>
 * <ul>
 *   <li>Multi-channel payment processing with configurable routing</li>
 *   <li>Automated reconciliation against bank statements</li>
 *   <li>Real-time liquidity position monitoring</li>
 *   <li>Double-entry GL posting with T-account mapping</li>
 *   <li>Refund processing with original-transaction linking</li>
 *   <li>Sanctions and AML compliance screening</li>
 *   <li>ISO 20022 XML message generation and parsing</li>
 *   <li>Batch processing via Quartz job scheduling</li>
 * </ul>
 *
 * @author EPS Development Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class EpsApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed at startup
     */
    public static void main(String[] args) {
        SpringApplication.run(EpsApplication.class, args);
    }
}
