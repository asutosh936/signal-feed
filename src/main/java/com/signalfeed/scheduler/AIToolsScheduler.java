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

/**
 * Triggers the AI tool discovery + email dispatch pipeline at five fixed times
 * per day.
 *
 * <p>Each {@code @Scheduled} method delegates to a shared private helper so
 * the fetch → send → log sequence is written exactly once. The run identifier
 * (e.g. {@code "run1"}) is threaded through all log lines so failures can be
 * correlated to a specific scheduled slot.
 *
 * <p>All exceptions are caught and logged; none are rethrown. A failure in one
 * run slot never affects subsequent slots.
 */
@Component
public class AIToolsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AIToolsScheduler.class);

    private final AIToolsService aiToolsService;
    private final EmailService emailService;

    public AIToolsScheduler(AIToolsService aiToolsService, EmailService emailService) {
        this.aiToolsService = aiToolsService;
        this.emailService = emailService;
        log.info("AIToolsScheduler initialised — 5 daily runs configured");
    }

    // ── Scheduled entry points ────────────────────────────────────────────────

    /** Run 1 — 08:00 IST (02:30 UTC) */
    @Scheduled(cron = "${scheduler.cron.run1}")
    public void run1() {
        runScheduled("run1");
    }

    /** Run 2 — 11:00 IST (05:30 UTC) */
    @Scheduled(cron = "${scheduler.cron.run2}")
    public void run2() {
        runScheduled("run2");
    }

    /** Run 3 — 14:00 IST (08:30 UTC) */
    @Scheduled(cron = "${scheduler.cron.run3}")
    public void run3() {
        runScheduled("run3");
    }

    /** Run 4 — 17:00 IST (11:30 UTC) */
    @Scheduled(cron = "${scheduler.cron.run4}")
    public void run4() {
        runScheduled("run4");
    }

    /** Run 5 — 20:00 IST (14:30 UTC) */
    @Scheduled(cron = "${scheduler.cron.run5}")
    public void run5() {
        runScheduled("run5");
    }

    // ── Shared pipeline ───────────────────────────────────────────────────────

    /**
     * Executes the full fetch → send pipeline for one scheduled slot.
     *
     * <p>Failure modes and their handling:
     * <ul>
     *   <li>{@link AIToolsFetchException} — AI call failed; email is NOT sent; failure is logged</li>
     *   <li>{@link EmailSendException} — email delivery failed; failure is logged</li>
     *   <li>Any other {@link Exception} — unexpected failure; failure is logged</li>
     * </ul>
     * No exception is rethrown — the scheduler thread must never be killed.
     *
     * @param runId human-readable slot identifier used in all log lines
     */
    void runScheduled(String runId) {
        log.info("=== [{}}] Scheduled run starting at {} ===", runId, LocalDateTime.now());
        LocalDateTime start = LocalDateTime.now();

        try {
            log.debug("[{}] Fetching trending AI tool...", runId);
            AITool tool = aiToolsService.fetchTrendingTool();

            log.info("[{}] Tool fetched — name='{}', category='{}'",
                    runId, tool.name(), tool.category());

            log.debug("[{}] Sending email...", runId);
            emailService.send(tool);

            long elapsedMs = Duration.between(start, LocalDateTime.now()).toMillis();
            log.info("=== [{}] Scheduled run complete — tool='{}', elapsed={}ms ===",
                    runId, tool.name(), elapsedMs);

        } catch (AIToolsFetchException e) {
            log.error("[{}] Scheduled run FAILED — could not fetch trending tool: {}",
                    runId, e.getMessage(), e);

        } catch (EmailSendException e) {
            log.error("[{}] Scheduled run FAILED — tool fetched but email not sent: {}",
                    runId, e.getMessage(), e);

        } catch (Exception e) {
            log.error("[{}] Scheduled run FAILED — unexpected error: {}",
                    runId, e.getMessage(), e);
        }
    }
}
