package app.offline.vocabulary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

data class LibraryItem(val id: Long, val title: String, val learned: Int, val total: Int)

@Composable
fun HomeScreen(
    selected: LibraryItem?,
    masteryPercent: Double,
    animations: Boolean,
    onStart: () -> Unit,
    onLibrary: () -> Unit,
    onImport: () -> Unit,
    onStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.TopCenter) {
        val height = maxHeight
        val width = maxWidth
        val wide = width > 560.dp && width > height * 1.15f
        val activate = if (selected == null) onImport else onStart
        Column(
            Modifier.widthIn(max = if (wide) 960.dp else 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).heightIn(min = height)
                .padding(horizontal = if (wide) 30.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(onStatistics)
            if (wide) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = (height - 72.dp).coerceAtLeast(0.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StartRing(animations, selected == null, activate,
                            Modifier.size((height - 76.dp).coerceIn(150.dp, 350.dp)))
                    }
                    DictionaryPanel(selected, masteryPercent, onLibrary, onImport, Modifier.weight(1f))
                }
            } else {
                val panelAllowance = if (selected == null) 166.dp else 208.dp
                val diameter = minOf(width - 18.dp, height - panelAllowance - 72.dp)
                    .coerceIn(204.dp, 406.dp)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = (height - 72.dp).coerceAtLeast(0.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(1.dp))
                    StartRing(animations, selected == null, activate, Modifier.size(diameter))
                    DictionaryPanel(selected, masteryPercent, onLibrary, onImport,
                        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onStatistics: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(Panel, RoundedCornerShape(13.dp))
            .border(1.dp, Hairline.copy(alpha = .8f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            BrandMark(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(11.dp))
        Text("Offline Vocabulary", modifier = Modifier.weight(1f), color = Paper,
            fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.65).sp)
        IconButton(onClick = onStatistics) {
            Box(Modifier.size(40.dp).background(Panel, CircleShape)
                .border(1.dp, Hairline.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
                VocabularyIcon(Glyph.Stats, Paper, Modifier.size(22.dp), stringResource(R.string.statistics))
            }
        }
    }
}

@Composable
private fun StartRing(animations: Boolean, empty: Boolean, onStart: () -> Unit, modifier: Modifier) {
    val breathing = if (animations) {
        val transition = rememberInfiniteTransition(label = "Ring glow")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse),
            label = "Brightness")
    } else null
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var starting by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(onStart)
    val activate: () -> Unit = {
        if (!starting) {
            starting = true
            scope.launch {
                try {
                    if (animations) {
                        scale.animateTo(.951f, tween(105))
                        scale.animateTo(1f, tween(140))
                    }
                    currentAction()
                } finally { starting = false }
            }
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight)
        Box(
            Modifier.size(diameter).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }, contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val full = size.minDimension
                val radius = full * .357f
                val glowRadius = full * .496f
                val light = .76f + (breathing?.value ?: .5f) * .2f
                // Transparent stops on either side of the rim keep the center and edges dark.
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFF17171D),
                        .44f to Color(0xFF18181E),
                        .58f to Color(0xFF1D1D24),
                        .65f to Color(0xFF2C2B35),
                        .699f to Color(0xFF777681).copy(alpha = light),
                        .718f to Color(0xFFC3C1CD),
                        .733f to Color(0xFF898691).copy(alpha = light),
                        .757f to Color(0xFF4F4C59).copy(alpha = light),
                        .805f to Color(0xFF323038).copy(alpha = light),
                        .89f to Color(0xFF1F1D25).copy(alpha = light),
                        1f to Ink.copy(alpha = 0f),
                        center = center, radius = glowRadius
                    ), radius = glowRadius
                )
                drawCircle(
                    Brush.linearGradient(
                        listOf(Color(0xFFF6F4FF), Color.White, Color(0xFFD5D1DE), Color(0xFFFAFAFF)),
                        start = Offset(center.x - radius, center.y - radius),
                        end = Offset(center.x + radius, center.y + radius)
                    ), radius, style = Stroke(full * .0058f)
                )
                // Short, low-contrast graduations echo the circular silhouette.
                val ticks = full * .433f
                repeat(72) { i ->
                    val angle = (i / 72.0 * 2.0 * PI).toFloat()
                    val dx = cos(angle)
                    val dy = sin(angle)
                    val major = (i % 18 == 0)
                    val length = if (major) full * .012f else full * .004f
                    drawLine(if (major) Quiet.copy(alpha = .5f) else Quiet.copy(alpha = .17f),
                        center + Offset(dx * ticks, dy * ticks),
                        center + Offset(dx * (ticks + length), dy * (ticks + length)),
                        strokeWidth = if (major) 1.15.dp.toPx() else 0.8.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            val label = if (empty) stringResource(R.string.choose_to_start) else stringResource(R.string.start_session)
            val actionLabel = if (empty) stringResource(R.string.add_dictionary) else stringResource(R.string.open_session)
            Column(
                Modifier.size(diameter * .71f).clip(CircleShape)
                    .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = activate)
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                        onClick(label = actionLabel) { activate(); true }
                    },
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VocabularyIcon(Glyph.Play, Lavender, Modifier.size(if (diameter < 250.dp) 17.dp else 20.dp))
                Spacer(Modifier.height(5.dp))
                Text(stringResource(R.string.start), color = Paper, fontSize = if (diameter < 250.dp) 29.sp else 37.sp,
                    lineHeight = if (diameter < 250.dp) 34.sp else 43.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DictionaryPanel(
    selected: LibraryItem?,
    masteryPercent: Double,
    onLibrary: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceShape = RoundedCornerShape(25.dp)
    Column(modifier.clip(surfaceShape).background(Panel).border(1.dp, Hairline.copy(alpha = .8f), surfaceShape)) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button,
            onClickLabel = stringResource(R.string.open_library), onClick = onLibrary).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(43.dp).background(Lavender.copy(alpha = .10f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { VocabularyIcon(Glyph.Book, Lavender, Modifier.size(23.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(selected?.title ?: stringResource(R.string.empty_library), fontSize = 17.sp,
                        lineHeight = 21.sp, color = Paper, fontWeight = FontWeight.Medium,
                        letterSpacing = (-.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(if (selected == null) stringResource(R.string.add_words_file) else stringResource(R.string.learned_count, selected.learned, selected.total),
                        color = Quiet, fontSize = 13.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(8.dp))
                VocabularyIcon(Glyph.Next, Quiet, Modifier.size(17.dp))
            }
            if (selected != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dictionary_mastery), Modifier.weight(1f), color = Quiet, fontSize = 13.sp)
                    Text(masteryLabel(masteryPercent), color = Lavender, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(5.dp))
                ThinProgress((masteryPercent / 100).toFloat())
            }
        }
        Spacer(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(Hairline))
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onImport)
                .heightIn(min = 49.dp).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            VocabularyIcon(Glyph.Plus, Lavender, Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_dictionary), color = Lavender, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
