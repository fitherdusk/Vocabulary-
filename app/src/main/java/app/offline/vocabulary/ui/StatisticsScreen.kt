package app.offline.vocabulary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.data.Stats

@Composable
fun StatisticsScreen(stats: Stats, dictionaryName: String?, dailyGoal: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        ScreenHeading("Статистика", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(dictionaryName ?: "Библиотека пуста", color = Quiet)
            Surface(color = Panel, shape = StudyShape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MasteryRing(stats.masteryPercent)
                    Text("Изучено ${stats.learned} из ${stats.total}", color = Paper, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StageLine("Новые", stats.newWords, Hairline)
                    StageLine("В процессе изучения", stats.learning, Color(0xFF8981B6))
                    StageLine("Изучено", stats.learned, Lavender)
                    Text("«Не знаю» и «Трудно» дают 10% за слово. Ответы «Знаю» подряд дают 40%, 70% и 100%.",
                        color = Quiet, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Активность", Modifier.padding(top = 4.dp, start = 2.dp), color = Paper,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Дневная цель", Modifier.weight(1f), color = Paper)
                        Text("${stats.today} / $dailyGoal", color = Lavender, fontWeight = FontWeight.Medium)
                    }
                    ThinProgress(stats.today.toFloat() / dailyGoal.coerceAtLeast(1))
                    Text("Разных слов за сегодня", color = Quiet, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Занятий", stats.sessions.toString(), Modifier.weight(1f))
                StatTile("Серия в днях", stats.streak.toString(), Modifier.weight(1f))
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Panel) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Повторить сегодня", Modifier.weight(1f), color = Paper)
                    Text(stats.due.toString(), fontWeight = FontWeight.SemiBold, color = Lavender,
                        style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun MasteryRing(percent: Double) {
    val value = (percent / 100).toFloat()
    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 11.dp.toPx()
            val diameter = size.minDimension - inset * 2f
            val top = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val circle = Size(diameter, diameter)
            val width = 5.dp.toPx()
            drawCircle(Hairline, radius = diameter / 2, style = Stroke(width = width))
            if (value > 0) {
                listOf(14f to .05f, 9f to .11f, 5f to 1f).forEach { (stroke, alpha) ->
                    drawArc(Lavender.copy(alpha = alpha), -90f, 360f * value, useCenter = false,
                        topLeft = top, size = circle, style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = formatMasteryPercent(percent)
            Text(label, color = Paper, fontWeight = FontWeight.SemiBold,
                fontSize = if (label.length > 5) 26.sp else 32.sp, lineHeight = 36.sp, textAlign = TextAlign.Center)
            Text("освоено", color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StageLine(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), color = Paper)
        Text(count.toString(), color = Paper, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Lavender)
            Text(label, color = Quiet, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
