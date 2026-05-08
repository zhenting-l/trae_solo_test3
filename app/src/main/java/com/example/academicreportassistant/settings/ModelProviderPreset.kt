package com.lzt.summaryofslides.settings

enum class ModelProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultGeneralModel: String,
    val defaultVisionModel: String,
    val defaultTextModel: String,
) {
    Custom(
        displayName = "自定义",
        defaultBaseUrl = "",
        defaultGeneralModel = "",
        defaultVisionModel = "",
        defaultTextModel = "",
    ),
    ZhiPu(
        displayName = "智谱(GLM)",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultGeneralModel = "glm-4v",
        defaultVisionModel = "glm-4v",
        defaultTextModel = "glm-4",
    ),
    Qwen(
        displayName = "通义(Qwen)",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultGeneralModel = "qwen-vl-max",
        defaultVisionModel = "qwen-vl-max",
        defaultTextModel = "qwen-max",
    ),
    DeepSeek(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultGeneralModel = "",
        defaultVisionModel = "",
        defaultTextModel = "deepseek-chat",
    ),
}
