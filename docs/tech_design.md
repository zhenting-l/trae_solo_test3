# 技术方案（Android）

## 技术选型
- 语言：Kotlin
- UI：Jetpack Compose + Material 3
- 本地数据：Room（条目、图片、模型输出、PDF路径）
- 设置：DataStore Preferences（供应商预设、baseUrl、apiKey、模型名等）
- 后台任务：WorkManager（可重试、前台通知可选）
- 网络：OkHttp（直接实现OpenAI兼容HTTP调用，减少依赖）
- 文件：
  - 图片：app专属目录 filesDir/entries/{entryId}/images
  - PDF：filesDir/entries/{entryId}/summary.pdf
  - zip：cacheDir/share/{entryId}-{timestamp}.zip
- 分享：FileProvider + Android Sharesheet（ACTION_SEND/ACTION_SEND_MULTIPLE）

## 目录/模块建议
- app
  - ui/
    - entry_list/
    - entry_detail/
    - settings/
    - share_picker/
  - data/
    - db/（Room entities/dao/database）
    - repo/（EntryRepository, FileRepository）
    - model/（domain models）
  - llm/
    - OpenAiCompatClient（vision + text）
    - prompts/（prompt构造器）
    - parsing/（结构化输出的解析：JSON优先，失败回退文本）
  - worker/
    - AnalyzeEntryWorker（逐页解析+融合+延伸+PDF）
  - util/
    - ZipUtil, PdfUtil, ImageTranscodeUtil

## 数据模型（概念）
- Entry
  - id (UUID/String)
  - title（用户输入，可空）
  - createdAt
  - status：DRAFT / PROCESSING / SUCCEEDED / FAILED
  - lastError（可空）
  - speakerName / speakerAffiliation / talkTitle / keywords（模型抽取，可空）
  - summaryPdfPath（可空）
  - finalSummaryMarkdownOrText（可空）
- EntryImage
  - id
  - entryId
  - localPath
  - createdAt
  - pageIndex（可空）
- SlideAnalysis
  - id
  - entryId
  - imageId
  - extractedJson（可空）
  - extractedText（可空）

## LLM调用链路（建议实现）
用户点击“开始分析”后触发WorkManager：
1. 逐页解析（Vision）
  - 输入：图片（压缩JPEG后base64）、统一提示词（要求返回JSON）
  - 输出：每页结构化结果：要点、公式/图示描述、引用线索、术语
2. 报告元信息融合（Text）
  - 输入：所有逐页JSON的压缩版（仅保留要点/线索）+用户可选补充信息
  - 输出：speaker/talkTitle/keywords + high-level摘要
3. 延伸挖掘（Text）
  - 输入：融合结果 + 研究领域关键词
  - 输出：相关工作脉络、代表论文清单（含链接/DOI/会议期刊）、开放问题、复现建议
4. 生成最终“详版”总结（Text）
  - 输出：可渲染成PDF的正文（建议用Markdown风格，但允许纯文本回退）
5. PDF生成
  - 将正文分页绘制到PdfDocument，落盘 summary.pdf

## OpenAI兼容（国内模型适配策略）
目标：统一为 /v1/chat/completions（或等价路径），由“供应商预设”填充baseUrl与推荐模型名。
- 预设示例（仅做UI预填，不保证所有厂商都支持视觉）：
  - 智谱：baseUrl = https://open.bigmodel.cn/api/paas/v4
  - 通义：baseUrl = https://dashscope.aliyuncs.com/compatible-mode/v1
  - DeepSeek：baseUrl = https://api.deepseek.com
- 对视觉输入：优先使用 content=[{type:text},{type:image_url{url:data:...}}] 的格式；若厂商不支持，后续再增加“厂商特化适配器”。

## 低内存策略
- UI只展示缩略图：使用AsyncImage/自定义解码时指定inSampleSize；避免全尺寸Bitmap常驻。
- 上传前转码：按最大边（例如1280）缩放并压缩质量（例如75），再base64；读取采用流式。
- WorkManager中串行处理图片，避免并行解码与base64同时占用内存。
- Room只存路径与文本，不存大块二进制。

## 风险点与应对
- 视觉模型接口差异：先做OpenAI兼容，预留ProviderAdapter扩展点；设置页提供“测试调用”。
- token限制：逐页先结构化，再对结构化结果做二次压缩合并；必要时分批汇总。
- PDF排版：先实现文本分页输出；后续可升级为HTML->打印或Markdown渲染（更重）。

