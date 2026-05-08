# Trae SOLO 对话交接文档（2026-05-08）

这份文件用于在另一台电脑的 trae-solo 客户端中快速、完整地理解本次会话的需求、实现结果、关键决策与排障过程。

说明：我无法保证把客户端里“逐字逐句”的完整聊天记录（包含所有工具输出）100%原样导出到本地文件；但下面记录了本次对话的全部关键需求与实现细节、代码落点与排障结论，足以在另一端完整复盘并继续开发。

---

## 1. 本次项目最终目标

把原 “Academic Report Assistant（学术报告分析助手）” 项目逐步重构为：

- 新应用名：Summary of Slides
- 支持一个条目导入最多 3 个 PDF（分享导入/手动上传都一致），超过提示：当前条目PDF文件过多，请新建条目后导入
- 支持“仅图片 / 仅 PDF / 图片+PDF”三种数据组合的自动分析策略
- 强化内存控制、防闪退；提供中断任务与重试机制
- 设置页改为“类目首页 → 子页面”的结构
- 提供导入内容（图片、PDF）的删除入口与确认弹窗
- 修复 CI/依赖冲突/Compose API 不兼容等一系列编译问题

---

## 2. 关键功能与实现落点

### 2.1 应用命名统一为 “Summary of Slides”

- Launcher 名称：`app_name`
  - `app/src/main/res/values/strings.xml`
- 通知渠道名/通知标题
  - `app/src/main/java/com/example/academicreportassistant/util/NotificationUtil.kt`
  - `app/src/main/java/com/example/academicreportassistant/worker/AnalyzeEntryWorker.kt`

### 2.2 一个条目最多导入 3 个 PDF（超限直接拒绝）

核心策略：

- 新增表 `entry_pdfs` 存储条目关联的 PDF 列表（排序、路径、显示名）
- 导入时：写入 `entry_pdfs`，文件命名为 `slides_1.pdf / slides_2.pdf / slides_3.pdf`
- 分享导入前做预检：避免“导入一半才报错”的部分成功
- 超限提示文案固定为：`当前条目PDF文件过多，请新建条目后导入`

关键文件：

- 数据结构/迁移：
  - `app/src/main/java/com/example/academicreportassistant/data/db/Entities.kt`
  - `app/src/main/java/com/example/academicreportassistant/data/db/Dao.kt`
  - `app/src/main/java/com/example/academicreportassistant/data/db/AppDatabase.kt`
  - `app/src/main/java/com/example/academicreportassistant/data/AppContainer.kt`
- 导入逻辑（限制、预检、落库）：
  - `app/src/main/java/com/example/academicreportassistant/data/repo/EntryRepository.kt`
- 分享打包时包含全部 PDF：
  - `app/src/main/java/com/example/academicreportassistant/ui/share/ShareViewModel.kt`
  - `app/src/main/java/com/example/academicreportassistant/ui/share/ShareScreen.kt`

### 2.3 分析策略（仅图片 / 仅 PDF / 图片+PDF）

入口：

- 条目详情页红色主按钮：根据数据状态决定行为
  - 仅 PDF：弹窗选“PDF直读（文字模型）”或“PDF转图片（视觉模型）”，点击即关闭弹窗并开始执行
  - 仅图片：自动走视觉模型
  - 图片+PDF：合并为一次分析（PDF 渲染为图片并与图片一起输入视觉模型）

Worker 行为：

- 图片+PDF：渲染 PDF 页为图片，拼接到图片列表后，统一走“逐页视觉模型 → 汇总文字模型”
- 仅 PDF + 选择 direct：先做 PDF 直读（layout_parsing→Markdown→文字模型总结），失败或太大则回退为“转图片视觉解析”

关键文件：

- UI/交互：
  - `app/src/main/java/com/example/academicreportassistant/ui/entrydetail/EntryDetailScreen.kt`
  - `app/src/main/java/com/example/academicreportassistant/ui/entrydetail/EntryDetailViewModel.kt`
- Worker 合并逻辑：
  - `app/src/main/java/com/example/academicreportassistant/worker/AnalyzeEntryWorker.kt`

### 2.4 内存控制与防闪退（关键）

发生过的典型问题：

- PDF 直读时 OOM：读取整份 PDF + base64 造成峰值内存过高
- WorkManager 前台服务在部分系统上被禁止：`startForegroundService() not allowed` 导致直接崩溃
- 图片解码阶段卡死/崩溃：停在“第1页：准备图片”

采取的措施：

- PDF 直读：改为文件流式 base64 编码，避免 `readBytes()` 的瞬时峰值
  - `app/src/main/java/com/example/academicreportassistant/llm/ZhiPuDocParser.kt`
- PDF 转图片：最多渲染 80 页（maxTotalPages=80）
  - `app/src/main/java/com/example/academicreportassistant/worker/AnalyzeEntryWorker.kt`
- 彻底移除 WorkManager 的 `setForeground(...)`（避免系统前台服务限制导致闪退）
  - `AnalyzeEntryWorker.kt` 中不再使用 `ForegroundInfo`，改为普通 `NotificationManager.notify`
- 图片压缩：Android 9+ 优先走 `ImageDecoder`，并在 `Dispatchers.IO` 执行
  - `app/src/main/java/com/example/academicreportassistant/util/ImageTranscodeUtil.kt`
  - `AnalyzeEntryWorker.kt`

### 2.5 任务中断/重启（“开始分析 ↔ 中断任务”）

实现为“状态驱动”：

- `status in (QUEUED, PROCESSING)`：按钮显示“中断任务”，点击取消 WorkManager 对应任务
- `status in (FAILED, CANCELLED)`：按钮显示“重新开始分析总结”
- 其余：按钮显示“开始分析并生成总结”

关键文件：

- UI 文案与点击分支：
  - `EntryDetailScreen.kt`
- 取消逻辑：
  - `EntryDetailViewModel.cancelAnalysis(...)`
  - `WorkEnqueuer.cancelAnalyzeEntry(...)`

并补充了全局 tag 用于“取消当前条目所有任务”：

- `WorkEnqueuer.kt` 为分析任务增加 tag：`analysis` 与 `entry-<entryId>`

### 2.6 删除上传内容（只删除 App 内部副本）

明确约束：删除指“删除 App 私有目录里复制的上传副本 + DB 记录”，不删除系统相册/下载目录里的源文件。

实现：

- 图片缩略图右上角 `...` → 删除 → 二次确认
- PDF 查看弹窗每行右侧 `X` → 删除 → 二次确认（确认弹窗不会关闭 PDF 列表弹窗；但若删除后 PDF 列表为空，则列表弹窗自动关闭）
- 删除后会做必要的重排（图片 displayOrder、PDF slides_1/2/3.pdf 重新编号）

关键文件：

- UI：`EntryDetailScreen.kt`
- 删除逻辑：`EntryRepository.deleteEntryImage(...)` / `EntryRepository.deleteEntryPdf(...)`
- DAO 支持：`Dao.kt`

### 2.7 设置页结构（类目首页 → 子页面）

实现：

- 设置首页：`模型调用设置` / `关于 Summary of Slides`
- 模型设置：沿用原 SettingsScreen，仅显示模型接入
- 关于页：单独 AboutScreen
- 路由：AppNav 新增 `SettingsModel` / `SettingsAbout`

关键文件：

- `app/src/main/java/com/example/academicreportassistant/ui/settings/SettingsHomeScreen.kt`
- `app/src/main/java/com/example/academicreportassistant/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/example/academicreportassistant/ui/settings/AboutScreen.kt`
- `app/src/main/java/com/example/academicreportassistant/ui/AppNav.kt`

---

## 3. CI/编译与依赖问题排障记录（关键结论）

### 3.1 commonmark Duplicate class

问题：

- 同时引入 `com.atlassian.commonmark:commonmark:0.13.0` 与 `org.commonmark:commonmark:0.22.0`，导致 duplicate class。

修复：

- 统一使用 `com.atlassian.commonmark:commonmark:0.13.0`（与 Markwon 兼容）
  - `gradle/libs.versions.toml`

### 3.2 Markwon ImagesPlugin unresolved / 类型不匹配

问题：

- `ImagesPlugin` 不在 `markwon-core`，需要 `io.noties.markwon:image`
- `ImagesPlugin.create(...)` 入参类型不是 Context，而是 ImagesConfigure

修复：

- 增加依赖 `io.noties.markwon:image`
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
- 代码改为 `ImagesPlugin.create()`
  - `app/src/main/java/com/example/academicreportassistant/util/MarkdownRender.kt`

### 3.3 Compose weight/height import 与 API 可见性

曾遇到：

- `weight` 在某些环境报 internal（已改为布局替代）
- `Modifier.height(...)` 未导入导致 `Unresolved reference: height`（已补 import）

---

## 4. 输出形态（用户在手机看到什么）

- “查看总结（Markdown）”：使用 Markwon 渲染后的富文本显示，不是纯 Markdown 源码
- “打开总结PDF”：由 Markdown 转 PDF 后的 PDF 排版

---

## 5. 仍需注意的限制（真实设备差异）

- Android 对后台/前台服务限制差异巨大，因此已避免依赖 WorkManager 的前台服务能力
- 图片解码/HEIC/厂商 ROM 仍可能有兼容性差异，但已通过 ImageDecoder + IO 分发增强鲁棒性

