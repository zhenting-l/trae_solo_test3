package com.lzt.summaryofslides.settings

enum class ModelProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultGeneralModel: String,
    val defaultVisionModel: String,
) {
    Custom(
        displayName = "自定义",
        defaultBaseUrl = "",
        defaultGeneralModel = "",
        defaultVisionModel = "",
    ),
    ZhiPu(
        displayName = "智谱(GLM)",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultGeneralModel = "glm-5v-turbo",
        defaultVisionModel = "glm-5v-turbo",
    ),
    Qwen(
        displayName = "通义(Qwen)",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultGeneralModel = "qwen-vl-max",
        defaultVisionModel = "qwen-vl-max",
    ),
    DeepSeek(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultGeneralModel = "",
        defaultVisionModel = "",
    ),
}
