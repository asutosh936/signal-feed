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

    @Mock private AIToolsService aiToolsService;
    @Mock private EmailService emailService;

    private AIToolsScheduler scheduler;

    private static final List<AITool> FIVE_TOOLS = List.of(
            new AITool("Perplexity","Research","AI search",List.of("P1","P2","P3"),List.of("C1","C2"),"https://perplexity.ai"),
            new AITool("Cursor","Coding","AI editor",List.of("P1","P2","P3"),List.of("C1","C2"),null),
            new AITool("Midjourney","Image Generation","AI images",List.of("P1","P2","P3"),List.of("C1","C2"),"https://midjourney.com"),
            new AITool("Notion AI","Productivity","AI writing",List.of("P1","P2","P3"),List.of("C1","C2"),null),
            new AITool("ElevenLabs","Audio","AI voice",List.of("P1","P2","P3"),List.of("C1","C2"),"https://elevenlabs.io")
    );

    @BeforeEach
    void setUp() {
        scheduler = new AIToolsScheduler(aiToolsService, emailService);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void runDaily_happyPath_fetchesToolsAndSendsOneConsolidatedEmail() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        scheduler.runDaily();

        verify(aiToolsService, times(1)).fetchTrendingTools();
        verify(emailService, times(1)).sendConsolidated(FIVE_TOOLS);
    }

    @Test
    void runDaily_happyPath_doesNotCallIndividualSend() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        scheduler.runDaily();

        verify(emailService, never()).send(any(AITool.class));
    }

    @Test
    void runDaily_happyPath_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);
        assertThatCode(() -> scheduler.runDaily()).doesNotThrowAnyException();
    }

    // ── Fetch failure — consolidated email NOT sent ───────────────────────────

    @Test
    void runDaily_fetchThrowsAIToolsFetchException_consolidatedEmailNotSent() {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new AIToolsFetchException("AI returned empty response"));

        scheduler.runDaily();

        verify(emailService, never()).sendConsolidated(any());
    }

    @Test
    void runDaily_fetchThrowsAIToolsFetchException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new AIToolsFetchException("parse error"));

        assertThatCode(() -> scheduler.runDaily()).doesNotThrowAnyException();
    }

    @Test
    void runDaily_fetchThrowsUnexpectedException_consolidatedEmailNotSent() {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new RuntimeException("network timeout"));

        scheduler.runDaily();

        verify(emailService, never()).sendConsolidated(any());
    }

    @Test
    void runDaily_fetchThrowsUnexpectedException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new IllegalStateException("connection refused"));

        assertThatCode(() -> scheduler.runDaily()).doesNotThrowAnyException();
    }

    // ── Email failure — does not rethrow ──────────────────────────────────────

    @Test
    void runDaily_consolidatedEmailThrowsEmailSendException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);
        doThrow(new EmailSendException("SMTP refused"))
                .when(emailService).sendConsolidated(any());

        assertThatCode(() -> scheduler.runDaily()).doesNotThrowAnyException();
    }

    @Test
    void runDaily_consolidatedEmailThrowsUnexpectedException_doesNotRethrow() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);
        doThrow(new RuntimeException("unexpected failure"))
                .when(emailService).sendConsolidated(any());

        assertThatCode(() -> scheduler.runDaily()).doesNotThrowAnyException();
    }

    // ── Interaction verification ──────────────────────────────────────────────

    @Test
    void runDaily_fetchCalledExactlyOnce() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        scheduler.runDaily();

        verify(aiToolsService, times(1)).fetchTrendingTools();
    }

    @Test
    void runDaily_sendConsolidatedPassesExactFetchedList() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        scheduler.runDaily();

        verify(emailService).sendConsolidated(FIVE_TOOLS);
    }

    @Test
    void runDaily_withEmptyToolList_sendConsolidatedCalledWithEmptyList() {
        when(aiToolsService.fetchTrendingTools()).thenReturn(List.of());

        scheduler.runDaily();

        verify(emailService, times(1)).sendConsolidated(List.of());
    }
}
