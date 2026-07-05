package com.signalfeed.scheduler;

import com.signalfeed.exception.AIToolsFetchException;
import com.signalfeed.exception.EmailSendException;
import com.signalfeed.model.AITool;
import com.signalfeed.service.AIToolsService;
import com.signalfeed.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fires once daily at 11:00 AM CST (17:00 UTC), fetches 5 trending AI tools
 * in a single AI call, then sends one spotlight email per tool.
 *
 * <p>Individual email failures are logged and skipped — one bad send never
 * blocks the remaining tools. The scheduler thread is never killed by an
 * exception from this class.
 *
 * <p>The same pipeline can be triggered on-demand via {@code GET /api/trends}.
 */
@Component
public class AIToolsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AIToolsScheduler.class);

    private final AIToolsService aiToolsService;
    private final EmailService emailService;

    public AIToolsScheduler(AIToolsService aiToolsService, EmailService emailService) {
        this.aiToolsService = aiToolsService;
        this.emailService = emailService;
        log.info("AIToolsScheduler initialised — daily run at 11:00 AM CST (17:00 UTC)");
    }

    // ── Scheduled entry point ─────────────────────────────────────────────────

    /** 11:00 AM CST = 17:00 UTC */
    @Scheduled(cron = "${scheduler.cron.daily}", zone = "${scheduler.timezone:America/Chicago}")
    public void runDaily() {
        runScheduled("daily");
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    /**
     * Fetches 5 trending AI tools and dispatches one email per tool.
     *
     * <p>Failure handling:
     * <ul>
     *   <li>{@link AIToolsFetchException} — fetch failed; no emails sent; logged</li>
     *   <li>{@link EmailSendException} per tool — that tool's email skipped; others continue</li>
     *   <li>Any other {@link Exception} — logged; never rethrown</li>
     * </ul>
     *
     * @param runId human-readable identifier threaded through all log lines
     */
    void runScheduled(String runId) {
        log.info("=== [{}] Scheduled run starting at {} ===", runId, LocalDateTime.now());
        LocalDateTime start = LocalDateTime.now();

        // ── Fetch 5 tools ─────────────────────────────────────────────────────
        List<AITool> tools;
        try {
            log.debug("[{}] Fetching 5 trending AI tools...", runId);
            tools = aiToolsService.fetchTrendingTools();
            log.info("[{}] Fetched {} tools", runId, tools.size());
        } catch (AIToolsFetchException e) {
            log.error("[{}] Scheduled run FAILED — could not fetch trending tools: {}",
                    runId, e.getMessage(), e);
            return;
        } catch (Exception e) {
            log.error("[{}] Scheduled run FAILED — unexpected error during fetch: {}",
                    runId, e.getMessage(), e);
            return;
        }

        // ── Send one consolidated email for all tools ─────────────────────────
        try {
            log.debug("[{}] Sending consolidated email for {} tools...", runId, tools.size());
            emailService.sendConsolidated(tools);
            log.info("[{}] Consolidated email sent for {} tools", runId, tools.size());
        } catch (EmailSendException e) {
            log.error("[{}] Scheduled run FAILED — consolidated email not sent: {}",
                    runId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("[{}] Scheduled run FAILED — unexpected error sending email: {}",
                    runId, e.getMessage(), e);
        }

        long elapsedMs = Duration.between(start, LocalDateTime.now()).toMillis();
        log.info("=== [{}] Scheduled run complete — {}ms elapsed ===", runId, elapsedMs);
    }
}
