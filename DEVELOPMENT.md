# JoyProxy Android 开发情况

> 最后更新：2026-06-16  
> 当前版本：**1.0.11**（versionCode 12）  
> 仓库：https://github.com/joyproxy/joy-proxy-android

---

## 一、项目概述

JoyProxy 是一款面向 Android 的代理 IP 工具，用户填写代理地址（IP 或域名 + 端口），即可通过系统 VPN 将整机或指定应用的流量转发到 HTTP / SOCKS5 代理服务器，无需 Root。

核心思路：

- **流量拦截**：Android `VpnService` 建立 TUN 虚拟网卡
- **协议处理**：sing-box `libbox`（Go 核心，gomobile 编译为 AAR）负责代理转发、路由、DNS
- **界面层**：Jetpack Compose + ViewModel + DataStore 持久化

---

## 二、技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose（Material 3） |
| 最低 SDK | 24（Android 7.0） |
| 目标 SDK | 35 |
| 代理核心 | sing-box v1.13.13 / libbox.aar |
| 持久化 | DataStore Preferences |
| 序列化 | kotlinx.serialization（代理历史 JSON） |
| 构建 | Gradle 8.11.1 + JDK 17 |
| CI | GitHub Actions（自动编译 libbox + 签名 APK + Release） |

---

## 三、架构说明

```
┌─────────────────────────────────────────┐
│  UI 层                                   │
│  MainActivity / HomeScreen / AppPicker    │
│  MainViewModel                          │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│  配置层                                  │
│  ProxySettings / ConfigBuilder          │
│  SavedProxy（历史记录）                  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│  数据层                                  │
│  AppPreferencesStore（单例 DataStore）    │
│  SettingsRepository / ProxyHistoryRepo  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│  VPN 层                                  │
│  VpnController → ProxyVpnService          │
│  BoxService → libbox CommandServer        │
│  PlatformInterfaceWrapper（原生回调桥）   │
└─────────────────────────────────────────┘
```

### 关键模块

| 路径 | 职责 |
|------|------|
| `vpn/ProxyVpnService.kt` | 继承 `VpnService`，对外暴露 VPN 服务 |
| `vpn/BoxService.kt` | 启动/停止 libbox，管理前台通知、TUN 建立 |
| `vpn/PlatformInterfaceWrapper.kt` | sing-box 原生回调（接口列表、WiFi、证书等） |
| `vpn/LocalResolver.kt` | 平台 DNS 解析，供 libbox 调用 |
| `config/ConfigBuilder.kt` | 将 `ProxySettings` 转为 sing-box JSON 配置 |
| `network/ProxyTester.kt` | 直连测试代理连通性（不经 VPN） |
| `data/AppPreferencesStore.kt` | 全局唯一 DataStore，避免多实例闪退 |

### VPN 连接流程

1. 用户点击「连接代理」
2. 先请求 **VPN 授权**（系统钥匙图标弹窗）
3. 再请求 **通知权限**（Android 13+）
4. 配置通过 `Intent` 直传 `ProxyVpnService`（避免 DataStore 写入延迟）
5. 等待 `libbox` 初始化完成 → 生成 sing-box 配置 → 建立 TUN
6. `VpnStatusBus` 通知 UI 真实连接状态（连接中 / 已连接 / 失败）

---

## 四、已实现功能

### 代理配置

- [x] HTTP / SOCKS5 协议
- [x] IP 或域名 + 端口
- [x] 可选用户名 / 密码（密码可显示明文）
- [x] 粘贴 `ip:端口` 或 `[ipv6]:端口` 自动拆分到地址和端口
- [x] 代理连通性测试（仅未连接时可用，避免经代理测代理）
- [x] **代理历史记录**：VPN **连接成功**后自动保存，下拉快选，单条删除，最多 20 条

### 代理范围

- [x] **全局**：所有应用走代理（本应用自身排除，防死循环）
- [x] **白名单**：仅选中应用走代理
- [x] **黑名单**：选中应用不走代理，其余走代理
- [x] 应用选择器（`PackageManager` + `<queries>` 声明，兼容 Android 11+）

> 修改代理范围或应用列表后，需**断开并重新连接**方可生效。

### DNS

- [x] **推荐**（原 Fake-IP）：虚拟 IP + 代理端远程解析，防 DNS 污染效果最好
- [x] **加密 DNS (DoH)**：HTTPS 加密查询，返回真实 IP
- [x] **自定义 DNS**：指定 DNS 服务器（如 223.5.5.5）
- [x] **系统默认**
- [x] 默认 DoH：阿里 DNS（`https://dns.alidns.com/dns-query`）
- [x] 代理地址为域名时，自动用本地 DNS 直连解析（避免引导死循环）
- [x] DNS 设置默认隐藏，**连点左上角「JoyProxy」标题 7 次**后显示（彩蛋入口）

> 修改 DNS 设置后，需**断开并重新连接**方可生效。

### 其他

- [x] 前台 VPN 服务 + 状态栏钥匙图标
- [x] 连接状态实时反馈（连接中 / 已连接 / 失败原因）
- [x] 全局崩溃日志：`Android/data/com.joyproxy.app/files/crash.log`
- [x] GitHub Actions 自动构建签名 Release APK

---

## 五、版本迭代记录

| 版本 | 主要内容 |
|------|----------|
| 1.0.0 | 初始版本：VpnService + libbox + Compose UI + CI |
| 1.0.1 | libbox API 兼容，升级 sing-box v1.13.13 |
| 1.0.2 | 代理测试、输入框光标修复、应用选择器崩溃修复、DNS 文案优化 |
| 1.0.3 | VPN 授权流程修复、配置 Intent 直传、连接状态总线 |
| 1.0.4 | sing-box 1.12+ TUN 配置格式（`address` 数组） |
| 1.0.5 | 关闭 `auto_redirect`，修复 root 权限报错 |
| 1.0.6 | 修复后台/有流量时进程崩溃（原生回调异常兜底 + crash.log） |
| 1.0.7 | 已连接禁用代理测试；DNS 说明优化；代理域名直连解析 |
| 1.0.8 | DNS 设置隐藏（7 次点击）；粘贴 ip:端口 自动拆分 |
| 1.0.9 | 代理历史记录与下拉快选 |
| **1.0.10** | **修复 v1.0.9 启动闪退（DataStore 重复实例）** |
| 1.0.11 | 代理历史仅在 VPN 连接成功时保存 |

---

## 六、已修复的重要问题

### 1. VPN 无法建立 / 钥匙图标不出现

- **原因**：通知权限弹窗干扰 VPN 授权顺序；配置防抖保存导致服务读到空配置
- **修复**：先 VPN 授权 → 再通知权限；配置 Intent 直传；等待 libbox 就绪

### 2. sing-box 配置报错

| 报错 | 修复 |
|------|------|
| `legacy tun address fields are deprecated` | `inet4_address` → `address` 数组 |
| `auto-redirect: root permission is required` | `auto_redirect = false` |

### 3. 连接后一切后台就闪退

- **原因**：`ReadWIFIState()` 等 libbox 回调在 Go 接口中不返回 error，Kotlin 抛异常会导致整个进程 abort
- **修复**：所有原生热路径回调 `try/catch` 兜底；`LocalResolver` 永不向上抛异常

### 4. v1.0.9 打开即闪退

- **原因**：`SettingsRepository` 与 `ProxyHistoryRepository` 各自创建了同名 `proxy_settings` DataStore，触发 `IllegalStateException`
- **修复**：抽取 `AppPreferencesStore.kt` 单例，两个 Repository 共用

### 5. 输入框光标错位

- **原因**：每输入一字就写 DataStore，触发 Compose 重组
- **修复**：本地 `mutableStateOf` + ViewModel 400ms 防抖保存

---

## 七、CI / 发布

### 触发方式

- 推送到 `main` / `master` 分支
- 或手动 `workflow_dispatch`

### 构建步骤

1. 克隆 sing-box v1.13.13，gomobile 编译 `libbox.aar`
2. Gradle `assembleRelease`，使用 GitHub Secrets 中的 PKCS12 密钥签名
3. 上传 Artifact 并创建 GitHub Release

### 下载地址

https://github.com/joyproxy/joy-proxy-android/releases

### 所需 Secrets

| Secret | 用途 |
|--------|------|
| `KEYSTORE_BASE64` | PKCS12 签名证书（Base64） |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

---

## 八、本地开发

### 环境要求

- JDK 17
- Android SDK（compileSdk 35）+ NDK 28
- Go 1.23+（编译 libbox 时）

### 编译 libbox

```bash
git clone --depth 1 --branch v1.13.13 https://github.com/SagerNet/sing-box.git
cd sing-box
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
export PATH="$(go env GOPATH)/bin:$PATH"
gomobile init
go run ./cmd/internal/build_libbox/main.go -target android
cp libbox.aar ../joy-proxy-android/app/libs/
```

### 编译 APK

```bash
./gradlew assembleRelease
```

输出：`app/build/outputs/apk/release/app-release.apk`

---

## 九、使用说明（简要）

1. 安装 APK，允许「未知来源」
2. 填写代理地址和端口（可从历史记录下拉选择）
3. 可选：选择代理范围（全局 / 白名单 / 黑名单）
4. 点击「连接代理」→ 允许 VPN 授权 → 允许通知（如有）
5. 状态栏出现钥匙图标即表示 VPN 已建立

### 隐藏功能

- 连点左上角 **JoyProxy** 标题 **7 次** → 显示 DNS 高级设置

### 调试

- 崩溃日志：`/sdcard/Android/data/com.joyproxy.app/files/crash.log`
- libbox 标准错误：`/sdcard/Android/data/com.joyproxy.app/files/stderr.log`

---

## 十、已知限制与待改进

| 项目 | 说明 |
|------|------|
| 协议支持 | 仅 HTTP / SOCKS5，不支持 VMess、Shadowsocks 等 |
| 配置热更新 | 修改代理范围、DNS、应用列表需手动重连，暂不支持连接中热重载 |
| DNS 解锁状态 | 关闭 App 后需重新点 7 次才能看到 DNS 设置 |
| 历史记录上限 | 最多保存 20 条代理配置 |
| 仅 Android | 无 iOS / 桌面端 |

### 可考虑的后续方向

- [ ] 连接中修改配置后提示「一键重连」
- [ ] 代理历史支持自定义备注名称
- [ ] 订阅链接 / 批量导入代理
- [ ] 流量统计与连接日志查看
- [ ] 开机自动连接
- [ ] 分应用规则导入导出

---

## 十一、许可证

本项目使用 sing-box libbox（**GPLv3**），整体遵循 GPLv3 许可证。详见 [LICENSE](LICENSE)。
