package com.eps.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger configuration for the EPS API documentation.
 * <p>
 * This configuration defines the OpenAPI 3.0 specification metadata,
 * including API title, description, version, contact details, servers,
 * and logical tag groupings for the various EPS domains.
 * </p>
 *
 * <p>
 * The interactive Swagger UI is available at:
 * <a href="http://localhost:8080/eps/swagger-ui.html">/eps/swagger-ui.html</a>
 * </p>
 *
 * @see <a href="https://springdoc.org/">SpringDoc OpenAPI</a>
 */
@Configuration
public class SwaggerConfig {

    @Value("${eps.system-id:EPS001}")
    private String systemId;

    @Value("${eps.institution-name:EPS Payment Bank}")
    private String institutionName;

    /**
     * Creates the OpenAPI bean with complete EPS API documentation metadata.
     *
     * @return a configured OpenAPI instance
     */
    @Bean
    public OpenAPI epsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EPS — Electronic Payment System API")
                        .description("""
                                ## Overview
                                Java re-implementation of the AS/400 EPS (Electronic Payment System).

                                This API provides endpoints for:
                                - **Payment Processing** — Initiate and manage payments across FAST, MEPS, PayNow, GIRO, and SWIFT channels
                                - **Reconciliation** — Automated and manual reconciliation against bank statements
                                - **Liquidity Management** — Real-time liquidity position monitoring and forecasting
                                - **GL Posting** — Double-entry general ledger posting with T-account mapping
                                - **Refunds** — Refund processing with original-transaction linking and approval workflows
                                - **Compliance** — Sanctions screening, AML checks, and regulatory reporting
                                - **Reporting** — Generate and download payment, reconciliation, and audit reports

                                ## Authentication
                                All endpoints require Bearer token authentication (JWT).
                                Contact the EPS administration team for API credentials.

                                ## ISO 20022
                                The system natively supports ISO 20022 XML message formats
                                (pacs.008, pacs.002, pacs.004, camt.053, camt.054, etc.).
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("EPS Development Team")
                                .email("eps-support@eps-bank.com")
                                .url("https://eps-bank.com/support"))
                        .license(new License()
                                .name("Proprietary — Internal Use Only")
                                .url("https://eps-bank.com/license"))
                        .summary("Electronic Payment System — Payment Processing & Settlement"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/eps")
                                .description("Local Development"),
                        new Server()
                                .url("https://eps-uat.eps-bank.com/eps")
                                .description("UAT Environment"),
                        new Server()
                                .url("https://eps.eps-bank.com/eps")
                                .description("Production Environment")
                ))
                .tags(List.of(
                        new Tag()
                                .name("Payments")
                                .description("Payment initiation, inquiry, and lifecycle management across all channels"),
                        new Tag()
                                .name("Reconciliation")
                                .description("Bank statement reconciliation — automated matching, manual resolution, exception handling"),
                        new Tag()
                                .name("Liquidity")
                                .description("Real-time and forecasted liquidity positions across nostro/vostro accounts"),
                        new Tag()
                                .name("GL Posting")
                                .description("General ledger entries, T-account mapping, suspense account management"),
                        new Tag()
                                .name("Refunds")
                                .description("Refund processing with configurable approval workflows"),
                        new Tag()
                                .name("Compliance")
                                .description("Sanctions screening, AML threshold checks, regulatory hold/release"),
                        new Tag()
                                .name("Reporting")
                                .description("Operational, regulatory, and audit report generation"),
                        new Tag()
                                .name("System")
                                .description("Health checks, configuration, and system administration")
                ));
    }
}
