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

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private JavaMailSender mailSender;

    private EmailService service;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final String FROM = "sender@example.com";
    private static final String TO   = "recipient@example.com";

    private static final AITool SAMPLE_TOOL = new AITool(
            "Perplexity",
            "Research",
            "AI-powered search engine with cited sources",
            List.of("Fast answers", "Cited sources", "Free tier"),
            List.of("Occasional hallucinations", "Limited API access"),
            "https://perplexity.ai"
    );

    private static final AITool TOOL_NO_LINK = new AITool(
            "Cursor",
            "Coding",
            "AI-powered code editor",
            List.of("Smart completions", "Tab to accept", "Multi-file edits"),
            List.of("Paid tier required", "Cloud dependency"),
            null
    );

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, FROM, TO);
        // Provide a real MimeMessage backed by a no-op Session
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));
    }

    // ── Subject line ─────────────────────────────────────────────────────────

    @Test
    void send_subject_containsToolName() throws Exception {
        service.send(SAMPLE_TOOL);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).contains("Perplexity");
    }

    @Test
    void send_subject_containsSpotlightLabel() throws Exception {
        service.send(SAMPLE_TOOL);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).contains("AI Tool Spotlight");
    }

    @Test
    void send_subject_containsRobotEmoji() throws Exception {
        service.send(SAMPLE_TOOL);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).contains("🤖");
    }

    @Test
    void send_subject_containsTimeBracket() throws Exception {
        service.send(SAMPLE_TOOL);

        MimeMessage sent = captureSentMessage();
        // format is [HH:mm] — just check the brackets and colon are present
        assertThat(sent.getSubject()).matches(".*\\[\\d{2}:\\d{2}\\].*");
    }

    // ── Recipients / sender ───────────────────────────────────────────────────

    @Test
    void send_setsFromAddress() throws Exception {
        service.send(SAMPLE_TOOL);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getFrom()).isNotEmpty();
        assertThat(sent.getFrom()[0].toString()).contains(FROM);
    }

    @Test
    void send_setsToAddress() throws Exception {
        service.send(SAMPLE_TOOL);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getAllRecipients()).isNotEmpty();
        assertThat(sent.getAllRecipients()[0].toString()).contains(TO);
    }

    // ── HTML body content ─────────────────────────────────────────────────────

    @Test
    void send_body_containsToolName() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("Perplexity");
    }

    @Test
    void send_body_containsCategory() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("Research");
    }

    @Test
    void send_body_containsDescription() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("AI-powered search engine with cited sources");
    }

    @Test
    void send_body_containsAllPros() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html)
                .contains("Fast answers")
                .contains("Cited sources")
                .contains("Free tier");
    }

    @Test
    void send_body_containsAllCons() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html)
                .contains("Occasional hallucinations")
                .contains("Limited API access");
    }

    @Test
    void send_body_containsLink_whenLinkPresent() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("https://perplexity.ai");
    }

    @Test
    void send_body_doesNotContainVisitButton_whenLinkNull() {
        String html = service.buildHtml(TOOL_NO_LINK, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).doesNotContain("Visit Tool");
    }

    @Test
    void send_body_containsHeaderDate() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("Wednesday, June 18, 2026");
    }

    @Test
    void send_body_containsHeaderTime() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).contains("08:30");
    }

    @Test
    void send_body_isHtmlDocument() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html)
                .contains("<!DOCTYPE html>")
                .contains("<html")
                .contains("</html>");
    }

    @Test
    void send_body_containsFooterBranding() {
        String html = service.buildHtml(SAMPLE_TOOL, "Wednesday, June 18, 2026", "08:30");
        assertThat(html).containsAnyOf("Signal Feed", "Powered by");
    }

    @Test
    void send_body_htmlEscapesSpecialChars() {
        AITool xssTool = new AITool(
                "<script>alert('xss')</script>",
                "Test & <Demo>",
                "Description with <b>bold</b>",
                List.of("Pro with <tag>", "Normal pro", "Third pro"),
                List.of("Con with &amp;", "Normal con"),
                null
        );
        String html = service.buildHtml(xssTool, "Thursday, June 18, 2026", "10:00");

        // raw angle brackets from tool fields must not appear unescaped
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).contains("Test &amp; &lt;Demo&gt;");
    }

    // ── buildSubject helper ───────────────────────────────────────────────────

    @Test
    void buildSubject_containsToolName() {
        String subject = service.buildSubject("Gemini", "14:00");
        assertThat(subject).contains("Gemini");
    }

    @Test
    void buildSubject_containsTime() {
        String subject = service.buildSubject("Gemini", "14:00");
        assertThat(subject).contains("[14:00]");
    }

    @Test
    void buildSubject_containsEmoji() {
        String subject = service.buildSubject("Gemini", "14:00");
        assertThat(subject).contains("🤖");
    }

    // ── Error handling: MailException → EmailSendException ───────────────────

    @Test
    void send_mailException_throwsEmailSendException() {
        doThrow(new MailSendException("SMTP refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("Perplexity");
    }

    @Test
    void send_mailException_causeIsPreserved() {
        MailSendException root = new MailSendException("connection timeout");
        doThrow(root).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class)
                .cause()
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("connection timeout");
    }

    // ── Error handling: MessagingException → EmailSendException ──────────────

    @Test
    void send_messagingException_throwsEmailSendException() throws Exception {
        // Force MessagingException during MIME assembly by having createMimeMessage()
        // return a message backed by a mock session that rejects setFrom.
        // Simpler approach: subclass EmailService to expose a hook, but instead we
        // use a spy to throw on createMimeMessage so MimeMessageHelper cannot initialise.
        JavaMailSender throwingSender = mock(JavaMailSender.class);
        // createMimeMessage returns a real MimeMessage but send() throws MessagingException
        // via a wrapped MailSendException — Spring wraps jakarta.mail.MessagingException.
        // For direct MessagingException path, test buildHtml/setSubject failures:
        // The cleanest way: use a MimeMessage subclass that throws on setSubject.
        Session session = Session.getInstance(new Properties());
        MimeMessage brokenMessage = new MimeMessage(session) {
            @Override
            public void setSubject(String subject, String charset) throws MessagingException {
                throw new MessagingException("forced MessagingException");
            }
        };
        when(throwingSender.createMimeMessage()).thenReturn(brokenMessage);

        EmailService svc = new EmailService(throwingSender, FROM, TO);

        assertThatThrownBy(() -> svc.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("Perplexity");
    }

    @Test
    void send_messagingException_causeIsPreserved() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage brokenMessage = new MimeMessage(session) {
            @Override
            public void setSubject(String subject, String charset) throws MessagingException {
                throw new MessagingException("charset error");
            }
        };
        JavaMailSender throwingSender = mock(JavaMailSender.class);
        when(throwingSender.createMimeMessage()).thenReturn(brokenMessage);

        EmailService svc = new EmailService(throwingSender, FROM, TO);

        assertThatThrownBy(() -> svc.send(SAMPLE_TOOL))
                .isInstanceOf(EmailSendException.class)
                .cause()
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("charset error");
    }

    // ── Interaction verification ──────────────────────────────────────────────

    @Test
    void send_callsCreateMimeMessage_exactlyOnce() {
        service.send(SAMPLE_TOOL);
        verify(mailSender, times(1)).createMimeMessage();
    }

    @Test
    void send_callsMailSenderSend_exactlyOnce() {
        service.send(SAMPLE_TOOL);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void send_withToolNoLink_stillSendsEmail() {
        service.send(TOOL_NO_LINK);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MimeMessage captureSentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
