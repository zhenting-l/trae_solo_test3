package com.lzt.summaryofslides.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings = vm.settings.collectAsState()

    var apiKey by remember { mutableStateOf("") }
    var generalModel by remember { mutableStateOf("") }
    var visionModel by remember { mutableStateOf("") }
    var enableWebEnrichment by remember { mutableStateOf(false) }
    var preferWebViewMarkdown by remember { mutableStateOf(true) }
    var pdfOcrFallbackEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(settings.value) {
        apiKey = settings.value.apiKey
        generalModel = settings.value.generalModel
        visionModel = settings.value.visionModel
        enableWebEnrichment = settings.value.enableWebEnrichment
        preferWebViewMarkdown = settings.value.preferWebViewMarkdown
        pdfOcrFallbackEnabled = settings.value.pdfOcrFallbackEnabled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { Button(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("模型调用设置", style = MaterialTheme.typography.titleMedium)
                    TextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("apiKey") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextField(
                        value = visionModel,
                        onValueChange = { visionModel = it },
                        label = { Text("视觉模型") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextField(
                        value = generalModel,
                        onValueChange = { generalModel = it },
                        label = { Text("通用模型") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("联网补充论文信息")
                        Switch(
                            checked = enableWebEnrichment,
                            onCheckedChange = { enableWebEnrichment = it },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("默认网页渲染总结")
                        Switch(
                            checked = preferWebViewMarkdown,
                            onCheckedChange = { preferWebViewMarkdown = it },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("PDF失败时用OCR回退")
                        Switch(
                            checked = pdfOcrFallbackEnabled,
                            onCheckedChange = { pdfOcrFallbackEnabled = it },
                        )
                    }

                    Button(
                        onClick = {
                            vm.save(
                                apiKey = apiKey,
                                generalModel = generalModel,
                                visionModel = visionModel,
                                enableWebEnrichment = enableWebEnrichment,
                                preferWebViewMarkdown = preferWebViewMarkdown,
                                pdfOcrFallbackEnabled = pdfOcrFallbackEnabled,
                            )
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
