package app.offline.vocabulary.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.R
import app.offline.vocabulary.data.Rating
import app.offline.vocabulary.data.StudyCard
import app.offline.vocabulary.data.StudyMode
import app.offline.vocabulary.data.StudySession
import app.offline.vocabulary.data.StudySettings
import app.offline.vocabulary.data.normalizeAnswer

internal val StudyMode.caption: String
    @Composable get() = when (this) {
        StudyMode.CARDS -> stringResource(R.string.flashcards)
        StudyMode.QUIZ -> stringResource(R.string.quiz)
        StudyMode.TYPING -> stringResource(R.string.typing)
        StudyMode.REVIEW -> stringResource(R.string.review)
    }

internal val StudyShape = RoundedCornerShape(24.dp)

@Composable
internal fun StudyAction(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = true
) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 50.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Lavender else PanelRaised,
            contentColor = if (primary) Ink else Paper
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    val value = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(PanelRaised)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }) {
        if (value > 0f) Box(Modifier.fillMaxWidth(value).fillMaxHeight().clip(CircleShape).background(Lavender))
    }
}

@Composable
fun StudyScreen(
    session: StudySession, settings: StudySettings, dictionaryName: String,
    onBack: () -> Unit, onRate: (Rating) -> Unit, busy: Boolean = false
) {
    key(session.id, session.index, session.card.wordId) {
        val card = session.card
        var flipped by rememberSaveable { mutableStateOf(false) }
        var typed by rememberSaveable { mutableStateOf("") }
        var submitted by rememberSaveable { mutableStateOf<String?>(null) }
        val focus = LocalFocusManager.current
        val flash = session.mode == StudyMode.CARDS || session.mode == StudyMode.REVIEW
        val hasBack = card.answer.isNotBlank() || card.example.isNotBlank() || card.extra.isNotBlank()
        val correct = submitted?.let { response ->
            if (session.mode == StudyMode.TYPING) card.acceptsTypedAnswer(response, session.reverse)
            else normalizeAnswer(response) == normalizeAnswer(card.answer)
        }
        val checkTyped = {
            if (typed.isNotBlank() && submitted == null) {
                focus.clearFocus()
                submitted = typed.trim()
            }
        }
        Column(Modifier.fillMaxSize().background(Ink)) {
            ScreenHeading(session.mode.caption, onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dictionaryName, Modifier.weight(1f), color = Quiet, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text("${session.index + 1} / ${session.total}", color = Paper,
                        style = MaterialTheme.typography.labelLarge)
                }
                ThinProgress(session.index.toFloat() / session.total.coerceAtLeast(1))
                StudyFace(card, session.reverse, flipped, settings.animations, settings.fontSize, flash)
                if (flash) {
                    if (hasBack && !flipped) {
                        val action = when {
                            session.reverse -> stringResource(R.string.show_word)
                            card.answer.isNotBlank() -> stringResource(R.string.show_translation)
                            else -> stringResource(R.string.show_details)
                        }
                        StudyAction(action, { flipped = true }, Modifier.fillMaxWidth(), !busy)
                    } else {
                        Text(stringResource(R.string.rate_word), color = Quiet, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudyAction(stringResource(R.string.unknown), { onRate(Rating.AGAIN) }, Modifier.weight(1f), !busy, false)
                            StudyAction(stringResource(R.string.hard), { onRate(Rating.HARD) }, Modifier.weight(1f), !busy, false)
                            StudyAction(stringResource(R.string.known), { onRate(Rating.KNOWN) }, Modifier.weight(1f), !busy)
                        }
                        if (flipped) {
                            TextButton(onClick = { flipped = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(stringResource(R.string.show_front))
                            }
                        }
                    }
                } else if (session.mode == StudyMode.QUIZ) {
                    Text(stringResource(R.string.choose_translation), color = Quiet, style = MaterialTheme.typography.bodyMedium)
                    card.options.forEach { option ->
                        val isAnswer = normalizeAnswer(option) == normalizeAnswer(card.answer)
                        val selected = option == submitted
                        val reveal = submitted != null
                        val stroke = when {
                            reveal && isAnswer -> Lavender
                            selected -> MaterialTheme.colorScheme.error
                            else -> Hairline
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !reveal && !busy) { submitted = option },
                            color = if (reveal && isAnswer) Lavender.copy(alpha = .14f) else Panel,
                            border = BorderStroke(1.dp, stroke), shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(option, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), color = Paper)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it }, label = { Text(stringResource(R.string.your_translation)) },
                        modifier = Modifier.fillMaxWidth(), readOnly = submitted != null,
                        shape = RoundedCornerShape(16.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { checkTyped() })
                    )
                    if (submitted == null) StudyAction(stringResource(R.string.check_answer), checkTyped, Modifier.fillMaxWidth(), typed.isNotBlank() && !busy)
                }
                if (!flash && correct != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (correct) stringResource(R.string.correct) else stringResource(R.string.incorrect), color = if (correct) Lavender else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold)
                        if (!correct) Text(stringResource(R.string.correct_answer, card.answer), color = Paper)
                        if (card.example.isNotBlank()) Text(card.example, color = Quiet)
                    }
                    StudyAction(if (session.index + 1 == session.total) stringResource(R.string.finish) else stringResource(R.string.next),
                        { onRate(if (correct) Rating.KNOWN else Rating.AGAIN) }, Modifier.fillMaxWidth(), !busy)
                }
            }
        }
    }
}

@Composable
private fun StudyFace(
    card: StudyCard, reverse: Boolean, flipped: Boolean,
    animations: Boolean, fontSize: Int, flash: Boolean
) {
    val animatedAngle by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(if (animations) 360 else 0), label = "Card flip"
    )
    val angle = if (animations) animatedAngle else if (flipped) 180f else 0f
    val back = angle >= 90f
    val distance = LocalDensity.current.density * 28f
    val original = if (reverse) card.answer else card.prompt
    val translation = if (reverse) card.prompt else card.answer
    val showingOriginal = back || !reverse
    val word = if (showingOriginal) original else translation
    val label = if (showingOriginal) stringResource(R.string.word) else stringResource(R.string.translation)
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            rotationY = if (back) angle - 180f else angle
            cameraDistance = distance
        },
        color = Panel, shape = shape, border = BorderStroke(1.dp, Hairline)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = if (flash) 265.dp else 155.dp).padding(22.dp)) {
            Text(label, color = Lavender, style = MaterialTheme.typography.labelLarge)
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (flash) 180.dp else 88.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(word, color = Paper, fontSize = fontSize.sp, lineHeight = (fontSize * 1.22f).sp,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                if (showingOriginal && card.transcription.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    val isRussianReading = card.transcription.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' } &&
                        card.transcription.none { it.isLetter() && !(it in 'А'..'я' || it == 'ё' || it == 'Ё') }
                    Text(if (isRussianReading) stringResource(R.string.reading_in_russian) else stringResource(R.string.transcription),
                        color = Quiet, style = MaterialTheme.typography.labelMedium)
                    Text(card.transcription, color = Lavender, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            if (back) {
                if (translation.isNotBlank()) {
                    HorizontalDivider(color = Hairline)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.translation), color = Quiet, style = MaterialTheme.typography.labelMedium)
                    Text(translation, color = Paper, style = MaterialTheme.typography.headlineSmall)
                }
                if (card.example.isNotBlank()) DetailLine(stringResource(R.string.example), card.example)
                if (card.extra.isNotBlank()) DetailLine(stringResource(R.string.extra_information), card.extra)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Spacer(Modifier.height(8.dp))
    Text(label, color = Quiet, style = MaterialTheme.typography.labelMedium)
    Text(value, color = Paper, style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun StudyCompleteScreen(total: Int, masteryPercent: Double, milestone: Int?, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Lavender.copy(alpha = .12f)),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(38.dp)) {
                val a = Offset(size.width * .15f, size.height * .52f)
                val b = Offset(size.width * .40f, size.height * .77f)
                val c = Offset(size.width * .89f, size.height * .25f)
                drawLine(Lavender, a, b, 3.dp.toPx(), StrokeCap.Round)
                drawLine(Lavender, b, c, 3.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.session_complete), color = Paper, style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(pluralStringResource(R.plurals.cards_reviewed, total, total), color = Quiet)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.mastery_summary, masteryLabel(masteryPercent)), color = Lavender)
        if (milestone != null) {
            Spacer(Modifier.height(16.dp))
            Text(pluralStringResource(R.plurals.streak_milestone, milestone, milestone),
                color = Lavender, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(32.dp))
        StudyAction(stringResource(R.string.return_home), onHome, Modifier.fillMaxWidth())
    }
}
