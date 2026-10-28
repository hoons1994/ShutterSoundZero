package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.R
import kotlinx.coroutines.launch

@Composable
internal fun HomeNoticeCard() {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                // Measure both notices so switching pages keeps the tallest content's height.
                // The height still adapts to screen width and the user's font size.
                beyondViewportPageCount = pagerState.pageCount - 1,
                verticalAlignment = Alignment.Top
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (page == 0) "무선 디버깅 안내" else "사용 안내",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (page == 0) "무선 디버깅이 켜지지 않나요?" else "설정 후 촬영음을 확인해 주세요",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (page == 0) stringResource(R.string.wireless_debugging_auto_blocker_guidance)
                        else "실제 촬영음은 기기, 카메라 앱과 소리 상태에 따라 달라질 수 있습니다. 카메라를 열어 확인해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 21.sp
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1) % 2) } }, modifier = Modifier.semantics { contentDescription = "이전 안내" }) {
                    Text("‹", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) { index ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                .background(
                                    if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1) % 2) } }, modifier = Modifier.semantics { contentDescription = "다음 안내" }) {
                    Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
