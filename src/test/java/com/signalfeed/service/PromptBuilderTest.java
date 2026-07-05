package com.signalfeed.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    // ── buildSystemPrompt ────────────────────────────────────────────────────

    @Test
    void buildSystemPrompt_containsAllJsonSchemaFields() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt)
                .contains("\"name\"")
                .contains("\"category\"")
                .contains("\"description\"")
                .contains("\"pros\"")
                .contains("\"cons\"")
                .contains("\"link\"");
    }

    @Test
    void buildSystemPrompt_requiresJsonOnlyResponse() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt.toUpperCase())
                .contains("ONLY")
                .contains("JSON");
    }

    @Test
    void buildSystemPrompt_prohibitsMarkdownFences() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("markdown fences");
    }

    @Test
    void buildSystemPrompt_specifies3Pros() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("exactly 3");
    }

    @Test
    void buildSystemPrompt_specifies2Cons() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("exactly 2");
    }

    @Test
    void buildSystemPrompt_mentionsWebSearch() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt.toLowerCase()).contains("web search");
    }

    @Test
    void buildSystemPrompt_mentionsSocialPlatforms() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).containsAnyOf("Twitter", "Reddit", "LinkedIn", "Hacker News", "Product Hunt");
    }

    @Test
    void buildSystemPrompt_isIdempotent() {
        String first = promptBuilder.buildSystemPrompt();
        String second = promptBuilder.buildSystemPrompt();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void buildSystemPrompt_isNotBlank() {
        assertThat(promptBuilder.buildSystemPrompt()).isNotBlank();
    }

    // ── buildUserPrompt ──────────────────────────────────────────────────────

    @Test
    void buildUserPrompt_containsFullFormattedDate() {
        LocalDate date = LocalDate.of(2026, 6, 17);
        String prompt = promptBuilder.buildUserPrompt(date);

        assertThat(prompt).contains("Wednesday, June 17, 2026");
    }

    @Test
    void buildUserPrompt_instructsWebSearch() {
        String prompt = promptBuilder.buildUserPrompt(LocalDate.now());

        assertThat(prompt).contains("Search the web");
    }

    @Test
    void buildUserPrompt_instructsReturnJsonOnly() {
        String prompt = promptBuilder.buildUserPrompt(LocalDate.now());

        assertThat(prompt.toUpperCase()).contains("JSON");
        assertThat(prompt.toUpperCase()).contains("ONLY");
    }

    @Test
    void buildUserPrompt_containsYear() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        String prompt = promptBuilder.buildUserPrompt(date);

        assertThat(prompt).contains("2026");
    }

    @Test
    void buildUserPrompt_isNotBlank() {
        assertThat(promptBuilder.buildUserPrompt(LocalDate.now())).isNotBlank();
    }

    @ParameterizedTest(name = "{0} -> contains {2}")
    @CsvSource({
            "2026-01-01, Thursday January 1 2026,  January",
            "2026-03-15, Sunday March 15 2026,     March",
            "2026-07-04, Saturday July 4 2026,     July",
            "2026-11-30, Monday November 30 2026,  November",
            "2026-12-31, Thursday December 31 2026,December"
    })
    void buildUserPrompt_formatsMonthNameCorrectly(String dateStr, String ignored, String expectedMonth) {
        LocalDate date = LocalDate.parse(dateStr);
        String prompt = promptBuilder.buildUserPrompt(date);

        assertThat(prompt).contains(expectedMonth);
    }

    @ParameterizedTest(name = "{0} -> day-of-week {2}")
    @CsvSource({
            "2026-06-15, Monday,    Monday",
            "2026-06-16, Tuesday,   Tuesday",
            "2026-06-17, Wednesday, Wednesday",
            "2026-06-18, Thursday,  Thursday",
            "2026-06-19, Friday,    Friday",
            "2026-06-20, Saturday,  Saturday",
            "2026-06-21, Sunday,    Sunday"
    })
    void buildUserPrompt_includesCorrectDayOfWeek(String dateStr, String ignored, String expectedDay) {
        LocalDate date = LocalDate.parse(dateStr);
        String prompt = promptBuilder.buildUserPrompt(date);

        assertThat(prompt).contains(expectedDay);
    }

    @Test
    void buildUserPrompt_leapDay_formatsCorrectly() {
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        String prompt = promptBuilder.buildUserPrompt(leapDay);

        assertThat(prompt).contains("February").contains("29").contains("2024");
    }

    @Test
    void buildUserPrompt_differentDatesProduceDifferentPrompts() {
        String jan = promptBuilder.buildUserPrompt(LocalDate.of(2026, 1, 1));
        String dec = promptBuilder.buildUserPrompt(LocalDate.of(2026, 12, 31));

        assertThat(jan).isNotEqualTo(dec);
    }

    // ── buildSystemPromptForMultiple ──────────────────────────────────────────

    @Test
    void buildSystemPromptForMultiple_isNotBlank() {
        assertThat(promptBuilder.buildSystemPromptForMultiple()).isNotBlank();
    }

    @Test
    void buildSystemPromptForMultiple_mentionsFiveTools() {
        String prompt = promptBuilder.buildSystemPromptForMultiple();
        assertThat(prompt).contains("5");
    }

    @Test
    void buildSystemPromptForMultiple_requiresJsonArrayResponse() {
        String prompt = promptBuilder.buildSystemPromptForMultiple();
        // Must describe a JSON array (opening bracket) as the expected output
        assertThat(prompt).containsAnyOf("JSON array", "array");
    }

    @Test
    void buildSystemPromptForMultiple_containsAllJsonSchemaFields() {
        String prompt = promptBuilder.buildSystemPromptForMultiple();
        assertThat(prompt)
                .contains("\"name\"")
                .contains("\"category\"")
                .contains("\"description\"")
                .contains("\"pros\"")
                .contains("\"cons\"")
                .contains("\"link\"");
    }

    @Test
    void buildSystemPromptForMultiple_requiresJsonOnlyResponse() {
        String prompt = promptBuilder.buildSystemPromptForMultiple();
        assertThat(prompt.toUpperCase()).contains("ONLY");
    }

    @Test
    void buildSystemPromptForMultiple_prohibitsMarkdownFences() {
        String prompt = promptBuilder.buildSystemPromptForMultiple();
        assertThat(prompt).contains("markdown fences");
    }

    @Test
    void buildSystemPromptForMultiple_isIdempotent() {
        assertThat(promptBuilder.buildSystemPromptForMultiple())
                .isEqualTo(promptBuilder.buildSystemPromptForMultiple());
    }

    @Test
    void buildSystemPromptForMultiple_isDifferentFromSingleToolPrompt() {
        assertThat(promptBuilder.buildSystemPromptForMultiple())
                .isNotEqualTo(promptBuilder.buildSystemPrompt());
    }

    // ── buildUserPromptForMultiple ────────────────────────────────────────────

    @Test
    void buildUserPromptForMultiple_containsFormattedDate() {
        LocalDate date = LocalDate.of(2026, 6, 18);
        String prompt = promptBuilder.buildUserPromptForMultiple(date);
        assertThat(prompt).contains("Thursday, June 18, 2026");
    }

    @Test
    void buildUserPromptForMultiple_mentionsFiveTools() {
        String prompt = promptBuilder.buildUserPromptForMultiple(LocalDate.now());
        assertThat(prompt).contains("5");
    }

    @Test
    void buildUserPromptForMultiple_instructsReturnJsonOnly() {
        String prompt = promptBuilder.buildUserPromptForMultiple(LocalDate.now());
        assertThat(prompt.toUpperCase()).contains("ONLY");
    }

    @Test
    void buildUserPromptForMultiple_differentDatesProduceDifferentPrompts() {
        String jan = promptBuilder.buildUserPromptForMultiple(LocalDate.of(2026, 1, 1));
        String dec = promptBuilder.buildUserPromptForMultiple(LocalDate.of(2026, 12, 31));
        assertThat(jan).isNotEqualTo(dec);
    }

    @Test
    void buildUserPromptForMultiple_isDifferentFromSingleToolPrompt() {
        LocalDate date = LocalDate.now();
        assertThat(promptBuilder.buildUserPromptForMultiple(date))
                .isNotEqualTo(promptBuilder.buildUserPrompt(date));
    }
}
