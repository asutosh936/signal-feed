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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Builds and sends AI tool spotlight emails via SMTP.
 *
 * <p>Two sending modes:
 * <ul>
 *   <li>{@link #send(AITool)} — single-tool spotlight (kept for targeted use)</li>
 *   <li>{@link #sendConsolidated(List)} — all tools in one email as a 2-column card grid</li>
 * </ul>
 *
 * <p>HTML bodies use inline CSS for broad email-client compatibility.
 * No template engine — assembled with {@link StringBuilder}.
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
    private final String[] to;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.email.from}") String from,
                        @Value("${app.email.to}") String[] to) {
        this.mailSender = mailSender;
        this.from = from;
        this.to = to;
        log.info("EmailService initialised — from='{}', to='{}'", from, Arrays.toString(to));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void send(AITool tool) {
        log.info("=== Starting email send for tool: '{}' ===", tool.name());

        LocalDateTime now = LocalDateTime.now();
        String time       = now.format(TIME_FORMATTER);
        String headerDate = now.toLocalDate().format(HEADER_DATE_FORMATTER);

        String subject = buildSubject(tool.name());
        String html    = buildHtml(tool, headerDate, time);

        dispatch(subject, html, "tool='" + tool.name() + "'");

        log.info("=== Email sent successfully — tool='{}', to='{}' ===", tool.name(), Arrays.toString(to));
    }

    public void sendConsolidated(List<AITool> tools) {
        log.info("=== Starting consolidated email send for {} tools ===", tools.size());

        LocalDateTime now = LocalDateTime.now();
        String time       = now.format(TIME_FORMATTER);
        String headerDate = now.toLocalDate().format(HEADER_DATE_FORMATTER);

        String subject = buildConsolidatedSubject(tools.size());
        String html    = buildConsolidatedHtml(tools, headerDate, time);

        dispatch(subject, html, tools.size() + " tools");

        log.info("=== Consolidated email sent — {} tools, to='{}' ===", tools.size(), Arrays.toString(to));
    }

    // ── Subject builders ──────────────────────────────────────────────────────

    String buildSubject(String toolName) {
        return "✨ AI Spotlight — " + toolName;
    }

    String buildConsolidatedSubject(int count) {
        return "🔥 Today's Top " + count + " AI Tools";
    }

    // ── HTML builders ─────────────────────────────────────────────────────────

    String buildHtml(AITool tool, String headerDate, String time) {
        log.debug("Building single-tool HTML for '{}'", tool.name());
        StringBuilder sb = new StringBuilder(2048);

        appendDocOpen(sb);
        appendHeader(sb, "AI Tool Spotlight", headerDate, time);
        appendToolSection(sb, tool);
        appendFooter(sb, "One trending AI tool, delivered daily.");
        appendDocClose(sb);

        log.debug("HTML body built ({} chars)", sb.length());
        return sb.toString();
    }

    String buildConsolidatedHtml(List<AITool> tools, String headerDate, String time) {
        log.debug("Building consolidated HTML for {} tools", tools.size());
        StringBuilder sb = new StringBuilder(8192);

        appendDocOpen(sb);
        appendHeader(sb, tools.size() + " Trending AI Tools", headerDate, time);
        appendCardGrid(sb, tools);
        appendFooter(sb, "Trending AI tools, delivered daily.");
        appendDocClose(sb);

        log.debug("Consolidated HTML body built ({} chars)", sb.length());
        return sb.toString();
    }

    // ── Rendering helpers ─────────────────────────────────────────────────────

    private void appendDocOpen(StringBuilder sb) {
        sb.append("<!DOCTYPE html>")
          .append("<html lang=\"en\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>AI Tool Spotlight</title>")
          .append("</head><body style=\"")
          .append("margin:0;padding:0;background:#f4f4f5;font-family:Arial,Helvetica,sans-serif;\">")
          .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
          .append("<tr><td align=\"center\" style=\"padding:24px 16px;\">")
          .append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
          .append("style=\"max-width:600px;background:#ffffff;border-radius:12px;")
          .append("box-shadow:0 2px 8px rgba(0,0,0,0.08);overflow:hidden;\">");
    }

    private void appendHeader(StringBuilder sb, String title, String date, String time) {
        sb.append("<tr><td style=\"")
          .append("background:linear-gradient(135deg,#1a1a2e 0%,#16213e 50%,#0f3460 100%);")
          .append("padding:24px 40px;text-align:center;\">")
          .append("<h1 style=\"margin:0 0 4px;font-size:20px;font-weight:700;")
          .append("color:#ffffff;letter-spacing:0.5px;\">").append(htmlEscape(title)).append("</h1>")
          .append("<p style=\"margin:0;font-size:12px;color:#94a3b8;\">")
          .append(htmlEscape(date)).append(" &middot; ").append(htmlEscape(time))
          .append("</p>")
          .append("</td></tr>");
    }

    // Single-tool view: large icon + centred name/category + 2-column pros/cons
    private void appendToolSection(StringBuilder sb, AITool tool) {
        String icon = categoryIcon(tool.category());

        sb.append("<tr><td style=\"padding:28px 40px 0;text-align:center;\">")
          .append("<p style=\"margin:0 0 8px;font-size:52px;\">").append(icon).append("</p>")
          .append("<span style=\"display:inline-block;background:#e0f2fe;color:#0369a1;")
          .append("font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;")
          .append("padding:4px 10px;border-radius:4px;margin-bottom:10px;\">")
          .append(htmlEscape(tool.category())).append("</span>")
          .append("<h2 style=\"margin:0 0 10px;font-size:24px;font-weight:700;color:#0f172a;\">")
          .append(htmlEscape(tool.name())).append("</h2>")
          .append("<p style=\"margin:0;font-size:14px;line-height:1.6;color:#475569;\">")
          .append(htmlEscape(tool.description())).append("</p>")
          .append("</td></tr>");

        // Divider
        sb.append("<tr><td style=\"padding:16px 40px 0;\">")
          .append("<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:0;\">")
          .append("</td></tr>");

        // 2-column pros / cons
        sb.append("<tr><td style=\"padding:16px 40px 0;\">")
          .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");

        sb.append("<td width=\"49%\" valign=\"top\">")
          .append("<h3 style=\"margin:0 0 8px;font-size:13px;font-weight:700;color:#15803d;")
          .append("text-transform:uppercase;letter-spacing:0.5px;\">&#9989; Pros</h3>")
          .append("<ul style=\"margin:0;padding:0;list-style:none;\">");
        appendListItems(sb, tool.pros(), "#15803d");
        sb.append("</ul></td>");

        sb.append("<td width=\"2%\"></td>");

        sb.append("<td width=\"49%\" valign=\"top\">")
          .append("<h3 style=\"margin:0 0 8px;font-size:13px;font-weight:700;color:#b45309;")
          .append("text-transform:uppercase;letter-spacing:0.5px;\">&#9888;&#65039; Cons</h3>")
          .append("<ul style=\"margin:0;padding:0;list-style:none;\">");
        appendListItems(sb, tool.cons(), "#b45309");
        sb.append("</ul></td>");

        sb.append("</tr></table></td></tr>");

        if (tool.link() != null && !tool.link().isBlank()) {
            sb.append("<tr><td style=\"padding:16px 40px 0;text-align:center;\">")
              .append("<a href=\"").append(tool.link()).append("\" ")
              .append("style=\"display:inline-block;background:#0f3460;color:#ffffff;")
              .append("text-decoration:none;font-size:13px;font-weight:700;")
              .append("padding:12px 28px;border-radius:8px;letter-spacing:0.5px;\" target=\"_blank\">")
              .append("Visit Tool &#8594;</a>")
              .append("</td></tr>");
        }

        sb.append("<tr><td style=\"height:20px;\"></td></tr>");
    }

    // Consolidated view: tools rendered as a 2-column card grid
    private void appendCardGrid(StringBuilder sb, List<AITool> tools) {
        sb.append("<tr><td style=\"padding:16px 20px 0;\">")
          .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");

        for (int i = 0; i < tools.size(); i += 2) {
            if (i > 0) {
                sb.append("<tr><td colspan=\"3\" style=\"height:12px;\"></td></tr>");
            }
            sb.append("<tr>");
            appendCardCell(sb, tools.get(i), i + 1);
            sb.append("<td width=\"2%\"></td>");
            if (i + 1 < tools.size()) {
                appendCardCell(sb, tools.get(i + 1), i + 2);
            } else {
                sb.append("<td width=\"49%\"></td>");
            }
            sb.append("</tr>");
        }

        sb.append("</table></td></tr>");
        sb.append("<tr><td style=\"height:16px;\"></td></tr>");
    }

    private void appendCardCell(StringBuilder sb, AITool tool, int num) {
        sb.append("<td width=\"49%\" valign=\"top\" style=\"background:#f8fafc;border:1px solid #e2e8f0;")
          .append("border-radius:10px;padding:14px;\">");
        appendCardContent(sb, tool, num);
        sb.append("</td>");
    }

    private void appendCardContent(StringBuilder sb, AITool tool, int num) {
        String icon = categoryIcon(tool.category());

        // Icon
        sb.append("<p style=\"margin:0 0 6px;font-size:28px;text-align:center;\">")
          .append(icon).append("</p>");

        // #N · Category badge
        sb.append("<p style=\"margin:0 0 6px;text-align:center;\">")
          .append("<span style=\"display:inline-block;background:#e0f2fe;color:#0369a1;font-size:10px;")
          .append("font-weight:700;letter-spacing:1px;text-transform:uppercase;")
          .append("padding:3px 8px;border-radius:4px;\">")
          .append("#").append(num).append(" &middot; ").append(htmlEscape(tool.category()))
          .append("</span></p>");

        // Tool name
        sb.append("<h2 style=\"margin:0 0 8px;font-size:14px;font-weight:700;color:#0f172a;text-align:center;\">")
          .append(htmlEscape(tool.name())).append("</h2>");

        // Description
        sb.append("<p style=\"margin:0 0 10px;font-size:11px;line-height:1.5;color:#475569;\">")
          .append(htmlEscape(tool.description())).append("</p>");

        sb.append("<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:0 0 8px;\">");

        // Pros
        sb.append("<p style=\"margin:0 0 3px;font-size:10px;font-weight:700;color:#15803d;\">&#9989; Pros</p>")
          .append("<ul style=\"margin:0 0 8px;padding:0;list-style:none;\">");
        for (String p : tool.pros()) {
            sb.append("<li style=\"font-size:11px;color:#15803d;padding:1px 0;\">&#8226; ")
              .append(htmlEscape(p)).append("</li>");
        }
        sb.append("</ul>");

        // Cons
        sb.append("<p style=\"margin:0 0 3px;font-size:10px;font-weight:700;color:#b45309;\">&#9888;&#65039; Cons</p>")
          .append("<ul style=\"margin:0 0 8px;padding:0;list-style:none;\">");
        for (String c : tool.cons()) {
            sb.append("<li style=\"font-size:11px;color:#b45309;padding:1px 0;\">&#8226; ")
              .append(htmlEscape(c)).append("</li>");
        }
        sb.append("</ul>");

        if (tool.link() != null && !tool.link().isBlank()) {
            sb.append("<a href=\"").append(tool.link()).append("\" ")
              .append("style=\"display:block;background:#0f3460;color:#ffffff;text-decoration:none;")
              .append("font-size:11px;font-weight:700;padding:8px;border-radius:6px;")
              .append("text-align:center;\" target=\"_blank\">Visit &#8594;</a>");
        }
    }

    private void appendFooter(StringBuilder sb, String tagline) {
        sb.append("<tr><td style=\"")
          .append("padding:24px 40px;text-align:center;border-top:1px solid #e2e8f0;\">")
          .append("<p style=\"margin:0;font-size:12px;color:#94a3b8;\">")
          .append("Powered by <strong>Signal Feed</strong> + Claude &nbsp;&middot;&nbsp; ")
          .append(htmlEscape(tagline))
          .append("</p></td></tr>");
    }

    private void appendDocClose(StringBuilder sb) {
        sb.append("</table>");
        sb.append("</td></tr></table>");
        sb.append("</body></html>");
    }

    private void appendListItems(StringBuilder sb, List<String> items, String colour) {
        for (String item : items) {
            sb.append("<li style=\"font-size:13px;color:").append(colour)
              .append(";padding:3px 0;\">&#8226; ")
              .append(htmlEscape(item))
              .append("</li>");
        }
    }

    private static String categoryIcon(String category) {
        if (category == null || category.isBlank()) return "🤖";
        String lower = category.toLowerCase();
        if (lower.contains("cod") || lower.contains("develop") || lower.contains("program")) return "💻";
        if (lower.contains("research") || lower.contains("search")) return "🔍";
        if (lower.contains("image") || lower.contains("art") || lower.contains("design") || lower.contains("visual")) return "🎨";
        if (lower.contains("audio") || lower.contains("voice") || lower.contains("music") || lower.contains("sound") || lower.contains("speech")) return "🎵";
        if (lower.contains("video") || lower.contains("film")) return "🎬";
        if (lower.contains("product") || lower.contains("writ") || lower.contains("note")) return "✍️";
        if (lower.contains("data") || lower.contains("analyt") || lower.contains("chart")) return "📊";
        if (lower.contains("chat") || lower.contains("assist") || lower.contains("convers")) return "💬";
        if (lower.contains("agent") || lower.contains("automat")) return "⚙️";
        if (lower.contains("secur")) return "🔒";
        return "🤖";
    }

    private static String htmlEscape(String value) {
        if (value == null) return "";
        return value
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&#39;");
    }

    // ── Shared SMTP dispatch ──────────────────────────────────────────────────

    private void dispatch(String subject, String html, String context) {
        log.debug("Dispatching email — subject='{}', from='{}', to='{}', html={} chars",
                subject, from, Arrays.toString(to), html.length());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.debug("Email dispatched successfully for {}", context);

        } catch (MailException e) {
            log.error("Transport failure for {} — MailException: {}", context, e.getMessage(), e);
            throw new EmailSendException("Failed to deliver email (" + context + "): " + e.getMessage(), e);

        } catch (MessagingException e) {
            log.error("MIME failure for {} — MessagingException: {}", context, e.getMessage(), e);
            throw new EmailSendException("Failed to build email (" + context + "): " + e.getMessage(), e);
        }
    }
}
