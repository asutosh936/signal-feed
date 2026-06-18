package com.signalfeed.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Constructor and accessors ────────────────────────────────────────────

    @Test
    void constructor_storesAllFields() {
        var tool = new AITool("Cursor", "Coding", "AI-powered code editor",
                List.of("P1", "P2", "P3"), List.of("C1", "C2"), "https://cursor.sh");

        assertThat(tool.name()).isEqualTo("Cursor");
        assertThat(tool.category()).isEqualTo("Coding");
        assertThat(tool.description()).isEqualTo("AI-powered code editor");
        assertThat(tool.pros()).containsExactly("P1", "P2", "P3");
        assertThat(tool.cons()).containsExactly("C1", "C2");
        assertThat(tool.link()).isEqualTo("https://cursor.sh");
    }

    @Test
    void constructor_allowsNullLink() {
        var tool = new AITool("Tool", "Coding", "Desc",
                List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);

        assertThat(tool.link()).isNull();
    }

    @Test
    void equalityAndHashCode_areValueBased() {
        var a = new AITool("Tool", "Cat", "Desc",
                List.of("P1", "P2", "P3"), List.of("C1", "C2"), "https://example.com");
        var b = new AITool("Tool", "Cat", "Desc",
                List.of("P1", "P2", "P3"), List.of("C1", "C2"), "https://example.com");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equality_returnsFalseForDifferentNames() {
        var a = new AITool("ToolA", "Cat", "Desc",
                List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);
        var b = new AITool("ToolB", "Cat", "Desc",
                List.of("P1", "P2", "P3"), List.of("C1", "C2"), null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toString_containsFieldValues() {
        var tool = new AITool("Cursor", "Coding", "Desc",
                List.of("P1"), List.of("C1"), null);

        assertThat(tool.toString()).contains("Cursor").contains("Coding");
    }

    // ── Jackson deserialization ──────────────────────────────────────────────

    @Test
    void deserialize_fullJson_mapsAllFields() throws Exception {
        String json = """
                {
                  "name": "Perplexity",
                  "category": "Research",
                  "description": "AI-powered search engine",
                  "pros": ["Fast answers", "Cited sources", "Free tier"],
                  "cons": ["Occasional hallucinations", "Limited API access"],
                  "link": "https://perplexity.ai"
                }
                """;

        AITool tool = MAPPER.readValue(json, AITool.class);

        assertThat(tool.name()).isEqualTo("Perplexity");
        assertThat(tool.category()).isEqualTo("Research");
        assertThat(tool.description()).isEqualTo("AI-powered search engine");
        assertThat(tool.pros()).containsExactly("Fast answers", "Cited sources", "Free tier");
        assertThat(tool.cons()).containsExactly("Occasional hallucinations", "Limited API access");
        assertThat(tool.link()).isEqualTo("https://perplexity.ai");
    }

    @Test
    void deserialize_nullLink_isAllowed() throws Exception {
        String json = """
                {
                  "name": "Tool",
                  "category": "Coding",
                  "description": "Desc",
                  "pros": ["P1", "P2", "P3"],
                  "cons": ["C1", "C2"],
                  "link": null
                }
                """;

        AITool tool = MAPPER.readValue(json, AITool.class);

        assertThat(tool.link()).isNull();
    }

    @Test
    void deserialize_unknownFields_areIgnored() throws Exception {
        String json = """
                {
                  "name": "Tool",
                  "category": "Coding",
                  "description": "Desc",
                  "pros": ["P1", "P2", "P3"],
                  "cons": ["C1", "C2"],
                  "link": null,
                  "unexpectedField": "should be ignored",
                  "anotherExtra": 42
                }
                """;

        AITool tool = MAPPER.readValue(json, AITool.class);

        assertThat(tool.name()).isEqualTo("Tool");
    }

    @Test
    void deserialize_missingLinkField_treatedAsNull() throws Exception {
        String json = """
                {
                  "name": "Tool",
                  "category": "Coding",
                  "description": "Desc",
                  "pros": ["P1", "P2", "P3"],
                  "cons": ["C1", "C2"]
                }
                """;

        AITool tool = MAPPER.readValue(json, AITool.class);

        assertThat(tool.link()).isNull();
    }

    @Test
    void deserialize_emptyProsList_producesEmptyList() throws Exception {
        String json = """
                {
                  "name": "Tool",
                  "category": "Coding",
                  "description": "Desc",
                  "pros": [],
                  "cons": [],
                  "link": null
                }
                """;

        AITool tool = MAPPER.readValue(json, AITool.class);

        assertThat(tool.pros()).isEmpty();
        assertThat(tool.cons()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Coding", "Writing", "Image Generation", "Video", "Productivity"})
    void deserialize_variousCategories_areAccepted(String category) throws Exception {
        String json = String.format("""
                {
                  "name": "Tool",
                  "category": "%s",
                  "description": "Desc",
                  "pros": ["P1", "P2", "P3"],
                  "cons": ["C1", "C2"],
                  "link": null
                }
                """, category);

        AITool tool = MAPPER.readValue(json, AITool.class);

        assertThat(tool.category()).isEqualTo(category);
    }

    // ── Serialization round-trip ─────────────────────────────────────────────

    @Test
    void roundTrip_serializeAndDeserialize_preservesValues() throws Exception {
        var original = new AITool("Cursor", "Coding", "AI code editor",
                List.of("Fast", "Smart", "Integrated"), List.of("Expensive", "Cloud-only"),
                "https://cursor.sh");

        String json = MAPPER.writeValueAsString(original);
        AITool restored = MAPPER.readValue(json, AITool.class);

        assertThat(restored).isEqualTo(original);
    }
}
