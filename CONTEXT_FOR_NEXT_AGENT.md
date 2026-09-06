# CONTEXT_FOR_NEXT_AGENT.md

## 项目当前状态

Picture Trans — 局域网文件传输 Android 应用（Kotlin/Compose，单 Activity）。
手机内置 Ktor HTTP 服务器，PC 浏览器扫码直连，双向传文件。**首个可用版本已完成并构建**（APK 在 `dist/`）。

## 最后一次完成的工作

- **全深色主题改造（2026-09-06）**：三处同改——`ui/Theme.kt` 删 `isSystemInDarkTheme()` 分支恒取 dark scheme（API31+ `dynamicDarkColorScheme` / 回落 `darkColorScheme`）；`res/values/themes.xml` 父主题由 `Theme.Material.Light.NoActionBar` 改 `Theme.Material.NoActionBar`（否则启动白闪）；`server/WebPage.kt` PC 端 SPA CSS 转深色（`:root` 变量 + `color-scheme: dark`，强调色拆出 `--accent-text:#7ab0ff` 供文字用，深色底上 #1b6ef3 对比度不足）。二维码**保持白底黑码**（功能例外，反色码旧扫描器兼容性差）。本地 `testDebugUnitTest`+`assembleDebug`+`assembleRelease` 全绿（30 测试 0 失败），新 APK 已回传 `dist/`。
- 全部功能实现：媒体列表（图片/视频/Download 三集合）、缩略图、流式下载 + Range、multipart 流式上传、进度追踪、二维码、token 鉴权
- 四项自查修复（2026-09-05）：API<29 Download 查询缺 DATA LIKE 过滤；API<29 缺 WRITE_EXTERNAL_STORAGE；ServerRunner stop/start 竞态（generation 计数器）；edge-to-edge 系统栏 insets
- 测试 30 个全绿（含真 socket E2E：鉴权 404/列表/逐字节下载/Range 206/上传/文件名清洗）；lint 0 警告
- 远程构建迁移（用户指定）：ssh 别名 `build-server`（Ubuntu 22.04，4C/8G），JDK17 + /opt/android-sdk 已装，源码在 `/root/build/picture-trans`，APK 回传 `dist/`。⚠️ 主机地址与端口**不写入仓库**，见本机私有 memory

## 遗留问题 / 待办

- [x] **F-Droid repo 侧准备完成**（2026-09-06）：`docs/fdroid/{com.xieguiawu.picturetrans.yml, fdroiddata-mr-0001.patch, SUBMIT_GUIDE.md}`
      + `scripts/{validate-fdroid-metadata.sh, verify-reproducible.sh, render-icon.py}`
      + `fastlane/metadata/android/{en-US,zh-CN}/` + CI。**尚未提交 fdroiddata**（需用户 GitLab 账号）。
- [ ] **提交前必须换真机截图**：`StoreScreenshotsTest` 在 Robolectric 下跑，
      Robolectric 的 native 网络层把 `lo`/127.0.0.1 报成非 loopback 的 site-local 地址，
      所以截图 2 的网址是 `http://127.0.0.1:8765/t/.../`。对「局域网互传」app 是误导性的
      （暗示只能本机）。Robolectric 4.14.1 **无** ShadowNetworkInterface，改不了；
      出店前用真机图替换 `fastlane/.../phoneScreenshots/`
- [ ] **图标 fillType 修复改变了 app 外观**（见下）——用户若不喜欢可回退 commit
- [ ] **构建改在本地**（2026-09-06）：build-server 密码未持久化（ssh 免密不可用、rbw 无条目），本会话退回本地 `./gradlew`（有 `~/Android/Sdk` + JDK 21，2m02s 全量）。要回云构建需用户重新提供密码并建议落 rbw / 配 deploy key。
- [ ] 真机验证：华为 Mate 40 Pro（NOH-AN00）装 `dist/app-release.apk` 实测互传 + **确认深色观感**（本机无设备连接，且无 adb）
- [ ] momus 审查两次超时（glm-5.3-flash thinking xhigh），已降级为自查 + 测试兜底；后续可换模型重审
- [ ] keystore.properties 缺失 → release 是 debug 签名回退；正式发版前配签名
- [x] git 仓库已初始化并推送 origin（`github.com/xieguaiwu/picture-trans`，2026-09-06 确认 master 与 origin 同步）
- [ ] 锁屏即停服务（前台服务未做）——路线图项
- [ ] WebPage SPA 的「下载所选」用连续 a.click，Chrome 会弹多文件下载许可，可接受

## 远程资源

- `build-server`: ssh 别名（主机/端口/密码一律不入库，见本机私有 memory 或询问用户；此前用 sshpass，构建式用法已验证）
  - 构建：`cd /root/build/picture-trans && ./gradlew assembleDebug assembleRelease --no-daemon`
  - 取回：`rsync -az -e ssh build-server:/root/build/picture-trans/app/build/outputs/apk/{debug,release}/ dist/`

## 技术要点

- Ktor 2.3.12 CIO + 手写单段 Range（未用 PartialContent 插件，为字节计数）；multipart `receiveMultipart()/forEachPart/streamProvider`（2.3 无 formFieldLimit 参数）；路由内 `call` 来自 `io.ktor.server.application.*`；`connector {}` 来自 `io.ktor.server.engine.*`
- `asImageBitmap` 在 `androidx.compose.ui.graphics`（不在 core-ktx）
- MediaStore：API 29+ IS_PENDING 流程；API 26-28 Files+DATA LIKE 过滤；上传按 MIME 分流（image→Images, video→Video, 其余→Downloads）
- 图像/名词纠错经验：本项目多次出现「近形拼写错位」（如 picture-trans/picturetrans 系），凡涉及标识符一律用 python 字节比对校验（package↔目录、import↔定义、字段名↔引用）

## 知识图谱

- graphify-out/: 不存在（未建；新增代码后如需可 `graphify update . --no-llm`）

## 2026-09-06 F-Droid 准备轮次修的两个真 bug

1. **启动图标渲染成实心白块**。`ic_launcher_foreground.xml` 第一条 path 有两条同向缠绕
   的子路径（圆角矩形 + 山形带），Android VectorDrawable 默认 `fillType="nonZero"`
   ⇒ 山形被矩形吞掉，图标变成白块（文件注释写的是「简单相片+箭头」，意图是经典 image 字形）。
   修复：加 `android:fillType="evenOdd"`。商店图标由 `scripts/render-icon.py`
   从**当前矢量**渲染，不再可能与 app 脱节。
2. **深色主题下标题黑底黑字**。`PictureTransTheme` 只设了 `MaterialTheme(colorScheme=dark)`
   却没包 `Surface` ⇒ 根层 `LocalContentColor` 仍是 Compose 默认 `Color.Black`，
   任何不在 Card/Button 里的裸 `Text` 都是深底黑字（实测首页标题「Picture Trans」看不见）。
   修复：Theme 里包 `Surface(color = colorScheme.background)`。
   ⚠️ 上一轮记录里「MainScreen 全程用 colorScheme 令牌故零改动」这句是错的——
   标题与底部提示都不在 Card 内。

## 签名

- release keystore：`~/Desktop/android-projects/picture-trans-keystore/picture-trans-release.keystore`
  （PKCS12，alias `picturetrans`，RSA 2048，有效期 10950 天），口令在仓库根 `keystore.properties`（已 gitignore）
- 证书 DER 的 SHA-256 = `c5ec83d6bf844902d137af09e6b2fb0c7a247eff5c5287954238c28df8c3aa4b`
  → 将来若走 Verified 徽章，这就是 `AllowedAPKSigningKeys` 的值
- ⚠️ **keystore 与口令同机存放，未做异地备份**；丢了就无法更新签名。用户需自行备份
- 签名决策（F-Droid 官方签名 vs 自有签名）**必须在首次发布前定，之后不可更换**

## 最后更新时间

2026-09-06 13:10