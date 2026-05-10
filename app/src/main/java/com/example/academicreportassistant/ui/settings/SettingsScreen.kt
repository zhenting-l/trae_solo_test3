package com.lzt.summaryofslides.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.lzt.summaryofslides.settings.ModelProviderPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings = vm.settings.collectAsState()

    var providerExpanded by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var generalModel by remember { mutableStateOf("") }
    var visionModel by remember { mutableStateOf("") }
    var enableWebEnrichment by remember { mutableStateOf(false) }

    LaunchedEffect(settings.value) {
        baseUrl = settings.value.baseUrl
        apiKey = settings.value.apiKey
        generalModel = settings.value.generalModel
        visionModel = settings.value.visionModel
        enableWebEnrichment = settings.value.enableWebEnrichment
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
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { providerExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("供应商预设：${settings.value.providerPreset.displayName}")
                        }
                        DropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false },
                        ) {
                            for (preset in ModelProviderPreset.entries) {
                                DropdownMenuItem(
                                    text = { Text(preset.displayName) },
                                    onClick = {
                                        providerExpanded = false
                                        vm.applyPreset(preset)
                                    },
                                )
                            }
                        }
                    }

                    TextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("baseUrl") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
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

                    Button(
                        onClick = {
                            vm.save(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                generalModel = generalModel,
                                visionModel = visionModel,
                                enableWebEnrichment = enableWebEnrichment,
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
