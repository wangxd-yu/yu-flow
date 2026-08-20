package org.yu.flow.module.oss.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssContentSnifferTest {

    @Test
    void pdfAndPngMagic() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        assertEquals("application/pdf", OssContentSniffer.sniff(pdf));
        assertTrue(OssContentSniffer.magicMatchesExtension("pdf", pdf));
        assertFalse(OssContentSniffer.magicMatchesExtension("pdf", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));

        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals("image/png", OssContentSniffer.sniff(png));
        assertTrue(OssContentSniffer.magicMatchesExtension("png", png));
    }

    @Test
    void officeOpenXml_isZipButNotNestedArchive() {
        byte[] pk = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0};
        assertEquals("application/zip", OssContentSniffer.sniff(pk));
        assertTrue(OssContentSniffer.magicMatchesExtension("docx", pk));
        assertFalse(OssContentSniffer.looksLikeNestedArchive("docx", pk));
        assertTrue(OssContentSniffer.looksLikeNestedArchive("zip", pk));
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                OssContentSniffer.resolveContentType("docx", pk));
    }

    @Test
    void txtHasNoMagicRequirement() {
        byte[] txt = "hello".getBytes(StandardCharsets.UTF_8);
        assertTrue(OssContentSniffer.magicMatchesExtension("txt", txt));
        assertEquals("text/plain", OssContentSniffer.resolveContentType("txt", txt));
    }
}
