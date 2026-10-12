package io.github.hoons1994.shuttersoundzero.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.theme.StatusAmber
import io.github.hoons1994.shuttersoundzero.ui.AppTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemVolumeCard(
    volume: SystemVolumeUiState,
    onVolumeChange: (Int) -> Int?,
    onOpenSoundSettings: () -> Unit
) {
    val current = volume.current
    val canAdjust = current != null && !volume.isFixed && volume.max > volume.min
    val sliderEnabled = canAdjust && !volume.isRingerMuted && volume.requested == null
    val colorScheme = MaterialTheme.colorScheme
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = colorScheme.primary,
        inactiveTrackColor = colorScheme.primaryContainer,
        disabledActiveTrackColor = colorScheme.onSurface.copy(alpha = 0.28f),
        disabledInactiveTrackColor = colorScheme.surfaceContainerHigh
    )
    var selectedVolume by remember(current, volume.requested, volume.error) {
        mutableFloatStateOf((volume.requested ?: current ?: volume.min).toFloat())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackLevel = maxWidth / LocalDensity.current.fontScale < 220.dp
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_system_volume),
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.system_volume_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        if (current != null && !stackLevel) {
                            SystemVolumeLevelBadge(selectedVolume.roundToInt(), volume.max)
                        }
                    }
                    if (current != null && stackLevel) {
                        SystemVolumeLevelBadge(
                            level = selectedVolume.roundToInt(),
                            max = volume.max,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            if (canAdjust) {
                Slider(
                    enabled = sliderEnabled,
                    value = selectedVolume,
                    onValueChange = { selectedVolume = it },
                    onValueChangeFinished = {
                        selectedVolume = (
                            onVolumeChange(selectedVolume.roundToInt()) ?: current
                        ).toFloat()
                    },
                    valueRange = volume.min.toFloat()..volume.max.toFloat(),
                    steps = (volume.max - volume.min - 1).coerceAtLeast(0),
                    colors = sliderColors,
                    thumb = {
                        Surface(
                            modifier = Modifier.width(10.dp).height(28.dp),
                            shape = RoundedCornerShape(50),
                            color = if (sliderEnabled) colorScheme.onPrimary else colorScheme.surface,
                            border = BorderStroke(
                                2.dp,
                                if (sliderEnabled) colorScheme.primary
                                else colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        ) {}
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(28.dp),
                            enabled = sliderEnabled,
                            colors = sliderColors,
                            drawStopIndicator = null,
                            drawTick = { _, _ -> },
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "시스템 음량 조절" }
                )
            } else {
                Text(
                    text = if (volume.isFixed || (volume.max <= volume.min && current != null)) {
                        stringResource(R.string.system_volume_fixed)
                    } else {
                        volume.error ?: stringResource(R.string.system_volume_loading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canAdjust) {
                Text(
                    text = when {
                        volume.isRingerMuted -> stringResource(R.string.system_volume_ringer_muted)
                        volume.requested != null -> stringResource(R.string.system_volume_applying)
                        else -> volume.error ?: stringResource(R.string.system_volume_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = if (volume.error != null || volume.isRingerMuted) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onOpenSoundSettings,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.system_volume_open_settings))
            }
        }
    }
}

@Composable
private fun SystemVolumeLevelBadge(level: Int, max: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = "$level / $max",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1
        )
    }
}

@Preview(name = "시스템 음량 · 라이트", widthDp = 360)
@Preview(name = "시스템 음량 · 다크", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "시스템 음량 · 큰 글자", widthDp = 320, fontScale = 2f)
@Composable
private fun SystemVolumeCardPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(vertical = 20.dp)) {
                SystemVolumeCard(
                    volume = SystemVolumeUiState(current = 7, max = 15),
                    onVolumeChange = { it },
                    onOpenSoundSettings = {}
                )
            }
        }
    }
}
