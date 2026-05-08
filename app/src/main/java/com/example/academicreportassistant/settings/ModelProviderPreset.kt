package com.example.academicreportassistant.settings

enum class ModelProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultVisionModel: String,
    val defaultTextModel: String,
) {
    Custom(
        displayName = "自定义",
        defaultBaseUrl = "",
        defaultVisionModel = "",
        defaultTextModel = "",
    ),
    ZhiPu(
        displayName = "智谱(GLM)",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultVisionModel = "glm-4v",
        defaultTextModel = "glm-4",
    ),
    Qwen(
        displayName = "通义(Qwen)",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultVisionModel = "qwen-vl-max",
        defaultTextModel = "qwen-max",
    ),
    DeepSeek(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultVisionModel = "",
        defaultTextModel = "deepseek-chat",
    ),
}

