# CLIProxyAPI 移动端（Android）瘦身剪裁规划指南

> **核心原则**：
> 1. **不做推倒重构**：保持 CLIProxyAPI 原有的目录组织、API 路由规范与配置逻辑，原汁原味提供核心代理功能。
> 2. **精准剪枝，剥离冗余**：仅砍掉手机端绝对用不到的重型组件（分布式数据库、对象存储、Git 备份仓、Redis 集群通信、WebRTC 音频、mDNS 组网、桌面终端 TUI 等）。
> 3. **保全核心自更新能力**：模型目录在线拉取更新、Web 管理页热更新、OAuth 凭据自动续期与 429 熔断轮换等核心能力 100% 完整保留。
> 4. **对上游最小侵入与友好合并**：
>    - **严禁重命名原文件**：原文件保留原有文件名，仅在第 1 行追加 `//go:build !android`。这样在后续 `git merge upstream/main` 时，Git 能够执行基于内容的 3-Way Auto-Merge，避免 `delete/modify` 树冲突。
>    - **严禁乱改核心业务代码**：对协议转译（translator）、执行器（executor）、推理思考（thinking）和认证调度（auth）做到 **0 业务侵入**；对 6 个剪枝边界点，建立可控、自包含的打桩维护机制。

---

## 目录索引
- [第一部分：无用组件剪除清单与核心自更新保全方案](#第一部分无用组件剪除清单与核心自更新保全方案)
  - [1.1 必须 100% 完整保留的核心能力清单](#11-必须-100-完整保留的核心能力清单)
  - [1.2 手机用不到的组件与重型依赖精准剪枝矩阵](#12-手机用不到的组件与重型依赖精准剪枝矩阵)
  - [1.3 核心自更新能力的保留机制推敲](#13-核心自更新能力的保留机制推敲)
  - [1.4 最小侵入式剪裁策略：原位 Build Tags 隔离与桩代码（Stub）自包含规范](#14-最小侵入式剪裁策略原位-build-tags-隔离与桩代码stub自包含规范)
  - [1.5 `go.mod` 依赖机制澄清与产物体积/内存客观预估](#15-gomod-依赖机制澄清与产物体积内存客观预估)
- [第二部分：代码剪裁具体实施手册与打桩代码（Stub）编写](#第二部分代码剪裁具体实施手册与打桩代码stub编写)
  - [2.1 剪裁实施全景：为什么采用 Build Tags 打桩能做到核心 0 侵入](#21-剪裁实施全景为什么采用-build-tags-打桩能做到核心-0-侵入)
  - [2.2 剪枝实战 1：WebRTC 实时媒体通道（internal/client/codex/live/media.go）](#22-剪枝实战-1webrtc-实时媒体通道internalclientcodexlivemediago)
  - [2.3 剪枝实战 2：企业级外部存储（internal/store/*.go）](#23-剪枝实战-2企业级外部存储internalstorego)
  - [2.4 剪枝实战 3：Redis 分布式通信客户端（internal/home/client.go）与 home.Current() 审计闭环](#24-剪枝实战-3redis-分布式通信客户端internalhomeclientgo与-homecurrent-审计闭环)
  - [2.5 剪枝实战 4：局域网 mDNS 组网广播（internal/discovery/zeroconf.go）](#25-剪枝实战-4局域网-mdns-组网广播internaldiscoveryzeroconfgo)
  - [2.6 剪枝实战 5：桌面终端 TUI 交互界面（internal/tui/*.go）](#26-剪枝实战-5桌面终端-tui-交互界面internaltuigo)
  - [2.7 剪枝实战 6：桌面跨平台浏览器调用与移动端 OAuth 闭环（internal/browser/browser.go）](#27-剪枝实战-6桌面跨平台浏览器调用与移动端-oauth-闭环internalbrowserbrowsergo)
  - [2.8 核心业务代码 0 侵入验证清单与防上游漂移 CI 守卫](#28-核心业务代码-0-侵入验证清单与防上游漂移-ci-守卫)
- [第三部分：移动端（GOOS=android）交叉编译与运行验证](#第三部分移动端goosandroid交叉编译与运行验证)
  - [3.1 交叉编译环境与 NDK 工具链选型](#31-交叉编译环境与-ndk-工具链选型)
  - [3.2 核心符号与 API 实施前置核实清单（Pre-Flight Checklist）](#32-核心符号与-api-实施前置核实清单pre-flight-checklist)
  - [3.3 双交付形态的交叉编译实战（CLI 二进制 vs C-Shared .so）](#33-双交付形态的交叉编译实战cli-二进制-vs-c-shared-so)
  - [3.4 编译参数深度优化与链接瘦身/符号隔离规范](#34-编译参数深度优化与链接瘦身符号隔离规范)
  - [3.5 一键交叉编译流水线脚本（scripts/build_android.sh）](#35-一键交叉编译流水线脚本scriptsbuild_androidsh)
  - [3.6 部署与实机运行验证清单（双轨验收流程）](#36-部署与实机运行验证清单双轨验收流程)
  - [3.7 移动端运行时环境生存与宿主适配指南](#37-移动端运行时环境生存与宿主适配指南)

---

# 第一部分：无用组件剪除清单与核心自更新保全方案

---

### 1.1 必须 100% 完整保留的核心能力清单

CLIProxyAPI 之所以强大，在于其协议转换深度和账号池高可用调度。在剪除边缘冗余时，**以下核心目录与模块严禁破坏，全量保留**：

| 模块目录 | 核心职责 | 保留理由 |
| :--- | :--- | :--- |
| `internal/translator/` | 协议翻译层（OpenAI ⇄ Claude ⇄ Gemini ⇄ Codex） | 核心基石。处理消息结构、工具调用（Function Calling）、多模态图片转换。 |
| `internal/thinking/` | 思考模式与推理管道 | 核心基石。统一处理 Claude 3.7+、GPT-5+、Gemini 的 `reasoning_effort` 与 `budget_tokens` 注入。 |
| `internal/runtime/executor/` | 各大模型服务商执行器 | 实际向 Anthropic/OpenAI/Google 发起网络请求并处理流式（SSE）响应的引擎。 |
| `internal/auth/` | OAuth 授权与凭据转换 | Claude PKCE 授权流、Codex 授权/设备码流、Gemini/Grok 认证处理。 |
| `sdk/cliproxy/auth/` | 凭据管理、429 冷却与轮换 | 核心生命线。多账号自动轮询、429 速率限制退避冷却、Token 过期前自动刷新（Auto-Refresh）。 |
| `internal/registry/` | 模型注册中心与**在线热更新** | **自更新核心**。动态从远端 GitHub/CDN 同步最新 `models.json`，无需更版即可支持新模型。 |
| `internal/managementasset/` | 管理后台静态资产**在线热更新** | **自更新核心**。动态从 Release 同步最新 `management.html` 管理单页。 |
| `internal/client/` & `utls` | TLS 客户端指纹伪装与反爬防护 | 模拟桌面端/浏览器 ClientHello 特征，防范厂商 Cloudflare 风控拦截。 |
| `sdk/auth/filestore.go` | 本地文件系统凭据仓储（`FileTokenStore`） | 手机端只需纯文件存储（`auths/*.json`）。读写极快、无需任何外部守护进程。 |

---

### 1.2 手机用不到的组件与重型依赖精准剪枝矩阵

经过对 CLIProxyAPI 全局代码引用链的详尽静态扫描，发现一个极具优势的代码结构特征：**所有手机用不到的重型三方依赖，在源码中均处于“单点集中引用”状态，并没有与核心业务代码交叉混杂**。

这 6 类无用组件在移动端完全是冗余包袱，是导致二进制体积膨胀（原本近 100MB）和不必要资源消耗的根源：

#### 1.2.1 外部企业级存储后端（剪除：节省编译体积 ~30MB）
* **涉及路径**：
  * `internal/store/postgresstore.go`、`postgres_cooldown_store.go`
  * `internal/store/gitstore.go`
  * `internal/store/objectstore.go`
* **涉及重型依赖**：
  * `github.com/jackc/pgx/v5`（PostgreSQL 驱动全家桶，全工程仅在 `postgresstore.go` 引入）
  * `github.com/go-git/go-git/v6`（纯 Go 实现的 Git 引擎，全工程仅在 `gitstore.go` 引入）
  * `github.com/minio/minio-go/v7`（S3 对象存储 SDK，全工程仅在 `objectstore.go` 引入）
* **剪除理由**：手机是本地单设备，只需操作内部私有存储的 `auths/` 目录（基于 `sdk/auth/filestore.go`）。将凭据同步到远程 Postgres 数据库、Git 仓库或 S3 桶，在移动端是 100% 的死代码。

#### 1.2.2 实时 WebRTC 音频通道（剪除：节省编译体积 ~15MB）
* **涉及路径**：
  * `internal/client/codex/live/media.go`
* **涉及重型依赖**：
  * `github.com/pion/webrtc/v4`（完整的 WebRTC 协议栈，全工程仅在 `media.go` 引入）
  * `github.com/pion/rtp`、`github.com/pion/sdp/v3`、`github.com/pion/ice/v4`、`github.com/pion/turn/v5` 等 10 多个子模块
* **剪除理由**：这是原项目针对 OpenAI Realtime 语音对讲实验性功能引入的 WebRTC 音视频传输栈。手机端移植主要用于文本、代码与多模态图文调用，保留如此庞大的底层音视频媒体栈毫无意义。

#### 1.2.3 分布式集群与多机协同（剪除：切断无用后台能耗与网络开销）
* **涉及路径**：
  * `internal/home/client.go`
* **涉及重型依赖**：
  * `github.com/redis/go-redis/v9`（全工程仅在 `internal/home/client.go` 引入）
* **剪除理由**：用于与云端主控服务（CLIProxyAPIHome）进行节点注册、集群分布式并发控制及 Redis 队列同步。移动端是独立运行的孤岛节点，在未开启 Home 模式时，项目内部所有引用 `internal/home` 的缓存均已内置优雅回退至本地内存模式。剪除 Redis Client 丝毫不影响单机缓存。

#### 1.2.4 局域网服务发现广播（剪除：避免移动端后台广播与电池消耗）
* **涉及路径**：
  * `internal/discovery/zeroconf.go`
* **涉及重型依赖**：
  * `github.com/libp2p/zeroconf/v2`（全工程仅在 `internal/discovery/zeroconf.go` 引入）
  * `github.com/miekg/dns`
* **剪除理由**：原版会在局域网内通过 mDNS（组播 224.0.0.251）对外广播自身是 `_ai-gateway._tcp`。在手机端，这种周期性广播会导致无线网卡频繁唤醒、严重增加耗电，必须关闭并剔除。

#### 1.2.5 桌面终端交互界面（TUI）（剪除：去除桌面依赖）
* **涉及路径**：
  * `internal/tui/` 全套文件
* **涉及重型依赖**：
  * `github.com/charmbracelet/bubbletea`（全工程仅在 `internal/tui/` 引入）
  * `github.com/charmbracelet/lipgloss`、`bubbles`
  * `github.com/atotto/clipboard`（仅在 `internal/tui/keys_tab.go` 引入）
* **剪除理由**：移动端没有终端交互窗口（TUI），服务的配置与交互通过内置 Web 页面（`management.html`）或配置文件完成，无需编译 Bubbletea 终端渲染引擎。

#### 1.2.6 桌面操作系统调用（剪除：修复移动端环境报错）
* **涉及路径**：
  * `internal/browser/browser.go`
* **涉及依赖**：
  * `github.com/skratchdot/open-golang`（在 Linux/Mac/Win 寻找 `xdg-open` / `open` / `cmd.exe` 打开浏览器，全工程仅在 `browser.go` 引入）
* **剪除理由**：Android 没有 `xdg-open` 命令，直接调用会抛出进程找不到的异常；浏览器打开行为应在宿主环境处理。

---

### 1.3 核心自更新能力的保留机制推敲

在剪去冗余的同时，必须保证项目最受好评的**“免改代码、免发版、动态自更新”**两大能力依然健壮运行：

#### 1.3.1 模型目录在线自动更新（Model Catalog Auto-Updater）
* **代码载体**：`internal/registry/model_updater.go`、`codex_client_models_updater.go`、`devin_models_updater.go`
* **保全机制**：
  1. **内置保底**：静态内置一份打包时的 `models/models.json`，即使设备完全无网，也能读取基础模型支持列表（Claude 3.5/3.7、GPT-4o/o3、Gemini 2.0 等）。
  2. **远端轮询**：网络通畅时，后台根据配置定期从官方渠道拉取最新定义：
     - `https://raw.githubusercontent.com/router-for-me/models/refs/heads/main/models.json`
     - `https://models.router-for-me/models.json`
  3. **热生效回调**：拉取到新模型后，触发 `SetModelRefreshCallback` 动态注册，无需重启服务即可识别新发布的模型别名与上下文上限。
* **移动端轻度调优**：原版默认 3 小时硬性拉取一次；在移动网络环境下，应支持通过配置将拉取策略调整为**“服务启动时拉取一次 + 提供手动触发刷新接口”**，兼顾模型时效性与手机流量控制。

#### 1.3.2 管理控制台 WebUI 在线热更（Management Asset Auto-Updater）
* **代码载体**：`internal/managementasset/updater.go`
* **保全机制**：
  1. 核心保留 `EnsureLatestManagementHTML()` 流程。
  2. 运行时定时检查 GitHub Release（`router-for-me/Cli-Proxy-API-Management-Center`）或备用 CDN（`https://cpamc.router-for-me/`），将最新的单页 `management.html` 下载覆盖至数据目录。
  3. 服务挂载路由 `/management.html` 直接读取该文件。即使前端修复了 UI 缺陷或增加了新参数面板，用户手机上的管理页面也能直接享受到最新版本。
* **离线降级保障**：在编译打包时，将当前稳定版本的 `management.html` 作为默认 asset 植入本地，若首次启动断网无法访问 GitHub，直接释放默认版本，绝不影响本地使用。

#### 1.3.3 OAuth Token 自动续期与 429 冷却状态机
* **代码载体**：`sdk/cliproxy/auth/auto_refresh_loop.go`、`conductor_cooldown.go`
* **保全机制**：
  - **自动刷新**：在 Refresh Token 过期前，自动调用上游 OAuth 刷新端点换取新 Access Token，并原子持久化写回本地 JSON。
  - **429 冷却与无感故障转移**：当某个账号遭遇并发超限或配额限制时，自动打上冷却标记（记录冷却到期时间戳），下一次请求无缝切换到备用有效账号，冷却时间过后自动解封。全套逻辑 100% 原样保留。

---

### 1.4 最小侵入式剪裁策略：原位 Build Tags 隔离与桩代码（Stub）自包含规范

**严禁做法**：
1. **严禁在原项目核心业务代码中到处乱改或删除 import**；
2. **严禁将原文件重命名为 `xxx_desktop.go`**！
   > **Git 底层机理警示**：在 Git 的版本控制模型中，重命名文件会被识别为 `delete + add`。如果未来上游官方修改了 `media.go` 或 `client.go`，本地执行 `git merge upstream/main` 时会直接触发破坏性的 `CONFLICT (modify/delete)` 树冲突，失去自动化合并能力。

**合规做法**：**“原位打标 + 独立补桩”**。

```
原文件 (如 media.go)           ──> 原地保留文件名不变！仅在首行插入 //go:build !android
新增桩文件 (media_android.go)   ──> 头部声明 //go:build android，且必须自包含全部依赖类型
```

* **上游合并友好性**：原文件保留原名，Git 3-Way Merge 仅在文件第 1 行存在微小差异，后续几百行代码的上游更新均能被 Git 100% 自动合入。
* **边界维护契约**：明确表述为**“对核心业务逻辑（translator/thinking/executor/auth）0 侵入，对 6 个剪枝边界点承担持续且极小的接口同步维护成本”**（若上游在边界包新增文件或方法，需在桩中同步补齐空实现）。

#### 典型打桩规约示例（6 处精准剪枝点）

1. **WebRTC 实时媒体通道**：
   * 原文件：`internal/client/codex/live/media.go`（全工程唯一引入 `pion/webrtc/v4` 的文件）。
   * **操作**：
     - **原位保留** `media.go` 文件名，在顶部第 1 行插入：`//go:build !android`。
     - 同目录下新建 `media_android.go`，顶部声明：`//go:build android`。
     - **关键约束（自包含类型）**：必须在 `media_android.go` 中完整声明 `mediaRelaySession`、`mediaRelayFactory`、`mediaSessionRoute`、`mediaSessionLimiter` 等类型，杜绝类型分裂引发的 `undefined type` 编译报错。
   * **效果**：`live.go` 和 `server_routes.go` 一行不改，`pion/webrtc` 在 Android 编译中直接消失。

2. **企业级外部存储（Postgres / Git / S3）**：
   * 原文件：`internal/store/` 下的 `postgresstore.go`、`gitstore.go`、`objectstore.go`、`postgres_cooldown_store.go`。
   * **操作**：
     - 原位保留所有文件名，在这些文件顶部均加入 `//go:build !android`。
     - 新建 `internal/store/store_android.go`，顶部声明 `//go:build android`，提供桩结构体（如 `PostgresStore`、`GitTokenStore`、`ObjectTokenStore`）。
   * **效果**：`cmd/server/main.go` 无需修改 import，编译时强制走本地 `sdk/auth/filestore.go`，彻底剥离 `pgx`、`go-git`、`minio-go`。

3. **分布式通信（Redis Client）**：
   * 原文件：`internal/home/client.go`（全工程唯一引入 `github.com/redis/go-redis/v9` 的文件）。
   * **操作**：
     - 原位保留 `client.go`，顶部加入 `//go:build !android`。
     - 新建 `client_android.go`，顶部加入 `//go:build android`，实现轻量桩 `Client`，方法返回 `ErrDisabled`。
   * **效果**：全工程 54 个依赖 `internal/home` 缓存的业务文件完全不受影响（自动回退至单机内存模式），彻底剥离 `go-redis`。

4. **局域网服务发现（mDNS Zeroconf）**：
   * 原文件：`internal/discovery/zeroconf.go`（全工程唯一引入 `libp2p/zeroconf/v2` 的文件）。
   * **操作**：
     - 原位保留 `zeroconf.go`，顶部加入 `//go:build !android`。
     - 新建 `zeroconf_android.go`，顶部加入 `//go:build android`，实现空操作的 `ZeroconfAdvertiser`。
   * **效果**：`sdk/cliproxy/discovery_advertiser.go` 保持原样，剥离 `zeroconf` 与 `miekg/dns`。

5. **桌面终端交互界面（TUI）**：
   * 原文件：`internal/tui/` 下的所有文件（全工程唯一引入 `bubbletea` 的模块）。
   * **操作**：
     - 原位保留所有原文件，头部加上 `//go:build !android`。
     - 新建 `internal/tui/tui_android.go`，声明 `//go:build android`，提供桩函数 `Run(...) error { return errors.New("TUI disabled on android") }`。
   * **效果**：`cmd/server/main.go` 正常编译，彻底剥离 `bubbletea`、`lipgloss` 和 `clipboard`。

6. **桌面浏览器调用（OpenURL）**：
   * 原文件：`internal/browser/browser.go`（全工程唯一引入 `skratchdot/open-golang` 的文件）。
   * **操作**：
     - 原位保留 `browser.go`，声明 `//go:build !android`。
     - 新建 `browser_android.go`，声明 `//go:build android`。
     - 增加**移动端双轨响应**：在 Termux 纯 CLI 环境下打印带明显提示的终端授权链接；在 Android App 嵌入形态下通过注册的回调函数交由 Android 宿主拉起 Chrome Custom Tabs。
   * **效果**：彻底消除 `open-golang` 依赖，解决移动端 OAuth 闭环问题。

---

### 1.5 `go.mod` 依赖机制澄清与产物体积/内存客观预估

#### 1.5.1 `go.mod` 依赖声明与编译图剪除机制澄清
工程实施前必须澄清一条关键的 Go 工具链机制：
* **`go mod tidy` 不会缩小 `go.mod` 文件**：Go 语言的模块依赖管理工具（`go mod`）在分析依赖树时，会综合考虑**所有操作系统平台与所有构建标签的并集**。因此，`go.mod` 中仍然会完整列出 `pion/webrtc`、`go-redis`、`pgx` 等依赖包的声明与校验和，执行 `go mod tidy` 不会且不应该删掉它们；
* **剪除发生在编译期（AST 构建与链接图）**：当指定 `GOOS=android` 进行构建时，Go 编译器在解析构建标签时会自动跳过标有 `//go:build !android` 的原版文件。编译器与链接器**绝不会**对被排除的依赖包进行 AST 语法树解析与符号链接，因此这些重型依赖在 Android 二进制产物中完全为 0 存在。

| 模块分类 | 裁剪前引入的巨型三方依赖 | `go.mod` 声明状态 | Android 目标实际编译与链接状态 |
| :--- | :--- | :--- | :--- |
| **企业数据库** | `github.com/jackc/pgx/v5` 全套依赖 | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **Git 引擎** | `github.com/go-git/go-git/v6` 全套依赖 | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **对象存储** | `github.com/minio/minio-go/v7` 全套依赖 | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **WebRTC 协议栈** | `github.com/pion/webrtc/v4` 及 10+ 个子模块 | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **分布式通信** | `github.com/redis/go-redis/v9` | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **局域网广播** | `github.com/libp2p/zeroconf/v2` | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **终端 TUI** | `github.com/charmbracelet/bubbletea` / `lipgloss` | 依然保留声明 | **完全不编译、不链接、0 参与** |
| **桌面系统调用** | `github.com/skratchdot/open-golang`、`clipboard` | 依然保留声明 | **完全不编译、不链接、0 参与** |

#### 1.5.2 客观的体积与内存指标预期（尊重 CGO 物理现实）

在启用 `CGO_ENABLED=1`（为确保 Android DNS 解析正确）并链接 Bionic C 库和系统证书链的前提下：

* **二进制产物体积**：
  - 原版桌面/服务端编译（未裁剪）：约 **95MB ~ 110MB**
  - Android 剪裁后产物（CGO 开启 + NDK Clang 链接 + `-s -w` 符号裁剪）：预期为 **20MB ~ 30MB**（体积削减约 70%~80%）。
  *(注：纯 Go 静态无 CGO 编译可压缩至 15MB 左右，但在 Android 上不可取，因为会丧失系统 DNS 解析能力)*
* **运行时内存表现（RSS）**：
  - **常驻空闲内存**：**35MB ~ 50MB**（原版约 150MB）
  - **单流 / 多并发 SSE 流式推理负载**：**80MB ~ 120MB**（涵盖 TLS 会话缓存、Gin 路由上下文与流式缓冲区）

# 第二部分：代码剪裁具体实施手册与打桩代码（Stub）编写

---

### 2.1 剪裁实施全景：为什么采用 Build Tags 打桩能做到核心 0 侵入

在 Go 语言中，只要一个包在指定目标平台下存在合法的 `.go` 文件声明了调用方所需要的导出符号与类型，编译器即可顺利通过类型检查与符号链接。

基于我们在第一部分验证的事实：**所有 6 大无用重型依赖在原项目中均处于单一文件/单一包的极端集中状态**。我们确立以下**上游合并友好型**打桩规范：
1. **原桌面端文件原地保留原文件名**：**严禁重命名文件**！仅在原文件顶部第 1 行插入 `//go:build !android`。后续上游发生修改时，Git 能够顺利进行行级 3-Way Auto-Merge，避免 `delete/modify` 树冲突。
2. **移动端打桩文件必须“类型自包含”**：新建 `xxx_android.go`，顶部声明 `//go:build android`。**凡是被剪裁文件定义、且被调用方引用的接口与结构体类型，必须在桩文件中完整声明**，杜绝类型分裂（Undefined Type）引发的编译中断。
3. **核心业务调用方代码（Call Sites）**：**0 修改、0 注释、0 侵入**。`cmd/server/main.go`、`sdk/cliproxy/` 以及所有翻译器代码保持 100% 原样。

```
                    ┌─────────────────────────┐
                    │ 编译目标: GOOS=android   │
                    └───────────┬─────────────┘
                                │
       ┌────────────────────────┴────────────────────────┐
       ▼                                                 ▼
[原文件: media.go (原名保留)]                    [桩文件: media_android.go]
  首行增加: //go:build !android                    首行声明: //go:build android
  (被 Go 编译器自动跳过，                           (被 Go 编译器加载，自包含所有依赖类型，
   重型三方依赖完全不参与依赖树解析)                   提供同名空桩，0 外部依赖，编译一次通过)
```

---

### 2.2 剪枝实战 1：WebRTC 实时媒体通道（internal/client/codex/live/media.go）

#### 2.2.1 依赖剖析与类型调用链
`internal/client/codex/live/live.go` 是 OpenAI Realtime API 的转发处理类。它在初始化时需要构造并持有：
- 结构体 `h.mediaLimiter = &mediaSessionLimiter{}`（定义于 `media.go`）
- 接口实例 `relay, relayErr = newPionMediaRelayWithLimiter(relayConfig, h.mediaLimiter)`（返回 `mediaRelayFactory`）
- 接口 `mediaRelaySession` 与参数结构体 `mediaSessionRoute`

因此，桩文件必须**自包含**上述 4 个类型的声明，否则 `live.go` 将直接因找不到类型而编译报错。

#### 2.2.2 实施步骤
1. **保留原文件名** `internal/client/codex/live/media.go`，在顶部第 1 行插入构建约束：
   ```go
   //go:build !android
   ```
2. 在同目录下新建 `media_android.go`，提供自包含类型的完整打桩实现：

```go
//go:build android

package live

import (
	"context"
	"errors"

	"github.com/router-for-me/CLIProxyAPI/v7/internal/config"
)

var errWebRTCDisabledOnAndroid = errors.New("codex live webrtc media relay is disabled on android")

// 补齐 live.go 依赖的接口与结构体类型定义，杜绝类型分裂
type mediaRelaySession interface {
	AcceptUpstreamAnswer(context.Context, string) (string, error)
	SetCallID(string)
	SetCloseHandler(func(string))
	Close() error
	CloseWithReason(string) error
}

type mediaRelayFactory interface {
	NewSession(context.Context, string, mediaSessionRoute) (mediaRelaySession, string, error)
}

type mediaSessionRoute struct {
	proxyURL   string
	credential string
	authIndex  string
}

type mediaSessionLimiter struct{}

type androidMediaRelay struct{}

func (r *androidMediaRelay) NewSession(_ context.Context, _ string, _ mediaSessionRoute) (mediaRelaySession, string, error) {
	return nil, "", errWebRTCDisabledOnAndroid
}

// newPionMediaRelayWithLimiter 桩实现，与 live.go 方法签名完全对齐
func newPionMediaRelayWithLimiter(_ config.CodexLiveMediaRelayConfig, _ *mediaSessionLimiter) (mediaRelayFactory, error) {
	return &androidMediaRelay{}, nil
}
```
* **效果**：`live.go` 保持原样，类型检查完全通过；`pion/webrtc/v4` 及其 10 余个底层子模块（`rtp`, `sdp`, `ice`, `turn` 等）在 Android 构建中被 100% 剥离。

---

### 2.3 剪枝实战 2：企业级外部存储（internal/store/*.go）

#### 2.3.1 依赖剖析与调用链
在 `cmd/server/main.go` 中，仅当用户显式配置了环境变量（如 `PGSTORE_DSN`、`GITSTORE_GIT_URL`、`OBJECTSTORE_ENDPOINT`）时才会初始化对应存储。在手机端，默认不配置任何远程存储变量，所有的实际凭据读写全部走本地私有目录（`sdk/auth/filestore.go`）。

#### 2.3.2 实施步骤
1. **原位保留** `internal/store/` 下的所有原 Go 文件名，在顶部第 1 行加入 `//go:build !android`：
   - `internal/store/postgresstore.go`
   - `internal/store/postgres_cooldown_store.go`
   - `internal/store/gitstore.go`
   - `internal/store/objectstore.go`
2. 在 `internal/store/` 目录下新建 `store_android.go`，声明同名桩结构体与构造函数：

```go
//go:build android

package store

import (
	"context"
	"errors"

	coreauth "github.com/router-for-me/CLIProxyAPI/v7/sdk/cliproxy/auth"
)

var errRemoteStoreDisabled = errors.New("remote enterprise storage is disabled on android")

// Postgres 桩
type PostgresStoreConfig struct {
	DSN      string
	Schema   string
	SpoolDir string
}

type PostgresStore struct{}

func NewPostgresStore(_ context.Context, _ PostgresStoreConfig) (*PostgresStore, error) {
	return nil, errRemoteStoreDisabled
}
func (s *PostgresStore) Bootstrap(_ context.Context, _ string) error { return errRemoteStoreDisabled }
func (s *PostgresStore) ConfigPath() string                         { return "" }
func (s *PostgresStore) AuthDir() string                            { return "" }
func (s *PostgresStore) WorkDir() string                            { return "" }
func (s *PostgresStore) Load(_ context.Context) ([]*coreauth.Auth, error) {
	return nil, errRemoteStoreDisabled
}
func (s *PostgresStore) Save(_ context.Context, _ *coreauth.Auth) (string, error) {
	return "", errRemoteStoreDisabled
}
func (s *PostgresStore) Delete(_ context.Context, _ string) error { return errRemoteStoreDisabled }

// ObjectStore 桩
type ObjectStoreConfig struct {
	Endpoint  string
	AccessKey string
	SecretKey string
	Bucket    string
	UseSSL    bool
	SpoolDir  string
}

type ObjectTokenStore struct{}

func NewObjectTokenStore(_ ObjectStoreConfig) (*ObjectTokenStore, error) {
	return nil, errRemoteStoreDisabled
}
func (s *ObjectTokenStore) Bootstrap(_ context.Context, _ string) error { return errRemoteStoreDisabled }
func (s *ObjectTokenStore) ConfigPath() string                         { return "" }
func (s *ObjectTokenStore) AuthDir() string                            { return "" }
func (s *ObjectTokenStore) Load(_ context.Context) ([]*coreauth.Auth, error) {
	return nil, errRemoteStoreDisabled
}
func (s *ObjectTokenStore) Save(_ context.Context, _ *coreauth.Auth) (string, error) {
	return "", errRemoteStoreDisabled
}
func (s *ObjectTokenStore) Delete(_ context.Context, _ string) error { return errRemoteStoreDisabled }

// GitStore 桩
type GitTokenStore struct{}

func NewGitTokenStore(_, _, _, _ string) *GitTokenStore {
	return &GitTokenStore{}
}
func (s *GitTokenStore) SetBaseDir(_ string)                   {}
func (s *GitTokenStore) EnsureRepository() error               { return errRemoteStoreDisabled }
func (s *GitTokenStore) ConfigPath() string                    { return "" }
func (s *GitTokenStore) AuthDir() string                       { return "" }
func (s *GitTokenStore) PersistConfig(_ context.Context) error { return errRemoteStoreDisabled }
func (s *GitTokenStore) Load(_ context.Context) ([]*coreauth.Auth, error) {
	return nil, errRemoteStoreDisabled
}
func (s *GitTokenStore) Save(_ context.Context, _ *coreauth.Auth) (string, error) {
	return "", errRemoteStoreDisabled
}
func (s *GitTokenStore) Delete(_ context.Context, _ string) error { return errRemoteStoreDisabled }
```
* **效果**：`cmd/server/main.go` 中的 `store.NewPostgresStore` 等引用顺利通过类型检查，`github.com/jackc/pgx/v5`、`github.com/go-git/go-git/v6`、`github.com/minio/minio-go/v7` 在 Android 目标中彻底不编译。

---

### 2.4 剪枝实战 3：Redis 分布式通信客户端（internal/home/client.go）与 home.Current() 审计闭环

#### 2.4.1 依赖剖析与源码全量审计闭环（nil vs &Client{} 判定）
`internal/home` 在项目中被用于集群多机协同与分布式键值缓存。针对前置评审中指出的“`home.Current()` nil 语义可能破坏降级”的深层顾虑，我们对全工程进行了详尽的针对性代码审计：

1. **`home.Current()` 的生命周期与来源**：
   - 全局单例定义于 `internal/home/global.go`：`var currentClient atomic.Pointer[Client]`，其默认值始终为 `nil`；
   - 全工程**唯一**调用 `home.SetCurrent(client)` 的位置在 `sdk/cliproxy/service_home.go:700`，且该逻辑受外层 `if s.cfg.Home.Enabled` 绝对守卫；
   - 在 Android 端，`cfg.Home.Enabled` 永久强制为 `false`，因此 `SetCurrent` 绝无可能被调用，`home.Current()` 在运行时天然恒为 `nil`。

2. **`internal/home/kv_helpers.go` 中的降级判定陷阱与解法**：
   - 源码中的核心降级检测函数 `CurrentKVClient()` 如下：
     ```go
     func CurrentKVClient() (*Client, bool, error) {
         client := Current()
         if client == nil {
             return nil, false, nil // homeMode=false, err=nil -> 完美回退本地内存缓存！
         }
         if !client.Enabled() {
             return nil, true, fmt.Errorf("home kv store unavailable: %w", ErrDisabled) // 致命：标记 homeMode=true 但报错！
         }
         ...
     ```
   - **核心洞察**：若桩代码的 `Current()` 返回一个非空结构体 `&Client{}` 且 `Enabled() == false`，则 `CurrentKVClient()` 会错误地返回 `homeMode=true` 并携带错误，导致 `KVGetJSONRequired` 误认为正处于集群模式从而报错中断，**直接阻断了本地内存降级路径**！
   - **正确设计**：**在 Android 目标上，`home.Current()` 必须恒定保持为 `nil`**。只要 `Current() == nil`，`CurrentKVClient()` 就能干净地返回 `(nil, false, nil)`，全工程所有缓存与推理记录即可无缝执行纯内存降级。

3. **全工程 6 大生产级引用点逐一审计核验**：
   - `internal/api/server_middleware.go:53`：`if s.cfg.Home.Enabled` 守卫，Disabled 时直接 `c.Next()`；
   - `internal/api/server_routes.go:886`：仅在 `s.cfg.Home.Enabled` 为真时才会调用 `loadHomeModelEntries`；
   - `internal/client/codex/optimize-multi-agent-v2/...go:341`：`if homeEnabled` 为假时直接使用本地 `registry`；
   - `internal/logging/request_logger_home.go:19`：`if !l.homeEnabled` 直接返回 `nil`；
   - `sdk/cliproxy/auth/conductor_home.go:418`：`PublishHomeDispatch` 遇 `client == nil` 直接返回 `nil`；
   - `internal/runtime/executor/helps/home_refresh.go:87`：`if !cfg.Home.Enabled` 直接返回 `(nil, false, nil)` 执行本地 Token 刷新。

#### 2.4.2 实施步骤
1. **原位保留** `internal/home/client.go` 文件名，顶部加入 `//go:build !android`。
2. 在 `internal/home/` 目录下新建 `client_android.go`：
   - 自包含定义被剪掉的 `PluginTask`、`KVSetOptions` 结构体；
   - 导出 `func New(homeCfg config.HomeConfig) *Client` 满足 `main.go` 与 `service_home.go` 编译；
   - 所有方法均做 `c == nil` 防御性设计，永不 Panic：

```go
//go:build android

package home

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"time"

	"github.com/router-for-me/CLIProxyAPI/v7/internal/config"
	"github.com/router-for-me/CLIProxyAPI/v7/sdk/pluginstore"
)

var (
	ErrDisabled     = errors.New("home client disabled on android")
	ErrNotConnected = errors.New("home client not connected")
)

// 自包含补齐原 client.go 定义的结构体，避免编译期类型分裂
type PluginTask struct {
	ID             uint      `json:"id"`
	Operation      string    `json:"operation"`
	PluginID       string    `json:"plugin_id"`
	TargetNodeType string    `json:"target_node_type,omitempty"`
	TargetNodeID   string    `json:"target_node_id,omitempty"`
	CreatedAt      time.Time `json:"created_at"`
	UpdatedAt      time.Time `json:"updated_at"`
}

type KVSetOptions struct {
	EX time.Duration
	PX time.Duration
	NX bool
	XX bool
}

type Client struct{}

// New 满足 cmd/server/main.go 与 sdk/cliproxy/service_home.go 的构造调用
func New(_ config.HomeConfig) *Client {
	return &Client{}
}

// 所有方法接收者均做 nil 安全设计，Enabled 恒为 false
func (c *Client) Enabled() bool                                { return false }
func (c *Client) HeartbeatOK() bool                            { return false }
func (c *Client) Close()                                       {}
func (c *Client) NewLifetime() *Client                         { return c }
func (c *Client) SetManagedLifetime(_ bool)                    {}
func (c *Client) Ping(_ context.Context) error                 { return ErrDisabled }
func (c *Client) GetConfig(_ context.Context) ([]byte, error)  { return nil, ErrDisabled }
func (c *Client) GetModels(_ context.Context, _ http.Header, _ url.Values) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) KVGet(_ context.Context, _ string) ([]byte, bool, error) {
	return nil, false, ErrDisabled
}
func (c *Client) KVSet(_ context.Context, _ string, _ []byte, _ KVSetOptions) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) KVSetNX(_ context.Context, _ string, _ []byte, _ time.Duration) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) KVExpire(_ context.Context, _ string, _ time.Duration) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) RPopAuth(_ context.Context, _, _ string, _ http.Header, _ int) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) LPushUsage(_ context.Context, _ []byte) error { return ErrDisabled }
func (c *Client) RPushRequestLog(_ context.Context, _ []byte) error { return ErrDisabled }
func (c *Client) RPushAppLog(_ context.Context, _ []byte) error { return ErrDisabled }
func (c *Client) RPushPluginStatus(_ context.Context, _ []byte) error { return ErrDisabled }
func (c *Client) GetPluginTasks(_ context.Context) ([]PluginTask, error) { return nil, ErrDisabled }
func (c *Client) GetPluginSync(_ context.Context, _ pluginstore.PluginSyncRequest) (pluginstore.PluginSyncResponse, error) {
	return pluginstore.PluginSyncResponse{}, ErrDisabled
}
func (c *Client) SetLifecycleConfig(_ config.CredentialConcurrencyConfig) error { return ErrDisabled }
func (c *Client) StartConfigSubscriber(_ context.Context, _ func([]byte) error) {}
```
* **效果**：`home.Current()` 恒定为 `nil`，全工程 54 处调用无缝、平滑降级至纯本地内存模式；`github.com/redis/go-redis/v9` 彻底从 Android 编译依赖树中剥离。

---

### 2.5 剪枝实战 4：局域网 mDNS 组网广播（internal/discovery/zeroconf.go）

#### 2.5.1 依赖剖析与调用链
`sdk/cliproxy/discovery_advertiser.go` 负责局域网服务发现广播。它仅依赖 `internal/discovery/zeroconf.go` 导出的两个方法：`Start(ctx, spec)` 和 `Stop()`。`zeroconf.go` 引入了 `github.com/libp2p/zeroconf/v2` 和 `github.com/miekg/dns`。

#### 2.5.2 实施步骤
1. **原位保留** `internal/discovery/zeroconf.go` 文件名，顶部声明 `//go:build !android`。
2. 同目录下新建 `zeroconf_android.go`，实现静默空桩：

```go
//go:build android

package discovery

import (
	"context"
)

type ZeroconfAdvertiser struct{}

func NewZeroconfAdvertiser() *ZeroconfAdvertiser {
	return &ZeroconfAdvertiser{}
}

func (a *ZeroconfAdvertiser) Start(_ context.Context, _ ServiceSpec) error {
	// Android 移动端静默忽略局域网广播请求，不启动组播监听
	return nil
}

func (a *ZeroconfAdvertiser) Stop() error {
	return nil
}
```
* **效果**：`sdk/cliproxy/discovery_advertiser.go` 一行不改，`libp2p/zeroconf/v2` 与 `miekg/dns` 彻底不编译，杜绝移动端网络后台广播耗电。

---

### 2.6 剪枝实战 5：桌面终端 TUI 交互界面（internal/tui/*.go）

#### 2.6.1 依赖剖析与调用链
原项目在 `--tui` 或 `--standalone` 模式下会启动 Bubbletea 终端控制台，整个 `internal/tui/` 包引入了 `bubbletea`、`lipgloss` 和 `clipboard`。
在 `cmd/server/main.go` 中，调用了：
- `hook := tui.NewLogHook(2000)`，随后调用 `hook.SetFormatter(...)`；
- `client := tui.NewClient(cfg.Port, password)`，随后轮询 `client.GetConfig()`；
- `tui.Run(...)` 启动界面。
因此桩文件必须对齐上述方法，否则编译即报方法未定义错误。

#### 2.6.2 实施步骤
1. 在 `internal/tui/` 下的所有原有 `.go` 文件顶部第 1 行均加入 `//go:build !android`。
2. 在 `internal/tui/` 目录下新建 `tui_android.go`：

```go
//go:build android

package tui

import (
	"errors"
	"io"

	log "github.com/sirupsen/logrus"
)

// LogHook 移动端静默桩实现，补齐 SetFormatter 方法
type LogHook struct{}

func NewLogHook(_ int) *LogHook {
	return &LogHook{}
}
func (h *LogHook) SetFormatter(_ log.Formatter) {}
func (h *LogHook) Levels() []log.Level {
	return log.AllLevels
}
func (h *LogHook) Fire(_ *log.Entry) error {
	return nil
}

// Client 桩实现，补齐 GetConfig 方法
type Client struct{}

func NewClient(_ int, _ string) *Client {
	return &Client{}
}
func (c *Client) GetConfig() (map[string]any, error) {
	return nil, errors.New("tui client disabled on android")
}

// Run 桩实现，移动端若误传 --tui 标志直接返回友好错误
func Run(_ int, _ string, _ *LogHook, _ io.Writer) error {
	return errors.New("terminal TUI mode is not supported on Android, please access via web or API")
}
```
* **效果**：`cmd/server/main.go` 正常编译，`github.com/charmbracelet/bubbletea`、`lipgloss`、`bubbles` 以及 `atotto/clipboard` 完全被排除。

---

### 2.7 剪枝实战 6：桌面跨平台浏览器调用与移动端 OAuth 闭环（internal/browser/browser.go）

#### 2.7.1 依赖剖析与移动端 OAuth 痛点
在 `sdk/auth/claude.go` 等登录流程中，调用了：
- `browser.IsAvailable()` 检查系统是否有浏览器能力；
- `browser.OpenURL(authURL)` 弹出授权链接；
- `browser.GetPlatformInfo()` 获取平台信息。

原版 `browser.go` 使用 `github.com/skratchdot/open-golang` 执行 `xdg-open` / `cmd.exe`。在 Android 平台：
1. **Termux CLI 场景**：没有 `xdg-open`，用户需要在控制台显著位置获得可点击或可复制的链接；
2. **Android App 嵌入场景**：不能只打日志，必须将授权 URL 传导给 Android 宿主（Kotlin 层），由宿主启动 Chrome Custom Tabs 完成授权。

#### 2.7.2 实施步骤
1. **原位保留** `internal/browser/browser.go` 文件名，顶部第 1 行加入 `//go:build !android`。
2. 同目录下新建 `browser_android.go`，提供**双轨 OAuth 响应机制**：

```go
//go:build android

package browser

import (
	"fmt"
	"runtime"
	"sync"

	log "github.com/sirupsen/logrus"
)

var (
	urlHandlerMu sync.RWMutex
	urlHandler   func(string) error
)

// SetURLHandler 允许 Android 宿主（如 JNI/Kotlin 层）注入 URL 打开回调
func SetURLHandler(handler func(string) error) {
	urlHandlerMu.Lock()
	defer urlHandlerMu.Unlock()
	urlHandler = handler
}

// OpenURL 移动端双轨实现
func OpenURL(url string) error {
	urlHandlerMu.RLock()
	handler := urlHandler
	urlHandlerMu.RUnlock()

	// 轨道 A：宿主 App 已注册回调，优先交由 Android Custom Tabs 打开
	if handler != nil {
		if err := handler(url); err == nil {
			return nil
		}
	}

	// 轨道 B：Termux CLI 或无回调兜底，控制台显著打印供用户复制点击
	fmt.Printf("\n=======================================================\n")
	fmt.Printf(" [OAuth 授权链接生成]\n")
	fmt.Printf(" 请在手机浏览器中打开以下链接完成认证授权:\n")
	fmt.Printf(" %s\n", url)
	fmt.Printf("=======================================================\n\n")
	log.Infof("OAuth authorization URL: %s", url)
	return nil
}

// IsAvailable 告知上游认证流浏览器通道可用，避免直接降级报错
func IsAvailable() bool {
	return true
}

func GetPlatformInfo() map[string]interface{} {
	return map[string]interface{}{
		"os":        runtime.GOOS,
		"arch":      runtime.GOARCH,
		"available": true,
	}
}
```
* **效果**：消除 `open-golang` 依赖，同时打通 Termux 终端与 Android App 内部拉取浏览器的 OAuth 完整闭环。

---

### 2.8 核心业务代码 0 侵入验证清单与防上游漂移 CI 守卫

#### 2.8.1 核心调用方代码 0 侵入验证清单

经过上述 6 处原位打标与自包含打桩改造后，我们对核心业务模块进行**侵入度审计**：

| 模块目录 | 是否有任何代码修改 | 兼容性保证说明 |
| :--- | :--- | :--- |
| `cmd/server/main.go` | **0 修改** | 所有导入的 `store`、`tui`、`home` 均由 `_android.go` 桩代码平替，类型与方法 100% 对齐。 |
| `sdk/cliproxy/` | **0 修改** | 所有 service 生命周期、执行器调度、凭据轮询完全保持原样。 |
| `internal/translator/` | **0 修改** | OpenAI ⇄ Claude ⇄ Gemini 协议互转原汁原味。 |
| `internal/thinking/` | **0 修改** | 深度思考（reasoning_effort / budget_tokens）逻辑 100% 完整保留。 |
| `internal/runtime/executor/` | **0 修改** | Claude、Codex、Gemini、xAI、Kimi、Antigravity 执行器保持完全一致。 |
| `internal/registry/` | **0 修改** | 动态模型目录拉取器（`model_updater.go`）正常运行，持续自更新。 |
| `internal/managementasset/` | **0 修改** | 管理后台单页下载器（`updater.go`）正常运行，持续自更新。 |
| `internal/client/` & `utls` | **0 修改** | TLS 客户端指纹混淆模拟完整保留。 |

#### 2.8.2 防范上游文件污染与类型漂移的 CI 守卫规范

针对评审指出的“上游一旦新增 `internal/store/mysqlstore.go`，本地若不打标会导致 Android 目标意外引入驱动与安全面污染”的实际维护风险，单靠“人工自律”是不够的。必须在 CI 流水线中设立**三道自动化防御门禁（Automated Drift Prevention Gates）**：

1. **门禁 1：依赖黑名单扫描门禁（Dependency Blacklist Scan）**
   在 CI 中使用 `go list` 针对 `GOOS=android` 的实际依赖图进行断言。若编译图解析出任何已被剪除的黑名单包，立即中断构建并报错：
   ```bash
   # CI 门禁脚本命令：检测是否意外漏入黑名单三方依赖
   FORBIDDEN_DEPS=$(GOOS=android go list -tags="android" -deps ./cmd/server 2>/dev/null | grep -E \
     'github.com/pion/webrtc|github.com/redis/go-redis|github.com/jackc/pgx|github.com/go-git/go-git|github.com/minio/minio-go|github.com/charmbracelet/bubbletea|github.com/libp2p/zeroconf' || true)

   if [ -n "$FORBIDDEN_DEPS" ]; then
       echo "❌ [CI FATAL] 检测到 Android 构建图意外引入了被剪裁的黑名单依赖:"
       echo "$FORBIDDEN_DEPS"
       exit 1
   fi
   echo "✅ [CI PASS] 依赖黑名单检查通过：无多余重型三方依赖被编译。"
   ```

2. **门禁 2：敏感剪裁目录未打标文件覆盖扫描（Untagged Files Coverage Gate）**
   检查所有敏感剪枝目录（`internal/store/`、`internal/home/`、`internal/discovery/`、`internal/tui/`）下的每一个 `.go` 文件，断言其首行必须显式带有 `//go:build !android` 或 `//go:build android`，**严禁任何“无标签裸奔文件”**。
   若上游未来新增了 `internal/store/mysqlstore.go`，CI 会在合并的第一秒直接爆红，精准提示工程师为新增文件补充打标：
   ```bash
   UNTAGGED_FILES=$(grep -rL -E '^//go:build (android|!android)' \
     internal/store internal/discovery internal/tui 2>/dev/null | grep '\.go$' | grep -v '_test\.go' || true)

   if [ -n "$UNTAGGED_FILES" ]; then
       echo "❌ [CI FATAL] 发现敏感剪裁目录下存在未打标的新增文件，将导致 Android 编译污染:"
       echo "$UNTAGGED_FILES"
       echo "--> 请在上述文件顶部第 1 行补充 '//go:build !android' 后重试。"
       exit 1
   fi
   echo "✅ [CI PASS] 剪裁目录标签覆盖度检查通过：100% 打标受控。"
   ```

3. **门禁 3：Android 目标符号与类型对齐自动化编译断言**
   在每次拉取上游（`git merge upstream/main`）后，自动化触发 `GOOS=android` 交叉编译测试：
   `GOOS=android CGO_ENABLED=1 CC="..." go build -tags="android" -o /dev/null ./cmd/server`
   若上游修改了某个原被剪枝文件中的结构体方法签名，编译期立即报错拦截，指导工程师在对应的 `_android.go` 桩代码中对齐该方法。

通过这三道自动化 CI 守卫，使得“最小侵入原位打标”从口头约定真正变为**确定性的工程纪律**。

---

# 第三部分：移动端（GOOS=android）交叉编译与运行验证

---

### 3.1 交叉编译环境与 NDK 工具链选型

在 Android 交叉编译中，必须遵循以下环境底线规范：

#### 3.1.1 Go 编译器版本基线：Go 1.22+
* **关键原因（Android 14+ 根证书寻址）**：
  Google 从 Android 14 (API 34) 起，将系统 CA 根证书库从传统的 `/system/etc/security/cacerts` 迁移到 Mainline 模块的 `/apex/com.android.conscrypt/cacerts`。若使用低于 Go 1.21 的版本交叉编译，Go 发起 HTTPS 请求访问 `api.anthropic.com` 或 `api.openai.com` 时，会抛出 `x509: certificate signed by unknown authority` 证书校验错误。本项目基准建议锁定在 **Go 1.22 ~ Go 1.26**。

#### 3.1.2 强制开启 CGO 与 NDK 工具链（DNS 解析合规）
* **关键原因（域名解析合规）**：
  Android 操作系统没有传统的 `/etc/resolv.conf` 文件，所有的 DNS 域名解析必须经过系统的 `netd` 守护进程。若编译时使用纯 Go（`CGO_ENABLED=0`），Go 内部的纯 Go resolver 会因为读不到配置文件而直接报 `lookup ...: no such host`。
* **规约**：必须设定 `CGO_ENABLED=1`，并通过 NDK 的 Clang 编译器进行交叉链接，确保 Go 的网络层调用 Android Bionic C 库的 `getaddrinfo()`。

#### 3.1.3 NDK 版本与 API 级别
* **NDK 推荐版本**：Android NDK r25c、r26b 或更高版本。
* **最低兼容 API Level**：`API 26`（Android 8.0 Oreo 以上，覆盖当前市场上 98%+ 的活跃 Android 设备）。

---

### 3.2 核心符号与 API 实施前置核实清单（Pre-Flight Checklist）

为避免编写 `cmd/mobile/main.go` 或编译脚本时因符号、签名或语义不匹配而受阻，我们在源码中对涉及的关键符号进行了逐项交叉比对核实：

| 核心待查符号 | 源码真实定义位置 | 真实签名与核验结论 | 移动端落地规约与避坑指导 |
| :--- | :--- | :--- | :--- |
| `config.LoadConfigOptional` | `sdk/config/config.go:46`<br>`internal/config/config_load.go:32` | **100% 存在**<br>`func LoadConfigOptional(configFile string, optional bool) (*Config, error)` | 传入 `(cfgPath, false)`。当文件不存在或配置为空时返回 `&Config{}`。 |
| `config.DefaultConfig` | `sdk/config/` 下**不存在此函数** | **需纠正**<br>原版在 `cmd/server/main.go:598-620` 采用内联初始化默认值 | **规约**：采用 `cfg := &config.Config{ CredentialInFlight: config.DefaultCredentialInFlightConfig() }; cfg.NormalizePluginsConfig()` 生成合法默认对象。 |
| `config.DefaultCredentialInFlightConfig()` | `internal/config/credential_in_flight.go:31` | **100% 存在**<br>`func DefaultCredentialInFlightConfig() CredentialInFlightConfig` | 返回包含 2s 快照间隔、10s 超时的合法在途凭据观测默认配置。 |
| `cfg.NormalizePluginsConfig()` | `internal/config/config_normalization.go:11` | **100% 存在**<br>`func (cfg *Config) NormalizePluginsConfig()` | 为 `Config` 填充默认的插件存储目录与认证规范。 |
| `config.HomeConfig` 结构体 | `internal/config/home.go:4` | **100% 存在**<br>包含 `Enabled bool`, `Host string`, `Port int`, `TLS HomeTLSConfig` 等 | 移动端强制设为 `cfg.Home = config.HomeConfig{}`（纯零值结构体），彻底清空 Host/Port/TLS，保障纯本地模式。 |
| `config.CodexLiveMediaRelayConfig` | `internal/config/codex_live.go:19` | **100% 存在**<br>定义于 `internal/config` 包 | 用于 `internal/client/codex/live/media_android.go` 桩代码入参，类型签名完全对齐。 |
| `pluginstore.PluginSyncRequest`<br>`PluginSyncResponse` | `sdk/pluginstore/pluginstore.go:52, 54` | **100% 存在**<br>公开导出的插件同步请求与响应结构体 | 用于 `internal/home/client_android.go` 中 `GetPluginSync` 桩方法，签名完全一致。 |
| `cfg.AuthDir`<br>`cfg.Host`<br>`cfg.Port` | `internal/config/config.go:12, 14, 39` | **100% 存在**<br>`AuthDir string`, `Host string`, `Port int` | 均为 `Config` 顶层导出字段，可直接安全赋值。 |
| `cliproxy.NewBuilder()`<br>`.WithConfig()`<br>`.WithConfigPath()` | `sdk/cliproxy/builder.go:85-306` | **100% 存在**<br>标准 Fluent 链式构建者模式 | 连续调用 `.WithConfig(cfg).WithConfigPath(cfgPath).Build()` 返回 `(*Service, error)`。 |
| `svc.Run(ctx)`<br>`svc.Shutdown(ctx)` | `sdk/cliproxy/service_lifecycle.go:32, 228` | **100% 存在**<br>`Run(ctx context.Context) error`<br>`Shutdown(ctx context.Context) error` | `Run` 阻塞直到 Context 取消或 Server 报错。停止时由 `context.CancelFunc` 配合 `Shutdown` 优雅回收。 |
| `/healthz`<br>`/v1/models` | `internal/api/server_routes.go:51, 574` | **100% 存在**<br>Gin 引擎已直接注册这两个端点 | 作为验收测试端点，`/healthz` 返回 `{"status":"ok"}`，`/v1/models` 返回统一聚合模型表。 |
| `home.CurrentKVClient()`<br>`home.Current()` | `internal/home/kv_helpers.go:20`<br>`internal/home/global.go:13` | **100% 存在**<br>当 `Current() == nil` 时返回 `(nil, false, nil)` | 移动端必须保持 `home.Current() == nil`，确保全工程平滑走入本地内存缓存回退。 |

---

### 3.3 双交付形态的交叉编译实战（CLI 二进制 vs C-Shared .so）

针对不同的使用场景，提供两种产物形态：

#### 形态 A：独立 CLI 可执行二进制（适用于 Termux / ADB Shell）
在 Termux 或已获取 Root 权限的 Android 设备上，用户可以直接像在 Linux 终端一样运行可执行文件。

* **编译命令**：
```bash
CGO_ENABLED=1 \
GOOS=android \
GOARCH=arm64 \
CC="$TOOLCHAIN/bin/aarch64-linux-android26-clang" \
CXX="$TOOLCHAIN/bin/aarch64-linux-android26-clang++" \
go build \
    -tags="android" \
    -trimpath \
    -ldflags="-s -w -extldflags '-Wl,--gc-sections'" \
    -o dist/cli-proxy-api-android-arm64 \
    ./cmd/server
```

#### 形态 B：C-Shared 动态链接库（`libcliproxy.so`，适用于内嵌 Android App）
在开发标准 Android APK 时，由于 Android 10+ 的 `W^X` 安全策略禁止在 App 私有数据区执行 `exec()` 二进制文件，必须将服务封装为 C-Shared 动态库，由 Android 前台服务通过 JNI `System.loadLibrary("cliproxy")` 加载。

##### JNI 线程附着与回调闭环深度防坑设计
在 Go runtime 生成的后台 goroutine 中直接回调 Java JNI 函数指针存在严重隐患：**Go 创建的 OS 线程未附着（Attach）到 Android ART 虚拟机，直接调用 `env->CallVoidMethod` 会导致 `JNI ERROR (app bug): Thread not attached` 崩溃**。

为此，我们在 `cmd/mobile/main.go` 中提供**两种健壮且互补的通信保障**：
1. **主动推送（JNI Push 轨）**：在 C 桥接层利用 `JavaVM*` 的 `GetEnv` 检查当前线程附着状态，遇 `JNI_EDETACHED` 自动执行 `AttachCurrentThread`，并在回调后配对 `DetachCurrentThread`；
2. **被动拉取（Channel Pull 轨）**：Go 侧将 URL 放入无阻塞缓冲 channel，并对外导出 `PollOAuthURL(timeoutMs)` C 函数。Kotlin 协程可在 `Dispatchers.IO` 下主动轮询，**彻底避开跨语言 JNI 线程上下文切换的潜在风险**。

* **导出入口代码（`cmd/mobile/main.go`）**：
```go
// cmd/mobile/main.go
package main

/*
#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

// 全局互斥锁，彻底杜绝跨线程注册与回调时的数据竞争与野指针崩溃
static pthread_mutex_t g_listener_mutex = PTHREAD_MUTEX_INITIALIZER;
static JavaVM *g_jvm = NULL;
static jobject g_listener_obj = NULL;
static jmethodID g_listener_mid = NULL;

// 1. 双重 JavaVM 缓存入口：JNI_OnLoad 与 nativeInit 双保险（兼容 c-shared 模式）
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    pthread_mutex_lock(&g_listener_mutex);
    g_jvm = vm;
    pthread_mutex_unlock(&g_listener_mutex);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_com_cliproxy_CLIProxy_nativeInit(JNIEnv *env, jclass clazz) {
    pthread_mutex_lock(&g_listener_mutex);
    if (g_jvm == NULL) {
        (*env)->GetJavaVM(env, &g_jvm);
    }
    pthread_mutex_unlock(&g_listener_mutex);
}

// 2. 线程安全的监听器注册函数
JNIEXPORT void JNICALL Java_com_cliproxy_CLIProxy_nativeRegisterOAuthListener(
    JNIEnv *env, jclass clazz, jobject listener) {
    pthread_mutex_lock(&g_listener_mutex);
    if (g_listener_obj != NULL) {
        (*env)->DeleteGlobalRef(env, g_listener_obj);
        g_listener_obj = NULL;
        g_listener_mid = NULL;
    }
    if (listener != NULL) {
        g_listener_obj = (*env)->NewGlobalRef(env, listener);
        jclass l_class = (*env)->GetObjectClass(env, listener);
        g_listener_mid = (*env)->GetMethodID(l_class, "onOAuthURL", "(Ljava/lang/String;)V");
    }
    pthread_mutex_unlock(&g_listener_mutex);
}

// 3. 线程安全的 C 触发器：带互斥锁与 JVM Attach/Detach 保护
static void trigger_java_oauth_url(const char *url_str) {
    pthread_mutex_lock(&g_listener_mutex);
    if (g_jvm == NULL || g_listener_obj == NULL || g_listener_mid == NULL) {
        pthread_mutex_unlock(&g_listener_mutex);
        return;
    }

    JNIEnv *env = NULL;
    int needs_detach = 0;
    jint res = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) == 0) {
            needs_detach = 1;
        } else {
            pthread_mutex_unlock(&g_listener_mutex);
            return;
        }
    } else if (res != JNI_OK) {
        pthread_mutex_unlock(&g_listener_mutex);
        return;
    }

    jstring j_url = (*env)->NewStringUTF(env, url_str);
    (*env)->CallVoidMethod(env, g_listener_obj, g_listener_mid, j_url);
    (*env)->DeleteLocalRef(env, j_url);

    if (needs_detach) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
    pthread_mutex_unlock(&g_listener_mutex);
}

// 4. JNI 拉取函数：在 C 层将 Go 导出的 char* 自动转为 jstring 并就地 free，Kotlin 零内存负担
char* PollOAuthURL(int timeoutMs);
void FreeCString(char* ptr);

JNIEXPORT jstring JNICALL Java_com_cliproxy_CLIProxy_nativePollOAuthURL(
    JNIEnv *env, jclass clazz, jint timeout_ms) {
    char *c_url = PollOAuthURL(timeout_ms);
    if (c_url == NULL) {
        return NULL;
    }
    jstring j_url = (*env)->NewStringUTF(env, c_url);
    FreeCString(c_url);
    return j_url;
}

// 5. JNI 启动与停止服务绑定
int StartServer(char *cConfigDir, char *cHost, int port);
void StopServer(void);

JNIEXPORT jint JNICALL Java_com_cliproxy_CLIProxy_nativeStartServer(
    JNIEnv *env, jclass clazz, jstring configDir, jstring host, jint port) {
    const char *c_dir = (*env)->GetStringUTFChars(env, configDir, NULL);
    const char *c_host = host != NULL ? (*env)->GetStringUTFChars(env, host, NULL) : NULL;
    int ret = StartServer((char*)c_dir, (char*)c_host, port);
    (*env)->ReleaseStringUTFChars(env, configDir, c_dir);
    if (c_host != NULL) {
        (*env)->ReleaseStringUTFChars(env, host, c_host);
    }
    return ret;
}

JNIEXPORT void JNICALL Java_com_cliproxy_CLIProxy_nativeStopServer(
    JNIEnv *env, jclass clazz) {
    StopServer();
}
*/
import "C"

import (
	"context"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"unsafe"

	"github.com/router-for-me/CLIProxyAPI/v7/internal/browser"
	"github.com/router-for-me/CLIProxyAPI/v7/internal/config"
	"github.com/router-for-me/CLIProxyAPI/v7/sdk/cliproxy"
	log "github.com/sirupsen/logrus"
)

var (
	serviceLock     sync.Mutex
	serviceCancel   context.CancelFunc
	serviceDoneChan chan struct{}
	runningState    atomic.Bool

	// 零 JNI 风险事件拉取管道
	oauthURLChan = make(chan string, 16)
)

func init() {
	// 挂载移动端双轨 OAuth 拦截器
	browser.SetURLHandler(func(url string) error {
		// 轨 1：放入 Channel 供 Kotlin 协程无锁拉取
		select {
		case oauthURLChan <- url:
		default:
			// 缓冲满时丢弃最旧项
			select {
			case <-oauthURLChan:
			default:
			}
			oauthURLChan <- url
		}

		// 轨 2：尝试通过 C-JNI 线程附着桥接主动通知 Java 监听器
		cURL := C.CString(url)
		defer C.free(unsafe.Pointer(cURL))
		C.trigger_java_oauth_url(cURL)
		return nil
	})
}

//export PollOAuthURL
// 供 Android Kotlin/Java 协程在 IO 线程轮询获取待授权的 URL（超时单位毫秒）
// 返回的字符串在 C 桥接层自动转为 jstring 并调用 FreeCString 释放
func PollOAuthURL(timeoutMs C.int) *C.char {
	timeout := time.Duration(timeoutMs) * time.Millisecond
	select {
	case url := <-oauthURLChan:
		return C.CString(url)
	case <-time.After(timeout):
		return nil
	}
}

//export FreeCString
// 释放 Go CString 分配的 C 堆内存
func FreeCString(ptr *C.char) {
	if ptr != nil {
		C.free(unsafe.Pointer(ptr))
	}
}

//export StartServer
// 启动服务，参数：
//   cConfigDir: 配置文件与 auths 凭据存放路径（如 context.filesDir.absolutePath）
//   cHost: 监听 IP。传入 NULL 或空串默认绑定 "127.0.0.1"（本地回环安全保护）；
//          若需局域网内其他设备（如电脑）共享手机代理，可传入 "0.0.0.0"
//   port: 监听端口号（如 8317）
// 返回值：1 成功，0 已在运行，-1 启动失败
func StartServer(cConfigDir *C.char, cHost *C.char, port C.int) C.int {
	serviceLock.Lock()
	defer serviceLock.Unlock()

	if runningState.Load() {
		return 0 // 已在运行中
	}

	configDir := C.GoString(cConfigDir)
	cfgPath := filepath.Join(configDir, "config.yaml")

	cfg, err := config.LoadConfigOptional(cfgPath, false)
	if err != nil || cfg == nil {
		// 经源码核验：与 cmd/server/main.go:598-620 对齐的合法默认配置构造
		cfg = &config.Config{
			CredentialInFlight: config.DefaultCredentialInFlightConfig(),
		}
		cfg.NormalizePluginsConfig()
	}

	// 监听地址配置（默认回环 127.0.0.1）
	host := "127.0.0.1"
	if cHost != nil {
		if h := strings.TrimSpace(C.GoString(cHost)); h != "" {
			host = h
		}
	}
	cfg.Host = host
	cfg.Port = int(port)
	cfg.AuthDir = filepath.Join(configDir, "auths")

	// 彻底清空 Home 配置（Host/Port/TLS/Enabled 均置零），杜绝任何潜在的 Redis 触发路径
	cfg.Home = config.HomeConfig{}

	builder := cliproxy.NewBuilder().
		WithConfig(cfg).
		WithConfigPath(cfgPath)

	svc, err := builder.Build()
	if err != nil {
		log.Errorf("failed to build cliproxy service: %v", err)
		return -1
	}

	ctx, cancel := context.WithCancel(context.Background())
	serviceCancel = cancel
	serviceDoneChan = make(chan struct{})
	runningState.Store(true)

	go func() {
		defer close(serviceDoneChan)
		if errRun := svc.Run(ctx); errRun != nil {
			log.Errorf("cliproxy service run returned: %v", errRun)
		}
		runningState.Store(false)
	}()

	return 1
}

//export StopServer
// 停止服务并阻塞等待后台 goroutine 优雅退出
func StopServer() {
	serviceLock.Lock()
	cancel := serviceCancel
	doneCh := serviceDoneChan
	serviceCancel = nil
	serviceDoneChan = nil
	serviceLock.Unlock()

	if cancel != nil {
		cancel()
	}
	if doneCh != nil {
		select {
		case <-doneCh:
		case <-time.After(5 * time.Second):
			log.Warn("cliproxy service shutdown wait timed out")
		}
	}
	runningState.Store(false)
}

func main() {}
```

##### 宿主 App 接入层 Kotlin JNI 绑定代码（`CLIProxy.kt`）

在 Android App 宿主工程中，创建对应的单例对象与回调接口，实现类型安全且零内存泄漏风险的 JNI 交互：

```kotlin
package com.cliproxy

import java.util.concurrent.atomic.AtomicBoolean

object CLIProxy {
    private val isRegistered = AtomicBoolean(false)

    init {
        // 加载 C-Shared 动态库
        System.loadLibrary("cliproxy")
        // 主动初始化并缓存全局 JavaVM 指针，与 JNI_OnLoad 形成双保险
        nativeInit()
    }

    private external fun nativeInit()
    private external fun nativeStartServer(configDir: String, host: String?, port: Int): Int
    private external fun nativeStopServer()
    private external fun nativeRegisterOAuthListener(listener: OAuthListener)
    private external fun nativePollOAuthURL(timeoutMs: Int): String?

    /**
     * 启动本地代理服务
     * @param configDir 配置文件与凭据持久化路径（通常为 context.filesDir.absolutePath）
     * @param host 监听地址，默认 "127.0.0.1"（本地安全保护）；若传 "0.0.0.0" 允许跨设备共享代理
     * @param port 监听端口号，默认 8317
     * @return 1 成功启动，0 已经在运行中，-1 启动失败
     */
    fun startServer(configDir: String, host: String? = "127.0.0.1", port: Int = 8317): Int {
        return nativeStartServer(configDir, host, port)
    }

    /**
     * 停止代理服务并优雅回收 goroutine 资源
     */
    fun stopServer() {
        nativeStopServer()
    }

    /**
     * 注册 OAuth 授权链接回调（Push 模式）
     * 内部使用 AtomicBoolean 保证单次注册，彻底杜绝多线程竞争与野指针风险
     */
    fun registerOAuthListener(listener: OAuthListener) {
        if (isRegistered.compareAndSet(false, true)) {
            nativeRegisterOAuthListener(listener)
        }
    }

    /**
     * 主动拉取待授权的 OAuth URL（Pull 模式，推荐在 IO 协程中轮询）
     * 底层在 C 桥接层自动完成内存释放，返回的 String? 完全由 JVM GC 托管，零内存泄露
     */
    fun pollOAuthURL(timeoutMs: Int = 1000): String? {
        return nativePollOAuthURL(timeoutMs)
    }
}

fun interface OAuthListener {
    fun onOAuthURL(url: String)
}
```

* **编译命令**：
```bash
CGO_ENABLED=1 \
GOOS=android \
GOARCH=arm64 \
CC="$TOOLCHAIN/bin/aarch64-linux-android26-clang" \
CXX="$TOOLCHAIN/bin/aarch64-linux-android26-clang++" \
go build -buildmode=c-shared \
    -tags="android" \
    -trimpath \
    -ldflags="-s -w -extldflags '-Wl,--gc-sections -Wl,-Bsymbolic'" \
    -o dist/libcliproxy.so \
    ./cmd/mobile
```

---

### 3.4 编译参数深度优化与链接瘦身/符号隔离规范

构建流水线通过以下关键参数实现稳健编译与体积控制：

| 构建参数 | 作用与深度原理解释 |
| :--- | :--- |
| `GOOS=android` | **核心触发开关**。Go 编译器在 `GOOS=android` 时会自动激活所有 `//go:build android` 标签，并自动剔除所有带有 `//go:build !android` 的原版文件。命令行附加 `-tags="android"` 仅作为显式冗余安全保障。 |
| `-trimpath` | 移除编译产物中包含的宿主机文件绝对路径，保证二进制跨机器可复现（Reproducible Builds），同时微量减少符号表。 |
| `-ldflags="-s -w"` | `-s` 剥离 DWARF 调试符号表，`-w` 剥离调试行号信息。这是将 Go 二进制体积直接削减 50% 以上的关键参数。 |
| `-extldflags '-Wl,--gc-sections'` | 传给底层 NDK Clang 链接器的指令，指示链接器剔除所有未被实际引用的 C 代码节区与死代码，进一步消除 CGO 依赖包袱。 |
| `-extldflags '-Wl,-Bsymbolic'` | **多库链接符号隔离（可选优化项）**：Go 的 `c-shared` 模式会导出大量 Go runtime 与 `_cgo_*` 符号。若宿主 App 同时加载了其他 Go 共享库（如通过 Gomobile 打包的 AAR），易引发符号冲突。`-Bsymbolic` 指示动态链接器将全局符号优先绑定在库内部。注：历史上个别 Go 版本对弱符号处理存在 corner case，建议默认构建先使用标准参数，仅在宿主发生多 Go 库符号冲突时开启，并通过 `nm -D dist/libcliproxy.so` 验证无异常。 |

> **Android 信号调度避坑（Signal Preemption）**：
> Go 1.14+ 引入了基于信号（`SIGURG`）的 goroutine 异步抢占机制。在某些老旧 Android 版本的 ART 虚拟机环境下，若 JNI 反向调用出现信号未捕获异常，可在宿主环境加载 `.so` 前通过环境变量配置 `GODEBUG=asyncpreemptoff=1` 进行安全回退。

---

### 3.5 一键交叉编译流水线脚本（scripts/build_android.sh）

在项目根目录下创建 `scripts/build_android.sh`，实现一键检测环境并完成真机与模拟器双目标的自动化构建：

```bash
#!/usr/bin/env bash
set -euo pipefail

# 1. 检查 Android NDK 环境变量
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ERROR: ANDROID_NDK_HOME is not set!"
    echo "Please export ANDROID_NDK_HOME=/path/to/android-ndk"
    exit 1
fi

# 2. 自动检测宿主机操作系统
HOST_OS="linux-x86_64"
if [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_OS="darwin-x86_64"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    HOST_OS="windows-x86_64"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_OS"
API_LEVEL="26"

OUTPUT_DIR="./dist/android"
mkdir -p "$OUTPUT_DIR"

compile_target() {
    local ARCH="$1"        # arm64-v8a 或 x86_64
    local GOARCH="$2"      # arm64 或 amd64
    local TARGET_CC="$3"   # aarch64-linux-android 或 x86_64-linux-android

    echo "=========================================================="
    echo "  Compiling for Android $ARCH (GOARCH=$GOARCH, API=$API_LEVEL)..."
    echo "=========================================================="

    local CC="$TOOLCHAIN/bin/${TARGET_CC}${API_LEVEL}-clang"
    local CXX="$TOOLCHAIN/bin/${TARGET_CC}${API_LEVEL}-clang++"

    local TARGET_DIR="$OUTPUT_DIR/$ARCH"
    mkdir -p "$TARGET_DIR"

    # A. 编译独立 CLI 可执行二进制文件 (适合 Termux / ADB)
    echo "--> Building Standalone Executable (cli-proxy-api)..."
    CGO_ENABLED=1 \
    GOOS=android \
    GOARCH="$GOARCH" \
    CC="$CC" \
    CXX="$CXX" \
    go build \
        -tags="android" \
        -trimpath \
        -ldflags="-s -w -extldflags '-Wl,--gc-sections'" \
        -o "$TARGET_DIR/cli-proxy-api" \
        ./cmd/server

    # B. 编译 C-Shared 动态库 (适合内嵌 Android App)
    if [ -d "./cmd/mobile" ]; then
        echo "--> Building C-Shared Library (libcliproxy.so)..."
        CGO_ENABLED=1 \
        GOOS=android \
        GOARCH="$GOARCH" \
        CC="$CC" \
        CXX="$CXX" \
        go build -buildmode=c-shared \
            -tags="android" \
            -trimpath \
            -ldflags="-s -w -extldflags '-Wl,--gc-sections -Wl,-Bsymbolic'" \
            -o "$TARGET_DIR/libcliproxy.so" \
            ./cmd/mobile
        rm -f "$TARGET_DIR/libcliproxy.h"
    fi

    echo "Finished $ARCH successfully!"
    ls -lh "$TARGET_DIR"
}

# 默认构建真机主流架构 (arm64-v8a)
compile_target "arm64-v8a" "arm64" "aarch64-linux-android"

# 可选构建 PC 模拟器架构 (x86_64)
if [ "${BUILD_EMULATOR:-0}" == "1" ]; then
    compile_target "x86_64" "amd64" "x86_64-linux-android"
fi

echo "=========================================================="
echo "  All Android artifacts built successfully in $OUTPUT_DIR"
echo "=========================================================="
```

---

### 3.6 部署与实机运行验证清单（双轨验收流程）

针对不同的使用场景，采取清晰解耦的双轨验证路径：

#### 场景 1：Termux / Root CLI 独立运行形态验收
在 Termux 环境（其内部专用目录支持可执行权限）中运行验证：

1. **拉起与连通检查**：
   ```bash
   chmod +x ./cli-proxy-api
   ./cli-proxy-api --port 8317 &
   curl -s http://127.0.0.1:8317/healthz
   # 预期输出: {"status":"ok"}
   ```
2. **模型自更新验证**：
   ```bash
   curl -s http://127.0.0.1:8317/v1/models | grep -o '"id":"[^"]*"' | head -n 5
   # 验证输出包含官方最新模型列表
   ```
3. **WebUI 自更新验证**：
   在手机浏览器访问 `http://127.0.0.1:8317/management.html`，确认自动从 GitHub Release 下载单页前端并正常交互。
4. **流式转译验证**：
   发送 `curl` 包含 `stream: true` 的推理请求，验证 SSE 逐字平稳打字机输出。
5. **体积与内存基准（阶段性基线）**：
   - 产物体积：初始标定预估 **20MB ~ 30MB**（原版 ~95MB，将在 P0 首次构建后实测回填确切字节数作为回归基线）；
   - 常驻空闲内存（RSS）：初始标定预估 **35MB ~ 50MB**（原版 ~150MB）；
   - 活跃流式高负载：**80MB ~ 120MB**。

#### 场景 2：标准 Android App 内嵌 `.so` 形态验收
在标准 APK 工程中集成 `libcliproxy.so`：
1. **JNI 加载与启动**：在 Android Service 启动阶段执行 `System.loadLibrary("cliproxy")`，调用 `StartServer(context.filesDir.absolutePath, null, 8317)`，返回值必须为 `1`。
2. **OAuth 授权跳转测试**：
   - 方式 A：调用 `registerOAuthListener`，当发起 OAuth 认证时，C-JNI 线程附着桥接成功拦截并唤起 Chrome Custom Tabs；
   - 方式 B：通过 Kotlin 协程在 IO 线程轮询 `PollOAuthURL(1000)`，收到 URL 后切至主线程打开 Custom Tabs。
3. **HTTP 客户端联通**：App 内部通过 OkHttp 请求 `http://127.0.0.1:8317/healthz` 响应正常。
4. **生命周期受控停止**：当 Service 销毁时调用 `StopServer()`，验证后台 goroutine 顺利回收，无内存泄露。

---

### 3.7 移动端运行时环境生存与宿主适配指南

当代理服务运行在真实的 Android 手机上时，面临移动操作系统严苛的资源和功耗管控策略。以下是保证服务长期稳定驻留的核心适配规约：

#### 3.7.1 Android 14+ 前台服务类型与完整权限清单声明

Android 14（API 34）对前台服务实施了极其严格的类型强制约束。**若 `targetSdkVersion >= 34`，但在调用 `startForeground()` 时未在 `AndroidManifest.xml` 中声明对应的 `foregroundServiceType`，系统会直接抛出 `MissingForegroundServiceTypeException` 并闪退**。

在宿主 App 的 `app/src/main/AndroidManifest.xml` 中，必须声明以下完整的权限与组件配置：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 基础网络通信权限 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- 防休眠唤醒锁与电池白名单 -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- 基础前台服务权限 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <!-- Android 13+ (API 33) 通知权限 (必填，否则通知不显示导致前台服务被系统判定为空壳杀掉) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Android 14+ (API 34) 细分前台服务类型权限 (必填，否则抛 MissingForegroundServiceTypeException) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <!-- 关闭全局备份，防止 adb backup 导出 auths/ 目录下的 OAuth 敏感凭据 -->
    <application
        android:allowBackup="false"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@style/Theme.CLIProxy">

        <!-- 代理承载前台服务：必须显式绑定 foregroundServiceType="dataSync" -->
        <service
            android:name=".service.CLIProxyForegroundService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

    </application>
</manifest>
```

#### 3.7.2 前台服务拉起与常驻通知实现（Kotlin 示例）

在宿主 Service 启动时，必须使用带有类型参数的 `ServiceCompat.startForeground`。在 Android 13+ 设备上，App 还应在首次拉起时通过 `ActivityResultContracts.RequestPermission()` 主动向用户申请 `POST_NOTIFICATIONS` 权限：

```kotlin
package com.cliproxy.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cliproxy.R

class CLIProxyForegroundService : Service() {
    companion object {
        const val NOTIFICATION_ID = 8317
        const val CHANNEL_ID = "cliproxy_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CLIProxy AI 代理服务")
            .setContentText("本地代理服务正在运行中 (127.0.0.1:8317)")
            .setSmallIcon(R.drawable.ic_proxy_running)
            .setOngoing(true)
            .build()

        // Android 14+ 必须显式传入 FOREGROUND_SERVICE_TYPE_DATA_SYNC，否则抛出 MissingForegroundServiceTypeException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

#### 3.7.3 电源管理与防 Doze 休眠断流（PARTIAL_WAKE_LOCK）
当手机灭屏一段时间后，Android 系统会进入深度睡眠（Doze Mode），切断网络活动并冻结 CPU。这会导致长文本 SSE 流式推理中断或 OAuth 15 分钟定时刷新失效。
- **规约**：
  1. 在 Android 端申请 `android.permission.WAKE_LOCK` 权限；
  2. 在有活跃请求正在传输时，通过 `PowerManager` 获取 `PARTIAL_WAKE_LOCK`，请求结束后释放；
  3. 引导用户将本 App 加入系统的**“电池优化白名单”（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）**。
- **Termux 用户**：只需在命令行中输入一条命令：`termux-wake-lock`，即可由 Termux 代为持有唤醒锁。

#### 3.7.4 本地回环明文通信放行（Network Security Config）
Android 9.0（API 28）起默认全系统禁止明文 HTTP 通信。由于本地代理提供的是 `http://127.0.0.1:8317`（非 HTTPS），宿主 App 或第三方客户端直接访问会抛出 `CLEARTEXT_COMMUNICATION_NOT_PERMITTED` 异常。
- **规约**：在宿主 App 的 `res/xml/network_security_config.xml` 中显式为本地回环放行明文：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <network-security-config>
      <domain-config cleartextTrafficPermitted="true">
          <domain includeSubdomains="true">127.0.0.1</domain>
          <domain includeSubdomains="true">localhost</domain>
      </domain-config>
  </network-security-config>
  ```
  并在 `AndroidManifest.xml` 的 `<application>` 标签下引用 `android:networkSecurityConfig="@xml/network_security_config"`。

---
*(全篇规划完结。本指南通过严格的原位打标、自包含桩代码、NDK 交叉编译规约、自动化 CI 守卫与移动端生存防护，完整实现了“手机用不到的彻底移除，核心转译与自更新 100% 保全，上游代码 0 业务侵入，移动端端到端闭环稳定运行”的核心工程目标。)*

