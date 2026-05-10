# Summary of Slides (Android)

## 目标
拍照/导入学术报告幻灯片 → 调用支持视觉的大模型做结构化解析与延伸挖掘 → 生成可分享的PDF总结文档，并支持历史回顾与一键分享（多文件自动zip）。

## 打开方式
- 使用 Android Studio 打开本目录并同步Gradle。
- 需要本机已安装Android SDK（Gradle Wrapper会自动下载所需Gradle发行版）。

## 使用步骤（当前实现）
1. 进入“设置”填写 baseUrl / apiKey / 视觉模型 / 文本模型（可先用预设再微调）。
2. 回到列表页点“新建”，进入条目详情。
3. 用“拍照”或“相册导入”添加多张幻灯片图片。
4. 点击“开始分析并生成总结”，后台会执行逐页解析、融合总结并生成 summary.pdf。
5. 在条目详情中可打开PDF，或点右上角“分享”选择照片与/或PDF分享（多项会自动zip）。

## 文档
- 产品需求：[prd.md](docs/prd.md)
- 技术方案：[tech_design.md](docs/tech_design.md)
