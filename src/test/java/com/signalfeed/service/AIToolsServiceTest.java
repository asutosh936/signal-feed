package com.signalfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalfeed.exception.AIToolsFetchException;
import com.signalfeed.model.AITool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIToolsServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private PromptBuilder promptBuilder;

    private AIToolsService service;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT = "You are an AI researcher.";
    private static final String USER_PROMPT   = "Today is Wednesday, June 18, 2026. Find a tool.";

    private static final String VALID_JSON = """
            {
              "name": "Perplexity",
              "category": "Research",
              "description": "AI-powered search engine with cited sources",
              "pros": ["Fast answers", "Cited sources", "Free tier"],
              "cons": ["Occasional hallucinations", "Limited API access"],
              "link": "https://perplexity.ai"
            }
            """;

    @BeforeEach
    void setUp() {
        service = new AIToolsService(chatClient, promptBuilder, new ObjectMapper());
        ReflectionTestUtils.setField(service, "webSearchToolName", "web_search_20250305");

        // Default prompt-builder stubs
        when(promptBuilder.buildSystemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(promptBuilder.buildUserPrompt(any(LocalDate.class))).thenReturn(USER_PROMPT);

        // Wire the fluent ChatClient chain
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    // ── Happy-path: valid JSON response ─────────────────────────────────────

    @Test
    void fetchTrendingTool_validJson_returnsCorrectAITool() {
        when(callResponseSpec.content()).thenReturn(VALID_JSON);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Perplexity");
        assertThat(tool.category()).isEqualTo("Research");
        assertThat(tool.description()).isEqualTo("AI-powered search engine with cited sources");
        assertThat(tool.pros()).containsExactly("Fast answers", "Cited sources", "Free tier");
        assertThat(tool.cons()).containsExactly("Occasional hallucinations", "Limited API access");
        assertThat(tool.link()).isEqualTo("https://perplexity.ai");
    }

    @Test
    void fetchTrendingTool_validJson_withNullLink_allowed() {
        String json = """
                {
                  "name": "Cursor",
                  "category": "Coding",
                  "description": "AI-powered code editor",
                  "pros": ["Smart completions", "Tab to accept", "Multi-file edits"],
                  "cons": ["Paid tier required", "Cloud dependency"],
                  "link": null
                }
                """;
        when(callResponseSpec.content()).thenReturn(json);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Cursor");
        assertThat(tool.link()).isNull();
    }

    @Test
    void fetchTrendingTool_unknownFieldsInJson_areIgnored() {
        String json = """
                {
                  "name": "Cursor",
                  "category": "Coding",
                  "description": "AI-powered code editor",
                  "pros": ["P1", "P2", "P3"],
                  "cons": ["C1", "C2"],
                  "link": null,
                  "unexpectedField": "should be ignored",
                  "anotherExtra": 99
                }
                """;
        when(callResponseSpec.content()).thenReturn(json);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Cursor");
    }

    // ── JSON extraction: preamble / trailing text ────────────────────────────

    @Test
    void fetchTrendingTool_jsonWithPreamble_extractsSuccessfully() {
        String responseWithPreamble = "Here is the trending tool:\n" + VALID_JSON;
        when(callResponseSpec.content()).thenReturn(responseWithPreamble);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Perplexity");
    }

    @Test
    void fetchTrendingTool_jsonWithTrailingText_extractsSuccessfully() {
        String responseWithTrailing = VALID_JSON + "\nI hope this helps!";
        when(callResponseSpec.content()).thenReturn(responseWithTrailing);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Perplexity");
    }

    @Test
    void fetchTrendingTool_jsonWithBothPreambleAndTrailing_extractsSuccessfully() {
        String wrapped = "Sure! " + VALID_JSON.strip() + " Let me know if you need more.";
        when(callResponseSpec.content()).thenReturn(wrapped);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Perplexity");
    }

    @Test
    void fetchTrendingTool_jsonWrappedInMarkdownFences_extractsSuccessfully() {
        String fenced = "```json\n" + VALID_JSON.strip() + "\n```";
        when(callResponseSpec.content()).thenReturn(fenced);

        AITool tool = service.fetchTrendingTool();

        assertThat(tool.name()).isEqualTo("Perplexity");
    }

    // ── Error: empty / blank / null response ─────────────────────────────────

    @Test
    void fetchTrendingTool_emptyResponse_throwsAIToolsFetchException() {
        when(callResponseSpec.content()).thenReturn("");

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void fetchTrendingTool_blankResponse_throwsAIToolsFetchException() {
        when(callResponseSpec.content()).thenReturn("   \n\t  ");

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void fetchTrendingTool_nullResponse_throwsAIToolsFetchException() {
        when(callResponseSpec.content()).thenReturn(null);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("empty");
    }

    // ── Error: no JSON in response ────────────────────────────────────────────

    @Test
    void fetchTrendingTool_noJsonObject_throwsAIToolsFetchException() {
        when(callResponseSpec.content()).thenReturn("I found a great tool but forgot to format it.");

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("No JSON object found");
    }

    // ── Error: malformed JSON ────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\": \"Tool\", broken",
            "{\"name\": [unclosed}",
            "{ invalid : json }",
            "{\"name\": \"Tool\" \"category\": \"Coding\"}"  // missing comma
    })
    void fetchTrendingTool_malformedJson_throwsAIToolsFetchException(String malformed) {
        when(callResponseSpec.content()).thenReturn(malformed);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class);
    }

    // ── Error: missing required fields ───────────────────────────────────────

    @Test
    void fetchTrendingTool_missingName_throwsAIToolsFetchException() {
        String json = buildJson(null, "Coding", "Desc", List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("name");
    }

    @Test
    void fetchTrendingTool_blankName_throwsAIToolsFetchException() {
        String json = buildJson("  ", "Coding", "Desc", List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("name");
    }

    @Test
    void fetchTrendingTool_missingCategory_throwsAIToolsFetchException() {
        String json = buildJson("Tool", null, "Desc", List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("category");
    }

    @Test
    void fetchTrendingTool_missingDescription_throwsAIToolsFetchException() {
        String json = buildJson("Tool", "Coding", null, List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("description");
    }

    @Test
    void fetchTrendingTool_emptyPros_throwsAIToolsFetchException() {
        String json = buildJson("Tool", "Coding", "Desc", List.of(), List.of("C1", "C2"), null);
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("pros");
    }

    @Test
    void fetchTrendingTool_emptyCons_throwsAIToolsFetchException() {
        String json = buildJson("Tool", "Coding", "Desc", List.of("P1", "P2", "P3"), List.of(), null);
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("cons");
    }

    @Test
    void fetchTrendingTool_multipleMissingFields_messageListsAll() {
        // Only name and pros — missing category, description, cons
        String json = """
                {
                  "name": "Tool",
                  "pros": ["P1"],
                  "link": null
                }
                """;
        when(callResponseSpec.content()).thenReturn(json);

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContainingAll("category", "description", "cons");
    }

    // ── Error: ChatClient throws ─────────────────────────────────────────────

    @Test
    void fetchTrendingTool_chatClientThrowsRuntimeException_wrapsInAIToolsFetchException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("API timeout"));

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .hasMessageContaining("ChatClient call failed")
                .cause()
                .hasMessageContaining("API timeout");
    }

    @Test
    void fetchTrendingTool_chatClientThrowsIllegalState_wrapsInAIToolsFetchException() {
        when(requestSpec.call()).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> service.fetchTrendingTool())
                .isInstanceOf(AIToolsFetchException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Interaction verification ──────────────────────────────────────────────

    @Test
    void fetchTrendingTool_passesSystemPromptToClient() {
        when(callResponseSpec.content()).thenReturn(VALID_JSON);

        service.fetchTrendingTool();

        verify(requestSpec).system(SYSTEM_PROMPT);
    }

    @Test
    void fetchTrendingTool_passesUserPromptToClient() {
        when(callResponseSpec.content()).thenReturn(VALID_JSON);

        service.fetchTrendingTool();

        verify(requestSpec).user(USER_PROMPT);
    }

    @Test
    void fetchTrendingTool_callsPromptBuilderWithTodaysDate() {
        when(callResponseSpec.content()).thenReturn(VALID_JSON);

        service.fetchTrendingTool();

        verify(promptBuilder).buildUserPrompt(LocalDate.now());
    }

    @Test
    void fetchTrendingTool_callsPromptChainExactlyOnce() {
        when(callResponseSpec.content()).thenReturn(VALID_JSON);

        service.fetchTrendingTool();

        verify(chatClient, times(1)).prompt();
        verify(requestSpec, times(1)).call();
        verify(callResponseSpec, times(1)).content();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal JSON string for the given field values. Null values are
     * serialized as JSON {@code null}; blank strings are serialized as quoted blanks.
     */
    private String buildJson(String name, String category, String description,
                             List<String> pros, List<String> cons, String link) {
        ObjectMapper m = new ObjectMapper();
        try {
            return m.writeValueAsString(new AITool(name, category, description, pros, cons, link));
        } catch (Exception e) {
            throw new RuntimeException("Test helper serialization failed", e);
        }
    }
}
