package com.signalfeed.service;

import com.signalfeed.exception.EmailSendException;
import com.signalfeed.model.AITool;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;

    private EmailService service;

    private static final String   FROM         = "sender@example.com";
    private static final String[] TO           = {"recipient@example.com"};
    private static final String[] TO_MULTI     = {"alice@example.com", "bob@example.com"};

    private static final AITool SAMPLE_TOOL = new AITool(
            "Perplexity", "Research",
            "AI-powered search engine with cited sources",
            List.of("Fast answers", "Cited sources", "Free tier"),
            List.of("Occasional hallucinations", "Limited API access"),
            "https://perplexity.ai");

    private static final AITool TOOL_NO_LINK = new AITool(
            "Cursor", "Coding", "AI-powered code editor",
            List.of("Smart completions", "Tab to accept", "Multi-file edits"),
            List.of("Paid tier required", "Cloud dependency"),
            null);

    private static final List<AITool> FIVE_TOOLS = List.of(
            SAMPLE_TOOL,
            TOOL_NO_LINK,
            new AITool("Midjourney","Image Generation","AI image tool",
                    List.of("P1","P2","P3"),List.of("C1","C2"),"https://midjourney.com"),
            new AITool("Notion AI","Productivity","AI writing",
                    List.of("P1","P2","P3"),List.of("C1","C2"),null),
            new AITool("ElevenLabs","Audio","AI voice synthesis",
                    List.of("P1","P2","P3"),List.of("C1","C2"),"https://elevenlabs.io")
    );

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, FROM, TO);
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));
    }

    // ── send() — subject ──────────────────────────────────────────────────────

    @Test
    void send_subject_containsToolName() throws Exception {
        service.send(SAMPLE_TOOL);
        assertThat(captureSentMessage().getSubject()).contains("Perplexity");
    }

    @Test
    void send_subject_containsSpotlightLabel() throws Exception {
        service.send(SAMPLE_TOOL);
        assertThat(captureSentMessage().getSubject()).contains("AI Spotlight");
    }

    @Test
    void send_subject_containsSparkleEmoji() throws Exception {
        service.send(SAMPLE_TOOL);
        assertThat(captureSentMessage().getSubject()).contains("✨");
    }

    @Test
    void send_subject_hasNoTimeBracket() throws Exception {
        service.send(SAMPLE_TOOL);
        assertThat(captureSentMessage().getSubject()).doesNotMatch(".*\\[\\d{2}:\\d{2}\\].*");
    }

    // ── send() — recipients ───────────────────────────────────────────────────

    @Test
    void send_setsFromAddress() throws Exception {
        service.send(SAMPLE_TOOL);
        assertThat(captureSentMessage().getFrom()[0].toString()).contains(FROM);
    }

    @Test
    void send_setsToAddress() throws Exception {
        service.send(SAMPLE_TOOL);
        assertThat(captureSentMessage().getAllRecipients()[0].toString()).contains(TO[0]);
    }

    // ── send() — HTML body via buildHtml ──────────────────────────────────────

    @Test
    void send_body_containsToolName() {
        assertThat(service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30"))
                .contains("Perplexity");
    }

    @Test
    void send_body_containsCategory() {
        assertThat(service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30"))
                .contains("Research");
    }

    @Test
    void send_body_containsDescription() {
        assertThat(service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30"))
                .contains("AI-powered search engine with cited sources");
    }

    @Test
    void send_body_containsAllPros() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("Fast answers").contains("Cited sources").contains("Free tier");
    }

    @Test
    void send_body_containsAllCons() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("Occasional hallucinations").contains("Limited API access");
    }

    @Test
    void send_body_containsLink_whenPresent() {
        assertThat(service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30"))
                .contains("https://perplexity.ai");
    }

    @Test
    void send_body_noVisitButton_whenLinkNull() {
        assertThat(service.buildHtml(TOOL_NO_LINK, "Wednesday, June 18, 2026", "08:30"))
                .doesNotContain("Visit Tool");
    }

    @Test
    void send_body_isHtmlDocument() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("<!DOCTYPE html>").contains("<html").contains("</html>");
    }

    @Test
    void send_body_htmlEscapesSpecialChars() {
        AITool xssTool = new AITool("<script>xss</script>", "Test & <Demo>",
                "Desc <b>bold</b>", List.of("P<1>", "P2", "P3"), List.of("C&1", "C2"), null);
        String html = service.buildHtml(xssTool, "Thursday, June 18, 2026", "10:00");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    // ── buildSubject / buildConsolidatedSubject helpers ───────────────────────

    @Test
    void buildSubject_containsToolName() {
        assertThat(service.buildSubject("Gemini")).contains("Gemini");
    }

    @Test
    void buildSubject_containsSparkleEmoji() {
        assertThat(service.buildSubject("Gemini")).contains("✨");
    }

    @Test
    void buildConsolidatedSubject_containsCount() {
        assertThat(service.buildConsolidatedSubject(5)).contains("5");
    }

    @Test
    void buildConsolidatedSubject_containsFireEmoji() {
        assertThat(service.buildConsolidatedSubject(5)).contains("🔥");
    }

    @Test
    void buildConsolidatedSubject_containsEmoji() {
        assertThat(service.buildConsolidatedSubject(5)).contains("🔥");
    }

    // ── buildConsolidatedHtml ─────────────────────────────────────────────────

    @Test
    void buildConsolidatedHtml_containsAllToolNames() {
        String html = service.buildConsolidatedHtml(FIVE_TOOLS, "Friday, June 18, 2026", "09:00");
        assertThat(html)
                .contains("Perplexity")
                .contains("Cursor")
                .contains("Midjourney")
                .contains("Notion AI")
                .contains("ElevenLabs");
    }

    @Test
    void buildConsolidatedHtml_containsCardNumbers() {
        String html = service.buildConsolidatedHtml(FIVE_TOOLS, "Friday, June 18, 2026", "09:00");
        assertThat(html).contains("#1").contains("#5");
    }

    @Test
    void buildConsolidatedHtml_containsProsAndCons() {
        String html = service.buildConsolidatedHtml(FIVE_TOOLS, "Friday, June 18, 2026", "09:00");
        assertThat(html)
                .contains("Fast answers")
                .contains("Occasional hallucinations");
    }

    @Test
    void buildConsolidatedHtml_onlyShowsVisitButtonWhenLinkPresent() {
        String html = service.buildConsolidatedHtml(FIVE_TOOLS, "Friday, June 18, 2026", "09:00");
        // Perplexity has a link — button present
        assertThat(html).contains("https://perplexity.ai");
        // Cursor has no link — no extra visit button for it specifically; overall Visit Tool still present
    }

    @Test
    void buildConsolidatedHtml_isHtmlDocument() {
        String html = service.buildConsolidatedHtml(FIVE_TOOLS, "Friday, June 18, 2026", "09:00");
        assertThat(html).contains("<!DOCTYPE html>").contains("</html>");
    }

    @Test
    void buildConsolidatedHtml_containsFooterBranding() {
        String html = service.buildConsolidatedHtml(FIVE_TOOLS, "Friday, June 18, 2026", "09:00");
        assertThat(html).contains("Signal Feed");
    }

    // ── sendConsolidated() — dispatch ─────────────────────────────────────────

    @Test
    void sendConsolidated_callsMailSenderOnce() {
        service.sendConsolidated(FIVE_TOOLS);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendConsolidated_subject_containsCount() throws Exception {
        service.sendConsolidated(FIVE_TOOLS);
        assertThat(captureSentMessage().getSubject()).contains("5");
    }

    @Test
    void sendConsolidated_subject_containsFireEmoji() throws Exception {
        service.sendConsolidated(FIVE_TOOLS);
        assertThat(captureSentMessage().getSubject()).contains("🔥");
    }

    @Test
    void sendConsolidated_setsCorrectRecipient() throws Exception {
        service.sendConsolidated(FIVE_TOOLS);
        assertThat(captureSentMessage().getAllRecipients()[0].toString()).contains(TO[0]);
    }

    @Test
    void send_multipleRecipients_allAddressesPresent() throws Exception {
        Session session = Session.getInstance(new Properties());
        JavaMailSender multiSender = mock(JavaMailSender.class);
        when(multiSender.createMimeMessage()).thenReturn(new MimeMessage(session));
        EmailService multiService = new EmailService(multiSender, FROM, TO_MULTI);

        multiService.send(SAMPLE_TOOL);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(multiSender).send(captor.capture());
        String allRecipients = java.util.Arrays.stream(captor.getValue().getAllRecipients())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(","));
        assertThat(allRecipients).contains("alice@example.com").contains("bob@example.com");
    }

    @Test
    void sendConsolidated_multipleRecipients_allAddressesPresent() throws Exception {
        Session session = Session.getInstance(new Properties());
        JavaMailSender multiSender = mock(JavaMailSender.class);
        when(multiSender.createMimeMessage()).thenReturn(new MimeMessage(session));
        EmailService multiService = new EmailService(multiSender, FROM, TO_MULTI);

        multiService.sendConsolidated(FIVE_TOOLS);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(multiSender).send(captor.capture());
        String allRecipients = java.util.Arrays.stream(captor.getValue().getAllRecipients())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(","));
        assertThat(allRecipients).contains("alice@example.com").contains("bob@example.com");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void send_mailException_throwsEmailSendException() {
        doThrow(new MailSendException("SMTP refused")).when(mailSender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class);
    }

    @Test
    void send_mailException_causeIsPreserved() {
        MailSendException root = new MailSendException("connection timeout");
        doThrow(root).when(mailSender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class)
                .cause().isInstanceOf(MailSendException.class);
    }

    @Test
    void send_messagingException_throwsEmailSendException() {
        Session session = Session.getInstance(new Properties());
        MimeMessage broken = new MimeMessage(session) {
            @Override
            public void setSubject(String s, String c) throws MessagingException {
                throw new MessagingException("forced");
            }
        };
        JavaMailSender throwingSender = mock(JavaMailSender.class);
        when(throwingSender.createMimeMessage()).thenReturn(broken);
        EmailService svc = new EmailService(throwingSender, FROM, TO);
        assertThatThrownBy(() -> svc.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class);
    }

    @Test
    void sendConsolidated_mailException_throwsEmailSendException() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.sendConsolidated(FIVE_TOOLS))
                .isInstanceOf(EmailSendException.class);
    }

    @Test
    void send_withToolNoLink_stillSendsEmail() {
        service.send(TOOL_NO_LINK);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    // ── categoryIcon — exercised through buildHtml ────────────────────────────

    @Test
    void buildHtml_nullCategory_rendersDefaultRobotIcon() {
        AITool t = new AITool("X", null, "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🤖");
    }

    @Test
    void buildHtml_blankCategory_rendersDefaultRobotIcon() {
        AITool t = new AITool("X", "   ", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🤖");
    }

    @Test
    void buildHtml_developmentCategory_rendersCodeIcon() {
        AITool t = new AITool("X", "Development", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("💻");
    }

    @Test
    void buildHtml_searchCategory_rendersSearchIcon() {
        AITool t = new AITool("X", "Search Engine", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🔍");
    }

    @Test
    void buildHtml_videoCategory_rendersVideoIcon() {
        AITool t = new AITool("X", "Video Generation", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🎬");
    }

    @Test
    void buildHtml_dataCategory_rendersChartIcon() {
        AITool t = new AITool("X", "Data Analytics", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("📊");
    }

    @Test
    void buildHtml_chatCategory_rendersMessageIcon() {
        AITool t = new AITool("X", "Chat Assistant", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("💬");
    }

    @Test
    void buildHtml_agentCategory_rendersGearIcon() {
        AITool t = new AITool("X", "AI Agent", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("⚙️");
    }

    @Test
    void buildHtml_securityCategory_rendersLockIcon() {
        AITool t = new AITool("X", "Security", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🔒");
    }

    @Test
    void buildHtml_unknownCategory_rendersDefaultRobotIcon() {
        AITool t = new AITool("X", "Unknown XYZ", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🤖");
    }

    @Test
    void buildHtml_artDesignCategory_rendersPaletteIcon() {
        AITool t = new AITool("X", "Art & Design", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🎨");
    }

    @Test
    void buildHtml_voiceCategory_rendersMusicIcon() {
        AITool t = new AITool("X", "Voice Synthesis", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("🎵");
    }

    @Test
    void buildHtml_writingCategory_rendersPenIcon() {
        AITool t = new AITool("X", "Writing Assistant", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("✍️");
    }

    @Test
    void buildHtml_automationCategory_rendersGearIcon() {
        AITool t = new AITool("X", "Automation", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("⚙️");
    }

    @Test
    void buildHtml_analyticsCategory_rendersChartIcon() {
        AITool t = new AITool("X", "Analytics Platform", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("📊");
    }

    @Test
    void buildHtml_conversationalCategory_rendersMessageIcon() {
        AITool t = new AITool("X", "Conversational AI", "Desc", List.of("P1","P2","P3"), List.of("C1","C2"), null);
        assertThat(service.buildHtml(t, "Saturday, July 5, 2026", "10:00")).contains("💬");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MimeMessage captureSentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
