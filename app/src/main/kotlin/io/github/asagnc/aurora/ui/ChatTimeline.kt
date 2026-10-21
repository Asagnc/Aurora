package io.github.asagnc.aurora.ui

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Follow new output until a reader deliberately drags; loading older rows preserves their anchor. */
@Composable internal fun ChatTimeline(owner: String?, revision: Any, modifier: Modifier,
    content: LazyListScope.() -> Unit) {
    key(owner) {
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var following by remember { mutableStateOf(true) }
        val away by remember { derivedStateOf { list.canScrollForward } }
        LaunchedEffect(list) {
            list.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) following = false
            }
        }
        LaunchedEffect(list) {
            snapshotFlow { list.isScrollInProgress to list.canScrollForward }.distinctUntilChanged().collect { (scrolling, more) ->
                if (!scrolling && !more) following = true
            }
        }
        LaunchedEffect(revision) {
            snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
            withFrameNanos { }
            if (following && list.layoutInfo.totalItemsCount > 0) {
                list.scrollToItem(list.layoutInfo.totalItemsCount - 1, Int.MAX_VALUE)
            }
        }
        Box(modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), state = list,
                verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 20.dp), content = content)
            if (away && !following) FilledTonalButton(onClick = {
                following = true
                scope.launch { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE) }
            }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Text("回到最新") }
        }
    }
}
