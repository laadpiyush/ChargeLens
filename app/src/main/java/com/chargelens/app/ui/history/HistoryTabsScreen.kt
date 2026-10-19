package com.chargelens.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chargelens.app.ui.components.ScreenTitle
import com.chargelens.app.ui.usage.UsageScreen

/** One bottom-nav tab holding charge history and battery usage, switched by tabs at the top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTabsScreen(
    charging: @Composable () -> Unit = { HistoryScreen() },
    usage: @Composable () -> Unit = { UsageScreen() },
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Charging", "Usage")
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("History", Modifier.padding(start = 4.dp))
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title) },
                    modifier = Modifier.testTag("history_tab_$i"),
                )
            }
        }
        when (tab) {
            0 -> charging()
            else -> usage()
        }
    }
}
