package com.lzt.summaryofslides.settings

enum class ModelProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultGeneralModel: String,
    val defaultVisionModel: String,
) {
    ZhiPu(
        displayName = "智谱(GLM)",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultGeneralModel = "glm-5.1",
        defaultVisionModel = "glm-5v-turbo",
    ),
}
