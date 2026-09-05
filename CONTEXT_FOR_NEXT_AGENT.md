# CONTEXT_FOR_NEXT_AGENT.md

## 项目当前状态

Picture Trans — 局域网文件传输 Android 应用（Kotlin/Compose，单 Activity）。
手机内置 Ktor HTTP 服务器，PC 浏览器扫码直连，双向传文件。**首个可用版本已完成并构建**（APK 在 `dist/`）。

## 最后一次完成的工作

- 全部功能实现：媒体列表（图片/视频/Download 三集合）、缩略图、流式下载 + Range、multipart 流式上传、进度追踪、二维码、token 鉴权
- 四项自查修复（2026-09-05）：API<29 Download 查询缺 DATA LIKE 过滤；API<29 缺 WRITE_EXTERNAL_STORAGE；ServerRunner stop/start 竞态（generation 计数器）；edge-to-edge 系统栏 insets
- 测试 30 个全绿（含真 socket E2E：鉴权 404/列表/逐字节下载/Range 206/上传/文件名清洗）；lint 0 警告
- 远程构建迁移（用户指定）：`root@223.109.239.36:12756`（Ubuntu 22.04，4C/8G），JDK17 + /opt/android-sdk 已装，源码在 `/root/build/picture-trans`，APK 回传 `dist/`

## 遗留问题 / 待办

- [ ] 真机验证：华为手机装 `dist/app-release.apk` 实测互传（本机无设备连接）
- [ ] momus 审查两次超时（glm-5.3-flash thinking xhigh），已降级为自查 + 测试兜底；后续可换模型重审
- [ ] keystore.properties 缺失 → release 是 debug 签名回退；正式发版前配签名
- [ ] git 仓库未初始化（本地无 git）
- [ ] 锁屏即停服务（前台服务未做）——路线图项
- [ ] WebPage SPA 的「下载所选」用连续 a.click，Chrome 会弹多文件下载许可，可接受

## 远程资源

- `build-server`: root@223.109.239.36:12756（密码见私有 memory / 询问用户；sshpass 使用；构建式用法已验证）
  - 构建：`cd /root/build/picture-trans && ./gradlew assembleDebug assembleRelease --no-daemon`
  - 取回：`rsync -az -e "ssh -p 12756" root@223.109.239.36:/root/build/picture-trans/app/build/outputs/apk/{debug,release}/ dist/`

## 技术要点

- Ktor 2.3.12 CIO + 手写单段 Range（未用 PartialContent 插件，为字节计数）；multipart `receiveMultipart()/forEachPart/streamProvider`（2.3 无 formFieldLimit 参数）；路由内 `call` 来自 `io.ktor.server.application.*`；`connector {}` 来自 `io.ktor.server.engine.*`
- `asImageBitmap` 在 `androidx.compose.ui.graphics`（不在 core-ktx）
- MediaStore：API 29+ IS_PENDING 流程；API 26-28 Files+DATA LIKE 过滤；上传按 MIME 分流（image→Images, video→Video, 其余→Downloads）
- 图像/名词纠错经验：本项目多次出现「近形拼写错位」（如 picture-trans/picturetrans 系），凡涉及标识符一律用 python 字节比对校验（package↔目录、import↔定义、字段名↔引用）

## 知识图谱

- graphify-out/: 不存在（未建；新增代码后如需可 `graphify update . --no-llm`）

## 最后更新时间

2026-09-05 21:30