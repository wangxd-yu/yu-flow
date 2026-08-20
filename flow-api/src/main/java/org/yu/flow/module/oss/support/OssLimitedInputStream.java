package org.yu.flow.module.oss.support;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 解压流计数：条目字节 + 累计未压缩字节，超限抛 {@link OssArchiveLimitException}。
 */
public final class OssLimitedInputStream extends FilterInputStream {

    private final Counter counter;
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final long maxRatioUncompressed;
    private long entryBytes;

    public OssLimitedInputStream(InputStream in, Counter counter,
                                 long maxEntryBytes, long maxTotalBytes, long maxRatioUncompressed) {
        super(in);
        this.counter = counter;
        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxRatioUncompressed = maxRatioUncompressed;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            account(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            account(n);
        }
        return n;
    }

    private void account(int n) {
        entryBytes += n;
        long total = counter.add(n);
        if (maxEntryBytes > 0 && entryBytes > maxEntryBytes) {
            throw new OssArchiveLimitException("单条目解压后超过上限");
        }
        if (maxTotalBytes > 0 && total > maxTotalBytes) {
            throw new OssArchiveLimitException("解压后总大小超过上限");
        }
        if (maxRatioUncompressed > 0 && total > maxRatioUncompressed) {
            throw new OssArchiveLimitException("压缩比超过安全上限（疑似 zip bomb）");
        }
    }

    public long getEntryBytes() {
        return entryBytes;
    }

    public static final class Counter {
        private long total;

        public synchronized long add(long n) {
            total += n;
            return total;
        }

        public synchronized long get() {
            return total;
        }
    }

    public static final class OssArchiveLimitException extends RuntimeException {
        public OssArchiveLimitException(String message) {
            super(message);
        }
    }
}
