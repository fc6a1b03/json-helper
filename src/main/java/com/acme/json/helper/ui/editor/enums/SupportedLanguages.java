package com.acme.json.helper.ui.editor.enums;

import com.acme.json.helper.common.enums.AnyFile;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.json.JsonFileType;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import org.jetbrains.yaml.YAMLFileType;
import org.toml.lang.psi.TomlFileType;

import java.util.Arrays;

/**
 * 支持语言
 * @author xuhaifeng
 * @date 2025-04-22
 */
public enum SupportedLanguages {
    YAML(YAMLFileType.YML, AnyFile.YAML),
    XML(XmlFileType.INSTANCE, AnyFile.XML),
    TOML(TomlFileType.INSTANCE, AnyFile.TOML),
    JSON(JsonFileType.INSTANCE, AnyFile.JSON),
    CLASS(JavaFileType.INSTANCE, AnyFile.CLASS),
    RECORD(JavaFileType.INSTANCE, AnyFile.RECORD),
    Text(PlainTextFileType.INSTANCE, AnyFile.Text),
    URL_PARAMS(PlainTextFileType.INSTANCE, AnyFile.URL_PARAMS),
    PROPERTIES(PropertiesFileType.INSTANCE, AnyFile.PROPERTIES);
    private final AnyFile extension;
    private final LanguageFileType fileType;

    SupportedLanguages(final LanguageFileType fileType, final AnyFile extension) {
        this.fileType = fileType;
        this.extension = extension;
    }

    /**
     * 通过任何文件获取
     *
     * @param any 任何
     * @return {@link SupportedLanguages }
     */
    public static SupportedLanguages getByAnyFile(final AnyFile any) {
        return Arrays.stream(SupportedLanguages.values())
                .filter(item -> item.extension.equals(any))
                .findFirst().orElse(SupportedLanguages.Text);
    }

    /**
     * 获取文件类型
     *
     * @return {@link T }
     */
    @SuppressWarnings("unchecked")
    public <T extends LanguageFileType> T getFileType() {
        return (T) fileType;
    }

    /**
     * 获得拓展
     *
     * @return {@link AnyFile }
     */
    public AnyFile getExtension() {
        return extension;
    }
}