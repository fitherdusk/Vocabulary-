package app.offline.vocabulary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.R

val Ink = Color(0xFF101012)
val Panel = Color(0xFF1B1B20)
val PanelRaised = Color(0xFF25252C)
val Lavender = Color(0xFFC3B9FF)
val Quiet = Color(0xFFAEADB7)
val Hairline = Color(0xFF37373F)
val Paper = Color(0xFFF5F4F9)

private val vocabularyColors = darkColorScheme(
    primary = Lavender, onPrimary = Color(0xFF282039),
    primaryContainer = Color(0xFF353042), onPrimaryContainer = Paper,
    secondary = Color(0xFFD1CDE3), onSecondary = Ink,
    secondaryContainer = PanelRaised, onSecondaryContainer = Paper,
    tertiary = Color(0xFFB9D8CF), onTertiary = Ink,
    background = Ink, onBackground = Paper,
    surface = Panel, onSurface = Paper, surfaceVariant = PanelRaised, onSurfaceVariant = Quiet,
    surfaceTint = Lavender, outline = Color(0xFF7D7A86), outlineVariant = Hairline,
    error = Color(0xFFFFB1A8), onError = Color(0xFF431D1D)
)

@Composable
fun VocabularyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = vocabularyColors, content = content)
}

internal enum class Glyph { Book, Stats, Home, Settings, Plus, Back, Next, More, Check, Play }

@Composable
internal fun VocabularyIcon(
    glyph: Glyph,
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val semantics = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Canvas(modifier.then(semantics)) {
        val u = size.minDimension / 24f
        val x0 = (size.width - u * 24f) / 2f
        val y0 = (size.height - u * 24f) / 2f
        fun pt(x: Float, y: Float) = Offset(x0 + x * u, y0 + y * u)
        val stroke = Stroke(1.65f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, pt(x1, y1), pt(x2, y2), stroke.width, StrokeCap.Round)
        fun shape(vararg points: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
            val path = Path()
            points.forEachIndexed { i, (x, y) ->
                val p = pt(x, y)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            if (close) path.close()
            if (fill) drawPath(path, color) else drawPath(path, color, style = stroke)
        }
        when (glyph) {
            Glyph.Book -> {
                // Two open pages meet at a single folded spine.
                shape(12f to 6.5f, 8.8f to 4.8f, 3f to 4.4f, 3f to 17.8f, 8.7f to 18.3f, 12f to 20f,
                    15.3f to 18.3f, 21f to 17.8f, 21f to 4.4f, 15.2f to 4.8f, close = true)
                line(12f, 7f, 12f, 19f)
            }
            Glyph.Stats -> {
                line(5.5f, 18.5f, 5.5f, 12.5f)
                line(12f, 18.5f, 12f, 5f)
                line(18.5f, 18.5f, 18.5f, 9f)
            }
            Glyph.Home -> {
                shape(3.5f to 10.5f, 12f to 3.6f, 20.5f to 10.5f, 20.5f to 20f,
                    14.6f to 20f, 14.6f to 14f, 9.4f to 14f, 9.4f to 20f, 3.5f to 20f, close = true)
            }
            Glyph.Settings -> {
                line(3.2f, 7.5f, 6f, 7.5f)
                line(12f, 7.5f, 20.8f, 7.5f)
                drawCircle(color, 2.6f * u, pt(9f, 7.5f), style = stroke)
                line(3.2f, 16.5f, 12f, 16.5f)
                line(18f, 16.5f, 20.8f, 16.5f)
                drawCircle(color, 2.6f * u, pt(15f, 16.5f), style = stroke)
            }
            Glyph.Plus -> { line(12f, 4.5f, 12f, 19.5f); line(4.5f, 12f, 19.5f, 12f) }
            Glyph.Back -> { shape(11f to 5f, 4f to 12f, 11f to 19f); line(4f, 12f, 20f, 12f) }
            Glyph.Next -> shape(9f to 6f, 15f to 12f, 9f to 18f)
            Glyph.More -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.4f * u, pt(it, 12f)) }
            Glyph.Check -> shape(4.5f to 12f, 9.5f to 17f, 19.5f to 7f)
            Glyph.Play -> shape(7.5f to 4f, 20f to 12f, 7.5f to 20f, close = true, fill = true)
        }
    }
}

@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 32f
        fun page(offset: Float, tone: Color) {
            val p = Path().apply {
                moveTo((3f + offset) * u, (21f + offset) * u)
                lineTo((15f + offset) * u, (17f + offset) * u)
                lineTo((27f + offset) * u, (20f + offset) * u)
                lineTo((15f + offset) * u, (24f + offset) * u)
                close()
            }
            drawPath(p, tone)
        }
        page(1f, Lavender.copy(alpha = .8f))
        val upper = Path().apply {
            moveTo(4f * u, 8f * u); lineTo(15f * u, 11.5f * u); lineTo(15f * u, 21f * u)
            lineTo(4f * u, 17.5f * u); close()
            moveTo(16.8f * u, 11.5f * u); lineTo(28f * u, 8f * u); lineTo(28f * u, 17.5f * u)
            lineTo(16.8f * u, 21f * u); close()
        }
        drawPath(upper, Paper)
    }
}

@Composable
fun ScreenHeading(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.background(Panel, CircleShape)) {
            VocabularyIcon(Glyph.Back, Paper, Modifier.size(22.dp), stringResource(R.string.back))
        }
        Text(title, color = Paper, fontSize = 27.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.8).sp)
    }
}

@Composable
fun VocabularyNavigation(
    settingsSelected: Boolean,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(Ink)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Hairline.copy(alpha = .55f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
            @Composable
            fun RowScope.Item(label: String, icon: Glyph, active: Boolean, onClick: () -> Unit) {
                val tone = if (active) Lavender else Quiet
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(52.dp, 28.dp).background(
                            if (active) Lavender.copy(alpha = .12f) else Color.Transparent,
                            RoundedCornerShape(18.dp)
                        ), contentAlignment = Alignment.Center
                    ) { VocabularyIcon(icon, tone, Modifier.size(21.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = tone, fontSize = 11.sp, lineHeight = 15.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            Item(stringResource(R.string.home), Glyph.Home, !settingsSelected, onHome)
            Item(stringResource(R.string.settings), Glyph.Settings, settingsSelected, onSettings)
            Item(stringResource(R.string.add), Glyph.Plus, false, onImport)
        }
    }
}
