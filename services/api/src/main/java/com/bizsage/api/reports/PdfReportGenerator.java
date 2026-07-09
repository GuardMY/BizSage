package com.bizsage.api.reports;

import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * V2: Generates a formatted diagnosis report PDF using Apache PDFBox.
 *
 * <p>The report includes: title, generation time, original question, AI diagnosis answer,
 * source list with confidence scores, timeliness/confidence/self-check status, and disclaimer.
 */
@Component
public class PdfReportGenerator {

  private static final Logger log = LoggerFactory.getLogger(PdfReportGenerator.class);

  private static final float MARGIN = 56f;
  private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
  private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
  private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
  private static final float BOTTOM_MARGIN = 72f;

  private static final Standard14Fonts.FontName BODY_FONT = Standard14Fonts.FontName.HELVETICA;
  private static final Standard14Fonts.FontName BOLD_FONT = Standard14Fonts.FontName.HELVETICA_BOLD;

  /**
   * Generate a PDF byte array for the given diagnosis report.
   */
  public byte[] generate(DiagnosisReport report) {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);

      try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
        float y = PAGE_HEIGHT - MARGIN;

        y = drawHeader(cs, y);
        y -= 20;
        y = drawHorizontalRule(cs, y);
        y -= 20;
        y = drawSection(cs, "Diagnosis Question", report.question(), y, false);
        y -= 16;
        y = drawSection(cs, "Diagnosis Answer", report.summary(), y, true);
        y -= 16;

        if (report.sources() != null && !report.sources().isEmpty()) {
          y = drawSources(cs, report.sources(), y);
          y -= 16;
        }

        y = drawMetadata(cs, report, y);
        y -= 20;
        y = drawHorizontalRule(cs, y);
        y -= 14;
        y = drawDisclaimer(cs, report.disclaimer(), y);
        drawFooter(document, page);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      document.save(baos);
      return baos.toByteArray();
    } catch (Exception e) {
      log.error("Failed to generate PDF report", e);
      throw new RuntimeException("PDF generation failed", e);
    }
  }

  private float drawHeader(PDPageContentStream cs, float y) throws Exception {
    cs.beginText();
    cs.setFont(font(BOLD_FONT), 20);
    cs.newLineAtOffset(MARGIN, y);
    cs.showText("BizSage - Diagnosis Report");
    cs.endText();

    y -= 24;
    cs.beginText();
    cs.setFont(font(BODY_FONT), 10);
    cs.newLineAtOffset(MARGIN, y);
    cs.showText("Generated: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
    cs.endText();
    return y - 8;
  }

  private float drawHorizontalRule(PDPageContentStream cs, float y) throws Exception {
    cs.setLineWidth(0.5f);
    cs.moveTo(MARGIN, y);
    cs.lineTo(PAGE_WIDTH - MARGIN, y);
    cs.stroke();
    return y - 4;
  }

  private float drawSection(PDPageContentStream cs, String title, String body, float y, boolean wrap) throws Exception {
    cs.beginText();
    cs.setFont(font(BOLD_FONT), 12);
    cs.newLineAtOffset(MARGIN, y);
    cs.showText(title);
    cs.endText();
    y -= 18;

    PDFont bodyFont = font(BODY_FONT);
    float bodyFontSize = 10f;
    float lineHeight = 14f;
    List<String> lines = wrap ? wrapLines(body, CONTENT_WIDTH, bodyFont, bodyFontSize) : List.of(safeText(body));
    for (String line : lines) {
      if (y < BOTTOM_MARGIN) {
        break;
      }
      cs.beginText();
      cs.setFont(bodyFont, bodyFontSize);
      cs.newLineAtOffset(MARGIN, y);
      cs.showText(line);
      cs.endText();
      y -= lineHeight;
    }
    return y;
  }

  private float drawSources(PDPageContentStream cs, List<ReportSource> sources, float y) throws Exception {
    cs.beginText();
    cs.setFont(font(BOLD_FONT), 12);
    cs.newLineAtOffset(MARGIN, y);
    cs.showText("Sources");
    cs.endText();
    y -= 18;

    PDFont bodyFont = font(BODY_FONT);
    float bodyFontSize = 9f;
    float lineHeight = 13f;
    int idx = 1;
    for (ReportSource src : sources) {
      if (y < BOTTOM_MARGIN) {
        break;
      }
      String line = String.format("%d. [%s] %s (confidence: %.0f%%)",
          idx++, src.sourceId(), src.title(), src.confidence() * 100);
      List<String> wrapped = wrapLines(line, CONTENT_WIDTH - 12, bodyFont, bodyFontSize);
      for (String entry : wrapped) {
        cs.beginText();
        cs.setFont(bodyFont, bodyFontSize);
        cs.newLineAtOffset(MARGIN + 12, y);
        cs.showText(entry);
        cs.endText();
        y -= lineHeight;
      }
    }
    return y;
  }

  private float drawMetadata(PDPageContentStream cs, DiagnosisReport report, float y) throws Exception {
    cs.beginText();
    cs.setFont(font(BOLD_FONT), 12);
    cs.newLineAtOffset(MARGIN, y);
    cs.showText("Report Metadata");
    cs.endText();
    y -= 18;

    PDFont bodyFont = font(BODY_FONT);
    float bodyFontSize = 10f;
    float lineHeight = 15f;
    String[] meta = {
        "Confidence: " + report.confidence(),
        "Timeliness: " + report.timeliness(),
        "Self-Check Status: " + report.selfCheckStatus()
    };
    for (String item : meta) {
      cs.beginText();
      cs.setFont(bodyFont, bodyFontSize);
      cs.newLineAtOffset(MARGIN + 12, y);
      cs.showText(item);
      cs.endText();
      y -= lineHeight;
    }
    return y;
  }

  private float drawDisclaimer(PDPageContentStream cs, String disclaimer, float y) throws Exception {
    PDFont bodyFont = font(BODY_FONT);
    float bodyFontSize = 8f;
    float lineHeight = 11f;
    List<String> lines = wrapLines("Disclaimer: " + safeText(disclaimer), CONTENT_WIDTH, bodyFont, bodyFontSize);
    for (String line : lines) {
      if (y < MARGIN) {
        break;
      }
      cs.beginText();
      cs.setFont(bodyFont, bodyFontSize);
      cs.newLineAtOffset(MARGIN, y);
      cs.showText(line);
      cs.endText();
      y -= lineHeight;
    }
    return y;
  }

  private void drawFooter(PDDocument document, PDPage page) throws Exception {
    try (PDPageContentStream cs = new PDPageContentStream(document, page,
        PDPageContentStream.AppendMode.APPEND, true)) {
      cs.beginText();
      cs.setFont(font(BODY_FONT), 7);
      cs.newLineAtOffset(MARGIN, 28);
      cs.showText("BizSage Diagnosis Report - Generated by AI. Verify critical decisions independently.");
      cs.endText();
    }
  }

  private PDFont font(Standard14Fonts.FontName fontName) {
    return new PDType1Font(fontName);
  }

  private List<String> wrapLines(String text, float maxWidth, PDFont font, float fontSize) throws Exception {
    String normalized = safeText(text);
    if (normalized.isBlank()) {
      return List.of("");
    }

    List<String> lines = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String word : normalized.split(" ")) {
      String candidate = current.isEmpty() ? word : current + " " + word;
      float candidateWidth = font.getStringWidth(candidate) / 1000f * fontSize;
      if (candidateWidth > maxWidth && !current.isEmpty()) {
        lines.add(current.toString());
        current = new StringBuilder(word);
      } else {
        if (!current.isEmpty()) {
          current.append(" ");
        }
        current.append(word);
      }
    }
    if (!current.isEmpty()) {
      lines.add(current.toString());
    }
    return lines.isEmpty() ? List.of(normalized) : lines;
  }

  private String safeText(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }
}
