package ai.claudecode.esgt2.rpt.infra;

import ai.claudecode.esgt2.rpt.domain.ReportSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Apache PDFBox 기반 보고서 PDF 렌더러.
 * 단순 텍스트 기반 레이아웃 (MVP 수준).
 */
@Component
public class PdfReportRenderer {

    /**
     * 보고서 데이터를 PDF 바이트 배열로 변환.
     *
     * @param entityName    법인명
     * @param reportingYear 보고 연도
     * @param framework     프레임워크 (예: "KSSB2")
     * @param sections      섹션 목록
     * @param totalEmission 총 배출량
     * @return PDF 바이트 배열
     */
    public byte[] render(String entityName, int reportingYear, String framework,
                          List<ReportSection> sections, BigDecimal totalEmission) {
        try (var doc = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            var bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (var cs = new PDPageContentStream(doc, page)) {
                float y = 780f;
                float margin = 50f;

                // 제목
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText(framework + " GHG Disclosure Report " + reportingYear);
                cs.endText();
                y -= 25;

                // 법인명
                cs.beginText();
                cs.setFont(regular, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Entity: " + (entityName != null ? entityName : "-"));
                cs.endText();
                y -= 30;

                // 섹션 헤더
                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("GHG Emissions by Scope (tCO2e)");
                cs.endText();
                y -= 20;

                // 섹션 데이터
                for (ReportSection section : sections) {
                    if (y < 100) break; // 페이지 넘침 방지 (MVP)
                    cs.beginText();
                    cs.setFont(regular, 10);
                    cs.newLineAtOffset(margin + 10, y);
                    String yoy = section.yoyDelta() != null
                        ? " (YoY: " + section.yoyDelta() + "%)"
                        : " (YoY: N/A)";
                    cs.showText(section.itemCode() + " - " + section.title() + ": "
                        + section.value() + yoy);
                    cs.endText();
                    y -= 16;
                }

                // 합계
                y -= 10;
                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Total Emissions: " + totalEmission + " tCO2e");
                cs.endText();
            }

            var out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("PDF 렌더링 실패: " + e.getMessage(), e);
        }
    }
}
