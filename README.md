# Json Helper Plugin

Json Helper 是一个 IntelliJ IDEA 插件，旨在提供 JSON 数据的格式化、压缩、转义、去转义、校验、生成等功能。通过右键菜单和工具窗口，用户可以方便地处理
JSON 数据，并支持 `JsonPath` 和 `JMESPath` 的搜索功能。

## 运行环境

- **JDK**: 21 或更高稳定版本
- **Gradle**: 8.12 或更高版本
- **IntelliJ IDEA**: 2023.1 或更高版本

## 项目结构

```plaintext
├── src
│   └── main
│       ├── java
│       │   └── com
│       │       └── acme
│       │           └── json
│       │               └── helper
│       │                   ├── core
│       │                   └── ui
│       └── resources
│           ├── icons
│           ├── messages
│           └── META-INF
```

- **core**: 包含插件的核心功能实现，如 JSON 格式化、压缩、转义、去转义、搜索等。
- **ui**: 包含插件的用户界面实现，如工具窗口和菜单。
- **icons**: 存放插件的图标资源。
- **messages**: 存放插件的多语言资源文件。
- **META-INF**: 存放插件的元数据文件。

## 调试步骤

1. **运行插件**:
   ```bash
   ./gradlew runIde
   ```
   这将启动一个带有插件的 IntelliJ IDEA 实例，供你测试和调试。

2. **打包插件**:
   ```bash
   ./gradlew buildPlugin
   ```
   这将生成一个 `.zip` 文件，包含插件的所有资源和代码，可以发布到插件市场或本地安装。

3. **清理构建**:
   ```bash
   ./gradlew clean
   ```
   这将清理构建目录，删除所有生成的文件。

## 版本迭代

### 0.1.0

- 创建项目，实现 JSON 的格式化、压缩、转义、去转义等基础核心功能。

### 0.1.5

- 增加工具窗口，将核心功能以右键菜单的形式呈现。
- 支持 `JsonPath` 及 `JMESPath` 的搜索功能。

## 贡献

欢迎贡献代码！请遵循以下步骤：

1. Fork 项目。
2. 创建你的分支 (`git checkout -b feature/YourFeatureName`)。
3. 提交你的更改 (`git commit -m 'Add some feature'`)。
4. 推送分支 (`git push origin feature/YourFeatureName`)。
5. 创建一个 Pull Request。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。