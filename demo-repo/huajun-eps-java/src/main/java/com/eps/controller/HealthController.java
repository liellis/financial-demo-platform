package com.eps.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST controller for system health check endpoints in the EPS.
 * Provides endpoints for checking application health, readiness, and liveness
 * for use by orchestration platforms (e.g., Kubernetes probes) and monitoring systems.
 *
 * <p>All endpoints return {@link ResponseEntity} with system status information
 * and response bodies as {@code Map<String, Object>}.</p>
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Controller", description = "System health check and monitoring APIs")
public class HealthController {

    /**
     * Returns comprehensive system information including application metadata,
     * uptime, and JVM resource utilization.
     *
     * @return 200 OK with system health and resource information
     */
    @GetMapping
    @Operation(summary = "Get system health information",
            description = "Returns comprehensive system information including application name, version, uptime, Java version, available processors, and memory usage.")
    public ResponseEntity<?> health() {
        try {
            Runtime runtime = Runtime.getRuntime();
            RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

            long uptimeMillis = runtimeMxBean.getUptime();
            Duration uptime = Duration.ofMillis(uptimeMillis);

            long hours = uptime.toHours();
            long minutes = uptime.toMinutesPart();
            long seconds = uptime.toSecondsPart();
            String uptimeFormatted = String.format("%d hours, %d minutes, %d seconds", hours, minutes, seconds);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("application", "EPS-Java (Electronic Payment System)");
            response.put("version", "1.0.0-SNAPSHOT");
            response.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            response.put("uptime", uptimeFormatted);
            response.put("uptimeMillis", uptimeMillis);
            response.put("javaVersion", System.getProperty("java.version"));
            response.put("javaVendor", System.getProperty("java.vendor"));
            response.put("availableProcessors", runtime.availableProcessors());
            response.put("freeMemory", runtime.freeMemory());
            response.put("totalMemory", runtime.totalMemory());
            response.put("maxMemory", runtime.maxMemory());
            response.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
            response.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
            response.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
            response.put("usedMemoryMB",
                    (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));

            log.debug("Health check returned system information");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving health information: {}", e.getMessage(), e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("error", "Failed to retrieve health information");
            error.put("detail", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Returns the readiness status of the application.
     * Indicates whether the application is ready to accept traffic.
     *
     * @return 200 OK with readiness status
     */
    @GetMapping("/ready")
    @Operation(summary = "Get application readiness status",
            description = "Returns the readiness status of the application. Used by orchestration platforms to determine if the application is ready to accept traffic.")
    public ResponseEntity<?> ready() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("component", "EPS-Java");
        response.put("ready", true);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        log.debug("Readiness check returned: ready=true");

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the liveness status of the application.
     * Indicates whether the application is running and not in a deadlocked state.
     *
     * @return 200 OK with liveness status
     */
    @GetMapping("/live")
    @Operation(summary = "Get application liveness status",
            description = "Returns the liveness status of the application. Used by orchestration platforms to determine if the application is still running.")
    public ResponseEntity<?> live() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("liveness", true);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        log.debug("Liveness check returned: liveness=true");

        return ResponseEntity.ok(response);
    }
}
