package com.acme.prism.core.fileinfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件头注释提取器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-21
 */
class FileCommentExtractorTest {

    static Stream<Arguments> lineCommentCases() {
        return Stream.of(
                Arguments.of("// hello world", "hello world"),
                Arguments.of("  // 缩进注释", "缩进注释"),
                Arguments.of("# python comment", "python comment"),
                Arguments.of("-- sql comment", "sql comment"),
                Arguments.of(";; lisp comment", "lisp comment"),
                Arguments.of("/* single block */", "single block"),
                Arguments.of("/** doc block */", "doc block"),
                Arguments.of("<!-- html comment -->", "html comment"),
                Arguments.of("// ==== 标题 ====", "标题"),
                Arguments.of("### IntelliJ IDEA ###", "IntelliJ IDEA"),
                Arguments.of("//=====\n// real comment", "real comment"),
                Arguments.of("// internal   blank", "internal blank"),
                Arguments.of("// TIP To <b>Run</b> code", "TIP To Run code"),
                Arguments.of("// first<br>second", "first second"),
                Arguments.of("// <a href=\"http://x\">link</a> text", "link text"),
                Arguments.of("// List<String> copy", "List<String> copy"),
                Arguments.of("// 压缩包内容搜索索引（trigram 倒排索引）", "压缩包内容搜索索引"),
                Arguments.of("// 缓存服务（项目级）(cached)", "缓存服务"),
                Arguments.of("// JSON压缩器，支持自定义", "JSON压缩器"),
                Arguments.of("// 时间类型处理。", "时间类型处理"),
                Arguments.of("// v1.0 版本发布", "v1.0 版本发布")
        );
    }

    @ParameterizedTest(name = "[{index}] 输入『{0}』应提取为『{1}』")
    @MethodSource("lineCommentCases")
    @DisplayName("正常：各风格单行/单行块注释提取与装饰剥离")
    void extractsLineComments(final String input, final String expected) {
        assertEquals(expected, FileCommentExtractor.extract(input), "注释提取结果不符");
    }

    static Stream<Arguments> noCommentCases() {
        return Stream.of(
                Arguments.of((Object) "//"),
                Arguments.of((Object) "package com.acme;"),
                Arguments.of((Object) "package com.acme;\npublic class Foo {}"),
                Arguments.of((Object) "\n\npackage com.acme;"),
                Arguments.of((Object) "#include <stdio.h>"),
                Arguments.of((Object) "#define MAX 10"),
                Arguments.of((Object) "/**/"),
                Arguments.of((Object) "// @author 拒绝者"),
                Arguments.of((Object) ""),
                Arguments.of((Object) "  \n \n ")
        );
    }

    @ParameterizedTest(name = "[{index}] 输入『{0}』应返回 null")
    @MethodSource("noCommentCases")
    @DisplayName("边界：代码即停/预处理指令/空注释/空文本返回 null")
    void rejectsNoComment(final String input) {
        assertNull(FileCommentExtractor.extract(input), "无头部注释应返回 null");
    }

    @Test
    @DisplayName("边界：null 输入返回 null")
    void rejectsNullInput() {
        assertNull(FileCommentExtractor.extract(null), "null 输入应返回 null");
    }

    @Test
    @DisplayName("正常：Python docstring 三引号提取（单行/多行/shebang 后）")
    void extractsPythonDocstring() {
        assertEquals("module doc", FileCommentExtractor.extract("\"\"\"module doc\"\"\"\nimport os"),
                "单行 docstring 应可提取");
        assertEquals("模块说明", FileCommentExtractor.extract("\"\"\"\n模块说明\n作者：拒绝者\n\"\"\""),
                "多行 docstring 应取首行文本");
        assertEquals("script doc", FileCommentExtractor.extract("#!/usr/bin/env python\n\"\"\"script doc\"\"\""),
                "shebang 后的 docstring 应可提取");
        assertEquals("doc", FileCommentExtractor.extract("'''doc'''"),
                "单引号 docstring 应可提取");
    }

    @Test
    @DisplayName("正常：package/import 声明跳过后提取类文档注释")
    void skipsPackageAndImports() {
        assertEquals("JSON压缩器",
                FileCommentExtractor.extract("package com.acme.json;\n\n/**\n * JSON压缩器\n * @author 拒绝者\n */\npublic class JsonCompressor {}"),
                "package 后的 Javadoc 应可提取");
        assertEquals("类说明",
                FileCommentExtractor.extract("package com.acme;\nimport java.util.List;\nimport java.io.File;\n// 类说明\npublic class Foo {}"),
                "import 后的行注释应可提取");
        assertEquals("service",
                FileCommentExtractor.extract("using System;\nusing System.IO;\n// service\nclass C {}"),
                "C# using 后的注释应可提取");
    }

    @Test
    @DisplayName("边界：大量 import 后注释仍可命中（声明行不占扫描预算，切分无段数上限）")
    void hitsCommentAfterManyImports() {
        final String imports = "import a.B;\n".repeat(60);
        assertEquals("Prism 工具窗口",
                FileCommentExtractor.extract("package com.acme.ui;\n" + imports
                        + "/**\n * Prism 工具窗口\n *\n * @author 拒绝者\n */\npublic class MainToolWindowFactory {}"),
                "60 个 import 后的 Javadoc 应可命中");
    }

    @Test
    @DisplayName("正常：shebang 跳过后提取 # 注释")
    void skipsShebang() {
        assertEquals("coding comment", FileCommentExtractor.extract("#!/usr/bin/env python\n# coding comment"),
                "shebang 应被跳过");
    }

    @Test
    @DisplayName("正常：XML 声明与 DOCTYPE 跳过后提取块注释")
    void skipsXmlDeclarations() {
        assertEquals("config file", FileCommentExtractor.extract("<?xml version=\"1.0\"?>\n<!-- config file -->"),
                "XML 声明应被跳过");
        assertEquals("page", FileCommentExtractor.extract("<!DOCTYPE html>\n<!-- page -->"),
                "DOCTYPE 应被跳过");
    }

    @Test
    @DisplayName("正常：多行块注释取首行非空文本")
    void extractsMultiLineBlock() {
        assertEquals("Licensed Apache",
                FileCommentExtractor.extract("/*\n * Licensed Apache\n * Version 2.0\n */"),
                "块注释首行文本不符");
        assertEquals("second line", FileCommentExtractor.extract("/*\n *\n * second line\n */"),
                "块内空行应被跳过");
        assertEquals("multi line html", FileCommentExtractor.extract("<!--\n  multi line html\n-->"),
                "HTML 块注释提取不符");
    }

    @Test
    @DisplayName("正常：未闭合块注释有文本时仍可提取")
    void extractsUnclosedBlock() {
        assertEquals("no end", FileCommentExtractor.extract("/* no end"), "未闭合块注释文本应可提取");
    }

    @Test
    @DisplayName("边界：扫描行数耗尽返回 null")
    void stopsAtScanLimit() {
        final String input = "// \n".repeat(12) + "// real";
        assertNull(FileCommentExtractor.extract(input), "超出扫描行数上限应返回 null");
    }

    @Test
    @DisplayName("正常：块注释内 Javadoc 标签行跳过取真描述")
    void skipsJavadocTagLines() {
        assertEquals("真描述", FileCommentExtractor.extract("/*\n * @author 拒绝者\n * 真描述\n */"),
                "@ 标签行应跳过并取后续描述行");
    }

    @Test
    @DisplayName("正常：截断窗口内括号补充段整体回退不加省略号")
    void fallsBackBeforeParentheses() {
        assertEquals("a".repeat(20), FileCommentExtractor.extract("// " + "a".repeat(20) + "（补充说明）更多内容"),
                "主名完整时应丢弃括号段且不加省略号");
        assertEquals("ab（补充）超长超过二十码点的内容继续超…",
                FileCommentExtractor.extract("// ab（补充）超长超过二十码点的内容继续超长"),
                "主段过短不回退，硬截并补省略号");
    }

    @Test
    @DisplayName("正常：恰好 20 码点不截断")
    void keepsExactLimit() {
        final String comment = "a".repeat(20);
        assertEquals(comment, FileCommentExtractor.extract("// " + comment), "恰好上限不应截断");
    }

    @Test
    @DisplayName("正常：超出 20 码点截断并补省略号")
    void truncatesBeyondLimit() {
        final String result = FileCommentExtractor.extract("// " + "a".repeat(60));
        assertEquals("a".repeat(20) + "…", result, "超长注释应截断至 20 码点并补省略号");
        final String cjk = FileCommentExtractor.extract("// " + "汉".repeat(60));
        assertEquals("汉".repeat(20) + "…", cjk, "中文应按码点截断");
    }

    @Test
    @DisplayName("边界：截断不切断 emoji 代理对")
    void truncatesWithoutBreakingSurrogatePair() {
        final String input = "// " + "a".repeat(19) + "😀" + "b".repeat(10);
        final String result = FileCommentExtractor.extract(input);
        assertEquals("a".repeat(19) + "😀…", result, "截断点不应切断代理对");
        assertTrue(result.codePointCount(0, result.length()) <= 21, "截断后码点数应在上限加省略号之内");
    }
}
