package com.lzt.summaryofslides.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lzt.summaryofslides.ui.entrydetail.EntryDetailScreen
import com.lzt.summaryofslides.ui.entrylist.EntryListScreen
import com.lzt.summaryofslides.ui.share.ShareScreen
import com.lzt.summaryofslides.ui.shareimport.ShareImportScreen
import com.lzt.summaryofslides.ui.shareimport.SharePayloadHolder
import com.lzt.summaryofslides.ui.settings.AboutScreen
import com.lzt.summaryofslides.ui.settings.SettingsHomeScreen
import com.lzt.summaryofslides.ui.settings.SettingsScreen
import com.lzt.summaryofslides.ui.summary.SummaryDetailScreen
import com.lzt.summaryofslides.ui.summary.SummaryHistoryScreen
import com.lzt.summaryofslides.ui.summary.SummaryScreen

object Routes {
    const val EntryList = "entry_list"
    const val EntryDetail = "entry_detail"
    const val Summary = "summary"
    const val SummaryHistory = "summary_history"
    const val SummaryDetail = "summary_detail"
    const val Share = "share"
    const val ShareImport = "share_import"
    const val Settings = "settings"
    const val SettingsModel = "settings_model"
    const val SettingsAbout = "settings_about"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val sharePayload by SharePayloadHolder.payload.collectAsState()
    LaunchedEffect(sharePayload) {
        if (sharePayload != null) {
            navController.navigate(Routes.ShareImport)
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.EntryList,
    ) {
        composable(Routes.EntryList) {
            EntryListScreen(
                onOpenEntry = { entryId -> navController.navigate("${Routes.EntryDetail}/$entryId") },
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable(
            route = "${Routes.EntryDetail}/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onShare = { navController.navigate("${Routes.Share}/$entryId") },
                onOpenSummary = { navController.navigate("${Routes.Summary}/$entryId") },
                onOpenSummaryHistory = { navController.navigate("${Routes.SummaryHistory}/$entryId") },
            )
        }
        composable(
            route = "${Routes.Summary}/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
            SummaryScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.SummaryHistory}/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
            SummaryHistoryScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onOpenEntrySummary = { summaryId -> navController.navigate("${Routes.SummaryDetail}/$summaryId") },
            )
        }
        composable(
            route = "${Routes.SummaryDetail}/{summaryId}",
            arguments = listOf(navArgument("summaryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val summaryId = requireNotNull(backStackEntry.arguments?.getString("summaryId"))
            SummaryDetailScreen(
                summaryId = summaryId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.Share}/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
            ShareScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ShareImport) {
            ShareImportScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { entryId -> navController.navigate("${Routes.EntryDetail}/$entryId") },
            )
        }
        composable(Routes.Settings) {
            SettingsHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenModelSettings = { navController.navigate(Routes.SettingsModel) },
                onOpenAbout = { navController.navigate(Routes.SettingsAbout) },
            )
        }
        composable(Routes.SettingsModel) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SettingsAbout) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
