# F-Droid 收录提交指引（Picture Trans）

本目录包含提交流程所需的一切。你只需要一个 GitLab 账号，约 2 分钟完成。

> ⚠️ **提交前先读「前置条件」**——本项目目前还差 tag 与真机验证。

## 前置条件（当前状态）

| 条件 | 状态 |
|---|---|
| LICENSE（MIT） | ✅ 仓库根 |
| 依赖全 FOSS（Ktor / Compose / zxing，仅 google() + mavenCentral()） | ✅ |
| 无专有二进制入库 | ✅ 纯 Kotlin，无 NDK、无 jar/aar |
| Gradle wrapper 已提交 | ✅ `gradlew` + `gradle/wrapper/` |
| git tag `v1.0.0` | ❌ **待打**（`scripts/validate-fdroid-metadata.sh` 会拦住） |
| fastlane 元数据（en-US + zh-CN） | ✅ 文案 + icon + 2 张截图 |
| 可复现构建验证 | ⬜ 待跑 `scripts/verify-reproducible.sh` |
| 真机冒烟（华为 NOH-AN00 互传） | ❌ 未做 |
| GitLab 账号 | ❌ 用户需注册 |

## 已就绪的文件

| 文件 | 用途 |
|---|---|
| `com.xieguiawu.picturetrans.yml` | fdroiddata metadata（类别 `File Transfer` 已对照官方 categories.yml 验证）|
| `fdroiddata-mr-0001.patch` | 完整 commit 补丁（可直接 `git am`，已在干净树验证）|
| `../fastlane/metadata/` | 双语商店文案与图素 |

## 提交方法（二选一）

### 方法 A：Web 界面（最简单，无需本地 GitLab 配置）

1. 打开 https://gitlab.com/fdroid/fdroiddata
2. 点右上角 **Fork**
3. 在你的 fork 里用 **Web IDE** 新建路径 `metadata/com.xieguiawu.picturetrans.yml`
4. 粘贴下方「metadata 内容」段全文
5. 提交到新分支 `add-picture-trans` → **Create merge request**（目标 `fdroid/fdroiddata` master）
6. MR 标题：`Add Picture Trans (com.xieguiawu.picturetrans)`
7. MR 描述：粘贴下方「MR 描述」段

### 方法 B：本地 git

```bash
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
git checkout -b add-picture-trans
git am /path/to/docs/fdroid/fdroiddata-mr-0001.patch
git remote add mine <你的-fork-地址>
git push mine add-picture-trans
# 在 GitLab 网页创建 MR: 你的 fork:add-picture-trans → fdroid/fdroiddata:master
```

## metadata 内容

> 与 `com.xieguiawu.picturetrans.yml` 逐字一致（改一处必改两处）。
> 校验：`bash scripts/validate-fdroid-metadata.sh docs/fdroid/com.xieguiawu.picturetrans.yml`

```yaml
# F-Droid 收录 metadata
# 提交位置：gitlab.com/fdroid/fdroiddata → metadata/com.xieguiawu.picturetrans.yml
# 流程：fork fdroiddata → 新建该文件 → MR（GitLab CI 自动 lint + build 验证）
# 校验：bash scripts/validate-fdroid-metadata.sh docs/fdroid/com.xieguiawu.picturetrans.yml
# 说明：无 AntiFeatures —— 纯局域网工具，不依赖任何专有网络服务，无追踪。
#       注意明文 HTTP：这是局域网内有意取舍，已在 full_description 声明并警告
#       不得暴露公网；F-Droid 不因明文 HTTP 拒绝收录（仅影响传输机密性）。
# 注意：Builds 只列已打 tag 的版本。

Categories:
  - File Transfer
License: MIT
AuthorName: xieguaiwu
AuthorEmail: xieguaiwu@users.noreply.github.com
SourceCode: https://github.com/xieguaiwu/picture-trans
IssueTracker: https://github.com/xieguaiwu/picture-trans/issues
Changelog: https://github.com/xieguaiwu/picture-trans/releases

AutoName: Picture Trans

RepoType: git
Repo: https://github.com/xieguaiwu/picture-trans

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

## MR 描述

```markdown
## Summary
Add Picture Trans (com.xieguiawu.picturetrans) — LAN file transfer between an
Android phone and a computer browser. The phone runs a built-in Ktor HTTP
server and shows a URL + QR code; no USB, no cloud, no PC-side install.

## Details
- MIT licensed, pure FOSS dependencies (Ktor, Compose, zxing) from
  google() + mavenCentral() only
- No AntiFeatures: no proprietary network service, no tracking, no ads
- Permissions scoped to the job: INTERNET + network state + media access via
  MediaStore (scoped storage on API 29+); allowBackup=false
- Traffic is plain HTTP by design (LAN only, no cert burden); the description
  warns against exposing the port. Random per-install URL token gates access.
- Reproducible build verified (dual-build SHA-256 match at tag v1.0.0)
- Fastlane metadata (en-US / zh-CN); screenshots rendered from the real UI
- Category File Transfer (validated against config/categories.yml)

## Build
`gradle: yes`, `subdir: app`, commit v1.0.0 (clean tree, wrapper committed)
```

## 评审关注点（reviewer 可能问）

- **明文 HTTP**：已在 `full_description` 主动声明是局域网内的有意取舍并警告勿暴露公网；
  F-Droid 不因明文 HTTP 拒绝收录（影响传输机密性，非合规项）
- **无 AntiFeatures**：与另外三个 app 不同，本应用不依赖任何专有网络服务，
  所以**不声明** NonFreeNet 是正确的，别照抄别的 app 的 yml
- **截图来源**：由 Robolectric + layoutlib 渲染真实 UI 代码生成
  （`app/src/test/java/.../StoreScreenshotsTest.kt`，
  重跑：`./gradlew :app:testDebugUnitTest --tests "*StoreScreenshotsTest" -PstoreScreenshots`），
  非伪造数据；如 reviewer 要求真机图，替换 `fastlane/.../phoneScreenshots/` 即可
- **许可证**：MIT（LICENSE 在仓库根）
- **签名**：当前走 F-Droid 官方签名。若要 Verified 徽章，用
  `AllowedAPKSigningKeys: c5ec83d6bf844902d137af09e6b2fb0c7a247eff5c5287954238c28df8c3aa4b`
  （picture-trans release keystore 证书 DER 的 SHA-256）+ `Binaries:` 指向 GitHub Release。
  **签名决策必须在首次发布前定，之后不可更换**

MR 合并后 24-48 小时出现在 F-Droid 主仓库（签名步骤人工介入）。
