package com.signalfeed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
     * response, tolerating any preamble or trailing text the model may add.
     */
    private static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("\\{[\\s\\S]*\\}", Pattern.DOTALL);

    /**
     * Greedy dot-all pattern: matches from the first '[' to the last ']'.
     */
    private static final Pattern JSON_ARRAY_PATTERN =
            Pattern.compile("\\[[\\s\\S]*\\]", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    /**
     * The built-in web-search tool identifier for the active AI provider
     * (e.g. {@code web_search_20250305} for Anthropic).
     *
     * <p><b>NOTE:</b> Spring AI 1.0.1 does not expose a native API for passing
     * Anthropic's server-side built-in tools. The property is retained for
     * observability and future upgrade. A {@code WARN} is logged on every run.
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

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Asks the AI to find one trending AI tool and returns it as a validated
     * {@link AITool}.
     *
     * @return the trending AI tool
     * @throws AIToolsFetchException on empty response, parse failure, or missing fields
     */
    public AITool fetchTrendingTool() {
        log.info("=== Starting single trending AI tool fetch ===");
        logWebSearchToolStatus();

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt   = promptBuilder.buildUserPrompt(LocalDate.now());

        log.debug("System prompt ({} chars), User prompt: {}", systemPrompt.length(), userPrompt);

        String rawContent = callChatClient(systemPrompt, userPrompt);
        log.debug("Raw AI response ({} chars): {}", rawContent.length(), rawContent);

        String json = extractJsonObject(rawContent);
        log.debug("Extracted JSON ({} chars): {}", json.length(), json);

        AITool tool = deserializeSingle(json);
        validateRequiredFields(tool, json);

        log.info("=== Single fetch complete — name='{}', category='{}', link='{}' ===",
                tool.name(), tool.category(),
                tool.link() != null ? tool.link() : "(none)");
        return tool;
    }

    /**
     * Asks the AI to find 5 trending AI tools in a single call and returns them
     * as a validated list.
     *
     * @return list of 5 trending AI tools
     * @throws AIToolsFetchException on empty response, parse failure, or missing fields
     */
    public List<AITool> fetchTrendingTools() {
        log.info("=== Starting batch fetch of 5 trending AI tools ===");
        logWebSearchToolStatus();

        String systemPrompt = promptBuilder.buildSystemPromptForMultiple();
        String userPrompt   = promptBuilder.buildUserPromptForMultiple(LocalDate.now());

        log.debug("System prompt ({} chars), User prompt: {}", systemPrompt.length(), userPrompt);

        String rawContent = callChatClient(systemPrompt, userPrompt);
        log.debug("Raw AI response ({} chars): {}", rawContent.length(), rawContent);

        String json = extractJsonArray(rawContent);
        log.debug("Extracted JSON array ({} chars): {}", json.length(), json);

        List<AITool> tools = deserializeList(json);

        for (int i = 0; i < tools.size(); i++) {
            validateRequiredFields(tools.get(i), "tool[" + i + "]");
        }

        log.info("=== Batch fetch complete — {} tools fetched ===", tools.size());
        return tools;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void logWebSearchToolStatus() {
        if (webSearchToolName != null && !webSearchToolName.isBlank()) {
            log.warn("Web-search tool '{}' is configured but Spring AI 1.0.1 does not " +
                     "natively pass Anthropic built-in tools via ChatClient. " +
                     "The model will use training knowledge. " +
                     "Upgrade Spring AI to enable live search.",
                     webSearchToolName);
        } else {
            log.debug("No web-search tool configured; model will use training knowledge.");
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
            log.error("ChatClient call failed — error='{}'", e.getMessage(), e);
            throw new AIToolsFetchException("ChatClient call failed: " + e.getMessage(), e);
        }

        if (content == null || content.isBlank()) {
            log.error("ChatClient returned null or blank content");
            throw new AIToolsFetchException("AI returned empty response");
        }

        log.debug("ChatClient returned {} chars", content.length());
        return content;
    }

    /** Extracts the first JSON object from the raw response (two-attempt strategy). */
    private String extractJsonObject(String rawContent) {
        String trimmed = rawContent.strip();
        if (trimmed.startsWith("{")) {
            log.debug("Response starts with '{{'; attempting direct parse");
            try {
                objectMapper.readTree(trimmed);
                log.debug("Direct parse succeeded");
                return trimmed;
            } catch (JsonProcessingException e) {
                log.debug("Direct parse failed ({}); falling back to regex", e.getMessage());
            }
        }

        log.debug("Running regex extraction for JSON object");
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(rawContent);
        if (!matcher.find()) {
            log.error("No JSON object found in AI response. Response was: {}", rawContent);
            throw new AIToolsFetchException("No JSON object found in AI response");
        }

        String extracted = matcher.group();
        log.debug("Regex extraction succeeded ({} chars from {} total)",
                extracted.length(), rawContent.length());
        return extracted;
    }

    /** Extracts the first JSON array from the raw response (two-attempt strategy). */
    private String extractJsonArray(String rawContent) {
        String trimmed = rawContent.strip();
        if (trimmed.startsWith("[")) {
            log.debug("Response starts with '['; attempting direct array parse");
            try {
                objectMapper.readTree(trimmed);
                log.debug("Direct array parse succeeded");
                return trimmed;
            } catch (JsonProcessingException e) {
                log.debug("Direct array parse failed ({}); falling back to regex", e.getMessage());
            }
        }

        log.debug("Running regex extraction for JSON array");
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(rawContent);
        if (!matcher.find()) {
            log.error("No JSON array found in AI response. Response was: {}", rawContent);
            throw new AIToolsFetchException("No JSON array found in AI response");
        }

        String extracted = matcher.group();
        log.debug("Regex extraction succeeded ({} chars from {} total)",
                extracted.length(), rawContent.length());
        return extracted;
    }

    private AITool deserializeSingle(String json) {
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

    private List<AITool> deserializeList(String json) {
        log.debug("Deserializing JSON array to List<AITool>");
        try {
            List<AITool> tools = objectMapper.readValue(json, new TypeReference<List<AITool>>() {});
            if (tools == null || tools.isEmpty()) {
                log.error("AI returned an empty tool list");
                throw new AIToolsFetchException("AI returned an empty tool list");
            }
            log.debug("Deserialization succeeded — {} tools", tools.size());
            return tools;
        } catch (JsonProcessingException e) {
            log.error("JSON array deserialization failed — error='{}', json='{}'", e.getMessage(), json, e);
            throw new AIToolsFetchException("Failed to parse AI response as tool list: " + e.getMessage(), e);
        }
    }

    private void validateRequiredFields(AITool tool, String context) {
        log.debug("Validating required fields for {}", context);
        List<String> missing = new ArrayList<>();

        if (isBlank(tool.name()))        missing.add("name");
        if (isBlank(tool.category()))    missing.add("category");
        if (isBlank(tool.description())) missing.add("description");
        if (isEmpty(tool.pros()))        missing.add("pros");
        if (isEmpty(tool.cons()))        missing.add("cons");

        if (!missing.isEmpty()) {
            log.error("AITool [{}] is missing required fields: {}", context, missing);
            throw new AIToolsFetchException("AITool missing required fields: " + missing);
        }

        log.debug("All required fields present for {} — pros={}, cons={}",
                context, tool.pros().size(), tool.cons().size());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }
}
