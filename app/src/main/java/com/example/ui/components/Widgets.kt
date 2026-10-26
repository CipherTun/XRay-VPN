package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProfileType
import com.example.ui.theme.LatencyGood
import com.example.ui.theme.LatencyMedium
import com.example.ui.theme.LatencyPoor
import java.util.Locale

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun LatencyBadge(
    latency: Int?,
    modifier: Modifier = Modifier
) {
    if (latency == null) {
        Box(
            modifier = modifier
                .size(8.dp)
                .background(Color.Gray.copy(alpha = 0.4f), CircleShape)
        )
        return
    }

    val (bg, text) = when {
        latency <= 0 -> Pair(LatencyPoor, "fail")
        latency < 150 -> Pair(LatencyGood, "${latency}ms")
        latency < 300 -> Pair(LatencyMedium, "${latency}ms")
        else -> Pair(LatencyPoor, "${latency}ms")
    }

    Surface(
        color = bg.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = bg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TypeAvatar(
    type: ProfileType,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val (icon, bg) = when (type) {
        ProfileType.SSH -> Pair(Icons.Default.Terminal, Color(0xFF26A69A))
        ProfileType.V2RAY -> Pair(Icons.Default.Security, Color(0xFF00897B))
        ProfileType.PROXY -> Pair(Icons.Default.Http, Color(0xFF00ACC1))
        ProfileType.WIREGUARD -> Pair(Icons.Default.Lock, Color(0xFF1E88E5))
        ProfileType.OPENVPN -> Pair(Icons.Default.Dns, Color(0xFF3949AB))
        ProfileType.CHAIN -> Pair(Icons.Default.Link, Color(0xFF5E35B1))
        ProfileType.GROUP -> Pair(Icons.Default.Layers, Color(0xFF8E24AA))
    }

    Box(
        modifier = modifier
            .size(size)
            .background(bg.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.name,
            tint = bg,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
fun Sparkline(
    downHistory: List<Float>,
    upHistory: List<Float>,
    modifier: Modifier = Modifier,
    downColor: Color = MaterialTheme.colorScheme.primary,
    upColor: Color = MaterialTheme.colorScheme.tertiary
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val w = size.width
        val h = size.height

        val maxVal = maxOf(
            (downHistory.maxOrNull() ?: 1f),
            (upHistory.maxOrNull() ?: 1f),
            100f
        )

        fun drawLine(data: List<Float>, color: Color) {
            if (data.size < 2) return
            val step = w / (data.size - 1).coerceAtLeast(1)
            val path = Path()

            data.forEachIndexed { i, value ->
                val x = i * step
                val normY = (value / maxVal).coerceIn(0f, 1f)
                val y = h - (normY * (h - 8f)) - 4f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 2.5.dp.toPx()))
        }

        drawLine(downHistory, downColor)
        drawLine(upHistory, upColor)
    }
}

fun fmtBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun fmtSpeed(bytesPerSec: Long): String {
    return "${fmtBytes(bytesPerSec)}/s"
}
