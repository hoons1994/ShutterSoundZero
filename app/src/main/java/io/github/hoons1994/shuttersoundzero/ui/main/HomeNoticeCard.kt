package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.R
import kotlinx.coroutines.launch

@Composable
internal fun HomeNoticeCard(
    status: HomeStatus = HomeStatus.SETUP_REQUIRED,
    onOpenCameraSettings: () -> Unit = {}
) {
    val firstNotice = rememberSaveable { preferredHomeNotice(status) }
    val notices = remember(firstNotice) {
        listOf(firstNotice) + HomeNotice.entries.filter { it != firstNotice }
    }
    val pagerState = rememberPagerState(pageCount = { notices.size })
    val scope = rememberCoroutineScope()
    var userSelectedPage by rememberSaveable { mutableStateOf(false) }
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(status, isDragged) {
        if (isDragged) userSelectedPage = true
        if (!userSelectedPage) pagerState.scrollToPage(notices.indexOf(preferredHomeNotice(status)))
    }

    fun selectPage(page: Int) {
        userSelectedPage = true
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.home_notice_heading),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(Modifier.height(16.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                // Measure every notice so switching pages keeps the tallest content's height.
                // The height still adapts to screen width and the user's font size.
                beyondViewportPageCount = pagerState.pageCount - 1,
                verticalAlignment = Alignment.Top
            ) { page ->
                val (_, title, body) = noticeResources(notices[page])
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 21.sp
                    )
                    when (notices[page]) {
                        HomeNotice.CHECK_CAMERA -> Unit
                        HomeNotice.REAPPLY -> TextButton(onClick = onOpenCameraSettings) {
                            Text(stringResource(R.string.home_notice_open_camera_settings))
                        }
                        HomeNotice.WIRELESS_DEBUGGING -> Unit
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val narrow = maxWidth < 240.dp
                val previousLabel = stringResource(R.string.home_notice_previous)
                val nextLabel = stringResource(R.string.home_notice_next)
                val previous: @Composable () -> Unit = {
                    IconButton(
                        onClick = { selectPage((pagerState.currentPage - 1 + notices.size) % notices.size) },
                        modifier = Modifier.semantics { contentDescription = previousLabel }
                    ) {
                        Text("‹", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                val next: @Composable () -> Unit = {
                    IconButton(
                        onClick = { selectPage((pagerState.currentPage + 1) % notices.size) },
                        modifier = Modifier.semantics { contentDescription = nextLabel }
                    ) {
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                val position: @Composable () -> Unit = {
                    Text(
                        text = stringResource(R.string.home_notice_position, pagerState.currentPage + 1, notices.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val indicators: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        notices.forEachIndexed { index, notice ->
                            val label = stringResource(noticeResources(notice).first)
                            IconButton(
                                onClick = { selectPage(index) },
                                modifier = Modifier.semantics {
                                    contentDescription = label
                                    selected = pagerState.currentPage == index
                                }
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.size(width = if (pagerState.currentPage == index) 16.dp else 6.dp, height = 6.dp)
                                        .background(
                                            if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
                if (narrow) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        indicators()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            previous()
                            position()
                            next()
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        previous()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            indicators()
                            position()
                        }
                        next()
                    }
                }
            }
        }
    }
}

private fun noticeResources(notice: HomeNotice): Triple<Int, Int, Int> = when (notice) {
    HomeNotice.WIRELESS_DEBUGGING -> Triple(R.string.home_notice_wireless_label, R.string.home_notice_wireless_title, R.string.wireless_debugging_auto_blocker_guidance)
    HomeNotice.REAPPLY -> Triple(R.string.home_notice_reapply_label, R.string.home_notice_reapply_title, R.string.home_notice_reapply_body)
    HomeNotice.CHECK_CAMERA -> Triple(R.string.home_notice_camera_label, R.string.home_notice_camera_title, R.string.home_notice_camera_body)
}
