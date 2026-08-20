package org.yu.flow.module.oss.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssArchiveExtractSupportTest {

    @Test
    void zipSlip_isRejected() {
        assertNull(OssArchiveExtractSupport.sanitizeEntryPath("../etc/passwd"));
        assertNull(OssArchiveExtractSupport.sanitizeEntryPath("docs/../../etc/passwd"));
        assertNull(OssArchiveExtractSupport.sanitizeEntryPath("/abs/a.pdf"));
        assertNull(OssArchiveExtractSupport.sanitizeEntryPath("C:\\windows\\x.txt"));
        assertNull(OssArchiveExtractSupport.sanitizeEntryPath("a/../../b.txt"));
    }

    @Test
    void relativePath_isPreserved() {
        assertEquals("docs/a.pdf", OssArchiveExtractSupport.sanitizeEntryPath("docs/a.pdf"));
        assertEquals("docs/中文.pdf", OssArchiveExtractSupport.sanitizeEntryPath("docs\\中文.pdf"));
        assertEquals("a/b.txt", OssArchiveExtractSupport.sanitizeEntryPath("./a/b.txt"));
    }

    @Test
    void junkAndNested_areDetected() {
        assertTrue(OssArchiveExtractSupport.isJunkPath("__MACOSX/._a"));
        assertTrue(OssArchiveExtractSupport.isJunkPath("folder/.DS_Store"));
        assertTrue(OssArchiveExtractSupport.isJunkPath("Thumbs.db"));
        assertTrue(OssArchiveExtractSupport.isNestedArchive("zip", null));
        assertTrue(OssArchiveExtractSupport.isNestedArchive("rar", "application/x-rar-compressed"));
        assertFalse(OssArchiveExtractSupport.isNestedArchive("pdf", "application/pdf"));
        assertFalse(OssArchiveExtractSupport.isNestedArchive("docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void innerAllowlist_skipsUnlisted() {
        assertFalse(OssArchiveExtractSupport.allowedByInnerList(
                "exe", "application/octet-stream", "pdf,png", null));
        assertTrue(OssArchiveExtractSupport.allowedByInnerList(
                "pdf", "application/pdf", "pdf,png", "application/pdf"));
        assertTrue(OssArchiveExtractSupport.allowedByInnerList("bin", "application/octet-stream", null, null));
    }

    @Test
    void outerAllowlist_requiresZipWhenNonEmpty() {
        assertTrue(OssArchiveExtractSupport.outerAllowlistAllowsZip(null, null));
        assertTrue(OssArchiveExtractSupport.outerAllowlistAllowsZip("zip,png", "application/zip,image/png"));
        assertFalse(OssArchiveExtractSupport.outerAllowlistAllowsZip("png,jpg", "image/png,image/jpeg"));
    }

    @Test
    void childKey_nestsUnderParentStem() {
        assertEquals("p/2026/uuid_pack/docs/a.pdf",
                OssArchiveExtractSupport.childObjectKey("p/2026/uuid_pack.zip", "docs/a.pdf"));
        assertEquals("p/obj_extracted/a.txt",
                OssArchiveExtractSupport.childObjectKey("p/obj", "a.txt"));
    }

    @Test
    void canBuildSampleZipBytes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("docs/ok.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("__MACOSX/._ok.txt"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertTrue(baos.size() > 0);
    }
}
