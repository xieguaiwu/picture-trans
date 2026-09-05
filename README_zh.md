# Picture Trans 快传

在手机与电脑之间通过局域网快速互传文件——不需要数据线，不需要云盘，电脑端零安装。

## 功能

- 手机内置 HTTP 服务器（Ktor/CIO），显示网址 + 二维码
- 电脑浏览器扫码或直接输入网址即可访问
- **手机 → 电脑**：浏览并下载手机相册、视频、Download 目录文件
- **电脑 → 手机**：拖拽上传——图片/视频自动进相册，其他文件进下载目录
- 视频在线预览（支持拖进度条，HTTP Range）、缩略图网格、多选下载
- 手机端实时显示传输进度与历史记录
- URL 路径带访问令牌（`/t/<token>/`），隔离局域网内其他设备

## 环境要求

- Android 8.0+（API 26+）
- 手机与电脑连接同一 WiFi / 热点
- 电脑端任意现代浏览器（Chrome/Edge/Firefox/Safari）

电脑端无需安装任何软件——只用一个浏览器。

## 构建

```bash
# 前置：JDK 17+，Android SDK（platform 35）
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK（有 keystore.properties 则正式签名，否则回退 debug 签名）
```

预构建 APK：
- `dist/app-debug.apk` — debug 包
- `dist/app-release.apk` — release 包（debug 签名回退，可直接侧载）

## 安装使用

1. 把 APK 传到手机（USB / 局域网 / 直接下载）
2. 允许「安装未知应用」，安装并打开
3. 授权照片/视频访问（可选——不授权也能传 Download 目录文件）
4. 点「启动」，手机与电脑连同一 WiFi
5. 电脑浏览器扫手机上的二维码，或手动输入显示的网址

## 用法速查

| 操作 | 方式 |
|---|---|
| 手机 → 电脑（图片/视频） | 打开网址 → 图片/视频 标签 → 点 ⬇ 或勾选后「下载所选」 |
| 手机 → 电脑（文件） | 「文件」标签显示 Download 目录内容 |
| 电脑 → 手机 | 「上传」标签 → 拖文件进来或点击选择 |
| 查看传输进度 | 手机 app 主界面（实时 + 历史） |
| 改端口 | 停止服务 → 修改端口 → 重新启动 |

## 隐私与安全

- 仅局域网：服务器监听 `0.0.0.0`，只在本地网络内可达
- 每次安装生成随机 16 字符令牌：网址形如 `http://<ip>:<port>/t/<token>/`，无令牌设备一律 404
- 局域网内明文 HTTP 是刻意取舍（免证书负担）；不要把端口暴露到公网
- 文件全部本地存储，不上传任何云端

## 项目结构

```
app/src/main/java/com/xieguiawu/picturetrans/
├── server/       Ktor HTTP 服务器、令牌校验、内嵌网页、进程级运行器
├── media/        MediaStore 仓库（API 26/29/33 分支）、模型、权限
├── transfer/     实时进度与历史追踪（线程安全 StateFlow）
├── ui/           Compose 界面：服务器卡片、二维码、进度、历史
├── util/         令牌存储、文件名清洗、计数字节流
├── net/          局域网 IPv4 枚举
└── qr/           ZXing 二维码生成
```

## 测试

30 个 JVM/Robolectric 测试，含真 socket 端到端套件：

```bash
./gradlew testDebugUnitTest
```

- `ServerE2eTest` — 随机端口起真实 CIO 服务器，验证鉴权 404、列表、逐字节下载、Range 206、multipart 上传、文件名清洗
- `MainActivitySmokeTest` — Robolectric 入口启动烟测
- TokenGuard / 文件名清洗 / 传输追踪 / 网络工具单测

## 局限与路线图

- 服务器在 app 打开时运行（锁屏即停——前台服务是后续工作）
- 旧设备（API 26–28）走传统文件权限路径
- 目录浏览仅限 Download（基于 MediaStore 查询）