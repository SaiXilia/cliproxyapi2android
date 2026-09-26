# CLIProxyAPI 移动端初始化责任清单 (Initialization Ledger)

本文档对照桌面端入口（`cmd/server/main.go`），明确移动端入口（`cmd/mobile/` 与 `cmd/server/`）在启动过程中的组件初始化、生命周期与所有权责任。

---

## 初始化组件对照表

| 组件名称 | 桌面端初始化位置 | 移动端（Android）处理责任 | 责任归属与实现方式 | 停止与销毁责任 |
| :--- | :--- | :--- | :--- | :--- |
| **FileTokenStore** | `cmd/server/main.go:497` | **必须显式设置** | 将 `cfg.AuthDir` 指向应用私有目录（`context.noBackupFilesDir/cliproxy/auths`） | 无状态内存对象，进程退出时自动销毁 |
| **协议翻译层 (Translators)** | `internal/translator/` | **全量保留** | `sdk/translator/registry.go` 初始化导入链完整保留 | 静态全局无状态 |
| **推理思考管道 (Thinking)** | `internal/thinking/` | **全量保留** | 核心推理管道完整保留 | 静态全局无状态 |
| **模型执行器 (Executors)** | `internal/runtime/executor/` | **全量保留** | Claude, Codex, Gemini, xAI 等执行器完整保留 | 随请求 context 销毁 |
| **模型目录自更新器** | `cmd/server/main.go:415` | **显式启动** | 调用 `registry.StartModelsUpdater`、`StartCodexClientModelsUpdater`、`StartDevinModelsUpdater`，传入会话 `ctx` | 会话停止时 `cancel(ctx)` 优雅停止 |
| **管理页 HTML 自更新器** | `cmd/server/main.go:390` | **显式启动** | 调用 `managementasset.StartAutoUpdater`，传入会话 `ctx` | 会话停止时 `cancel(ctx)` 优雅停止 |
| **OAuth 凭据自动刷新** | `sdk/cliproxy/service_lifecycle.go` | **由 SDK Run 内部接管** | `sdk/cliproxy/auth/auto_refresh_loop.go` 由 `Service.Run` 自动拉起，禁止外部重复启动 | 随 `Service.Shutdown` 退出 |
| **配置热更新监听器** | `internal/watcher/events.go` | **受控初始化** | 启动前确保 `config.yaml` 真实存在，避免空路径导致 watcher 报错 | 随 `Service.Shutdown` 退出 |
| **分布式通信 (Home)** | `cmd/server/main.go:370` | **平台禁用** | 移动端强制设为 `cfg.Home = config.HomeConfig{}`，`home.Current()` 恒定为 `nil` | 无后台任务 |
| **局域网服务发现 (mDNS)** | `cmd/server/main.go:430` | **平台禁用** | 桩实现静默空操作，不发起组播广播 | 无后台任务 |
| **终端 TUI** | `cmd/server/main.go:450` | **平台禁用** | 桩实现返回错误提示，仅提供无操作日志 Hook | 无后台任务 |
| **浏览器打开** | `internal/browser/` | **移动双轨接管** | 注册 Kotlin Chrome Custom Tabs 回调或 CLI 控制台打印 | 回调引用随停止置空 |
| **日志脱敏与轮转** | `internal/logging/` | **受控文件输出** | 输出到 `noBackupFilesDir/cliproxy/logs`，禁用敏感 token 打印 | 文件描述符正常关闭 |
