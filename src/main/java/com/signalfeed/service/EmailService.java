package com.signalfeed.service;

import com.signalfeed.exception.EmailSendException;
import com.signalfeed.model.AITool;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds and sends a single-tool spotlight HTML email via SMTP.
 *
 * <p>Subject format: {@code 🤖 AI Tool Spotlight — {name} [HH:mm]}
 *
 * <p>The HTML body uses inline CSS for broad email-client compatibility.
 * No template engine is used in the MVP — the body is assembled with a
 * {@link StringBuilder}.
 *
 * <p>On any transport or MIME-construction failure the method throws
 * {@link EmailSendException} (a {@link RuntimeException}) so the scheduler
 * can log and continue without propagating.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter HEADER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String from;
    private final String to;

    /**
     * Constructor injection so {@code @Value} fields are available in unit tests
     * without {@code ReflectionTestUtils}.
     */
    public EmailService(JavaMailSender mailSender,
                        @Value("${app.email.from}") String from,
                        @Value("${app.email.to}") String to) {
        this.mailSender = mailSender;
        this.from = from;
        this.to = to;
        log.info("EmailService initialised — from='{}', to='{}'", from, to);
    }

    /**
     * Builds and dispatches a spotlight email for the given AI tool.
     *
     * @param tool the trending AI tool to spotlight; must not be null
     * @throws EmailSendException if the email cannot be built or delivered
     */
    public void send(AITool tool) {
        log.info("=== Starting email send for tool: '{}' (category: '{}') ===",
                tool.name(), tool.category());

        LocalDateTime now = LocalDateTime.now();
        String time        = now.format(TIME_FORMATTER);
        String headerDate  = now.toLocalDate().format(HEADER_DATE_FORMATTER);

        String subject = buildSubject(tool.name(), time);
        String html    = buildHtml(tool, headerDate, time);

        log.debug("Email subject  : {}", subject);
        log.debug("Email from     : {}", from);
        log.debug("Email to       : {}", to);
        log.debug("HTML body size : {} chars", html.length());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);   // true → text/html

            log.debug("MimeMessage assembled; calling JavaMailSender.send()");
            mailSender.send(message);

            log.info("=== Email sent successfully — tool='{}', to='{}', time='{}' ===",
                    tool.name(), to, time);

        } catch (MailException e) {
            log.error("Transport failure sending email for tool '{}' — MailException: {}",
                    tool.name(), e.getMessage(), e);
            throw new EmailSendException(
                    "Failed to deliver email for tool '" + tool.name() + "': " + e.getMessage(), e);

        } catch (MessagingException e) {
            log.error("MIME construction failure for tool '{}' — MessagingException: {}",
                    tool.name(), e.getMessage(), e);
            throw new EmailSendException(
                    "Failed to build email for tool '" + tool.name() + "': " + e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    String buildSubject(String toolName, String time) {
        return "🤖 AI Tool Spotlight — " + toolName + " [" + time + "]";
    }

    String buildHtml(AITool tool, String headerDate, String time) {
        log.debug("Building HTML body for tool: '{}'", tool.name());

        StringBuilder sb = new StringBuilder(2048);

        // ── Document wrapper ──────────────────────────────────────────────────
        sb.append("<!DOCTYPE html>")
          .append("<html lang=\"en\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>AI Tool Spotlight</title>")
          .append("</head><body style=\"")
          .append("margin:0;padding:0;background:#f4f4f5;font-family:Arial,Helvetica,sans-serif;")
          .append("\">");

        // ── Outer container ───────────────────────────────────────────────────
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
          .append("<tr><td align=\"center\" style=\"padding:32px 16px;\">");

        // ── Card ──────────────────────────────────────────────────────────────
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
          .append("style=\"max-width:600px;background:#ffffff;border-radius:12px;")
          .append("box-shadow:0 2px 8px rgba(0,0,0,0.08);overflow:hidden;\">");

        // ── Header banner ─────────────────────────────────────────────────────
        sb.append("<tr><td style=\"")
          .append("background:linear-gradient(135deg,#1a1a2e 0%,#16213e 50%,#0f3460 100%);")
          .append("padding:32px 40px;text-align:center;")
          .append("\">")
          .append("<p style=\"margin:0;font-size:32px;\">&#129302;</p>")   // 🤖
          .append("<h1 style=\"margin:8px 0 4px;font-size:22px;font-weight:700;")
          .append("color:#ffffff;letter-spacing:0.5px;\">AI Tool Spotlight</h1>")
          .append("<p style=\"margin:0;font-size:13px;color:#94a3b8;\">")
          .append(htmlEscape(headerDate)).append(" &middot; ").append(htmlEscape(time))
          .append("</p>")
          .append("</td></tr>");

        // ── Tool header: category badge + name ────────────────────────────────
        sb.append("<tr><td style=\"padding:32px 40px 0;\">")
          .append("<span style=\"")
          .append("display:inline-block;background:#e0f2fe;color:#0369a1;")
          .append("font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;")
          .append("padding:4px 10px;border-radius:4px;margin-bottom:12px;")
          .append("\">").append(htmlEscape(tool.category())).append("</span>")
          .append("<h2 style=\"margin:0 0 12px;font-size:26px;font-weight:700;color:#0f172a;\">")
          .append(htmlEscape(tool.name()))
          .append("</h2>")
          .append("<p style=\"margin:0 0 0;font-size:15px;line-height:1.6;color:#475569;\">")
          .append(htmlEscape(tool.description()))
          .append("</p>")
          .append("</td></tr>");

        // ── Divider ───────────────────────────────────────────────────────────
        sb.append("<tr><td style=\"padding:24px 40px 0;\">")
          .append("<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:0;\">")
          .append("</td></tr>");

        // ── Pros ──────────────────────────────────────────────────────────────
        sb.append("<tr><td style=\"padding:24px 40px 0;\">")
          .append("<h3 style=\"margin:0 0 12px;font-size:14px;font-weight:700;")
          .append("color:#15803d;text-transform:uppercase;letter-spacing:0.5px;\">")
          .append("&#9989; Pros</h3>")       // ✅
          .append("<ul style=\"margin:0;padding:0;list-style:none;\">");
        appendListItems(sb, tool.pros(), "#15803d");
        sb.append("</ul>").append("</td></tr>");

        // ── Cons ──────────────────────────────────────────────────────────────
        sb.append("<tr><td style=\"padding:16px 40px 0;\">")
          .append("<h3 style=\"margin:0 0 12px;font-size:14px;font-weight:700;")
          .append("color:#b45309;text-transform:uppercase;letter-spacing:0.5px;\">")
          .append("&#9888;&#65039; Cons</h3>")  // ⚠️
          .append("<ul style=\"margin:0;padding:0;list-style:none;\">");
        appendListItems(sb, tool.cons(), "#b45309");
        sb.append("</ul>").append("</td></tr>");

        // ── Visit Tool button (only when link is present) ─────────────────────
        if (tool.link() != null && !tool.link().isBlank()) {
            log.debug("Tool has link '{}'; adding Visit button", tool.link());
            sb.append("<tr><td style=\"padding:28px 40px 0;text-align:center;\">")
              .append("<a href=\"").append(tool.link()).append("\" ")
              .append("style=\"display:inline-block;background:#0f3460;color:#ffffff;")
              .append("text-decoration:none;font-size:14px;font-weight:700;")
              .append("padding:14px 32px;border-radius:8px;letter-spacing:0.5px;\"")
              .append(" target=\"_blank\">")
              .append("Visit Tool &#8594;")   // →
              .append("</a>")
              .append("</td></tr>");
        } else {
            log.debug("Tool has no link; skipping Visit button");
        }

        // ── Footer ────────────────────────────────────────────────────────────
        sb.append("<tr><td style=\"")
          .append("padding:32px 40px;text-align:center;border-top:1px solid #e2e8f0;margin-top:28px;")
          .append("\">")
          .append("<p style=\"margin:0;font-size:12px;color:#94a3b8;\">")
          .append("Powered by <strong>Signal Feed</strong> + Claude &nbsp;&middot;&nbsp; ")
          .append("One trending AI tool, delivered daily.")
          .append("</p>")
          .append("</td></tr>");

        // ── Close card + outer container ──────────────────────────────────────
        sb.append("</table>");         // card
        sb.append("</td></tr></table>"); // outer
        sb.append("</body></html>");

        log.debug("HTML body built ({} chars)", sb.length());
        return sb.toString();
    }

    /** Appends {@code <li>} elements styled with the given colour. */
    private void appendListItems(StringBuilder sb, List<String> items, String colour) {
        for (String item : items) {
            sb.append("<li style=\"")
              .append("font-size:14px;color:").append(colour).append(";")
              .append("padding:4px 0;")
              .append("\">&#8226; ")   // •
              .append(htmlEscape(item))
              .append("</li>");
        }
    }

    /** Minimal HTML escaping — prevents XSS in tool-supplied text fields. */
    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&#39;");
    }
}
