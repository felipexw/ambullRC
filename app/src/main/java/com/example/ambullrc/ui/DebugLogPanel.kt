package com.example.ambullrc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small, read-only, auto-scrolling panel showing [entries] (oldest first) — the on-screen
 * diagnostic widget from feature 004. Stateless: renders exactly what it's given, no logic beyond
 * scrolling to the newest entry when the list grows (function over form; no styling effort beyond
 * basic readability).
 */
@Composable
fun DebugLogPanel(entries: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .background(Color.Black)
            .padding(8.dp)
            .testTag("debug_log")
    ) {
        items(entries) { entry ->
            Text(text = entry, color = Color.Green, fontSize = 12.sp)
        }
    }
}
