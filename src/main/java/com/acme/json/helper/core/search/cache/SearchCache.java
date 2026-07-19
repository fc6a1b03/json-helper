package com.acme.json.helper.core.search.cache;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.search.item.HttpRequestItem;
import com.acme.json.helper.core.search.item.PortSearchItem;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索缓存
 *
 * @author 拒绝者
 * @date 2025-11-05
 */
@Service(Service.Level.PROJECT)
public final class SearchCache implements Supplier<SequencedCollection<ProjectNavigationItem>>, Disposable {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * Git 仓库缓存容量（LRU 策略，超出后淘汰最久未访问项）
     */
    private static final int GIT_CACHE_CAPACITY = 20;
    /**
     * 应用图标缓存（按可执行路径缓存系统提取图标，避免重复系统调用）
     */
    private static final Map<String, Icon> APP_ICON_CACHE = new ConcurrentHashMap<>();
    /**
     * HTTP 文件扫描最大目录深度
     */
    private static final int MAX_SCAN_DEPTH = 3;
    /**
     * 系统命令执行超时（毫秒）
     */
    private static final int COMMAND_TIMEOUT_MS = 3000;
    /**
     * 端口缓存刷新间隔（毫秒）
     * <p>
     * Search Everywhere 每次输入都会触发取数，10 秒 TTL 保证连续搜索仅一次系统命令调用；
     * 杀进程后通过 {@link #invalidatePortCache()} 强制刷新，数据时效可控
     */
    private static final long PORT_CACHE_REFRESH_INTERVAL = 10000;
    /**
     * Windows netstat 输出解析模式（仅匹配 LISTENING 状态的监听端口，过滤外向连接噪声）
     */
    private static final Pattern WIN_NETSTAT_PATTERN = Pattern.compile(
            "^\\s*TCP\\s+[^\\s:]+:(\\d+)\\s+[^\\s:]+:\\d+\\s+LISTENING\\s+(\\d+)", Pattern.CASE_INSENSITIVE
    );
    /**
     * Unix netstat 输出解析模式
     */
    private static final Pattern UNIX_NETSTAT_PATTERN = Pattern.compile(
            "^\\S+\\s+\\S+\\s+\\S+\\s+\\S+:(\\d+).*\\s+(\\d+)/", Pattern.CASE_INSENSITIVE);
    /**
     * Linux ss 输出解析模式（iproute2 自带，现代发行版替代 netstat 的兜底命令）
     */
    private static final Pattern SS_LISTEN_PATTERN = Pattern.compile(
            "^LISTEN\\s+\\S+\\s+\\S+\\s+\\S+:(\\d+)\\s+.*users:\\(\\(\"([^\"]*)\",pid=(\\d+)", Pattern.CASE_INSENSITIVE);
    /**
     * Git URL 前缀清理模式（git@ / http(s):// / ssh:// / git://）
     */
    private static final Pattern GIT_URL_PREFIX_PATTERN = Pattern.compile("^(git@|https?://|ssh://|git://)");
    /**
     * Git URL 主机部分模式（主机名及其后的冒号或斜杠）
     */
    private static final Pattern GIT_URL_HOST_PATTERN = Pattern.compile("^[^/]+[:/]");
    /**
     * Git URL 结尾 .git 后缀模式
     */
    private static final Pattern GIT_SUFFIX_PATTERN = Pattern.compile("\\.git$");
    /**
     * Git仓库缓存 (使用LRU策略，最多保留 {@value #GIT_CACHE_CAPACITY} 个)
     */
    private final LinkedHashMap<String, Long> gitRepositoryCache = new LinkedHashMap<>(GIT_CACHE_CAPACITY, 0.75f, Boolean.TRUE) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Long> eldest) {
            return this.size() > GIT_CACHE_CAPACITY;
        }
    };
    /**
     * HTTP请求文件缓存（Search Everywhere 在后台线程读取，需保证可见性）
     */
    private volatile SequencedCollection<HttpRequestItem> httpCached;
    /**
     * 项目缓存
     */
    private volatile SequencedCollection<ProjectNavigationItem> cached;
    /**
     * 全量端口进程缓存
     */
    private volatile List<PortSearchItem> allPortsCached;
    /**
     * 全量端口缓存时间戳
     */
    private volatile long allPortsCacheTime;
    /**
     * IDEA 子进程端口缓存
     */
    private volatile List<PortSearchItem> ideaChildPortsCached;
    /**
     * IDEA 子进程端口缓存时间戳
     */
    private volatile long ideaChildPortsCacheTime;
    /**
     * 端口缓存刷新锁（防止并发重复执行系统命令）
     */
    private final Object portCacheLock = new Object();

    public static SearchCache getInstance(@NotNull final Project project) {
        return project.getService(SearchCache.class);
    }

    /**
     * 解析应用图标（提取可执行文件内嵌的系统图标，失败时回退默认图标）
     * <p>
     * 在后台线程加载端口数据时调用，按可执行路径缓存，渲染层零成本
     *
     * @param path 应用路径或完整命令行
     * @return 应用图标
     */
    private static Icon resolveAppIcon(final String path) {
        // 命令行形态取首段作为可执行路径
        final String executable = StrUtil.isEmpty(path) ? "" : path.split(" ")[0];
        if (executable.isEmpty()) {
            return AllIcons.Actions.Execute;
        }
        return APP_ICON_CACHE.computeIfAbsent(executable, key -> {
            try {
                if (!new File(key).exists()) {
                    return AllIcons.Actions.Execute;
                }
                final Icon icon = FileSystemView.getFileSystemView().getSystemIcon(new File(key));
                return Objects.nonNull(icon) ? icon : AllIcons.Actions.Execute;
            } catch (final Exception ignored) {
                return AllIcons.Actions.Execute;
            }
        });
    }

    /**
     * 从Git URL提取组织和仓库名
     *
     * @param url Git URL
     * @return 组织/仓库对
     */
    private static String[] extractOrgAndRepo(final String url) {
        // 清理URL协议前缀与 .git 后缀
        String cleanUrl = GIT_SUFFIX_PATTERN.matcher(GIT_URL_PREFIX_PATTERN.matcher(url).replaceFirst("")).replaceFirst("");
        // 处理主机名后的路径
        cleanUrl = GIT_URL_HOST_PATTERN.matcher(cleanUrl).replaceFirst("");
        cleanUrl = GIT_URL_HOST_PATTERN.matcher(cleanUrl).replaceFirst("");
        final String[] parts = cleanUrl.split("/");
        final String unknownName = BUNDLE.getString("search.unknown.name");
        return parts.length >= 2 ? new String[]{parts[parts.length - 2], parts[parts.length - 1]} : new String[]{unknownName, unknownName};
    }

    /**
     * 获取缓存项目
     *
     * @return {@link SequencedCollection}<{@link ProjectNavigationItem}>
     */
    @Override
    public SequencedCollection<ProjectNavigationItem> get() {
        SequencedCollection<ProjectNavigationItem> snapshot = this.cached;
        if (Objects.isNull(snapshot)) {
            synchronized (this) {
                snapshot = this.cached;
                if (Objects.isNull(snapshot)) {
                    snapshot = this.load();
                    this.cached = snapshot;
                }
            }
        }
        return snapshot;
    }

    /**
     * 加载缓存项目
     *
     * @return {@link SequencedCollection}<{@link ProjectNavigationItem}>
     */
    public SequencedCollection<ProjectNavigationItem> load() {
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        final LinkedHashSet<ProjectNavigationItem> result = new LinkedHashSet<>();
        final Set<String> openedPaths = new HashSet<>(openProjects.length);
        for (final Project openProject : openProjects) {
            result.add(ProjectNavigationItem.opened(openProject.getName(), openProject.getPresentableUrl(), 0));
            final String presentableUrl = openProject.getPresentableUrl();
            if (StrUtil.isNotEmpty(presentableUrl)) {
                openedPaths.add(presentableUrl);
            }
        }
        final RecentProjectsManagerBase recentProjectsManager = (RecentProjectsManagerBase) RecentProjectsManager.getInstance();
        for (final String recentPath : recentProjectsManager.getRecentPaths()) {
            if (Objects.isNull(recentPath) || openedPaths.contains(recentPath)) {
                continue;
            }
            result.add(ProjectNavigationItem.recent(recentProjectsManager.getProjectName(recentPath), recentPath, 0));
        }
        synchronized (this.gitRepositoryCache) {
            for (final String repositoryUrl : this.gitRepositoryCache.keySet()) {
                if (Objects.nonNull(repositoryUrl)) {
                    result.add(this.createGitRepositoryItem(repositoryUrl));
                }
            }
        }
        return result;
    }

    /**
     * 获取HTTP请求文件缓存
     *
     * @return HTTP请求文件列表
     */
    public SequencedCollection<HttpRequestItem> getHttp() {
        SequencedCollection<HttpRequestItem> snapshot = this.httpCached;
        if (Objects.isNull(snapshot)) {
            synchronized (this) {
                snapshot = this.httpCached;
                if (Objects.isNull(snapshot)) {
                    snapshot = this.loadHttp(new LinkedHashSet<>());
                    this.httpCached = snapshot;
                }
            }
        }
        return snapshot;
    }

    /**
     * 收集 Scratches 目录中的 HTTP 请求文件
     * <p>
     * 遍历 Scratches 目录及其子目录, 收集所有 HTTP 请求文件, 并将它们添加到目标集合中.
     *
     * @param httpFiles 目标集合, 用于存储收集到的 HTTP 请求文件
     * @return 收集到的 HTTP 请求文件集合
     */
    @SuppressWarnings("DataFlowIssue")
    private SequencedCollection<HttpRequestItem> loadHttp(final SequencedCollection<HttpRequestItem> httpFiles) {
        final Path scratchPath = ApplicationManager.getApplication().runReadAction((Computable<Path>) () -> {
            try {
                return Opt.ofNullable(ScratchFileService.getInstance().getVirtualFile(ScratchRootType.getInstance()))
                        .filter(item -> Objects.requireNonNull(item).exists()).map(item -> Objects.requireNonNull(item).toNioPath()).orElse(null);
            } catch (final Exception ignored) {
                return null;
            }
        });
        if (Objects.nonNull(scratchPath)) {
            this.walkDirectory(scratchPath, httpFiles, MAX_SCAN_DEPTH);
        }
        return httpFiles;
    }

    /**
     * 遍历目录收集 HTTP 文件
     * <p>
     * 递归遍历指定目录及其子目录, 收集所有扩展名为 "http" 的文件, 并将它们添加到目标集合中.
     *
     * @param directory 目录路径
     * @param httpFiles 目标集合, 用于存储找到的 HTTP 文件信息
     * @param maxDepth  最大遍历深度
     * @throws NullPointerException 如果 directory 或 httpFiles 为 null
     */
    @SuppressWarnings("SameParameterValue")
    private void walkDirectory(@NotNull final Path directory, final SequencedCollection<HttpRequestItem> httpFiles, final int maxDepth) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) return;
        try {
            Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
                /**
                 * 访问文件时处理 HTTP 文件
                 * <p>
                 * 遍历文件系统中的文件, 当遇到扩展名为 "http" 的文件时, 将其添加到 HTTP 文件列表中
                 * @param file  当前访问的文件路径
                 * @param attrs 文件属性
                 * @return 继续遍历文件系统
                 */
                @Override
                public @NotNull FileVisitResult visitFile(final @NotNull Path file, final @NotNull BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file) && FileUtilRt.extensionEquals(file.toString(), "http")) {
                        httpFiles.add(new HttpRequestItem(file.getFileName().toString(), file.toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                /**
                 * 处理文件访问失败的情况
                 * <p>
                 * 当访问文件时发生错误, 此方法会被调用. 根据错误情况决定是否继续遍历文件系统.
                 * @param file 发生错误的文件路径
                 * @param exc  引起访问失败的异常
                 * @return 文件访问结果, 如果继续遍历则返回 {@link FileVisitResult#CONTINUE}
                 */
                @Override
                public @NotNull FileVisitResult visitFileFailed(final @NotNull Path file, final @NotNull IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException ignored) {
            // 忽略IO异常，继续执行
        }
    }

    /**
     * 添加Git仓库到缓存
     *
     * @param repositoryUrl 仓库URL
     */
    public void addGitRepository(final String repositoryUrl) {
        if (StrUtil.isBlank(repositoryUrl) || !ProjectNavigationItem.GIT_URL_PATTERN.matcher(repositoryUrl).matches()) {
            return;
        }
        synchronized (this.gitRepositoryCache) {
            this.gitRepositoryCache.put(repositoryUrl, System.currentTimeMillis());
        }
        this.cached = null;
    }

    /**
     * 移除Git仓库到缓存
     *
     * @param repositoryUrl 仓库URL
     */
    public void removeGitRepository(final String repositoryUrl) {
        synchronized (this.gitRepositoryCache) {
            this.gitRepositoryCache.remove(repositoryUrl);
        }
        this.cached = null;
    }

    /**
     * 创建Git仓库导航项
     *
     * @param url 仓库URL
     * @return 导航项
     */
    private ProjectNavigationItem createGitRepositoryItem(final String url) {
        final String[] orgRepo = extractOrgAndRepo(url);
        return ProjectNavigationItem.gitRepository(url, "%s/%s".formatted(orgRepo[0], orgRepo[1]), System.currentTimeMillis());
    }

    /**
     * 获取端口进程缓存（所有系统端口）
     * <p>
     * 使用 TTL 缓存避免 Search Everywhere 每次输入都执行系统命令
     *
     * @return 端口进程列表
     */
    public List<PortSearchItem> getPorts() {
        final long now = System.currentTimeMillis();
        final List<PortSearchItem> snapshot = this.allPortsCached;
        if (Objects.nonNull(snapshot) && now - this.allPortsCacheTime < PORT_CACHE_REFRESH_INTERVAL) {
            return snapshot;
        }
        synchronized (this.portCacheLock) {
            if (Objects.nonNull(this.allPortsCached) && System.currentTimeMillis() - this.allPortsCacheTime < PORT_CACHE_REFRESH_INTERVAL) {
                return this.allPortsCached;
            }
            final List<PortSearchItem> loaded = this.loadPorts(Boolean.FALSE);
            this.allPortsCached = loaded;
            this.allPortsCacheTime = System.currentTimeMillis();
            return loaded;
        }
    }

    /**
     * 获取 IDEA 子进程端口缓存
     * <p>
     * 用于默认显示，只返回当前 IDEA 实例启动的子进程
     *
     * @return IDEA 子进程端口列表
     */
    public List<PortSearchItem> getIdeaChildPorts() {
        final long now = System.currentTimeMillis();
        final List<PortSearchItem> snapshot = this.ideaChildPortsCached;
        if (Objects.nonNull(snapshot) && now - this.ideaChildPortsCacheTime < PORT_CACHE_REFRESH_INTERVAL) {
            return snapshot;
        }
        synchronized (this.portCacheLock) {
            if (Objects.nonNull(this.ideaChildPortsCached) && System.currentTimeMillis() - this.ideaChildPortsCacheTime < PORT_CACHE_REFRESH_INTERVAL) {
                return this.ideaChildPortsCached;
            }
            final List<PortSearchItem> loaded = this.loadPorts(Boolean.TRUE);
            this.ideaChildPortsCached = loaded;
            this.ideaChildPortsCacheTime = System.currentTimeMillis();
            return loaded;
        }
    }

    /**
     * 使端口缓存失效，强制下次重新加载
     */
    public void invalidatePortCache() {
        synchronized (this.portCacheLock) {
            this.allPortsCached = null;
            this.ideaChildPortsCached = null;
            this.allPortsCacheTime = 0;
            this.ideaChildPortsCacheTime = 0;
        }
    }

    /**
     * 加载端口进程信息
     * <p>
     * 使用 ProcessHandle API 精确匹配 IDEA 子进程
     *
     * @param onlyIdeaChild 是否只加载 IDEA 子进程
     * @return 端口进程列表
     */
    private List<PortSearchItem> loadPorts(final boolean onlyIdeaChild) {
        final List<PortSearchItem> result = new ArrayList<>();
        final Map<String, ProcessInfo> processMap = new HashMap<>();
        try {
            // 获取当前 IDEA 进程的所有子进程 PID
            final Set<Long> ideaChildPids = onlyIdeaChild ? getIdeaChildProcessIds() : Set.of();
            if (SystemInfo.isWindows) {
                loadWindowsPorts(result, processMap, ideaChildPids, onlyIdeaChild);
            } else {
                loadUnixPorts(result, processMap, ideaChildPids, onlyIdeaChild);
            }
        } catch (final Exception _) {
        }
        return result;
    }

    /**
     * 获取当前 IDEA 进程的所有子进程 PID（包括直接子进程和后代进程）
     *
     * @return 子进程 PID 集合
     */
    private Set<Long> getIdeaChildProcessIds() {
        final Set<Long> childPids = new HashSet<>();
        try {
            final ProcessHandle current = ProcessHandle.current();
            // 添加当前进程本身
            childPids.add(current.pid());
            // 获取所有后代进程（子进程 + 子进程的后代）
            current.descendants().forEach(ph -> childPids.add(ph.pid()));
        } catch (final Exception ignored) {
            // ProcessHandle 不支持时忽略
        }
        return childPids;
    }

    /**
     * 加载 Windows 系统端口信息
     * <p>
     * 注意：一个进程可能监听多个端口，使用 pid + port 作为唯一键
     *
     * @param ideaChildPids IDEA 子进程 PID 集合
     * @param onlyIdeaChild 是否只加载 IDEA 子进程
     */
    private void loadWindowsPorts(final List<PortSearchItem> result, final Map<String, ProcessInfo> processMap,
                                  final Set<Long> ideaChildPids, final boolean onlyIdeaChild) throws Exception {
        // 第1步：获取网络连接信息 (使用 IntelliJ ExecUtil)
        final GeneralCommandLine netstatCmd = new GeneralCommandLine("netstat", "-ano", "-p", "TCP");
        final ProcessOutput netstatOutput = ExecUtil.execAndGetOutput(netstatCmd, COMMAND_TIMEOUT_MS);
        if (netstatOutput.getExitCode() != 0) {
            throw new ExecutionException("netstat failed: " + netstatOutput.getStderr());
        }
        final Set<Long> foundPids = new HashSet<>();
        try (final BufferedReader reader = new BufferedReader(new StringReader(netstatOutput.getStdout()))) {
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                final Matcher matcher = WIN_NETSTAT_PATTERN.matcher(line);
                if (matcher.find()) {
                    final int port = Integer.parseInt(matcher.group(1));
                    final long pid = Long.parseLong(matcher.group(2));
                    if (pid > 0) {
                        // 如果只加载 IDEA 子进程，则过滤
                        if (onlyIdeaChild && !ideaChildPids.contains(pid)) {
                            continue;
                        }
                        foundPids.add(pid);
                        final String key = "%d:%d".formatted(pid, port);
                        processMap.put(key, new ProcessInfo(pid, port, BUNDLE.getString("search.pid.prefix") + pid, "", ideaChildPids.contains(pid)));
                    }
                }
            }
        }
        // 如果没有找到端口，返回空列表
        if (processMap.isEmpty()) return;
        // 第2步：获取进程名称（tasklist 一次性输出全部进程）
        final Map<Long, String> pidToName = new HashMap<>();
        try {
            final ProcessOutput tasklistOutput = ExecUtil.execAndGetOutput(new GeneralCommandLine("tasklist", "/fo", "csv", "/nh"), COMMAND_TIMEOUT_MS);
            if (tasklistOutput.getExitCode() == 0) {
                try (final BufferedReader reader = new BufferedReader(new StringReader(tasklistOutput.getStdout()))) {
                    String line;
                    while (Objects.nonNull(line = reader.readLine())) {
                        // CSV格式: "Image Name","PID","Session Name","Session#","Mem Usage"
                        final String[] parts = line.split(",");
                        if (parts.length >= 2) {
                            try {
                                final String name = parts[0].replace("\"", "").trim();
                                final long pid = Long.parseLong(parts[1].replace("\"", "").trim());
                                pidToName.put(pid, name);
                            } catch (final NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (final Exception ignored) {
        }
        // 第3步：获取可执行文件路径
        // wmic 已在 Windows 11 24H2 起被微软移除，改用 PowerShell CIM 一次性查询全部进程，避免逐 PID 创建进程
        final Map<Long, String> pidToPath = new HashMap<>();
        try {
            final GeneralCommandLine cimCmd = new GeneralCommandLine(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance Win32_Process | Select-Object ProcessId,ExecutablePath | ConvertTo-Csv -NoTypeInformation"
            );
            final ProcessOutput cimOutput = ExecUtil.execAndGetOutput(cimCmd, COMMAND_TIMEOUT_MS);
            if (cimOutput.getExitCode() == 0) {
                try (final BufferedReader reader = new BufferedReader(new StringReader(cimOutput.getStdout()))) {
                    String line;
                    while (Objects.nonNull(line = reader.readLine())) {
                        // CSV格式: "ProcessId","ExecutablePath"
                        final String[] parts = line.split("\",\"");
                        if (parts.length == 2) {
                            try {
                                final long pid = Long.parseLong(parts[0].replace("\"", "").trim());
                                if (foundPids.contains(pid)) {
                                    final String path = parts[1].replace("\"", "").trim();
                                    if (!path.isEmpty()) {
                                        pidToPath.put(pid, path);
                                    }
                                }
                            } catch (final NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (final Exception ignored) {
        }

        // 为每个端口创建结果项
        for (final ProcessInfo info : processMap.values()) {
            final String name = pidToName.getOrDefault(info.pid(), BUNDLE.getString("search.pid.prefix") + info.pid());
            final String path = pidToPath.getOrDefault(info.pid(), "");
            result.add(new PortSearchItem(info.port(), info.pid(), name, path, resolveAppIcon(path)));
        }
    }

    /**
     * 加载 Unix (Linux/Mac) 系统端口信息
     *
     * @param ideaChildPids IDEA 子进程 PID 集合
     * @param onlyIdeaChild 是否只加载 IDEA 子进程
     */
    private void loadUnixPorts(
            final List<PortSearchItem> result, final Map<String, ProcessInfo> processMap, final Set<Long> ideaChildPids, final boolean onlyIdeaChild
    ) {
        // 尝试使用 lsof 获取所有监听中的 TCP 端口（-sTCP:LISTEN 过滤外向连接）
        try {
            final GeneralCommandLine lsofCmd = new GeneralCommandLine("lsof", "-iTCP", "-sTCP:LISTEN", "-n", "-P");
            final ProcessOutput lsofOutput = ExecUtil.execAndGetOutput(lsofCmd, COMMAND_TIMEOUT_MS);
            if (lsofOutput.getExitCode() == 0) {
                try (final BufferedReader reader = new BufferedReader(new StringReader(lsofOutput.getStdout()))) {
                    String line;
                    boolean firstLine = Boolean.TRUE;
                    while (Objects.nonNull(line = reader.readLine())) {
                        if (firstLine) {
                            firstLine = Boolean.FALSE;
                            continue;
                        }
                        final String[] parts = line.split("\\s+");
                        if (parts.length >= 9) {
                            try {
                                final String name = parts[0];
                                final long pid = Long.parseLong(parts[1]);
                                final String address = parts[8];
                                if (onlyIdeaChild && !ideaChildPids.contains(pid)) {
                                    continue;
                                }
                                final int colonIndex = address.lastIndexOf(':');
                                if (colonIndex > 0) {
                                    final String portStr = address.substring(colonIndex + 1);
                                    if (!portStr.equals("*") && portStr.matches("\\d+")) {
                                        final int port = Integer.parseInt(portStr);
                                        final String key = "%d:%d".formatted(pid, port);
                                        processMap.put(key, new ProcessInfo(pid, port, name, "", ideaChildPids.contains(pid)));
                                    }
                                }
                            } catch (final NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (final Exception ignored) {
        }
        // 如果 lsof 没有结果，尝试 ss（现代 Linux 发行版 iproute2 自带，比 netstat 更常见）
        if (processMap.isEmpty()) {
            try {
                final GeneralCommandLine ssCmd = new GeneralCommandLine("ss", "-tlnp");
                final ProcessOutput ssOutput = ExecUtil.execAndGetOutput(ssCmd, COMMAND_TIMEOUT_MS);
                if (ssOutput.getExitCode() == 0) {
                    try (final BufferedReader reader = new BufferedReader(new StringReader(ssOutput.getStdout()))) {
                        String line;
                        while (Objects.nonNull(line = reader.readLine())) {
                            final Matcher matcher = SS_LISTEN_PATTERN.matcher(line);
                            if (matcher.find()) {
                                final int port = Integer.parseInt(matcher.group(1));
                                final String name = matcher.group(2);
                                final long pid = Long.parseLong(matcher.group(3));
                                if (onlyIdeaChild && !ideaChildPids.contains(pid)) {
                                    continue;
                                }
                                final String key = "%d:%d".formatted(pid, port);
                                processMap.put(key, new ProcessInfo(pid, port, name, "", ideaChildPids.contains(pid)));
                            }
                        }
                    }
                }
            } catch (final Exception ignored) {
            }
        }
        // 如果 ss 没有结果，尝试 netstat（老旧发行版兜底）
        if (processMap.isEmpty()) {
            try {
                final GeneralCommandLine netstatCmd = new GeneralCommandLine("netstat", "-tlnp");
                final ProcessOutput netstatOutput = ExecUtil.execAndGetOutput(netstatCmd, COMMAND_TIMEOUT_MS);
                if (netstatOutput.getExitCode() == 0) {
                    try (final BufferedReader reader = new BufferedReader(new StringReader(netstatOutput.getStdout()))) {
                        String line;
                        while (Objects.nonNull(line = reader.readLine())) {
                            final Matcher matcher = UNIX_NETSTAT_PATTERN.matcher(line);
                            if (matcher.find()) {
                                final int port = Integer.parseInt(matcher.group(1));
                                final String pidStr = matcher.group(2);
                                final int slashIndex = pidStr.indexOf('/');
                                if (slashIndex > 0) {
                                    final long pid = Long.parseLong(pidStr.substring(0, slashIndex));
                                    if (onlyIdeaChild && !ideaChildPids.contains(pid)) {
                                        continue;
                                    }
                                    final String name = pidStr.substring(slashIndex + 1);
                                    final String key = "%d:%d".formatted(pid, port);
                                    processMap.put(key, new ProcessInfo(pid, port, name, "", ideaChildPids.contains(pid)));
                                }
                            }
                        }
                    }
                }
            } catch (final Exception ignored) {
            }
        }
        // 如果没有找到端口，返回空列表
        if (processMap.isEmpty()) return;
        // 收集所有 PID 用于查询完整命令
        final Set<Long> pids = new HashSet<>();
        for (final ProcessInfo info : processMap.values()) {
            pids.add(info.pid());
        }
        // 获取进程完整命令
        final Map<Long, String> pidToCmd = new HashMap<>();
        for (final Long pid : pids) {
            try {
                // 使用 ps 获取完整命令
                final GeneralCommandLine psCmd = new GeneralCommandLine("ps", "-p", String.valueOf(pid), "-o", "command=");
                final ProcessOutput psOutput = ExecUtil.execAndGetOutput(psCmd, COMMAND_TIMEOUT_MS);
                if (psOutput.getExitCode() == 0 && !psOutput.getStdout().isEmpty()) {
                    pidToCmd.put(pid, psOutput.getStdout().trim());
                }
                // Linux: 尝试从 /proc 读取
                if (SystemInfo.isLinux) {
                    try {
                        final String cmd = Files.readString(Path.of("/proc", String.valueOf(pid), "cmdline")).replace('\0', ' ').trim();
                        if (!cmd.isEmpty()) {
                            pidToCmd.put(pid, cmd);
                        }
                    } catch (final Exception ignored) {
                    }
                }
            } catch (final Exception ignored) {
            }
        }
        // 为每个端口创建结果项
        for (final ProcessInfo info : processMap.values()) {
            final String cmd = pidToCmd.getOrDefault(info.pid(), info.name());
            result.add(
                    new PortSearchItem(info.port(), info.pid(), StrUtil.isNotEmpty(info.name()) ? info.name() : BUNDLE.getString("search.unknown.app"), cmd, resolveAppIcon(cmd))
            );
        }
    }

    /**
     * 进程信息内部类
     */
    private record ProcessInfo(long pid, int port, String name, String path, boolean isIdeaChild) {
    }

    @Override
    public void dispose() {
        this.cached = null;
        this.httpCached = null;
        this.allPortsCached = null;
        this.ideaChildPortsCached = null;
        synchronized (this.gitRepositoryCache) {
            this.gitRepositoryCache.clear();
        }
    }
}
