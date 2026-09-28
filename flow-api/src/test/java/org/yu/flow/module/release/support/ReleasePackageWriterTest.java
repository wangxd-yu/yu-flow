package org.yu.flow.module.release.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ReleasePackageWriterTest {

    @Test
    void writesManifestWithFileHashesAndDigest() throws Exception {
        ReleasePackageWriter.Result result = ReleasePackageWriter.write(release(), bundle());

        Map<String, byte[]> entries = unzip(result.bytes());
        assertEquals(ReleasePackageFormat.ENTRIES, List.copyOf(entries.keySet()));
        assertEquals(ReleasePackageWriter.sha256(entries.get(ReleasePackageFormat.MANIFEST)), result.digest());

        JsonNode manifest = new ObjectMapper().readTree(entries.get(ReleasePackageFormat.MANIFEST));
        assertEquals(ReleasePackageFormat.KIND, manifest.get("kind").asText());
        assertEquals("v2026.10", manifest.get("releaseCode").asText());
        assertEquals("DEV", manifest.get("sourceEnv").asText());
        for (String name : List.of(ReleasePackageFormat.BUNDLE, ReleasePackageFormat.RELEASE, ReleasePackageFormat.CHANGELOG)) {
            assertEquals(ReleasePackageWriter.sha256(entries.get(name)), manifest.get("files").get(name).asText(), name);
        }

        JsonNode bundle = new ObjectMapper().readTree(entries.get(ReleasePackageFormat.BUNDLE));
        assertEquals("api-1", bundle.get("apis").get(0).get("id").asText());
    }

    @Test
    void changelogListsAssetsAndRequirements() throws Exception {
        Map<String, byte[]> entries = unzip(ReleasePackageWriter.write(release(), bundle()).bytes());
        String md = new String(entries.get(ReleasePackageFormat.CHANGELOG), StandardCharsets.UTF_8);

        assertTrue(md.contains("# 发布说明 v2026.10 · 十月版本"));
        assertTrue(md.contains("新增支付回调"));
        assertTrue(md.contains("| 接口 | 下单 | api-1 | 手工加入 |"));
        assertTrue(md.contains("| 环境变量 | PAY_URL | 支付网关地址 | 下单 |"));
    }

    private static ReleasePackageFormat.ReleaseInfo release() {
        ReleasePackageFormat.ReleaseInfo info = new ReleasePackageFormat.ReleaseInfo();
        info.setCode("v2026.10");
        info.setName("十月版本");
        info.setRemark("新增支付回调");
        ReleasePackageFormat.ReleaseEntry entry = new ReleasePackageFormat.ReleaseEntry();
        entry.setAssetType(ReleaseAssetResolver.API);
        entry.setAssetId("api-1");
        entry.setAssetName("下单");
        entry.setOrigin("MANUAL");
        info.getItems().add(entry);
        return info;
    }

    private static AssetBundle bundle() {
        AssetBundle bundle = new AssetBundle();
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);
        bundle.setKind(AssetBundle.KIND);
        bundle.setSourceEnv("DEV");
        bundle.setExportedBy("dev1");
        bundle.setExportedAt("2026-09-27 23:59:00");
        FlowApiDO api = new FlowApiDO();
        api.setId("api-1");
        api.setName("下单");
        bundle.getApis().add(api);
        TransferRequirementDTO req = new TransferRequirementDTO();
        req.setKind(TransferRequirementDTO.KIND_ENV_VAR);
        req.setKey("PAY_URL");
        req.setRemark("支付网关地址");
        req.getUsedBy().add("下单");
        bundle.getRequirements().add(req);
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
}
