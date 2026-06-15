# JoyProxy

Android 代理 IP 工具，基于 sing-box `libbox` + `VpnService` 实现。

## 功能

- 支持 HTTP / SOCKS5 代理（IP 或域名 + 端口）
- 三种代理范围：全局 / 白名单（仅选中 App）/ 黑名单（排除选中 App）
- DNS 防污染：Fake-IP（推荐）、DoH 安全 DNS、自定义 DNS、系统默认
- 无需 Root

## 编译

本项目依赖 `libbox.aar`（sing-box 核心），不在仓库中直接提交。GitHub Actions 会在编译时自动从 sing-box v1.11.8 构建。

### 本地编译

1. 安装 JDK 17、Android SDK、Go 1.23、NDK 28
2. 构建 libbox：

```bash
git clone --depth 1 --branch v1.11.8 https://github.com/SagerNet/sing-box.git
cd sing-box
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.7
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.7
export PATH="$(go env GOPATH)/bin:$PATH"
gomobile init
go run ./cmd/internal/build_libbox/main.go -target android
cp libbox.aar ../joy-proxy-android/app/libs/
```

3. 编译 APK：

```bash
./gradlew assembleRelease
```

APK 输出：`app/build/outputs/apk/release/app-release.apk`

## 许可证

本项目使用 sing-box libbox（GPLv3），整体遵循 GPLv3 许可证。
