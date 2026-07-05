package com.signalfeed.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds system and user prompts for AI tool discovery.
 *
 * <p>Two prompt pairs are provided:
 * <ul>
 *   <li><b>Single-tool</b> — returns a JSON object for one tool</li>
 *   <li><b>Multi-tool</b> — returns a JSON array of 5 distinct tools</li>
 * </ul>
 */
@Component
public class PromptBuilder {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    // ── Single-tool prompts ───────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
            You are an AI tool researcher with access to real-time web search.
            Your job is to find ONE trending AI tool being discussed right now
            across social media platforms such as X/Twitter, Reddit, LinkedIn,
            Product Hunt, and Hacker News.

            You must respond with ONLY a valid JSON object — no markdown fences,
            no preamble, no explanation. The JSON must match this exact schema:

            {
              "name":        string,
              "category":    string,
              "description": string,
              "pros": [string, string, string],
              "cons": [string, string],
              "link":        string | null
            }

            Field rules:
            - "name": the tool's official name
            - "category": one of "Coding", "Writing", "Image Generation", "Video", "Audio", "Productivity", "Research", "Data", or another short label
            - "description": what the tool does — maximum 120 characters
            - "pros": exactly 3 specific pros, each under 80 characters
            - "cons": exactly 2 specific cons, each under 80 characters
            - "link": the tool's official URL, or null if unavailable
            """;

    private static final String USER_PROMPT_TEMPLATE =
            "Today is %s. Search the web and find ONE AI tool that is trending right now. " +
            "Choose a tool you have not mentioned recently. Return ONLY the JSON object.";

    // ── Multi-tool prompts (5 tools in one call) ──────────────────────────────

    private static final String SYSTEM_PROMPT_MULTIPLE = """
            You are an AI tool researcher with access to real-time web search.
            Your job is to find 5 distinct AI tools that are currently trending
            across social media platforms such as X/Twitter, Reddit, LinkedIn,
            Product Hunt, and Hacker News.

            You must respond with ONLY a valid JSON array containing exactly 5 objects —
            no markdown fences, no preamble, no explanation. Each object must match
            this exact schema:

            {
              "name":        string,
              "category":    string,
              "description": string,
              "pros": [string, string, string],
              "cons": [string, string],
              "link":        string | null
            }

            Field rules:
            - "name": the tool's official name
            - "category": one of "Coding", "Writing", "Image Generation", "Video", "Audio", "Productivity", "Research", "Data", or another short label
            - "description": what the tool does — maximum 120 characters
            - "pros": exactly 3 specific pros, each under 80 characters
            - "cons": exactly 2 specific cons, each under 80 characters
            - "link": the tool's official URL, or null if unavailable
            - All 5 tools must be distinct — no duplicates
            - Cover different categories where possible
            """;

    private static final String USER_PROMPT_MULTIPLE_TEMPLATE =
            "Today is %s. Search the web and find 5 distinct AI tools that are trending right now. " +
            "Cover different categories (e.g. Coding, Writing, Research, Image, Productivity) where possible. " +
            "Return ONLY the JSON array of exactly 5 objects.";

    // ── Public API ────────────────────────────────────────────────────────────

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(LocalDate date) {
        return String.format(USER_PROMPT_TEMPLATE, date.format(DATE_FORMATTER));
    }

    public String buildSystemPromptForMultiple() {
        return SYSTEM_PROMPT_MULTIPLE;
    }

    public String buildUserPromptForMultiple(LocalDate date) {
        return String.format(USER_PROMPT_MULTIPLE_TEMPLATE, date.format(DATE_FORMATTER));
    }
}
