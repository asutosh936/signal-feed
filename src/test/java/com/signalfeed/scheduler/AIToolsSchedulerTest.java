package com.signalfeed.scheduler;

import com.signalfeed.exception.AIToolsFetchException;
import com.signalfeed.exception.EmailSendException;
import com.signalfeed.model.AITool;
import com.signalfeed.service.AIToolsService;
import com.signalfeed.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIToolsSchedulerTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private AIToolsService aiToolsService;
    @Mock private EmailService emailService;

    private AIToolsScheduler scheduler;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final AITool SAMPLE_TOOL = new AITool(
            "Perplexity",
            "Research",
            "AI-powered search engine with cited sources",
            List.of("Fast answers", "Cited sources", "Free tier"),
            List.of("Occasional hallucinations", "Limited API access"),
            "https://perplexity.ai"
    );

    @BeforeEach
    void setUp() {
        scheduler = new AIToolsScheduler(aiToolsService, emailService);
    }

    // ── Happy path: all 5 scheduled methods ──────────────────────────────────

    @Test
    void run1_happyPath_fetchesToolAndSendsEmail() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run1();

        verify(aiToolsService, times(1)).fetchTrendingTool();
        verify(emailService, times(1)).send(SAMPLE_TOOL);
    }

    @Test
    void run2_happyPath_fetchesToolAndSendsEmail() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run2();

        verify(aiToolsService, times(1)).fetchTrendingTool();
        verify(emailService, times(1)).send(SAMPLE_TOOL);
    }

    @Test
    void run3_happyPath_fetchesToolAndSendsEmail() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run3();

        verify(aiToolsService, times(1)).fetchTrendingTool();
        verify(emailService, times(1)).send(SAMPLE_TOOL);
    }

    @Test
    void run4_happyPath_fetchesToolAndSendsEmail() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run4();

        verify(aiToolsService, times(1)).fetchTrendingTool();
        verify(emailService, times(1)).send(SAMPLE_TOOL);
    }

    @Test
    void run5_happyPath_fetchesToolAndSendsEmail() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run5();

        verify(aiToolsService, times(1)).fetchTrendingTool();
        verify(emailService, times(1)).send(SAMPLE_TOOL);
    }

    // ── Error: AIToolsFetchException — email must NOT be sent ────────────────

    @Test
    void run1_fetchThrowsAIToolsFetchException_emailIsNotSent() {
        when(aiToolsService.fetchTrendingTool())
                .thenThrow(new AIToolsFetchException("AI returned empty response"));

        scheduler.run1();

        verify(emailService, never()).send(any());
    }

    @Test
    void run1_fetchThrowsAIToolsFetchException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTool())
                .thenThrow(new AIToolsFetchException("parse error"));

        assertThatCode(() -> scheduler.run1()).doesNotThrowAnyException();
    }

    @Test
    void run2_fetchThrowsAIToolsFetchException_emailIsNotSent() {
        when(aiToolsService.fetchTrendingTool())
                .thenThrow(new AIToolsFetchException("no JSON found"));

        scheduler.run2();

        verify(emailService, never()).send(any());
    }

    // ── Error: EmailSendException — must not rethrow ──────────────────────────

    @Test
    void run1_emailThrowsEmailSendException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);
        doThrow(new EmailSendException("SMTP refused"))
                .when(emailService).send(any());

        assertThatCode(() -> scheduler.run1()).doesNotThrowAnyException();
    }

    @Test
    void run3_emailThrowsEmailSendException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);
        doThrow(new EmailSendException("connection timeout"))
                .when(emailService).send(any());

        assertThatCode(() -> scheduler.run3()).doesNotThrowAnyException();
    }

    // ── Error: unexpected exception from fetch — email NOT sent, no rethrow ──

    @Test
    void run1_fetchThrowsUnexpectedException_emailIsNotSent() {
        when(aiToolsService.fetchTrendingTool())
                .thenThrow(new RuntimeException("unexpected NPE"));

        scheduler.run1();

        verify(emailService, never()).send(any());
    }

    @Test
    void run1_fetchThrowsUnexpectedException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTool())
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        assertThatCode(() -> scheduler.run1()).doesNotThrowAnyException();
    }

    // ── Error: unexpected exception from email send — no rethrow ─────────────

    @Test
    void run1_emailThrowsUnexpectedException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);
        doThrow(new RuntimeException("unexpected failure"))
                .when(emailService).send(any());

        assertThatCode(() -> scheduler.run1()).doesNotThrowAnyException();
    }

    // ── Interaction verification ──────────────────────────────────────────────

    @Test
    void run1_happyPath_fetchCalledExactlyOnce() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run1();

        verify(aiToolsService, times(1)).fetchTrendingTool();
    }

    @Test
    void run1_happyPath_sendCalledWithFetchedTool() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run1();

        verify(emailService).send(SAMPLE_TOOL);
    }

    @Test
    void runScheduled_delegatesCorrectly_allFiveRunsAreDistinct() {
        when(aiToolsService.fetchTrendingTool()).thenReturn(SAMPLE_TOOL);

        scheduler.run1();
        scheduler.run2();
        scheduler.run3();
        scheduler.run4();
        scheduler.run5();

        // All 5 runs independently fetch and send
        verify(aiToolsService, times(5)).fetchTrendingTool();
        verify(emailService, times(5)).send(SAMPLE_TOOL);
    }
}
