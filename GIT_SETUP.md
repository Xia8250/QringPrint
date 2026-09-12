# 云端更新服务接入（GitHub 公开仓库 + jsDelivr 加速）

**免费、无需信用卡、国内可访问。** 整个 App 升级体系只需要一个公开 GitHub 仓库。

## 一次性配置（只需做一次）

### 1. 注册 GitHub
- 打开 https://github.com/signup
- 用邮箱注册（QQ / 163 / Gmail 都行），完全免费
- 推荐顺手装 GitHub 手机 App 用来二步验证

### 2. 创建公开仓库
- 右上角 `+` → `New repository`
  - Repository name: `huanxongkuaiyin-updates`（随便起，但记住）
  - Description: 浣熊快印 云端更新仓库
  - 选 **Public**（必须公开！私有仓库 jsDelivr 加速不了）
  - **不要**勾 Add README / .gitignore / license
- Create repository

### 3. 让 Codex 改 `UpdateConfig.kt`
把第 8 行的 `manifestUrl` 改成：
```
https://cdn.jsdelivr.net/gh/<你的GitHub用户名>/huanxongkuaiyin-updates@latest/update.json
```
- 把你自己的 GitHub 用户名替换掉
- 用 `@latest` 而不是 `@main`，这样 jsDelivr 会拉最新 release tag，缓存更可控
- 改完让 Codex 帮你重打 APK

## 每次发版

### 1. 拿到新 APK
Codex 会把新 APK 放在 `D:\QrintPrint-Android\HuanxongKuaiyin-4.0-fixed-vN.apk`

### 2. 跑脚本生成 `update.json`
打开 PowerShell：
```powershell
cd D:\QrintPrint-main
.\gen_update_json.ps1 `
  -ApkPath "D:\QrintPrint-Android\HuanxongKuaiyin-4.0-fixed-vN.apk" `
  -Mode github `
  -GitHubUser "你的GitHub用户名" `
  -GitHubRepo "huanxongkuaiyin-updates" `
  -GitHubTag "v4.1" `
  -VersionCode 42 `
  -VersionName "4.1" `
  -Title "浣熊快印 4.1" `
  -Changelog "- 修复 xxx`n- 优化 xxx"
```
脚本会：
- 自动算 `sizeBytes` 和 `sha256`
- 自动拼 jsDelivr URL
- 在 APK 所在目录写出 `update.json`

### 3. 推到 GitHub
第一次需要先 `git clone` 仓库到本地 + 配 SSH key 或 PAT（用 HTTPS 推）。

最简：直接用 GitHub 网页上传（适合偶尔发版）：
1. 打开仓库页面
2. 点 `Add file` → `Upload files`
3. 拖两个文件：
   - `update.json`
   - `huanxongkuaiyin-4.1.apk`（重命名成英文名）
4. Commit changes
5. **创建 tag**：左边栏点 `Releases` → `Create a new release` → Tag 填 `v4.1` → 描述填 changelog → Publish release

之后用 git 命令行（推荐，常用）：
```powershell
# 第一次
cd D:\
git clone https://github.com/<user>/huanxongkuaiyin-updates.git
cd huanxongkuaiyin-updates

# 之后每次
Copy-Item D:\QrintPrint-Android\HuanxongKuaiyin-4.0-fixed-vN.apk .\huanxongkuaiyin-4.1.apk -Force
Copy-Item D:\QrintPrint-Android\update.json .\update.json -Force
git add .
git commit -m "v4.1"
git tag v4.1
git push origin main --tags
```

### 4. 装旧版的用户启动 App
- 启动 2 秒后静默检查
- 远程 versionCode > 当前 → 弹更新对话框
- 点「立即更新」→ 后台下载 → 装好后弹安装器
- `@latest` 标签会让 jsDelivr 拉最新 release 里的文件

## 为什么用 `@latest` 而不是 `@main`？

jsDelivr 的 `@<tag>` 格式可以指向 GitHub Release tag，这样：
- 缓存可控：每次发版用新 tag，不会污染 main 分支
- 撤回容易：删掉 release tag 就回滚
- jsDelivr 对 release tag 缓存友好

## 字段说明

| 字段 | 必填 | 说明 |
|---|---|---|
| versionCode | 是 | 整数，必须比当前 App 的 versionCode **大**才弹更新 |
| versionName | 是 | 显示给用户看的版本号字符串 |
| title | 否 | 弹窗标题，默认 "发现新版本" |
| changelog | 否 | 弹窗里的更新说明，支持 `\n` 换行 |
| force | 否 | `true` 时用户必须更新，不能跳过/关闭弹窗 |
| minSupportedVersionCode | 否 | 低于这个 versionCode 的旧版本会被强制更新（不弹也强制） |
| url | 是 | 新 APK 的 jsDelivr 下载 URL |
| sizeBytes | 否 | 包大小字节数（弹窗里展示 "12.4 MB"），填 0 就不显示 |
| sha256 | 否 | 下载完后用这个值校验完整性（脚本自动算） |

## 故障排查

| 现象 | 可能原因 |
|---|---|
| 启动后没弹更新 | `versionCode` 没比当前 App 大；GitHub 仓库不是 Public；URL 输错用户名/仓库名 |
| 弹窗但下载失败 | APK 文件名和 `update.json` 里的 `url` 字段不一致；jsDelivr 缓存未刷新（等 5-10 分钟或用 `?v=时间戳`） |
| 下载完装不上 | AndroidManifest 没加 `REQUEST_INSTALL_PACKAGES` 权限（已加）；`file_paths.xml` 没加 updates 路径（已加） |
| 国内下载慢 | jsDelivr 偶尔抽风，等几分钟自动恢复；或换 GitHub 直链 + 用户手动复制 |
| jsDelivr 完全连不上 | 极少数地区封了 jsDelivr，换 Cloudflare R2（需信用卡）或 Vercel Blob（需绑卡） |

## 备选方案（如果 GitHub 方案也不行）

| 方案 | 免费 | 需信用卡 | 国内访问 | 备注 |
|---|---|---|---|---|
| **GitHub + jsDelivr（当前）** | ✅ | ❌ | ⚠️ 大部分 OK | 优先试这个 |
| Cloudflare R2 | ✅ | ✅ | ⚠️ 看地区 | 出口免费，10GB 存储 |
| Backblaze B2 | ✅ | ✅ | ⚠️ 看地区 | 10GB 存储 + 1GB/天出口免费 |
| 腾讯云 COS | ✅（新人礼包）| 部分 | ✅ 优 | 实名 + 50GB 一年免费 |
| 阿里云 OSS | ✅（新人礼包）| 部分 | ✅ 优 | 实名 + 40GB 一年免费 |
| Gitee 码云 Release | ✅ | ❌ | ✅ 优 | 但单文件 100MB 限制，APK 263MB 超了 ❌ |

## 隐私

GitHub 公开仓库 = 任何人拿到 URL 都能下载你的 APK。APK 本身就是公开分发内容，问题不大。但注意：
- 不要把签名密钥、API key 等敏感信息放仓库
- Changelog 内容公开可见
