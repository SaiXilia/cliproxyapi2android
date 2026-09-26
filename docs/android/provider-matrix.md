# CLIProxyAPI Android 服务商与协议兼容性矩阵 (Provider Matrix)

本文档严格按照《CLIProxyAPI Android 迁移实施计划》第 7.2 节规范制定：
> **核心纪律**：每个服务商的登录、刷新和推理是否通过，最终以实际环境测试标识；没有账号完成真实端到端测试的项目明确标记为“待真机验证”，严禁虚标通过。

---

## 1. 核心大模型服务商支持矩阵

| 服务商 | 认证方式 | 协议转译支持 | 流式 SSE | 深度思考 (Thinking) | 移动端适配通道 | 当前验证状态 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Anthropic Claude** | OAuth PKCE / API Key | OpenAI ⇄ Claude | 支持 (SSE) | 支持 (`budget_tokens`, `extended_thinking`) | Custom Tabs / CLI 链接 | **代码对齐，待注入凭据真机验证** |
| **OpenAI / Codex** | OAuth 设备码 / API Key | 原生 / Claude ⇄ OpenAI | 支持 (SSE / WS) | 支持 (`reasoning_effort`) | 控制台设备码 / Custom Tabs | **代码对齐，待注入凭据真机验证** |
| **Google Gemini** | OAuth / API Key | OpenAI ⇄ Gemini | 支持 (SSE) | 支持 (`thinking_config`) | Custom Tabs / API Key | **代码对齐，待注入凭据真机验证** |
| **xAI Grok** | API Key / OAuth | OpenAI 兼容 | 支持 (SSE) | 支持 | Custom Tabs / API Key | **代码对齐，待注入凭据真机验证** |
| **Moonshot Kimi** | API Key / OAuth | OpenAI 兼容 | 支持 (SSE) | 支持 | Custom Tabs / API Key | **代码对齐，待注入凭据真机验证** |
| **Antigravity** | OAuth / API Key | OpenAI 兼容 | 支持 (SSE) | 支持 (`reasoning_replay`) | Custom Tabs / API Key | **代码对齐，待注入凭据真机验证** |
| **Cognition Devin** | OAuth / API Key | OpenAI 兼容 | 支持 (SSE) | 支持 | Custom Tabs / API Key | **代码对齐，待注入凭据真机验证** |

---

## 2. 核心架构能力保留与运行表现

| 能力模块 | 移动端处理策略 | 达成效果 |
| :--- | :--- | :--- |
| **凭据多账号轮询** | 100% 完整保留（`sdk/cliproxy/auth/`） | 本地多个账号配置自动轮询负载均衡 |
| **429 智能冷却** | 100% 完整保留（`conductor_cooldown.go`） | 遇到速率限制自动打标冷却并无缝切换备用账号 |
| **Token 自动续期** | 100% 完整保留（`auto_refresh_loop.go`） | 运行时自动刷新即将过期的 Access Token |
| **模型目录热更新** | 100% 完整保留（`internal/registry/`） | 后台自动从官方 CDN / GitHub 同步最新模型列表 |
| **管理控制台热更新** | 100% 完整保留（`managementasset/`） | 联网时自动拉取最新 `management.html`，离线时优雅降级至内置单页 |
| **TLS 指纹混淆** | 100% 完整保留（`utls`） | 模拟浏览器 ClientHello，规避云厂商风控拦截 |

---

## 3. 裁剪模块明确不支持清单 (Disabled on Android)

| 裁剪模块 | 包含三方依赖 | 移动端状态 | 请求被触发时的响应 |
| :--- | :--- | :--- | :--- |
| **企业级远程数据库** | `jackc/pgx/v5` | 强制禁用 | 仅支持本地文件凭据仓储（`auths/*.json`） |
| **Git 远程同步仓** | `go-git/go-git/v6` | 强制禁用 | 启动时配置远程存储返回明确报错，强制走本地存储 |
| **S3 对象存储** | `minio/minio-go/v7` | 强制禁用 | 启动时配置远程存储返回明确报错，强制走本地存储 |
| **Redis 分布式通信** | `redis/go-redis/v9` | 强制禁用 | `home.Current()` 恒为 `nil`，无感降级至纯单机内存缓存 |
| **WebRTC 实时媒体** | `pion/webrtc/v4` 及子包 | 强制禁用 | 实时语音对讲 SDP 协商返回明确的不支持错误，普通图文与代码不受影响 |
| **局域网 mDNS 组播** | `libp2p/zeroconf/v2` | 强制禁用 | 静默空操作，彻底消除后台 Wi-Fi 组播唤醒耗电 |
| **桌面终端 TUI** | `charmbracelet/bubbletea` | 强制禁用 | 传入 `--tui` 返回终端不支持提示，引导使用 Web 控制台或原生 App |
| **桌面浏览器调用** | `skratchdot/open-golang` | 强制替换 | 拦截 URL 转发至 Chrome Custom Tabs 或控制台展示，避免执行桌面命令报错 |
