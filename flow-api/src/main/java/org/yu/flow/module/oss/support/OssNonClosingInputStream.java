package org.yu.flow.module.oss.support;

import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * 忽略 close，避免 Digest/MinIO 关闭流时连带关掉仍在遍历的 {@link java.util.zip.ZipInputStream}。
 */
public final class OssNonClosingInputStream extends FilterInputStream {

    public OssNonClosingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public void close() {
        // keep underlying open
    }
}
