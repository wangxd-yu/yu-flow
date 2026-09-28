package org.yu.flow.module.release.support;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ReleasePackageReaderTest {

    @Test
    void readsWhatWriterProduces() throws Exception {
        ReleasePackageWriter.Result written = ReleasePackageWriter.write(release(), bundle());

        ReleasePackageReader.Parsed parsed = ReleasePackageReader.read(written.bytes());

        assertEquals(written.digest(), parsed.digest());
        assertEquals("v2026.10", parsed.release().getCode());
        assertEquals("2026-09-27 10:00:00", parsed.release().getItems().get(0).getRegressionPassedAt());
        assertEquals("api-1", parsed.bundle().getApis().get(0).getId());
        assertEquals("DEV", parsed.manifest().getSourceEnv());
    }

    @Test
    void rejectsTamperedContent() throws Exception {
        Map<String, byte[]> entries = unzip(ReleasePackageWriter.write(release(), bundle()).bytes());
        String bundleJson = new String(entries.get(ReleasePackageFormat.BUNDLE), StandardCharsets.UTF_8);
        entries.put(ReleasePackageFormat.BUNDLE, bundleJson.replace("下单", "被改过").getBytes(StandardCharsets.UTF_8));

        FlowException ex = assertThrows(FlowException.class, () -> ReleasePackageReader.read(zip(entries)));
        assertTrue(ex.getMessage().contains("校验失败"));
    }

    @Test
    void rejectsUnexpectedEntries() throws Exception {
        Map<String, byte[]> entries = unzip(ReleasePackageWriter.write(release(), bundle()).bytes());
        entries.put("../evil.sh", "rm -rf /".getBytes(StandardCharsets.UTF_8));

        FlowException ex = assertThrows(FlowException.class, () -> ReleasePackageReader.read(zip(entries)));
        assertTrue(ex.getMessage().contains("不允许的文件"));
    }

    @Test
    void rejectsNonPackage() {
        assertThrows(FlowException.class, () -> ReleasePackageReader.read("not a zip".getBytes(StandardCharsets.UTF_8)));
        assertThrows(FlowException.class, () -> ReleasePackageReader.read(new byte[0]));
    }

    @Test
    void recordsUncompressedSize() throws Exception {
        byte[] bytes = ReleasePackageWriter.write(release(), bundle()).bytes();
        long expected = unzip(bytes).values().stream().mapToLong(b -> b.length).sum();

        assertEquals(expected, ReleasePackageReader.read(bytes).size());
    }

    @Test
    void validatesHeaderAndItems() {
        ReleasePackageFormat.Manifest manifest = new ReleasePackageFormat.Manifest();
        manifest.setReleaseCode("v2026.10");
        manifest.setSourceEnv("DEV");

        ReleasePackageFormat.ReleaseInfo badCode = release();
        badCode.setCode("v1\",\"x\":\"y");
        assertTrue(assertThrows(FlowException.class, () -> ReleasePackageReader.validate(manifest, badCode))
                .getMessage().contains("版本号"));

        ReleasePackageFormat.ReleaseInfo mismatch = release();
        mismatch.setCode("v2026.11");
        assertTrue(assertThrows(FlowException.class, () -> ReleasePackageReader.validate(manifest, mismatch))
                .getMessage().contains("不一致"));

        ReleasePackageFormat.ReleaseInfo unknownType = release();
        unknownType.getItems().get(0).setAssetType("SHELL");
        assertThrows(FlowException.class, () -> ReleasePackageReader.validate(manifest, unknownType));

        ReleasePackageFormat.ReleaseInfo badEvidence = release();
        badEvidence.getItems().get(0).setRegressionPassedAt("昨天");
        assertThrows(FlowException.class, () -> ReleasePackageReader.validate(manifest, badEvidence));

        manifest.setSourceEnv("DEV'; DROP");
        assertThrows(FlowException.class, () -> ReleasePackageReader.validate(manifest, release()));
    }

    private static ReleasePackageFormat.ReleaseInfo release() {
        ReleasePackageFormat.ReleaseInfo info = new ReleasePackageFormat.ReleaseInfo();
        info.setCode("v2026.10");
        ReleasePackageFormat.ReleaseEntry entry = new ReleasePackageFormat.ReleaseEntry();
        entry.setAssetType("API");
        entry.setAssetId("api-1");
        entry.setAssetName("下单");
        entry.setRegressionEnv("DEV");
        entry.setRegressionPassedAt("2026-09-27 10:00:00");
        info.getItems().add(entry);
        return info;
    }

    private static AssetBundle bundle() {
        AssetBundle bundle = new AssetBundle();
        bundle.setKind(AssetBundle.KIND);
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);
        bundle.setSourceEnv("DEV");
        FlowApiDO api = new FlowApiDO();
        api.setId("api-1");
        api.setName("下单");
        bundle.getApis().add(api);
        return bundle;
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
