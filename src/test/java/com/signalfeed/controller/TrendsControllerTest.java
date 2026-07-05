package com.signalfeed.controller;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrendsControllerTest {

    @Mock private AIToolsService aiToolsService;
    @Mock private EmailService emailService;

    private MockMvc mockMvc;

    private static final List<AITool> FIVE_TOOLS = List.of(
            new AITool("Perplexity","Research","AI search engine",List.of("P1","P2","P3"),List.of("C1","C2"),"https://perplexity.ai"),
            new AITool("Cursor","Coding","AI code editor",List.of("P1","P2","P3"),List.of("C1","C2"),null),
            new AITool("Midjourney","Image Generation","AI image tool",List.of("P1","P2","P3"),List.of("C1","C2"),"https://midjourney.com"),
            new AITool("Notion AI","Productivity","AI writing assistant",List.of("P1","P2","P3"),List.of("C1","C2"),null),
            new AITool("ElevenLabs","Audio","AI voice synthesis",List.of("P1","P2","P3"),List.of("C1","C2"),"https://elevenlabs.io")
    );

    @BeforeEach
    void setUp() {
        TrendsController controller = new TrendsController(aiToolsService, emailService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── HTTP status ───────────────────────────────────────────────────────────

    @Test
    void getTrends_happyPath_returns200() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"))
               .andExpect(status().isOk());
    }

    @Test
    void getTrends_fetchFails_returns503() throws Exception {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new AIToolsFetchException("AI returned empty response"));

        mockMvc.perform(get("/api/trends"))
               .andExpect(status().isServiceUnavailable());
    }

    // ── Response body — count ─────────────────────────────────────────────────

    @Test
    void getTrends_happyPath_countMatchesToolCount() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"))
               .andExpect(jsonPath("$.count").value(5));
    }

    // ── Response body — emailSent flag ────────────────────────────────────────

    @Test
    void getTrends_happyPath_emailSentIsTrue() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"))
               .andExpect(jsonPath("$.emailSent").value(true));
    }

    @Test
    void getTrends_consolidatedEmailFails_emailSentIsFalse() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);
        doThrow(new EmailSendException("SMTP refused"))
                .when(emailService).sendConsolidated(any());

        mockMvc.perform(get("/api/trends"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.emailSent").value(false));
    }

    @Test
    void getTrends_consolidatedEmailThrowsUnexpectedException_emailSentIsFalse() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);
        doThrow(new RuntimeException("unexpected")).when(emailService).sendConsolidated(any());

        mockMvc.perform(get("/api/trends"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.emailSent").value(false));
    }

    // ── Response body — tools array ───────────────────────────────────────────

    @Test
    void getTrends_happyPath_toolsArrayHasFiveElements() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"))
               .andExpect(jsonPath("$.tools.length()").value(5));
    }

    @Test
    void getTrends_happyPath_firstToolNameIsCorrect() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"))
               .andExpect(jsonPath("$.tools[0].name").value("Perplexity"));
    }

    @Test
    void getTrends_happyPath_toolsContainExpectedFields() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"))
               .andExpect(jsonPath("$.tools[0].category").value("Research"))
               .andExpect(jsonPath("$.tools[0].description").value("AI search engine"))
               .andExpect(jsonPath("$.tools[0].link").value("https://perplexity.ai"));
    }

    @Test
    void getTrends_fetchFails_bodyContainsErrorMessage() throws Exception {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new AIToolsFetchException("No JSON array found in AI response"));

        mockMvc.perform(get("/api/trends"))
               .andExpect(jsonPath("$.error").value("No JSON array found in AI response"));
    }

    // ── Interaction verification ──────────────────────────────────────────────

    @Test
    void getTrends_fetchCalledExactlyOnce() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"));

        verify(aiToolsService, times(1)).fetchTrendingTools();
    }

    @Test
    void getTrends_happyPath_sendConsolidatedCalledOnceWithAllTools() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"));

        verify(emailService, times(1)).sendConsolidated(FIVE_TOOLS);
    }

    @Test
    void getTrends_happyPath_individualSendNeverCalled() throws Exception {
        when(aiToolsService.fetchTrendingTools()).thenReturn(FIVE_TOOLS);

        mockMvc.perform(get("/api/trends"));

        verify(emailService, never()).send(any(AITool.class));
    }

    @Test
    void getTrends_fetchFails_sendConsolidatedNeverCalled() throws Exception {
        when(aiToolsService.fetchTrendingTools())
                .thenThrow(new AIToolsFetchException("parse error"));

        mockMvc.perform(get("/api/trends"));

        verify(emailService, never()).sendConsolidated(any());
    }
}
