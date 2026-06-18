package com.signalfeed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalfeed.exception.AIToolsFetchException;
import com.signalfeed.model.AITool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AIToolsService {

    private static final Logger log = LoggerFactory.getLogger(AIToolsService.class);

    /**
     * Greedy dot-all pattern: matches from the first '{' to the last '}' in the
     * response, tolerating any preamble or trailing text Claude may add despite
     * instructions to return JSON only.
     */
    private static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("\\{[\\s\\S]*\\}", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    /**
     * The built-in web-search tool identifier for the active AI provider
     * (e.g. {@code web_search_20250305} for Anthropic).
     *
     * <p><b>NOTE:</b> Spring AI 1.0.1 does not expose a native API for passing
     * Anthropic's server-side built-in tools (the {@code AnthropicApi.Tool} record
     * has no {@code type} field and the string is absent from the jar). The property
     * is retained here for observability and for future upgrade to a Spring AI
     * version that supports built-in tools natively. Claude will therefore operate
     * from training knowledge rather than live web search in this version.
     */
    @Value("${app.ai.web-search-tool-name:}")
    private String webSearchToolName;

    public AIToolsService(ChatClient chatClient,
                          PromptBuilder promptBuilder,
                          ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Asks the configured AI model to find one trending AI tool and returns it
     * as a parsed, validated {@link AITool}.
     *
     * @return the trending AI tool
     * @throws AIToolsFetchException if the model call fails, returns empty content,
     *                               returns unparseable JSON, or omits required fields
     */
    public AITool fetchTrendingTool() {
        log.info("=== Starting trending AI tool fetch ===");
        logWebSearchToolStatus();

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt   = promptBuilder.buildUserPrompt(LocalDate.now());

        log.debug("System prompt ({} chars)", systemPrompt.length());
        log.debug("User prompt: {}", userPrompt);

        String rawContent = callChatClient(systemPrompt, userPrompt);
        log.debug("Raw AI response ({} chars): {}", rawContent.length(), rawContent);

        String json = extractJsonObject(rawContent);
        log.debug("Extracted JSON ({} chars): {}", json.length(), json);

        AITool tool = deserialize(json);
        validateRequiredFields(tool, json);

        log.info("=== Fetch complete — name='{}', category='{}', link='{}' ===",
                tool.name(), tool.category(),
                tool.link() != null ? tool.link() : "(none)");
        return tool;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void logWebSearchToolStatus() {
        if (webSearchToolName != null && !webSearchToolName.isBlank()) {
            log.warn("Web-search tool '{}' is configured but Spring AI 1.0.1 does not " +
                     "natively pass Anthropic built-in tools via ChatClient. " +
                     "Claude will use training knowledge. " +
                     "Upgrade Spring AI or add native tool support to enable live search.",
                     webSearchToolName);
        } else {
            log.debug("No web-search tool configured; Claude will use training knowledge.");
        }
    }

    private String callChatClient(String systemPrompt, String userPrompt) {
        log.debug("Calling ChatClient...");
        String content;
        try {
            content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("ChatClient call failed — provider={}, error='{}'",
                    chatClient.getClass().getSimpleName(), e.getMessage(), e);
            throw new AIToolsFetchException("ChatClient call failed: " + e.getMessage(), e);
        }

        if (content == null || content.isBlank()) {
            log.error("ChatClient returned null or blank content");
            throw new AIToolsFetchException("AI returned empty response");
        }

        log.debug("ChatClient returned {} chars", content.length());
        return content;
    }

    private String extractJsonObject(String rawContent) {
        // Attempt 1: the entire response IS valid JSON (ideal case)
        String trimmed = rawContent.strip();
        if (trimmed.startsWith("{")) {
            log.debug("Response starts with '{{'; attempting direct parse");
            try {
                objectMapper.readTree(trimmed);   // validate structure only
                log.debug("Direct parse succeeded — no regex extraction needed");
                return trimmed;
            } catch (JsonProcessingException e) {
                log.debug("Direct parse failed ({}); falling back to regex extraction", e.getMessage());
            }
        }

        // Attempt 2: extract the JSON object via regex
        log.debug("Running regex extraction on raw response");
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(rawContent);
        if (!matcher.find()) {
            log.error("No JSON object found in AI response. Response was: {}", rawContent);
            throw new AIToolsFetchException("No JSON object found in AI response");
        }

        String extracted = matcher.group();
        log.debug("Regex extraction succeeded ({} chars extracted from {} total)",
                extracted.length(), rawContent.length());
        return extracted;
    }

    private AITool deserialize(String json) {
        log.debug("Deserializing JSON to AITool");
        try {
            AITool tool = objectMapper.readValue(json, AITool.class);
            log.debug("Deserialization succeeded — raw name='{}'", tool.name());
            return tool;
        } catch (JsonProcessingException e) {
            log.error("JSON deserialization failed — error='{}', json='{}'", e.getMessage(), json, e);
            throw new AIToolsFetchException("Failed to parse AI response as AITool: " + e.getMessage(), e);
        }
    }

    private void validateRequiredFields(AITool tool, String json) {
        log.debug("Validating required fields on AITool");
        List<String> missing = new ArrayList<>();

        if (isBlank(tool.name()))        missing.add("name");
        if (isBlank(tool.category()))    missing.add("category");
        if (isBlank(tool.description())) missing.add("description");
        if (isEmpty(tool.pros()))        missing.add("pros");
        if (isEmpty(tool.cons()))        missing.add("cons");

        if (!missing.isEmpty()) {
            log.error("AITool is missing required fields: {} — json='{}'", missing, json);
            throw new AIToolsFetchException("AITool missing required fields: " + missing);
        }

        log.debug("All required fields present — pros={}, cons={}",
                tool.pros().size(), tool.cons().size());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }
}
