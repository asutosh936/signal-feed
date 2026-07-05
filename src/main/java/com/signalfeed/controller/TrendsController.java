package com.signalfeed.controller;

import com.signalfeed.exception.AIToolsFetchException;
import com.signalfeed.model.AITool;
import com.signalfeed.service.AIToolsService;
import com.signalfeed.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-demand trigger for the same 5-tool fetch-and-email pipeline that
 * the scheduler runs automatically at 11:00 AM CST.
 *
 * <pre>
 *   GET /api/trends
 * </pre>
 *
 * <p>Returns a JSON body with the list of tools fetched and the number of
 * emails successfully sent. Individual email failures are logged and skipped
 * but do not affect the HTTP response status — the fetch result is still
 * returned to the caller.
 *
 * <p>If the AI fetch itself fails (e.g. API key issue, parse error), a
 * {@code 503 Service Unavailable} is returned with an error message.
 */
@RestController
@RequestMapping("/api")
public class TrendsController {

    private static final Logger log = LoggerFactory.getLogger(TrendsController.class);

    private final AIToolsService aiToolsService;
    private final EmailService emailService;

    public TrendsController(AIToolsService aiToolsService, EmailService emailService) {
        this.aiToolsService = aiToolsService;
        this.emailService = emailService;
    }

    /**
     * Fetches 5 trending AI tools, sends one spotlight email per tool, and
     * returns the results.
     *
     * @return 200 with {@code { count, emailsSent, tools }} on success,
     *         503 with {@code { error }} if the AI fetch fails
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrends() {
        log.info("=== GET /api/trends — on-demand fetch triggered ===");

        // ── Fetch 5 tools ─────────────────────────────────────────────────────
        List<AITool> tools;
        try {
            tools = aiToolsService.fetchTrendingTools();
            log.info("GET /api/trends — fetched {} tools", tools.size());
        } catch (AIToolsFetchException e) {
            log.error("GET /api/trends — fetch failed: {}", e.getMessage(), e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }

        // ── Send one consolidated email ───────────────────────────────────────
        boolean emailSent = false;
        try {
            emailService.sendConsolidated(tools);
            emailSent = true;
            log.debug("GET /api/trends — consolidated email sent");
        } catch (Exception e) {
            log.error("GET /api/trends — consolidated email failed: {}", e.getMessage(), e);
        }

        log.info("=== GET /api/trends complete — emailSent={}, {} tools ===",
                emailSent, tools.size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", tools.size());
        body.put("emailSent", emailSent);
        body.put("tools", tools);
        return ResponseEntity.ok(body);
    }
}
