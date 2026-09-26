# CLIProxyAPI Android 补丁修改台账 (Patch Ledger)

本台账严格记录所有对上游原始代码的直接修改，确保在后续拉取上游 `git merge upstream/main` 时具备 100% 审计追踪能力。

---

## 补丁原则回顾
1. **原位保留原文件名**：仅在文件顶部第 1 行插入 `//go:build !android`；
2. **严禁破坏核心业务**：translator, thinking, executor, auth 保持 0 侵入；
3. **补齐自包含桩代码**：新建 `*_android.go`，顶部声明 `//go:build android`。

---

## 补丁明细清单

| 编号 | 修改文件 | 涉及模块 | 修改类型 | 目的与原因 | 对应桩文件 | 合并风险与对策 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **P-01** | `internal/store/postgresstore.go`<br>`internal/store/postgres_cooldown_store.go`<br>`internal/store/gitstore.go`<br>`internal/store/objectstore.go` | 外部企业存储 | 首行追加 `//go:build !android` | 剥离 `pgx/v5`、`go-git/v6`、`minio-go/v7` 等 ~30MB 冗余存储驱动 | `internal/store/store_android.go` | **极低**。仅改动第 1 行 build tags，Git 3-way merge 可自动合入。 |
| **P-02** | `internal/tui/*.go` (全包原文件) | 终端 TUI | 首行追加 `//go:build !android` | 剥离 `bubbletea`、`lipgloss`、`bubbles`、`clipboard` 桌面终端交互 | `internal/tui/tui_android.go` | **极低**。移动端不提供终端交互，由桩接管。 |
| **P-03** | `internal/browser/browser.go` | 桌面浏览器启动 | 首行追加 `//go:build !android` | 剥离 `open-golang`，避免 Android 执行 `xdg-open` 找不到二进制崩溃 | `internal/browser/browser_android.go` | **极低**。改为终端链接打印与 Kotlin Custom Tabs 回调。 |
| **P-04** | `internal/discovery/zeroconf.go`<br>`internal/cmd/discover.go` | 局域网服务发现与广播 | 首行追加 `//go:build !android` | 剥离 `libp2p/zeroconf/v2` 与 `miekg/dns`，消除移动端组播后台耗电 | `internal/discovery/zeroconf_android.go` | **极低**。对外提供空操作静默桩。 |
| **P-05** | `internal/home/client.go` | 分布式 Redis 协同 | 首行追加 `//go:build !android` | 剥离 `go-redis/v9`，确保 `home.Current()` 恒为 `nil`，无缝走入纯单机内存降级 | `internal/home/client_android.go` | **极低**。所有引用均有 `Current() == nil` 安全回退路径。 |
| **P-06** | `internal/client/codex/live/media.go`<br>`internal/client/codex/live/tcp_proxy.go` | WebRTC 音频实时传输 | 首行追加 `//go:build !android` | 剥离 `pion/webrtc/v4` 及其 10 余个底层音视频与媒体子依赖 (~15MB) | `internal/client/codex/live/media_android.go` | **极低**。自包含对齐 `mediaRelaySession` 等 4 个接口与结构体。 |
