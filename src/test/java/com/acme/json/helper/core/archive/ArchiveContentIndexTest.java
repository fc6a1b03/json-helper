package com.acme.json.helper.core.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压缩包内容搜索索引（trigram 倒排）单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class ArchiveContentIndexTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("正常：trigram 索引命中内容并定位行号")
    void queriesContentWithLineNumber() throws IOException {
        final File zip = createZipWithContent("content.zip",
                new String[]{"a.txt", "b/c.txt", "b/d.log"},
                new String[]{"hello world\nsecond line\nthird needle here", "nothing useful", "needle again"});
        final ArchiveIndex index = ArchiveIndex.build(zip, ArchiveFormats.ZIP);
        assertNotNull(index);
        final ArchiveContentIndex contentIndex = ArchiveContentIndex.build(zip, ArchiveFormats.ZIP, index);
        final List<ArchiveContentIndex.ContentHit> hits = contentIndex.query("needle");
        assertAll(
                () -> assertEquals(2, hits.size(), "两个文件应命中 needle"),
                () -> assertTrue(hits.stream().anyMatch(h -> "a.txt".equals(h.path()) && h.line() == 3),
                        "a.txt 的 needle 应定位到第 3 行"),
                () -> assertTrue(hits.stream().anyMatch(h -> "b/d.log".equals(h.path()) && h.line() == 1),
                        "b/d.log 的 needle 应定位到第 1 行"),
                () -> assertTrue(contentIndex.query("not-exist-word").isEmpty(), "不存在的词应无命中"),
                () -> assertTrue(contentIndex.query("ab").isEmpty(), "短于 3 字符的搜索词不走内容索引")
        );
    }

    @Test
    @DisplayName("正常：大小写不敏感命中")
    void queriesCaseInsensitive() throws IOException {
        final File zip = createZipWithContent("case.zip", new String[]{"a.txt"}, new String[]{"Hello NEEDLE World"});
        final ArchiveIndex index = ArchiveIndex.build(zip, ArchiveFormats.ZIP);
        final ArchiveContentIndex contentIndex = ArchiveContentIndex.build(zip, ArchiveFormats.ZIP, index);
        assertEquals(1, contentIndex.query("needle").size(), "大写内容应以小写搜索词命中");
    }

    @Test
    @DisplayName("边界：超过单条目体积上限的内容不参与索引")
    void skipsOversizedEntries() throws IOException {
        final String hugeText = "x".repeat(70 * 1024) + "\nneedle";
        final File zip = createZipWithContent("huge.zip", new String[]{"huge.txt"}, new String[]{hugeText});
        final ArchiveIndex index = ArchiveIndex.build(zip, ArchiveFormats.ZIP);
        final ArchiveContentIndex contentIndex = ArchiveContentIndex.build(zip, ArchiveFormats.ZIP, index);
        assertAll(
                () -> assertEquals(0, contentIndex.indexedCount(), "超大条目不参与内容索引"),
                () -> assertTrue(contentIndex.query("needle").isEmpty(), "超大条目内容不可命中")
        );
    }

    /**
     * 创建带内容的测试 zip 文件
     */
    private File createZipWithContent(final String name, final String[] entries, final String[] contents) throws IOException {
        final File file = tempDir.resolve(name).toFile();
        try (final ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < entries.length; i++) {
                output.putNextEntry(new ZipEntry(entries[i]));
                output.write(contents[i].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return file;
    }
}
