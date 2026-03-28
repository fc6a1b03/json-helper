# Json Helper - AI Agent Guide

## Project Overview

**Json Helper** 是一个面向 IntelliJ IDEA 的 JSON 效率插件，提供强大的 JSON 数据操作和转换能力。

**项目元数据：**

| 属性          | 值                                       |
|-------------|-----------------------------------------|
| Group ID    | `com.acme`                              |
| Artifact ID | `json-helper`                           |
| 插件 ID       | `com.acme.json.helper`                  |
| 作者          | 拒绝者                                     |
| 许可证         | MIT                                     |
| GitHub      | https://github.com/fc6a1b03/json-helper |

**核心功能：**

- JSON 编辑、格式化、压缩、修复、转义与反转义
- 从 JSON 生成 Java Class / Record
- 从 Java 类字段复制 JSON 结构
- JsonPath / JMESPath 查询与树形浏览
- URL、JWT、本地文件路径、Web 路径自动解析为 JSON
- JSON 与 XML / YAML / TOML / Properties / CSV / XLSX / Base64 / URL Params 互转
- Search Everywhere 集成：项目搜索与 HTTP 请求文件搜索
- 代码截图复制

## Technology Stack

- **语言**: Java 25
- **构建工具**: Gradle 9.4.1 + IntelliJ Platform Gradle Plugin 2.13.1
- **目标平台**: IntelliJ IDEA 2026.1 (Build 261+)
- **UI 框架**: IntelliJ Platform UI (Swing-based)

### 依赖版本

| 依赖                   | 版本     | 用途           |
|----------------------|--------|--------------|
| IntelliJ Platform    | 2026.1 | IDE 集成       |
| Hutool               | 5.8.44 | 工具库          |
| Jackson              | 3.1.0  | JSON/数据格式处理  |
| Fastjson2            | 2.0.61 | JSON 解析      |
| Auth0 JWT            | 4.5.1  | JWT Token 解析 |
| Apache POI           | 5.5.1  | Excel 文件支持   |
| json-repair          | 0.4.0  | 畸形 JSON 修复   |
| JToon                | 1.0.9  | TOON 格式处理    |
| Apache Commons Lang3 | 3.20.0 | 通用工具         |

### 捆绑插件依赖

- `org.toml.lang` - TOML 语言支持
- `com.intellij.java` - Java 语言支持
- `com.intellij.gradle` - Gradle 支持
- `com.intellij.properties` - Properties 文件支持
- `com.intellij.modules.json` - JSON 模块
- `org.jetbrains.plugins.yaml` - YAML 支持

## Project Structure

```
src/main/
├── java/com/acme/json/helper/
│   ├── common/                    # 通用工具类和枚举
│   │   ├── enums/
│   │   │   ├── AnyFile.java       # 支持的文件类型枚举
│   │   │   └── SupportedLanguages.java
│   │   ├── ActionEventCheck.java  # 动作事件检查（sealed interface）
│   │   ├── Clipboard.java         # 剪贴板操作
│   │   ├── CollectionTypeHandler.java
│   │   ├── TemporalTypeHandler.java
│   │   └── UastSupported.java     # UAST (Universal AST) 支持
│   ├── core/                      # 核心功能模块
│   │   ├── editor/                # 编辑器集成
│   │   │   ├── FileDropHandler.java
│   │   │   ├── JsonEditorPushProvider.java
│   │   │   └── record/EditorState.java
│   │   ├── json/                  # JSON 处理
│   │   │   ├── JsonCompressor.java
│   │   │   ├── JsonEscaper.java
│   │   │   ├── JsonFormatter.java
│   │   │   ├── JsonOperation.java      # Sealed interface
│   │   │   ├── JsonRepairer.java
│   │   │   ├── JsonSearchEngine.java
│   │   │   └── JsonUnEscaper.java
│   │   ├── notice/
│   │   │   └── Notifier.java      # 通知系统
│   │   ├── parser/                # 解析逻辑
│   │   │   ├── AnyParser.java     # 自动格式检测
│   │   │   ├── ClassParser.java   # Java 类解析
│   │   │   ├── JsonNodeParser.java
│   │   │   ├── JsonParser.java    # JSON 转换分发
│   │   │   ├── JwtParser.java
│   │   │   ├── PathParser.java
│   │   │   ├── TypeResolver.java
│   │   │   └── converter/         # 格式转换器
│   │   │       ├── Base64Converter.java
│   │   │       ├── ClassConverter.java
│   │   │       ├── CsvConverter.java
│   │   │       ├── DataFormatConverter.java
│   │   │       ├── JavaStructure.java
│   │   │       ├── PropertiesConverter.java
│   │   │       ├── RecordConverter.java
│   │   │       ├── TableStructure.java
│   │   │       ├── TomlConverter.java
│   │   │       ├── ToonConverter.java
│   │   │       ├── UrlParamsConverter.java
│   │   │       ├── XlsxConverter.java
│   │   │       ├── XmlConverter.java
│   │   │       └── YamlConverter.java
│   │   ├── screenshot/
│   │   │   └── CodeScreenshotSupplier.java
│   │   ├── search/                # 搜索功能
│   │   │   ├── cache/SearchCache.java
│   │   │   ├── HttpRequestSearch.java
│   │   │   ├── ProjectSearch.java
│   │   │   └── item/
│   │   │       ├── HttpRequestItem.java
│   │   │       └── ProjectNavigationItem.java
│   │   └── settings/              # 插件设置
│   │       ├── PluginSettings.java
│   │       └── PluginSettingsState.java
│   └── ui/                        # 用户界面
│       ├── MainToolWindowFactory.java
│       ├── action/                # 动作
│       │   ├── json/
│       │   │   ├── CopyJsonAction.java
│       │   │   ├── CreateClassFromJsonAction.java
│       │   │   └── JsonHelperAction.java
│       │   ├── screenshot/
│       │   │   └── CodeScreenshot.java
│       │   └── search/
│       │       ├── HttpRequestSearchAction.java
│       │       └── ProjectSearchAction.java
│       ├── dialog/                # 对话框
│       │   ├── ConvertAnyDialog.java
│       │   └── CreateClassDialog.java
│       ├── editor/                # 编辑器组件
│       │   ├── CustomizeEditorFactory.java
│       │   └── Editor.java        # Sealed interface
│       ├── panel/                 # UI 面板
│       │   ├── JsonTreePanel.java
│       │   └── MainPanel.java
│       └── search/                # 搜索 UI
│           ├── HttpRequestSearchFactory.java
│           └── ProjectSearchFactory.java
└── resources/
    ├── META-INF/
    │   ├── plugin.xml             # 插件配置
    │   └── pluginIcon.svg         # 插件图标
    ├── icons/
    │   └── pluginIcon.svg
    └── messages/                  # 国际化
        ├── JsonHelperBundle.properties      # 英文
        └── JsonHelperBundle_zh_CN.properties # 中文
```

## Build Commands

### 开发命令

```bash
# 打印版本信息
gradle printAllVersions
gradle printVersion
gradle printJavaVersion
gradle printGradleVersion

# 运行 IDE 进行测试
gradle runIde

# 编译
gradle compileJava

# 构建插件
gradle buildPlugin

# 完整构建并校验
gradle clean buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
```

### 输出产物

构建完成后，插件 ZIP 文件位于：

```
build/distributions/json-helper-x.x.x.zip
```

安装方式：在 IntelliJ IDEA 中 `Settings` -> `Plugins` -> 齿轮按钮 -> `Install Plugin from Disk...`

## Code Style Guidelines

### 语言和注释

- **主要文档语言**: 中文 (zh-CN)
- **代码注释**: 中文
- **变量/方法名**: 英文 (camelCase)
- **类名**: 英文 (PascalCase)
- **资源文件**: 双语支持（英文 + 中文）

### Java 编码规范

1. **使用 Sealed Interfaces**：项目使用 Java 25 sealed interfaces
   ```java
   public sealed interface JsonOperation permits 
       JsonCompressor, JsonEscaper, JsonFormatter, 
       JsonSearchEngine, JsonUnEscaper, JsonRepairer { }
   ```

2. **模式匹配 switch**：使用现代模式匹配语法
   ```java
   switch (someValue) {
       case final ActionEventCheck.Check.Failed failed -> failed.action().run();
       case final ActionEventCheck.Check.Success ignored -> { /* ... */ }
   }
   ```

3. **使用 `var` 进行局部变量类型推断**

4. **使用 `Opt` from Hutool** 进行 null-safe 操作
   ```java
   Opt.ofNullable(value).map(...).ifPresent(...);
   ```

5. **Javadoc 格式**：包含作者和日期
   ```java
   /**
    * 功能描述
    * @author 拒绝者
    * @date 2025-01-18
    */
   ```

6. **Final 参数**：适当情况下方法参数标记为 `final`

7. **使用 `Boolean.TRUE`/`Boolean.FALSE`** 替代原始 `true`/`false`

8. **Record 类型**：广泛使用 record 作为数据传输对象
   ```java
   public record EditorState(Integer editorId, String content) { }
   ```

### 性能要求

**所有代码必须满足高性能和低损耗要求：**

1. **避免内存泄漏**
    - 及时释放资源（流、连接、临时文件）
    - 使用 `try-with-resources`
    - 避免在静态集合中持有大对象引用

2. **大数据量处理**
    - 使用流式处理（Streaming）
    - 避免将整个文件加载到内存
    - 对超过 1MB 的 JSON 使用增量解析

3. **缓存策略**
    - 使用 `SearchCache` 等现有缓存机制
    - 使用 project service 托管缓存生命周期
    - 设置合理的过期时间和大小限制

4. **异步执行**
    - 耗时操作在后台线程执行
    - 使用 `Task.Backgroundable` 显示进度
    - 避免阻塞 UI 线程

5. **对象复用**
    - 优先使用 `EnumMap` 而非线性查找
    - 避免在循环中创建临时对象
    - 使用 `StringBuilder` 进行字符串拼接

6. **算法优化**
    - 选择最优时间复杂度算法
    - 避免重复计算
    - 使用合适的数据结构

7. **延迟加载**
    - 懒加载（Lazy Loading）重型组件
    - 分页展示大量数据

### IntelliJ Platform 编码模式

1. **Actions**: 继承 `AnAction`，重写 `actionPerformed` 和 `update`
   ```java
   public class CopyJsonAction extends AnAction {
       @Override
       public @NotNull ActionUpdateThread getActionUpdateThread() {
           return ActionUpdateThread.BGT;
       }
       
       @Override
       public void update(@NotNull final AnActionEvent e) { }
       
       @Override
       public void actionPerformed(@NotNull final AnActionEvent e) { }
   }
   ```

2. **Tool Windows**: 实现 `ToolWindowFactory`

3. **Settings**: 使用 `ApplicationConfigurable` 和 `ApplicationService`

4. **Notifications**: 使用 `Notifier` 工具类

5. **线程安全**
    - UI 更新必须在 EDT 执行：使用 `ApplicationManager.getApplication().invokeLater()`
    - 后台任务使用 `Task.Backgroundable`
    - PSI 读取使用官方读锁 API

## Internationalization (i18n)

所有用户可见字符串必须外部化到资源文件：

- **基础资源**: `messages.JsonHelperBundle.properties` (英文)
- **中文资源**: `messages.JsonHelperBundle_zh_CN.properties`

访问模式：

```java
private static final ResourceBundle BUNDLE =
        ResourceBundle.getBundle("messages.JsonHelperBundle");

String text = BUNDLE.getString("key.name");
```

## Testing

**注意**: 本项目目前没有自动化测试。测试通过以下方式手动完成：

1. 运行 `gradle runIde` 启动带插件的 IDE 实例
2. 测试右键菜单中的所有动作
3. 验证工具窗口功能
4. 检查各种格式转换

## CI/CD

GitHub Actions 工作流 (`.github/workflows/build_jar.yml`)：

- **触发方式**: 手动 (`workflow_dispatch`)
- **运行环境**: `ubuntu-latest`
- **Java 版本**: 25
- **Gradle 版本**: 9.4.1
- **流程**:
    1. 从 settings.gradle 提取版本号
    2. 设置 JDK 25
    3. 设置 Gradle 9.4.1
    4. 缓存 IntelliJ Platform 构件
    5. 执行构建和校验
    6. 上传 ZIP 工件
    7. 创建 GitHub Release

## Plugin Configuration

`META-INF/plugin.xml` 关键配置：

- **Actions**: 右键菜单项、键盘快捷键
- **Tool Window**: "Json Helper" 窗口锚定在右侧
- **Settings**: Tools 菜单下的插件配置
- **Search Contributors**: Search Everywhere 集成

## Architecture Patterns

### 转换器注册表模式

使用 `EnumMap` 实现高效的格式转换分发：

```java
public class JsonParser {
    private static final Map<AnyFile, DataFormatConverter> CONVERTERS = createConverters();

    private static Map<AnyFile, DataFormatConverter> createConverters() {
        final EnumMap<AnyFile, DataFormatConverter> converters = new EnumMap<>(AnyFile.class);
        register(converters, AnyFile.XML, new XmlConverter());
        // ... 其他转换器
        return Map.copyOf(converters);
    }
}
```

### 状态管理

编辑器状态使用 record 存储，支持序列化/反序列化：

```java
public record EditorState(Integer editorId, String content) {
    // Base64 编码/解码
    public static String encode(List<EditorState> states) {
    }

    public static List<EditorState> decode(String data) {
    }
}
```

### 通知系统

`Notifier` 类提供线程安全的通知：

- 自动检测当前线程环境
- 智能选择执行方式（直接执行或切换到 EDT）
- 静默处理异常避免影响主流程

## Development Environment Setup

### 前置要求

- JDK 25 (Temurin 推荐)
- Gradle 9.4.1
- IntelliJ IDEA 2026.1+

### 推荐 JVM 参数 (runIde)

```
-Xms1g
-Xmx4g
-XX:+UseZGC
-Dfile.encoding=UTF-8
-Duser.timezone=GMT+8
```

### Gradle 优化配置

```properties
org.gradle.daemon=true
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.vfs.watch=true
org.gradle.incremental=true
org.gradle.workers.max=20
```

## Common Tasks

### 添加新的 JSON 操作

1. 创建类实现 `JsonOperation` (sealed interface)
2. 在 `JsonOperation.java` 的 `permits` 子句中添加
3. 在 `MainPanel.java` 或适当的面板/动作类中注册 UI
4. 在两个属性文件中添加 i18n 键

### 添加新的格式转换器

1. 在 `core/parser/converter/` 创建转换器类
2. 实现 `DataFormatConverter` 接口
3. 在 `JsonParser.java` 的 `createConverters()` 中注册
4. 在 `AnyFile.java` 枚举中添加新文件类型（如果需要）
5. 更新 `SupportedLanguages.java`（如果需要）

### 添加新的 Action

1. 在 `ui/action/` 创建动作类
2. 继承 `AnAction`
3. 在 `META-INF/plugin.xml` 中注册 `<action>` 元素
4. 在属性文件中添加 i18n 字符串

## Security Considerations

1. **本地数据处理**: 所有 JSON、Java 代码处理在本地完成，无外部数据传输
2. **HTTP 请求搜索**: 仅用于本地项目分析
3. **剪贴板操作**: 需要用户显式触发
4. **文件操作**: 处理前验证 JSON 格式

## Related Documentation

- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [API Changes List 2026](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)
- [Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
- [Audit Document](doc/2026.1-java25-audit.md)

## License

MIT License - 详见 `LICENSE` 文件
