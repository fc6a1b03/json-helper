package com.acme.json.helper.core.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压缩包格式识别与条目索引单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class ArchiveIndexTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "[{index}] {0} 应识别为 {1}")
    @CsvSource({
            "a.zip, ZIP", "a.jar, ZIP", "a.war, ZIP", "a.7z, SEVEN_Z",
            "a.tar, TAR", "a.tar.gz, TAR_GZ", "a.tgz, TAR_GZ",
            "a.tar.bz2, TAR_BZ2", "a.tbz2, TAR_BZ2", "a.tar.xz, TAR_XZ", "a.txz, TAR_XZ",
            "a.gz, GZIP", "a.bz2, BZIP2", "a.xz, XZ"
    })
    @DisplayName("正常：支持的扩展名正确识别格式")
    void detectsSupportedFormats(final String fileName, final String expected) {
        assertEquals(ArchiveFormats.valueOf(expected), ArchiveFormats.of(fileName), "扩展名应识别为对应格式");
    }

    @ParameterizedTest(name = "[{index}] 不支持输入：{0}")
    @ValueSource(strings = {"a.txt", "a.json", "a", "", "a.zipx"})
    @DisplayName("边界：不支持的格式返回 null")
    void rejectsUnsupportedFormats(final String fileName) {
        assertNull(ArchiveFormats.of(fileName), "不支持的格式应返回 null");
    }

    @Test
    @DisplayName("正常：zip 索引构建目录树（目录在前、名称排序、子层懒取）")
    void buildsZipIndexTree() throws IOException {
        final File zip = createZip("sample.zip",
                new String[]{"src/", "src/main/", "src/main/App.java", "src/main/Util.java", "README.md", "lib/core.jar"});
        final ArchiveIndex index = ArchiveIndex.build(zip, ArchiveFormats.ZIP);
        assertNotNull(index, "索引应构建成功");
        final List<ArchiveIndex.Node> root = index.childrenOf(ArchiveIndex.ROOT_PARENT);
        assertAll(
                () -> assertEquals(3, root.size(), "根层应有 src/ lib/ README.md 三项"),
                () -> assertTrue(root.get(0).directory() && "lib".equals(root.get(0).name()), "目录应排在文件前（lib）"),
                () -> assertTrue(root.get(1).directory() && "src".equals(root.get(1).name()), "目录应排在文件前（src）"),
                () -> assertFalse(root.get(2).directory(), "README.md 为文件"),
                () -> assertEquals(1, index.childrenOf("src").size(), "src 子层应有 main"),
                () -> assertEquals(2, index.childrenOf("src/main").size(), "src/main 子层应有两个文件"),
                () -> assertNotNull(index.find("src/main/App.java"), "应能按路径定位条目"),
                () -> assertEquals(6, index.entryCount(), "条目总数应正确")
        );
    }

    @Test
    @DisplayName("边界：Windows 风格反斜杠路径归一化为正斜杠")
    void normalizesBackslashPaths() throws IOException {
        final File zip = createZip("bs.zip", new String[]{"dir\\", "dir\\a.txt"});
        final ArchiveIndex index = ArchiveIndex.build(zip, ArchiveFormats.ZIP);
        assertNotNull(index);
        assertEquals(1, index.childrenOf("dir").size(), "反斜杠路径应归一后归入 dir 子层");
    }

    @Test
    @DisplayName("边界：条目内容读取与大小限制")
    void readsEntryContentWithLimit() throws IOException {
        final File zip = createZip("content.zip", new String[]{"a.txt"});
        final ArchiveFormats format = ArchiveFormats.ZIP;
        assertAll(
                () -> assertNotNull(format.readEntryContent(zip, "a.txt", 1024), "未超限应能读取内容"),
                () -> assertNull(format.readEntryContent(zip, "a.txt", 1), "超限应返回 null"),
                () -> assertNull(format.readEntryContent(zip, "not-exist.txt", 1024), "不存在条目应返回 null")
        );
    }

    @Test
    @DisplayName("边界：体积超限的压缩包不允许索引")
    void rejectsOversizedArchive() {
        final File huge = new File(tempDir.resolve("huge.zip").toString()) {
            @Override
            public long length() {
                return ArchiveIndex.MAX_ARCHIVE_SIZE_STREAM + 1;
            }
        };
        assertFalse(ArchiveIndex.isIndexable(huge, ArchiveFormats.ZIP), "超大包应拒绝索引");
    }

    /**
     * 创建测试 zip 文件
     *
     * @param name    文件名
     * @param entries 条目路径（目录以 / 结尾）
     * @return 创建的文件
     */
    private File createZip(final String name, final String[] entries) throws IOException {
        final File file = tempDir.resolve(name).toFile();
        try (final ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (final String entryPath : entries) {
                output.putNextEntry(new ZipEntry(entryPath));
                if (!entryPath.endsWith("/") && !entryPath.endsWith("\\")) {
                    output.write("demo".getBytes());
                }
                output.closeEntry();
            }
        }
        return file;
    }
}
