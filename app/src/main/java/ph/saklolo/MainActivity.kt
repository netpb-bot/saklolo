package ph.saklolo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.media.AudioManager
import android.media.ToneGenerator
import android.location.LocationManager
import android.os.Looper
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Night tokens (demo-video/saklolo-film.html): near-black for OLED, ice ink, teal for "working", navy fills.
// secondary = the filled main action (navy fill, light ink: a big light fill is the costliest thing on OLED).
private val Ice = Color(0xFFD9E6FF)
private val Navy = Color(0xFF002D72)
private val Teal = Color(0xFF7FD6D0)
private val SafeGreen = Color(0xFF2FB36A) // same meaning as GREEN triage: safe
private val VictimDark = darkColorScheme(
    primary = Ice, onPrimary = Color(0xFF001233),
    primaryContainer = Navy, onPrimaryContainer = Ice,
    secondary = Navy, onSecondary = Ice,
    tertiary = Teal,
    background = Color(0xFF040913), surface = Color(0xFF040913),
    surfaceContainer = Color(0xFF0A1222), surfaceContainerHigh = Color(0xFF141C2B),
    onSurface = Color(0xFFEEF3FB), onSurfaceVariant = Color(0xFF8C9BB5),
    outline = Color(0xFF4A5872), outlineVariant = Color(0xFF1E2738),
    error = Color(0xFFE8333D), // SOS and 911 only
)

// Rescuer · Sunlight: pure white, black ink, outlines not shadows (tokens mock).
private val RescuerLight = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFC9DBFF), onPrimaryContainer = Color(0xFF001233),
    secondary = Navy, onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9DBFF), onSecondaryContainer = Color(0xFF001233),
    tertiary = Color(0xFF00605A),
    background = Color.White, surface = Color.White,
    surfaceContainer = Color(0xFFEDEFF2), surfaceContainerHigh = Color(0xFFDDE1E6),
    onSurface = Color.Black, onSurfaceVariant = Color(0xFF1F252C),
    outline = Color.Black, outlineVariant = Color(0xFF1F252C),
    error = Color(0xFFB3141B), // 911 only
)

// Archivo (display) + JetBrains Mono (labels), bundled: nothing is fetched at runtime.
@OptIn(ExperimentalTextApi::class)
private fun archivo(w: Int, wdth: Float) =
    Font(R.font.archivo, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.width(wdth)))
@OptIn(ExperimentalTextApi::class)
private fun mono(w: Int) = Font(R.font.jetbrains_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Sans = FontFamily(listOf(400, 500, 600, 700, 800, 900).map { archivo(it, 100f) })
val Head = FontFamily(archivo(700, 88f), archivo(800, 88f), archivo(900, 88f)) // condensed, big headlines only
val Mono = FontFamily(mono(400), mono(600), mono(700), mono(800))
private val Type = Typography().run {
    fun TextStyle.f() = copy(fontFamily = Sans)
    Typography(displayLarge.f(), displayMedium.f(), displaySmall.f(), headlineLarge.f(), headlineMedium.f(), headlineSmall.f(),
        titleLarge.f(), titleMedium.f(), titleSmall.f(), bodyLarge.f(), bodyMedium.f(), bodySmall.f(), labelLarge.f(), labelMedium.f(), labelSmall.f())
}
private val Btn = RoundedCornerShape(20.dp)

private fun icon(d: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes(d), fill = SolidColor(Color.Black)).build()
private val MicIcon = icon("M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z")
private val SpeakerIcon = icon("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z")

// Mono uppercase caption: kickers, chips, metadata.
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: TextUnit = 13.sp) =
    Text(text.uppercase(), modifier, fontFamily = Mono, fontSize = size, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = color)

// "● BEACON ON": a status, never a button.
@Composable
fun Pill(text: String, color: Color) = Row(
    Modifier.background(color.copy(alpha = 0.12f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    Box(Modifier.size(9.dp).background(color, CircleShape))
    Text(text.uppercase(), fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
}

// Triage colour always with its word.
@Composable
fun TriChip(tri: Tri, size: TextUnit = 13.sp) = Text(
    tri.name, Modifier.background(TriBg.getValue(tri), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    color = if (tri == Tri.RED) Color.White else Color.Black, fontFamily = Mono, fontSize = size, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
)

// The two button styles. Filled = navy (victim) / navy (rescuer); ghost = 2 dp line, optional accent colour.
@Composable
fun Filled(label: String, modifier: Modifier = Modifier.fillMaxWidth(), min: Dp = 64.dp, size: TextUnit = 20.sp,
           container: Color = MaterialTheme.colorScheme.secondary, ink: Color = MaterialTheme.colorScheme.onSecondary,
           enabled: Boolean = true, onClick: () -> Unit) = Button(
    onClick, modifier.heightIn(min = min), enabled = enabled, shape = Btn,
    colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = ink),
) { Text(label, fontSize = size, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }

@Composable
fun Ghost(label: String, modifier: Modifier = Modifier.fillMaxWidth(), min: Dp = 64.dp, size: TextUnit = 20.sp, accent: Color? = null,
          enabled: Boolean = true, onClick: () -> Unit) = OutlinedButton(
    onClick, modifier.heightIn(min = min), enabled = enabled, shape = Btn,
    border = BorderStroke(2.dp, accent?.copy(alpha = 0.7f) ?: MaterialTheme.colorScheme.outline),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent ?: MaterialTheme.colorScheme.onSurface),
) { Text(label, fontSize = size, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }

// House + signal arcs (the launcher mark), drawn instead of shipped as an image.
@Composable
fun Logo(size: Dp = 36.dp) = Canvas(Modifier.size(size)) {
    scale(this.size.width / 108f, Offset.Zero) {
        drawRoundRect(Navy, cornerRadius = CornerRadius(24f))
        val house = Path().apply { moveTo(28f, 56f); lineTo(54f, 32f); lineTo(80f, 56f); moveTo(36f, 50f); lineTo(36f, 76f); lineTo(72f, 76f); lineTo(72f, 50f) }
        drawPath(house, Ice, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        listOf(14f, 8f).forEach { r -> drawArc(Ice, 225f, 90f, false, Offset(54 - r, 62 - r), Size(2 * r, 2 * r), style = Stroke(5f, cap = StrokeCap.Round)) }
        drawCircle(Ice, 5f, Offset(54f, 62f))
    }
}

// Mic level as a rolling bar graph. Polls only while the mic is open (~15 fps, draw phase only); idle = flat, no work.
@Composable
fun Waveform(live: Boolean, modifier: Modifier = Modifier, height: Dp = 56.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val n = 28
    var lv by remember { mutableStateOf(FloatArray(n)) }
    LaunchedEffect(live) {
        lv = FloatArray(n)
        while (live) { delay(66); lv = lv.copyOfRange(1, n) + kotlin.math.sqrt(Voice.level / 0.12f).coerceIn(0f, 1f) } // ponytail: 0.12 rms = full bar, the gain knob
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val slot = size.width / n
        val bar = slot * 0.45f
        lv.forEachIndexed { i, v ->
            val h = maxOf(bar, v * size.height)
            drawRoundRect(color.copy(alpha = if (live) 0.35f + 0.65f * v else 0.25f), Offset(i * slot + (slot - bar) / 2, (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

enum class Lang { EN, FIL }
enum class Screen { HOME, PROTOCOL, BOARD, DETAIL, SURVIVAL, EVAL }
enum class Sos { IDLE, CANCELLING, CONFIRMED, RESCUED }

val LocalLang = staticCompositionLocalOf { Lang.EN }

// Demo overlay and menu state (judges only; a victim never sees it).
object Demo {
    var on by mutableStateOf(false)
    var ai by mutableStateOf("") // last AI call, raw scores
    var base by mutableStateOf<Facts?>(null) // last report: frozen keyword baseline
    var rules by mutableStateOf<Facts?>(null) // last report: rules alone
    var result by mutableStateOf<Facts?>(null) // last report: what the app used (rules + model, or rules)
    var inject by mutableStateOf<String?>(null) // injected sentence, handled by the chat as if spoken
    var said by mutableStateOf("") // last report text (spoken → Whisper, or injected)
    var sttMs by mutableStateOf<Long?>(null) // Whisper decode for that report; null = injected or typed
    var heardMs: Long? = null // set by survival mode's listener, which arrives through inject
    var aiMs by mutableStateOf<Long?>(null) // EmbeddingGemma + heads for that report

    // Injected as if spoken: same model, validator and dictionary path, only the mic is skipped.
    const val REPORT = "Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo. Tatlo kami, ay hindi, apat pala, " +
        "kasama si lola. Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!"
    // hard.json #4: no bleeding keyword, rules miss it, the AI reads HEAVY_BLEEDING (eval_log). The "why AI" beat.
    const val HARD = "Ang dami pong lumalabas na dugo sa hita ni kuya, basang-basa na yung tuwalya."
    const val MYTH = "Pwede bang sindihan ang lighter para makakita?"
    const val UPDATE = "Hindi na humihinga si lola!"
}

// The 22 bytes as (field, hex): ver|hops, id, lat, lon, kind, people, vuln, cond, place, battery, rev, age.
fun hexFields(b: ByteArray): List<Pair<String, String>> {
    var i = 0
    return listOf("ver|hop" to 1, "id" to 4, "lat" to 4, "lon" to 4, "kind" to 1, "ppl" to 1, "vuln" to 1, "cond" to 1,
        "place" to 1, "batt" to 1, "rev" to 1, "age" to 2).map { (name, n) ->
        name to b.copyOfRange(i, i + n).joinToString("") { "%02x".format(it) }.also { i += n }
    }
}

@Composable
fun t(en: String, fil: String) = if (LocalLang.current == Lang.FIL) fil else en

fun Txt.of(lang: Lang) = if (lang == Lang.FIL) fil else en

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Voice.init(applicationContext)
        Voice.prepare(applicationContext) // offline speech packs: check, download if missing
        Voice.loadWhisper(applicationContext) // offline Whisper: Filipino/Taglish speech with no signal
        // Parsed once here so every step change is in-memory only (< 300 ms per step).
        val protocols = Protocols(assets.open("protocols.json").bufferedReader().use { it.readText() })
        Llm.load(applicationContext, protocols) // app start, not mic tap
        val prefs = getPreferences(MODE_PRIVATE)
        setContent {
            var lang by remember { mutableStateOf(Lang.valueOf(prefs.getString("lang", "EN")!!)) }
            var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
            var sos by rememberSaveable { mutableStateOf(Sos.IDLE) }
            var facts by remember { mutableStateOf(Facts()) }
            var busy by remember { mutableStateOf(false) }
            var asked by remember { mutableStateOf(emptyList<String>()) } // follow-ups shown so far, at most MAX_FOLLOWUPS
            var q by remember { mutableStateOf<String?>(null) } // follow-up waiting for an answer
            var worseBit by remember { mutableStateOf(false) } // sticky: decided only here, on the victim phone
            val plan = protocols.plan(facts.hazard, facts.cond)

            val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            LaunchedEffect(Unit) {
                if (Beacon.permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) askPerms.launch(Beacon.permissions)
            }
            // Nothing is broadcast before SOS. After it, every fact change goes out as a new revision.
            LaunchedEffect(sos, facts, worseBit) {
                if (sos == Sos.CONFIRMED) Beacon.send(applicationContext, facts.toRecord(worseBit))
            }

            fun nextQuestion() {
                q = if (asked.size < MAX_FOLLOWUPS) followUps(facts).firstOrNull { it !in asked } else null
                q?.let { asked = asked + it }
            }

            // Every fact change goes through here. A new life threat jumps straight to its protocol (never waits);
            // a follow-up whose fact just got filled (by a tile or a report) moves on.
            fun update(answered: Boolean = false, f: (Facts) -> Facts) {
                val old = facts
                facts = f(old).inferred()
                // Answering "Is anyone hurt?" or a step like "Is the water still rising?" fills an unknown: never worse.
                if (worse(old, facts) && q != "hurt" && !answered) worseBit = true
                if ((facts.cond - old.cond).any { it == Cond.NOT_BREATHING || it == Cond.HEAVY_BLEEDING }) screen = Screen.PROTOCOL
                if (q != null && q !in missing(facts)) nextQuestion()
            }

            // Rules run at once (the fallback); the model result replaces them if it validates. The frozen keyword
            // baseline is shown in the demo overlay for the comparison.
            fun report(said: String) {
                val dict = Dictionary.extract(said)
                Log.d("Saklolo", "rules: $dict")
                Demo.base = Dictionary.baseline(said); Demo.rules = dict; Demo.result = null; Demo.said = said; Demo.aiMs = null
                val at = q
                busy = true
                lifecycleScope.launch {
                    val t0 = SystemClock.uptimeMillis()
                    val (model, raw) = withContext(Dispatchers.Default) { Llm.extract(said) }
                    Demo.aiMs = SystemClock.uptimeMillis() - t0
                    Log.d("Saklolo", "model: ${model ?: "not loaded, using rules"}")
                    Demo.ai = "extract(\"$said\")\n$raw\n→ ${model ?: "not loaded → rules"}"
                    Demo.result = model ?: dict
                    busy = false
                    update { it.merge(model ?: dict) }
                    if (at != null && q == at) nextQuestion() // asked once; don't repeat it
                }
            }

            fun exit(a: Ans) = update { it.withExit(a) }

            // SAFE replaces any SOS from this phone. sos goes back to IDLE so the next fact change can't resend the SOS.
            fun safe() {
                sos = Sos.IDLE
                Beacon.send(applicationContext, Record(0, Code.SAFE, Tri.GREEN))
                screen = Screen.BOARD
            }

            val followUp = q?.let { id ->
                FollowUpQ(asked.size, id, protocols.followup(id, facts.hazard), facts.hazard)
            }

            // Survival mode: on by itself after 2 min with no touch and no speech, and never while CPR or bleeding control
            // is in the plan (those must stay lit). Black until tapped; a ring or an ACK wakes it.
            var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            var dark by remember { mutableStateOf(false) }
            val lifeThreat = plan.any { it.first.id == "NOT_BREATHING" || it.first.id == "BLEEDING" }
            LaunchedEffect(lastTouch, screen, sos, busy, lifeThreat, Beacon.rescuePending) {
                if (sos == Sos.CONFIRMED && (screen == Screen.HOME || screen == Screen.PROTOCOL) && !busy && !lifeThreat && !Beacon.rescuePending) {
                    delay(120_000); screen = Screen.SURVIVAL
                }
            }
            LaunchedEffect(lastTouch, screen) { dark = false; if (screen == Screen.SURVIVAL) { delay(15_000); dark = true } }
            LaunchedEffect(Beacon.acked) { if (Beacon.acked) lastTouch = SystemClock.elapsedRealtime() }
            // A rescuer's "Rescued" needs an answer on this phone: leave survival mode and show the question.
            LaunchedEffect(Beacon.rescuePending) { if (Beacon.rescuePending && screen == Screen.SURVIVAL) screen = Screen.HOME }
            // Confirmed rescued: the conversation ends. Only CONFIRMED broadcasts or starts survival mode, so nothing
            // restarts the beacon behind the victim's back.
            LaunchedEffect(Beacon.state) { if (Beacon.state == Beacon.State.STOPPED && sos == Sos.CONFIRMED) sos = Sos.RESCUED }
            val black = screen == Screen.SURVIVAL && dark && !Alert.ringing
            LaunchedEffect(black) { window.attributes = window.attributes.apply { screenBrightness = if (black) 0.01f else -1f } }

            // Only the chat hears an injection, so confirm SOS first (as a real hold would); otherwise it would fire late.
            fun inject(said: String) {
                screen = Screen.HOME
                if (sos != Sos.CONFIRMED) { sos = Sos.CONFIRMED; if (asked.isEmpty()) nextQuestion() }
                Demo.inject = said
            }
            val menu: @Composable () -> Unit = {
                // One scrolling row, so the judge view leaves room for the step underneath.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        t("Report", "Ulat") to { inject(Demo.REPORT) },
                        t("No keyword", "Walang keyword") to { inject(Demo.HARD) },
                        t("Myth", "Mito") to { inject(Demo.MYTH) },
                        t("Update", "Update") to { inject(Demo.UPDATE) },
                        t("Survival", "Survival") to { screen = Screen.SURVIVAL },
                        t("Eval", "Eval") to { screen = Screen.EVAL },
                    ).forEach { (label, go) ->
                        OutlinedButton(onClick = go, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(label, fontSize = 12.sp) }
                    }
                }
            }

            var openId by rememberSaveable { mutableStateOf<Int?>(null) }
            val rescuing = screen == Screen.BOARD || screen == Screen.DETAIL
            // Scan for the whole time the board or a record is open (the hot/cold meter needs it).
            DisposableEffect(rescuing) {
                if (rescuing) Beacon.startScan(applicationContext)
                onDispose { if (rescuing) Beacon.stopScan(applicationContext) }
            }

            LaunchedEffect(rescuing) { WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = rescuing }

            BackHandler(screen != Screen.HOME) { screen = if (screen == Screen.DETAIL) Screen.BOARD else Screen.HOME }
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); lastTouch = SystemClock.elapsedRealtime() } }
            }) {
            MaterialTheme(colorScheme = if (rescuing) RescuerLight else VictimDark, typography = Type) {
                CompositionLocalProvider(LocalLang provides lang) {
                    Shell(lang, onLang = { lang = it; prefs.edit().putString("lang", it.name).apply() }, menu, quiet911 = sos == Sos.CONFIRMED && !rescuing) {
                        when (screen) {
                            // One branch for both, so a life threat (screen = PROTOCOL) keeps the same conversation.
                            Screen.HOME, Screen.PROTOCOL -> if (sos == Sos.CONFIRMED && Beacon.rescuePending) ConfirmRescue(
                                onYes = { Beacon.confirmRescued(applicationContext) },
                                onNo = { Beacon.renew(applicationContext) },
                            ) else if (sos == Sos.RESCUED) Rescued(
                                onRestart = { sos = Sos.CONFIRMED }, // same facts; the id is already fresh
                                onDone = { sos = Sos.IDLE; facts = Facts(); asked = emptyList(); q = null; worseBit = false },
                            ) else if (sos == Sos.CONFIRMED) Chat(
                                plan, facts, busy, followUp, onFacts = { update(f = it) }, onLearned = { update(answered = true, f = it) }, onReport = ::report,
                                onExit = { a, _ -> exit(a) }, onSkip = ::nextQuestion, onSurvive = { screen = Screen.SURVIVAL },
                                onActive = { lastTouch = SystemClock.elapsedRealtime() }, onSafe = ::safe, touched = lastTouch,
                            ) else Home(
                                sos, onSos = { sos = it; if (it == Sos.CONFIRMED && asked.isEmpty()) nextQuestion() },
                                onSafe = ::safe, onRescue = { screen = Screen.BOARD },
                            )
                            Screen.BOARD -> Board(onOpen = { openId = it; screen = Screen.DETAIL })
                            Screen.DETAIL -> Detail(Beacon.heard[openId], onBack = { screen = Screen.BOARD })
                            // Speech wakes it only after SOS: inject() would otherwise confirm an SOS nobody sent.
                            Screen.SURVIVAL -> Survival(onExit = { screen = Screen.HOME }, onHeard = if (sos == Sos.CONFIRMED) { said -> lastTouch = SystemClock.elapsedRealtime(); Demo.heardMs = Voice.sttMs; inject(said) } else null)
                            Screen.EVAL -> Eval(protocols)
                        }
                    }
                }
            }
            // Tap anywhere to wake: the root sees the touch first, this layer swallows it so nothing underneath is pressed.
            if (black) Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures {} })
            }
        }
    }
}

// Every screen: demo label + EN/FIL toggle on top, Call 911 at the bottom.
@Composable
fun Shell(lang: Lang, onLang: (Lang) -> Unit, menu: @Composable () -> Unit, quiet911: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Logo()
                Column(Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { Demo.on = !Demo.on }) }) {
                    Text("Saklolo", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, lineHeight = 24.sp)
                    Kicker(t("Demo · not medically reviewed", "Demo · hindi pa nasuri ng doktor"), size = 10.sp)
                }
                Row(Modifier.border(1.5.dp, c.outline, CircleShape).clip(CircleShape)) {
                    Lang.entries.forEach { l ->
                        val on = lang == l
                        Text(
                            l.name,
                            Modifier.background(if (on) c.onSurface else Color.Transparent)
                                .clickable(role = Role.RadioButton) { onLang(l) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) c.surface else c.onSurfaceVariant,
                        )
                    }
                }
            }
            if (Demo.on) DemoPanel(menu)
            VictimBanners()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            // After SOS the step is the main thing on screen, so 911 drops to a red outline (still on every screen).
            Button(
                onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = if (quiet911) 56.dp else 64.dp), shape = Btn,
                border = if (quiet911) BorderStroke(2.dp, c.error) else null,
                colors = ButtonDefaults.buttonColors(containerColor = if (quiet911) Color.Transparent else c.error, contentColor = if (quiet911) c.error else Color.White),
            ) {
                Text(t("Call 911", "Tumawag sa 911"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("  " + t("if there's signal", "kung may signal"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// Byte cells by field group; cool hues only, warm ones mean triage.
private val Cells = listOf(Color(0xFF9CC2FF), Color(0xFFC8B6FF), Color(0xFF7FD6D0), Color(0xFFB9C1CC))

private val Amber = Color(0xFFFFC53D)

// One fact per row, as words; "—" when unknown.
@Composable
fun factRows(f: Facts?): List<String> {
    fun s(x: String?) = x?.ifEmpty { null } ?: "—"
    return listOf(
        s(f?.hazard?.let { hazardLabel(it) }),
        s(f?.people?.takeIf { it > 0 }?.let { peopleLabel(it) }),
        s(f?.trapped?.let { if (it) t("Trapped", "Naiipit") else t("Not trapped", "Hindi naiipit") }),
        s(f?.let { place(it.floor, it.spot) }),
        s(f?.vuln?.sorted()?.map { vulnLabel(it) }?.joinToString(", ")),
        s(f?.cond?.sorted()?.map { condLabel(it) }?.joinToString(", ")),
    )
}

// Rules alone vs rules + on-device AI for the last report (the demo beat), sized to read on a projector. Only cells the
// AI changed are lit, so the AI gets credit for exactly what it added. (The frozen keyword baseline lives in Eval.)
@Composable
fun Compare(rules: Facts, result: Facts?) {
    val c = MaterialTheme.colorScheme
    val names = listOf(t("What", "Ano"), t("People", "Ilan"), t("Trapped", "Naiipit"), t("Where", "Saan"), t("Who", "Sino"), t("Hurt", "Pinsala"))
    val k = factRows(rules)
    val s = factRows(result)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("", Modifier.width(72.dp))
        Text(t("Rules", "Rules"), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
        Text(t("Rules + AI", "Rules + AI"), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Amber)
        result?.let { triage(it) }?.let { TriChip(it, 15.sp) }
    }
    names.indices.forEach { i ->
        val diff = result != null && k[i] != s[i]
        Row(Modifier.fillMaxWidth().background(if (diff) Amber.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp)).padding(vertical = 3.dp)) {
            Text(names[i], Modifier.width(72.dp).padding(start = 4.dp), fontSize = 15.sp, color = c.onSurfaceVariant)
            Text(k[i], Modifier.weight(1f), fontSize = 17.sp, color = if (diff) c.onSurfaceVariant else c.onSurface)
            Text(if (result == null) "…" else s[i], Modifier.weight(1f), fontSize = 17.sp, fontWeight = if (diff) FontWeight.ExtraBold else FontWeight.Normal, color = if (diff) Amber else c.onSurface)
        }
    }
    // Honest credit: only what the model found beyond the rules counts as AI.
    if (result != null && rules != null) Text(
        if (Llm.state != Llm.State.READY) t("AI not loaded · rules only", "Walang AI · rules lang")
        else (listOfNotNull(result.hazard?.takeIf { rules.hazard == null }?.let { hazardLabel(it) }) +
            (result.cond - rules.cond).map { condLabel(it) }).joinToString(", ").ifEmpty { null }?.let { t("AI added: $it", "Dinagdag ng AI: $it") }
            ?: t("AI added nothing · rules covered it", "Walang dinagdag ang AI · sakop ng rules"),
        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Amber,
    )
}

// Speech → meaning → bytes, each stage with what ran it and how long it took on this phone. Nothing leaves the phone.
@Composable
fun Pipeline() {
    val c = MaterialTheme.colorScheme
    fun ms(v: Long?) = v?.let { "%.1f s".format(it / 1000.0) } ?: "—"
    val hear = when {
        Demo.said.isEmpty() -> "—"
        Demo.sttMs != null -> ms(Demo.sttMs)
        else -> t("typed", "tinype")
    }
    // Three equal boxes on a ~360 dp phone: one-word titles, model on line 2, time on line 3.
    val stages = listOf(
        Triple(t("Hear", "Dinig"), if (Voice.whisperReady) "Whisper" else t("Phone STT", "STT ng phone"), hear),
        Triple(t("Grasp", "Unawa"), "Gemma 300M", if (Llm.state == Llm.State.READY) ms(Demo.aiMs) else Llm.state.name.lowercase()),
        Triple(t("Send", "Padala"), "BLE", "${Beacon.bytes?.size ?: RECORD_SIZE} bytes"),
    )
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        stages.forEachIndexed { i, (what, how, time) ->
            if (i > 0) Text("→", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Amber)
            Column(Modifier.weight(1f).fillMaxHeight().border(1.dp, c.outline, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(what, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(how, fontSize = 12.sp, lineHeight = 15.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = Amber, maxLines = 1)
            }
        }
    }
    // Provable: the manifest has no INTERNET permission. Airplane mode is claimed only when it's really on.
    val ctx = LocalContext.current
    val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    Kicker(
        t("No internet permission", "Walang internet permission") + if (airplane) t(" · airplane mode", " · airplane mode") else "",
        color = c.tertiary, size = 12.sp,
    )
}

// The 22 bytes read back in words, decoded from what actually went out (field order = hexFields).
@Composable
fun byteWords(b: ByteArray): List<String> {
    val r = Record.decode(b) ?: return emptyList()
    val noFix = t("no fix", "walang fix")
    fun coord(v: Int) = if (v == NO_FIX) noFix else "%.4f".format(v / 1e5)
    return listOf(
        "v1", "id", coord(r.lat), coord(r.lon),
        "${r.tri.name} · ${codeLabel(r.code)}" + if (r.trapped) t(" · trapped", " · naiipit") else "",
        if (r.people > 0) peopleLabel(r.people) else t("? people", "? katao"),
        r.vuln.sorted().map { vulnLabel(it) }.joinToString(", ").ifEmpty { "—" },
        r.cond.sorted().map { condLabel(it) }.joinToString(", ").ifEmpty { "—" },
        place(r.floor, r.spot).ifEmpty { t("place ?", "lugar ?") },
        "${r.battery}%", "rev ${r.rev}", "${r.age} min",
    )
}

@Composable
fun DemoPanel(menu: @Composable () -> Unit) {
    val mono = Mono
    var scores by remember { mutableStateOf(false) }
    // Bounded so the screen underneath and Call 911 always keep their space.
    Column(
        Modifier.fillMaxWidth().heightIn(max = 330.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).border(1.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(t("Judge view · all on this phone", "Judge view · lahat sa phone na ito"), Modifier.weight(1f), color = Amber, size = 12.sp)
            if (Demo.ai.isNotEmpty()) Text(
                if (scores) t("Scores ▾", "Scores ▾") else t("Scores ▸", "Scores ▸"),
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { scores = !scores }.padding(horizontal = 8.dp, vertical = 6.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber,
            )
        }
        menu()
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pipeline()
        if (Demo.said.isNotEmpty()) Text("“${Demo.said}”", fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Demo.rules?.let { Compare(it, Demo.result) }
        if (scores) Text(Demo.ai, fontSize = 11.sp, fontFamily = mono, lineHeight = 14.sp)
        Beacon.bytes?.let { b ->
            Text(t("The whole emergency in ${b.size} bytes", "Ang buong emergency sa ${b.size} bytes"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val words = byteWords(b)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hexFields(b).forEachIndexed { i, (name, hex) ->
                    Column(Modifier.background(Cells[i % Cells.size], RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                        Text(words.getOrNull(i) ?: name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(hex, fontSize = 10.sp, fontFamily = mono, color = Color.Black.copy(alpha = 0.7f))
                    }
                }
            }
        }
        }
    }
}

@Composable
fun ColumnScope.Home(sos: Sos, onSos: (Sos) -> Unit, onSafe: () -> Unit, onRescue: () -> Unit) {
    // One action on this screen: no title, no status panel. The line under SOS says what it does.
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        when (sos) {
            Sos.IDLE -> {
                SosButton(onConfirm = { onSos(Sos.CANCELLING) })
                Text(
                    t("The beacon starts right away, even without signal.", "Magsisimula agad ang beacon kahit walang signal."),
                    fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Sos.CANCELLING -> CancelBar(onDone = { onSos(Sos.CONFIRMED) }, onCancel = { onSos(Sos.IDLE) })
            Sos.CONFIRMED, Sos.RESCUED -> {} // the chat or the Rescued screen takes over
        }
    }
    ModelRow()
    // Bayanihan: "I'm safe" tells others and opens the board; a tanod or crew who was never in danger just opens it.
    if (sos == Sos.IDLE) {
        Ghost(t("I'm safe · help find others", "Ligtas ako · tumulong maghanap"), accent = SafeGreen, onClick = onSafe)
        TextButton(onClick = onRescue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(t("Rescuer mode · no broadcast", "Rescuer mode · walang broadcast"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Hold 1.5 s to confirm; releasing early snaps the ring back. Flat red, static halos: nothing redraws until a press.
@Composable
fun SosButton(onConfirm: () -> Unit) {
    var pressing by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        if (pressing) 1f else 0f,
        if (pressing) tween(1500, easing = LinearEasing) else tween(120),
        label = "sos",
    )
    LaunchedEffect(pressing) {
        if (pressing) { delay(1500); onConfirm() }
    }
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(292.dp).clip(CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressing = true; tryAwaitRelease(); pressing = false }) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2
            drawCircle(c.error.copy(alpha = 0.07f), r - 12.dp.toPx())
            drawCircle(c.error.copy(alpha = 0.14f), r - 26.dp.toPx())
            val ring = r - 5.dp.toPx()
            drawCircle(c.primary.copy(alpha = 0.12f), ring, style = Stroke(6.dp.toPx()))
            if (fill > 0f) drawArc(
                Color.White, -90f, 360f * fill, false, Offset(center.x - ring, center.y - ring), Size(2 * ring, 2 * ring),
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(
            Modifier.size(228.dp).clip(CircleShape).background(c.error),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("SOS", fontFamily = Head, fontSize = 76.sp, lineHeight = 76.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
            Kicker(t("Press and hold", "Pindutin nang matagal"), color = Color.White, size = 14.sp)
        }
    }
}

// 3-second cancel window after the hold.
@Composable
fun CancelBar(onDone: () -> Unit, onCancel: () -> Unit) {
    val left = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        left.animateTo(0f, tween(3000, easing = LinearEasing))
        onDone()
    }
    val c = MaterialTheme.colorScheme
    Kicker(t("Starting beacon", "Sinisimulan ang beacon"), color = c.tertiary)
    Text(t("Sending SOS…", "Ipinapadala ang SOS…"), fontFamily = Head, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    LinearProgressIndicator(
        { left.value }, Modifier.fillMaxWidth().height(10.dp),
        color = c.primary, trackColor = c.surfaceContainerHigh, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
    )
    Ghost(t("Cancel", "Kanselahin"), min = 72.dp, onClick = onCancel)
}

// Speech → model answer, with the dictionary alongside (logged for the demo overlay). Never throws, never null.
suspend fun hearAnswer(question: String, said: String): Ans {
    val dict = Dictionary.answer(said)
    val model = withContext(Dispatchers.Default) { Llm.answer(question, said) }
    Log.d("Saklolo", "answer: model=$model dict=$dict <- $said")
    Demo.ai = "answer(\"$question\", \"$said\")\n→ model ${model ?: "invalid"}, dictionary $dict"
    return combine(model, dict)
}

// Question → approved answer, or null for NONE. The model wins when valid; the dictionary only runs without it.
suspend fun findFaq(said: String, faqs: List<Faq>): Faq? {
    val model = withContext(Dispatchers.Default) { Llm.matchFaq(said, faqs) }
    val id = model ?: Dictionary.matchFaq(said, faqs)
    Log.d("Saklolo", "matchFaq: model=$model → $id <- $said")
    Demo.ai = "matchFaq(\"$said\")\n→ model ${model ?: "invalid"} → $id"
    return faqs.firstOrNull { it.id == id }
}

// Tap to speak: asks for the mic permission once; a miss says so and the buttons/text box still work.
// Hands-free (from the first trigger on): the mic waits for speech and reopens by itself after silence, noise or a reply.
// It turns itself off after Voice.standbyMs with no speech, or at once when tapped while listening; once off it stays
// off (new spoken lines don't reopen it) until the next tap. A broken mic (3 times in a row) gives up and calls onMiss,
// so the screen offers typing. The screen stays on only while hands-free: Android silences a background app's mic.
@Composable
fun MicButton(label: String, enabled: Boolean = true, trigger: Int = 0, hero: Boolean = false, onMiss: () -> Unit = {}, onText: (String) -> Unit) {
    val ctx = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) } // last words, shown under the hero mic only
    var missed by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) } // switched off (Stop or no speech for a while); a tap turns it back on
    var lastVoice by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var fails by remember { mutableIntStateOf(0) }
    var reopen by remember { mutableStateOf(0 to 0L) } // (n, pause ms): reopen the waiting mic after a pause
    fun later(ms: Long) { reopen = reopen.first + 1 to ms }
    fun granted() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun hear(standby: Boolean) {
        if (listening) return
        listening = true; missed = false; denied = false
        if (!standby) Voice.stop() // a tap means "my turn now"; a waiting mic just ignores our voice (Voice.ownSound)
        Voice.listen(ctx, standby) { said, failed ->
            listening = false
            fails = if (failed) fails + 1 else 0
            val quietFor = SystemClock.elapsedRealtime() - lastVoice
            when {
                // The reply's spoken line reopens the mic; this is the fallback if no line follows.
                said != null -> { heard = said; lastVoice = SystemClock.elapsedRealtime(); onText(said); if (handsFree) later(10_000) }
                handsFree && fails >= 3 -> { handsFree = false; missed = true; onMiss() }
                // Nobody has spoken for a while: switch off rather than hold the mic and the screen all night.
                handsFree && !failed && quietFor >= Voice.standbyMs -> { handsFree = false; paused = true }
                handsFree -> later(if (failed) 2_000L * fails else 300)
                else -> { missed = true; onMiss() }
            }
        }
    }
    fun stop() { Voice.cancel(); listening = false; handsFree = false; paused = true } // cancel() drops the callback
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) hear(handsFree) else { denied = true; missed = false; handsFree = false; onMiss() } }
    var asked by rememberSaveable { mutableStateOf(false) }
    // Each new trigger (Saklolo finished speaking) starts or keeps the waiting mic, unless it was switched off. The
    // first one asks for the mic right after SOS, so hands-free works without ever touching the mic button.
    LaunchedEffect(trigger) {
        if (trigger <= 0 || paused) return@LaunchedEffect
        handsFree = true
        lastVoice = SystemClock.elapsedRealtime()
        if (granted()) hear(standby = true) else if (!asked) { asked = true; askMic.launch(Manifest.permission.RECORD_AUDIO) }
    }
    LaunchedEffect(reopen) { if (reopen.first > 0) { delay(reopen.second); if (handsFree && granted()) hear(standby = true) } }
    val view = LocalView.current
    DisposableEffect(handsFree) { if (handsFree) view.keepScreenOn = true; onDispose { if (handsFree) view.keepScreenOn = false } }
    DisposableEffect(Unit) { onDispose { Voice.cancel() } } // leaving the screen closes the mic; no late result

    // Always on screen after SOS: dark disc, light ink. Hero = the "Tell me what happened" screen; else a compact bar.
    val c = MaterialTheme.colorScheme
    val on = listening || (enabled && !Alert.ringing)
    val status = when {
        listening -> t("Listening · tap to stop", "Nakikinig · pindutin para ihinto")
        !enabled -> label
        paused -> t("Mic off · tap to talk", "Patay ang mic · pindutin para magsalita")
        else -> label
    }
    val tap = Modifier.clickable(enabled = on, role = Role.Button, onClickLabel = status) {
        when {
            listening -> stop()
            granted() -> { paused = false; handsFree = true; lastVoice = SystemClock.elapsedRealtime(); hear(standby = false) }
            else -> { paused = false; askMic.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
    val ink = if (listening) c.tertiary else c.primary
    // Mic icon when idle, a stop square while listening: the disc says what a tap will do.
    val glyph = @Composable { s: Dp ->
        if (listening) Box(Modifier.size(s * 0.6f).background(ink, RoundedCornerShape(s * 0.12f)))
        else Icon(MicIcon, null, Modifier.size(s), tint = ink.copy(alpha = if (on) 1f else 0.5f))
    }
    val disc = if (listening) c.tertiary.copy(alpha = 0.16f) else c.surfaceContainerHigh
    if (hero) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) { // static rings, drawn once; the outer ones fade past the box like the film
                listOf(62.dp to 0.10f, 84.dp to 0.06f, 110.dp to 0.04f, 140.dp to 0.025f).forEach { (r, a) ->
                    drawCircle(c.primary.copy(alpha = a), r.toPx(), style = Stroke(1.5.dp.toPx()))
                }
            }
            Box(Modifier.size(104.dp).clip(CircleShape).background(disc).then(tap), contentAlignment = Alignment.Center) { glyph(48.dp) }
        }
        Waveform(listening, Modifier.padding(horizontal = 8.dp), height = 40.dp, color = ink)
        Text(status, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (listening) c.tertiary else c.onSurfaceVariant, textAlign = TextAlign.Center)
        heard?.let { Text("“$it”", fontSize = 16.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(CircleShape)
            .border(2.dp, if (listening) c.tertiary else c.outline, CircleShape).then(tap).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) { glyph(30.dp) }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            if (listening) Waveform(true, height = 24.dp, color = ink)
            Text(
                status, fontSize = if (listening) 14.sp else 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (listening) c.tertiary else c.onSurface.copy(alpha = if (on) 1f else 0.6f),
            )
        }
    }
    if (denied) Text(t("The microphone is off. Use the buttons or type it.", "Naka-off ang mikropono. Gamitin ang mga button o i-type."), fontSize = 16.sp)
    if (missed) Text(t("I didn't catch that. Tap the mic, use the buttons, or type it.", "Hindi ko narinig. Pindutin ang mic, gamitin ang mga button, o i-type."), fontSize = 16.sp)
}

data class FollowUpQ(val n: Int, val id: String, val text: Txt, val hazard: Hazard?)

// Follow-up question (mocks V7, V9). Every question carries its own answer buttons, so a victim who can't or won't
// speak is never stuck. On the protocol screen it waits below the step: "answer when you can".
@Composable
// bare: the hero mic screen already asked it, so only the tiles show.
fun FollowUp(f: FollowUpQ, waiting: Boolean, onAns: (Ans) -> Unit, onFacts: ((Facts) -> Facts) -> Unit, onSkip: () -> Unit, bare: Boolean = false) {
    val c = MaterialTheme.colorScheme
    // Below a step it's one line, so the step stays the only big thing on screen; a tap (or just saying it) answers.
    var open by remember(f.id) { mutableStateOf(false) }
    if (waiting && !open) return Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Btn).border(1.5.dp, c.tertiary.copy(alpha = 0.5f), Btn)
            .clickable(role = Role.Button) { open = true }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Kicker(t("When you can", "Kapag kaya mo"), color = c.tertiary, size = 11.sp)
        Text(f.text.of(LocalLang.current), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.tertiary)
    }
    Column(
        if (bare) Modifier.fillMaxWidth()
        else Modifier.fillMaxWidth().background(c.surfaceContainer, Btn).border(1.5.dp, if (waiting) c.tertiary.copy(alpha = 0.5f) else c.outline, Btn).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bare) Kicker(t("Or tap one", "O pindutin ang isa"))
        else {
            Kicker(
                if (waiting) t("Answer when you can", "Sagutin kapag kaya mo") else t("Question ${f.n} of $MAX_FOLLOWUPS", "Tanong ${f.n} sa $MAX_FOLLOWUPS"),
                color = c.tertiary,
            )
            Text(f.text.of(LocalLang.current), fontFamily = Head, fontSize = if (waiting) 22.sp else 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
            Text(t("Say it, or tap one.", "Sabihin, o pindutin ang isa."), fontSize = 16.sp, color = c.onSurfaceVariant)
        }
        when (f.id) {
            "exit" -> Answers(null, onAns)
            // No Skip: "Something else" is the way out, so the hazard is never left unknown.
            "what" -> listOf(
                listOf(Hazard.COLLAPSE to t("Collapse", "Gumuho"), Hazard.FLOOD to t("Flood", "Baha")),
                listOf(Hazard.LANDSLIDE to t("Landslide", "Landslide"), Hazard.OTHER to t("Something else", "Iba pa")),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (h, label) -> Big(label, Modifier.weight(1f), min = if (bare) 64.dp else 72.dp) { onFacts { it.copy(hazard = h) } } }
                }
            }
            // The dispatcher's universal questions: breathing, awake, bleeding. A life threat jumps to its protocol.
            "hurt" -> {
                listOf(
                    listOf(Cond.NOT_BREATHING to t("Not breathing", "Hindi humihinga"), Cond.UNCONSCIOUS to t("Not waking up", "Hindi magising")),
                    listOf(Cond.HEAVY_BLEEDING to t("Bleeding badly", "Malakas ang dugo"), Cond.INJURED to t("Hurt", "May sugat")),
                    // Under rubble, pinned is what decides the rescue team's tools.
                    if (f.hazard == Hazard.COLLAPSE || f.hazard == Hazard.LANDSLIDE) listOf(Cond.PINNED to t("Pinned", "Naipit")) else emptyList(),
                ).filter { it.isNotEmpty() }.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (k, label) -> Big(label, Modifier.weight(1f)) { onFacts { it.copy(cond = it.cond + k) } } }
                    }
                }
                Ghost(t("No one is hurt", "Walang nasaktan"), min = 56.dp, size = 18.sp, onClick = onSkip)
            }
            // ponytail: 5+ is sent as 5; voice gives the exact count
            "count" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n -> Big(if (n == 5) "5+" else "$n", Modifier.weight(1f)) { onFacts { it.copy(people = n) } } }
            }
            // Flood: roof or floor is what a boat crew needs. ponytail: 3+ is sent as 3; voice gives the exact floor
            "place" -> {
                if (f.hazard == Hazard.FLOOD) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ROOF to t("Roof", "Bubong"), 1 to "1", 2 to "2", 3 to "3+").forEach { (n, label) ->
                        Big(label, Modifier.weight(1f)) { onFacts { it.copy(floor = n) } }
                    }
                }
                // ponytail: "Inside" is sent as ROOM; voice gives the exact spot
                if (f.hazard == Hazard.LANDSLIDE) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Spot.ROOM to t("Inside", "Sa loob"), Spot.OUTSIDE to t("Outside", "Sa labas")).forEach { (s, label) ->
                        Big(label, Modifier.weight(1f)) { onFacts { it.copy(spot = s) } }
                    }
                }
                Ghost(t("Skip", "Laktawan"), Modifier, min = 56.dp, size = 18.sp, onClick = onSkip)
            }
            else -> Ghost(t("Skip", "Laktawan"), Modifier, min = 56.dp, size = 18.sp, onClick = onSkip)
        }
    }
}

// YES / NO / NOT SURE, always shown; a spoken answer lights its button.
@Composable
fun Answers(lit: Ans?, onAns: (Ans) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Big(t("YES", "OO"), Modifier.weight(1f), lit == Ans.YES) { onAns(Ans.YES) }
        Big(t("NO", "HINDI"), Modifier.weight(1f), lit == Ans.NO) { onAns(Ans.NO) }
    }
    Big(t("NOT SURE", "HINDI SIGURADO"), Modifier.fillMaxWidth(), lit == Ans.UNSURE) { onAns(Ans.UNSURE) }
}

@Composable
fun SurviveButton(reset: Long, hold: Boolean, onClick: () -> Unit) {
    // ponytail: shown only when there is nothing to do but wait, so it presses itself after 10 s idle.
    LaunchedEffect(reset, hold) { if (!hold) { delay(10_000); onClick() } }
    Ghost(t("Save battery", "Tipirin ang baterya"), size = 18.sp, onClick = onClick)
}

// Triage colours live outside the M3 scheme and only ever mean triage (tokens mock).
private val TriBg = mapOf(Tri.RED to Color(0xFFC9252C), Tri.YELLOW to Color(0xFFFFC53D), Tri.GREEN to Color(0xFF2FB36A))

// Answers "is my SOS out?" first. A beacon that isn't running is the worst silent failure, so that state is a full
// card that says NOT and carries the one button that fixes it. Healthy: a status pill + the triage rescuers see.
@Composable
fun SosStrip(f: Facts) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    if (Beacon.state == Beacon.State.STOPPED) return
    val bt = Beacon.state == Beacon.State.NO_BLUETOOTH
    if (bt || Beacon.state == Beacon.State.NO_PERMISSION) return Column(
        Modifier.fillMaxWidth().border(3.dp, c.onSurface, Btn).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t("Your SOS is NOT on", "HINDI pa naka-on ang SOS mo"), fontFamily = Head, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (bt) t("Turn on Bluetooth so rescuers can find you.", "Buksan ang Bluetooth para mahanap ka ng rescuers.")
            else t("Allow \"Nearby devices\" so rescuers can find you.", "Payagan ang \"Nearby devices\" para mahanap ka ng rescuers."),
            fontSize = 18.sp, lineHeight = 24.sp,
        )
        Filled(if (bt) t("Turn on Bluetooth", "Buksan ang Bluetooth") else t("Open settings", "Buksan ang settings"), container = c.primary, ink = c.onPrimary) {
            ctx.startActivity(
                if (bt) Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
            )
        }
    }
    // One block: the beacon, its triage, and what went out in it in words (known facts only; unknowns are asked by the
    // question card, not shown as "?").
    val sent = listOfNotNull(
        f.hazard?.let { hazardLabel(it) },
        f.people.takeIf { it > 0 }?.let { peopleLabel(it) },
        f.trapped?.let { if (it) t("Trapped", "Naiipit") else t("Can get out", "Makakalabas") },
        place(f.floor, f.spot).ifEmpty { null },
    ) + f.vuln.sorted().map { vulnLabel(it) } + f.cond.sorted().map { condLabel(it) }
    // "I understood": facts just learned (spoken or tapped) are lit for a few seconds, so the victim sees what was
    // heard and can correct it by saying it again. Nothing is spoken here: the next step must never wait.
    val lang = LocalLang.current
    var seen by remember(lang) { mutableStateOf(sent.toSet()) }
    val fresh = sent.filter { it !in seen }
    LaunchedEffect(fresh) { if (fresh.isNotEmpty()) { delay(5000); seen = seen + fresh } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Beacon.state == Beacon.State.OFF) Pill(t("Starting SOS…", "Sinisimulan…"), c.onSurfaceVariant)
            else Pill(t("Beacon on", "Naka-on ang beacon"), c.tertiary)
            TriChip(triage(f))
            if (fresh.isNotEmpty()) Kicker(t("Understood", "Naintindihan"), color = c.tertiary)
        }
        if (sent.isNotEmpty()) {
            Kicker(t("Rescuers see", "Nakikita ng rescuers"), size = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sent.forEach { s ->
                    val new = s in fresh
                    Text(
                        s,
                        Modifier.background(if (new) c.tertiary.copy(alpha = 0.18f) else c.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .border(1.5.dp, if (new) c.tertiary else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onSurface,
                    )
                }
            }
        }
    }
}

// Honest beacon status (mock): never a spinner that implies someone answered.
@Composable
fun BeaconLine() {
    val c = MaterialTheme.colorScheme
    val (line, ok) = when (Beacon.state) {
        Beacon.State.OFF -> t("Beacon starting…", "Nagsisimula ang beacon…") to true
        Beacon.State.SENDING -> if (Beacon.acked) t("Answered · help is coming", "May sumagot · paparating ang tulong") to true
            else t("Beacon: sending · no answer yet", "Beacon: nagpapadala · wala pang sumasagot") to true
        Beacon.State.STOPPED -> t("Beacon stopped: a rescuer marked you rescued", "Huminto ang beacon: minarkahan kang nailigtas") to true
        Beacon.State.NO_BLUETOOTH -> t("Beacon off: turn on Bluetooth", "Patay ang beacon: buksan ang Bluetooth") to false
        Beacon.State.NO_PERMISSION -> t("Beacon off: allow Nearby devices in Settings", "Patay ang beacon: payagan ang Nearby devices sa Settings") to false
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(if (ok) c.tertiary else c.onSurface, CircleShape))
        Text(line, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (ok) c.tertiary else c.onSurface)
    }
}

@Composable
fun place(floor: Int, spot: Spot): String = listOfNotNull(
    when (floor) { 0 -> null; ROOF -> t("Roof", "Bubong"); else -> t("Floor $floor", "Palapag $floor") },
    spotWord(spot, LocalLang.current == Lang.FIL),
).joinToString(" · ")

@Composable
fun hazardLabel(h: Hazard) = when (h) {
    Hazard.COLLAPSE -> t("Collapse", "Gumuho")
    Hazard.FLOOD -> t("Flood", "Baha")
    Hazard.LANDSLIDE -> t("Landslide", "Pagguho ng lupa")
    Hazard.OTHER -> t("Other emergency", "Ibang emergency")
}

@Composable
fun vulnLabel(v: Vuln) = when (v) {
    Vuln.INFANT -> t("Baby", "Sanggol")
    Vuln.CHILD -> t("Child", "Bata")
    Vuln.ELDERLY -> t("Elderly", "Matanda")
    Vuln.PREGNANT -> t("Pregnant", "Buntis")
    Vuln.PWD -> t("PWD", "PWD")
}

@Composable
fun condLabel(c: Cond) = when (c) {
    Cond.NOT_BREATHING -> t("Not breathing", "Hindi humihinga")
    Cond.UNCONSCIOUS -> t("Unconscious", "Walang malay")
    Cond.HEAVY_BLEEDING -> t("Heavy bleeding", "Malakas na pagdurugo")
    Cond.PINNED -> t("Pinned", "Naipit ang katawan")
    Cond.INJURED -> t("Injured", "Sugatan")
    Cond.WATER_RISING -> t("Water rising", "Tumataas ang tubig")
}

// Big outlined tile/answer button (mock V7); filled when selected.
@Composable
fun Big(label: String, modifier: Modifier = Modifier, on: Boolean = false, min: Dp = 72.dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = min), shape = Btn,
        contentPadding = PaddingValues(horizontal = 8.dp), // five count buttons share one 360 dp row
        border = BorderStroke(1.5.dp, if (on) c.onPrimaryContainer else c.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) c.onPrimaryContainer else c.surfaceContainerHigh,
            contentColor = if (on) c.onPrimary else c.onSurface,
        ),
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }
}

// ---- Chat (Callert-style, pre-written content): the victim talks, Saklolo answers only from protocols.json ----

private val GREETING = Txt("Tell me what happened, or ask me anything.", "Sabihin mo ang nangyari, o magtanong ka.")
private val CANT_ANSWER = Txt("I can't answer that safely. Follow the steps and wait for rescuers.", "Hindi ko masasagot iyan nang ligtas. Sundin ang mga hakbang at hintayin ang rescuers.")
private val APPROVED = Txt("APPROVED ANSWER · written in advance, not by AI", "APROBADONG SAGOT · isinulat na noon pa, hindi ng AI")
private val NO_APPROVED = Txt("NO APPROVED ANSWER", "WALANG APROBADONG SAGOT")
private val STAY = Txt("Stay where you are. Rescuers are coming.", "Manatili ka riyan. Paparating ang rescuers.")
private val SAFER_STEP = Txt("We'll take the safer choice.", "Pipiliin natin ang mas ligtas.")
private val SAFER_EXIT = Txt("We'll treat it as trapped. That's the safer choice.", "Ituturing nating naiipit kayo. Ito ang mas ligtas.")

// "I understood: NO" (mock V10/V11) as a chat line; UNSURE says which safer choice we took.
fun understood(a: Ans, safer: Txt): Txt {
    val (en, fil) = when (a) { Ans.YES -> "YES" to "OO"; Ans.NO -> "NO" to "HINDI"; Ans.UNSURE -> "NOT SURE" to "HINDI SIGURADO" }
    val tail = if (a == Ans.UNSURE) safer else Txt("", "")
    return Txt("I understood: $en. ${tail.en}".trim(), "Naintindihan ko: $fil. ${tail.fil}".trim())
}

// Saklolo's latest reply (an "I understood" line or a myth answer) with an optional small label. Only the latest is
// shown: a transcript is a chat, and a panicking reader re-reads old lines instead of acting on the new one.
data class Msg(val text: Txt, val label: Txt? = null)

@Composable
fun Reply(m: Msg) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    // An approved answer is the one light card on the victim screens: pre-written text, set apart from everything else.
    val approved = m.label == APPROVED
    Column(
        Modifier.fillMaxWidth().background(if (approved) Color.White else c.surfaceContainer, Btn).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(m.text.of(lang), fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = if (approved) Color(0xFF14182A) else c.onSurface)
        m.label?.let { Kicker(it.of(lang), color = if (approved) Color(0xFF5B6573) else c.tertiary, size = 11.sp) }
    }
}

// Only the current step, sized for arm's length (CPR runs for minutes with the phone on the floor). Past and future
// steps are not shown: one thing at a time.
@Composable
fun StepCard(proto: Protocol, n: Int, cur: Node, aloud: Boolean) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    // CPR and bleeding are RED triage by rule, so their band is the triage red; every other protocol is navy.
    val red = proto.id == "NOT_BREATHING" || proto.id == "BLEEDING"
    Column(Modifier.fillMaxWidth().clip(Btn).background(c.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().background(if (red) TriBg.getValue(Tri.RED) else c.secondary).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(proto.title.of(lang), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (red) Color.White else c.onSecondary)
            Kicker(t("Step $n", "Hakbang $n"), color = if (red) Color.White else c.onSecondary)
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(cur.text.of(lang), fontFamily = Head, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp)
            cur.detail?.let { Text(it.of(lang), fontSize = 20.sp, lineHeight = 27.sp, color = c.onSurfaceVariant) }
            if (aloud) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(SpeakerIcon, null, Modifier.size(24.dp), tint = c.tertiary)
                Kicker(t("Reading aloud…", "Binabasa…"), color = c.tertiary)
            }
        }
    }
}

// After SOS: one hands-free conversation. Saklolo speaks each line, then the mic reopens by itself; what the victim
// says is routed by keywords (instant, no model) to next / repeat / yes-no / question / new information.
// Buttons and tiles always work, with or without the model.
@Composable
fun ColumnScope.Chat(
    plan: List<Pair<Protocol, String>>, facts: Facts, busy: Boolean, followUp: FollowUpQ?,
    onFacts: ((Facts) -> Facts) -> Unit, onLearned: ((Facts) -> Facts) -> Unit, onReport: (String) -> Unit,
    onExit: (Ans, Boolean) -> Unit, onSkip: () -> Unit, onSurvive: () -> Unit, onActive: () -> Unit, onSafe: () -> Unit,
    touched: Long,
) {
    val lang = LocalLang.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<Msg?>(null) }
    var auto by remember { mutableStateOf(true) } // hands-free until a listen misses; tapping the mic turns it back on
    var listenKey by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var aloud by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var afterReport by remember { mutableStateOf(false) }
    var pendingAsk by remember { mutableStateOf<String?>(null) }

    // Position in the plan; a new plan (a new life threat) starts again at its first protocol, CPR/bleeding on top.
    var p by remember(plan) { mutableIntStateOf(0) }
    var id by remember(plan) { mutableStateOf(plan.firstOrNull()?.second) }
    var done by remember(plan) { mutableStateOf(emptyList<String>()) }
    val proto = plan.getOrNull(p)?.first
    // Resolved against the facts on every change: "we're on the roof" mid-step swaps in the roof version at once.
    val node = id?.let { i -> proto?.let { it.node(it.resolve(i, facts)) } }
    // Like a dispatcher: one safety tip first, then the questions, then the rest of the steps.
    // CPR and bleeding control never pause for questions.
    var tipGiven by remember(plan) { mutableStateOf(false) }
    val lifeThreat = plan.any { it.first.id == "NOT_BREATHING" || it.first.id == "BLEEDING" }
    val asking = followUp != null && tipGiven && !lifeThreat
    fun go(next: String?) {
        last = null // a new step clears the old reply; callers set the "I understood" line after this
        tipGiven = true
        node?.let { done = done + it.id }
        if (next != null) id = next else { p++; id = plan.getOrNull(p)?.second; done = emptyList() }
    }
    // Some step answers are facts rescuers need too ("Is the water still rising?"); unclear counts as yes, the safer reading.
    fun stepAnswer(n: Node, a: Ans) {
        n.fact?.let { k -> if (a != Ans.NO) onLearned { it.copy(cond = it.cond + k) } }
        go(n.after(a))
        last = Msg(understood(a, SAFER_STEP))
    }

    fun talk(text: String) { aloud = Voice.speak(text, lang) { aloud = false; if (auto) listenKey++ } }
    val line = (if (asking) followUp?.text?.of(lang) else null)
        ?: node?.let { n -> listOfNotNull(n.text, n.detail).joinToString(" ") { it.of(lang) } }
        ?: followUp?.text?.of(lang) ?: (if (plan.isEmpty()) GREETING else STAY).of(lang)
    LaunchedEffect(node, followUp, lang, replay) { if (!busy) talk(line) }
    LaunchedEffect(busy) { if (!busy && afterReport) { afterReport = false; replay++ } }
    DisposableEffect(Unit) { onDispose { Voice.stop(); Voice.cancel() } }

    fun ask(said: String) {
        val faqs = proto?.faqs.orEmpty()
        thinking = true
        scope.launch {
            val f = findFaq(said, faqs)
            thinking = false
            val text = f?.answer ?: CANT_ANSWER
            last = Msg(text, if (f != null) APPROVED else NO_APPROVED)
            talk(text.of(lang))
        }
    }
    // A question asked before anything is known: understand the report first, then answer from that protocol.
    LaunchedEffect(busy, proto) {
        val q = pendingAsk ?: return@LaunchedEffect
        if (busy) return@LaunchedEffect
        pendingAsk = null
        if (proto != null) ask(q) else { last = Msg(CANT_ANSWER, NO_APPROVED); talk(CANT_ANSWER.of(lang)) }
    }

    fun handle(said: String) {
        onActive()
        val stepQuestion = node?.takeIf { it.isQuestion && !asking }
        when (Dictionary.route(said, yesNoWaiting = stepQuestion != null || followUp?.id == "exit")) {
            // "ok" during a question repeats it; it never skips a step nobody heard.
            Route.NEXT -> if (node != null && !node.isQuestion && !asking) go(node.next) else replay++
            Route.REPEAT -> replay++
            Route.ANSWER -> {
                // "Yes, we're on the roof": keep the facts in the answer too (floor, count, who), not just the YES.
                Dictionary.extract(said).takeIf { it != Facts() }?.let { x -> onFacts { it.merge(x) } }
                thinking = true
                scope.launch {
                    if (stepQuestion != null) {
                        stepAnswer(stepQuestion, hearAnswer(stepQuestion.text.en, said))
                    } else {
                        val a = hearAnswer(followUp!!.text.en, said)
                        last = Msg(understood(a, SAFER_EXIT))
                        onExit(a, true)
                    }
                    thinking = false
                }
            }
            Route.QUESTION -> {
                // Facts inside a question still count ("lumilindol ulit, tatakbo ba ako?").
                if (proto == null || Dictionary.extract(said).let { it.hazard != null || it.cond.isNotEmpty() }) {
                    if (proto == null) pendingAsk = said else ask(said)
                    onReport(said)
                } else ask(said)
            }
            Route.REPORT -> { afterReport = true; onReport(said) }
        }
    }
    LaunchedEffect(Demo.inject) { Demo.inject?.let { Demo.inject = null; Demo.sttMs = Demo.heardMs; Demo.heardMs = null; handle(it) } }

    val scroll = rememberScrollState()
    // New content goes to the top now (no log to scroll), so jump back up to the step.
    LaunchedEffect(last, node, followUp) { scroll.scrollTo(0) }
    // Nothing known and nothing to do yet: the "Tell me what happened" screen, mic in the middle, tiles below it.
    val hero = node == null && facts.hazard == null && (followUp == null || followUp.id == "what")
    // Life threats can start at any moment, so they stay one tap away (a slim chip), except while the "Is anyone hurt?"
    // card already shows them.
    fun flip(x: Cond) = onFacts { it.copy(cond = if (x in it.cond) it.cond - x else it.cond + x) }
    val emergency = @Composable { if (followUp?.id != "hurt") EmergencyChip(facts.cond, ::flip) }
    val extras = @Composable {
        var typed by rememberSaveable { mutableStateOf("") }
        // Typing is the fallback for when voice fails, not a third input to choose from.
        if (!auto || Voice.packs != Voice.Packs.READY) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), shape = Btn, placeholder = { Text(t("Or type it here", "O i-type dito")) })
            Ghost(t("Send", "Ipadala"), Modifier, min = 56.dp, size = 16.sp, enabled = !busy && !thinking) { if (typed.isNotBlank()) { Demo.sttMs = null; handle(typed); typed = "" } }
        }
        // False alarm or out of danger: SAFE replaces this phone's SOS on rescuer boards (PRD risks). It stops the only
        // signal, so it takes a second tap within 3 s (same pattern as the rescuer's "Rescued").
        var confirmSafe by remember { mutableStateOf(false) }
        LaunchedEffect(confirmSafe) { if (confirmSafe) { delay(3000); confirmSafe = false } }
        TextButton(
            onClick = { if (confirmSafe) onSafe() else confirmSafe = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = if (confirmSafe) c.onSurface else SafeGreen),
        ) {
            Text(
                if (confirmSafe) t("Tap again: stop my SOS", "Pindutin ulit: ihinto ang SOS ko") else t("I'm safe now", "Ligtas na ako"),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
    // Fixed order, one main card: is my SOS out → what rescuers see → what I do now (step, else question, else rest).
    // Fixed top band in both layouts, from one call site so it remembers which facts are new across the switch.
    SosStrip(facts)
    if (hero) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        last?.let { Reply(it) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Tell me what happened.", "Sabihin mo ang nangyari."), fontFamily = Head, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            Kicker(t("Any language · works with no signal", "Kahit anong wika · kahit walang signal"), color = c.tertiary, size = 12.sp)
        }
    }
    if (!hero) Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        last?.let { Reply(it) }
        if (proto != null && node != null && !asking) {
            StepCard(proto, done.size + 1, node, aloud)
            if (node.isQuestion) Answers(null) { a -> stepAnswer(node, a) }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ghost(t("Repeat", "Ulitin"), Modifier, size = 16.sp) { replay++ }
                // CPR keeps the screen lit for minutes: dark navy fill, light ink.
                Filled(t("Next", "Susunod"), Modifier.weight(1f), size = 22.sp) { go(node.next) }
            }
        } else if (followUp == null) Column(
            // Nothing to do but wait: the only action offered is saving battery.
            Modifier.fillMaxWidth().background(c.surfaceContainer, Btn).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text((if (facts.hazard == null) GREETING else STAY).of(lang), fontFamily = Head, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
            SurviveButton(touched, hold = aloud || thinking || busy, onClick = onSurvive)
        }
        // Follow-ups wait below the first step, and always below a life-threat protocol.
        followUp?.let { FollowUp(it, waiting = node != null && !asking, onAns = { a -> onExit(a, false) }, onFacts = onFacts, onSkip = onSkip) }
        extras()
    }
    if (!hero) emergency()
    // One call site in both layouts, so the hands-free mic keeps its state. It carries the busy state; buttons stay usable.
    MicButton(
        if (busy || thinking) t("Understanding…", "Inuunawa…") else t("Tap to talk", "Pindutin para magsalita"),
        enabled = !busy && !thinking, trigger = listenKey, hero = hero, onMiss = { auto = false },
    ) { auto = true; Demo.sttMs = Voice.sttMs.takeIf { Voice.whisperReady }; handle(it) }
    if (hero) Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        followUp?.let { FollowUp(it, waiting = false, onAns = { a -> onExit(a, false) }, onFacts = onFacts, onSkip = onSkip, bare = true) }
            ?: SurviveButton(touched, hold = aloud || thinking || busy, onClick = onSurvive)
        extras()
    }
    if (hero) emergency()
}

// The life-threat tiles folded into one slim pinned chip; a tap opens the two tiles in place. Picking one jumps to its
// protocol (update()), which closes it again.
@Composable
fun EmergencyChip(cond: Set<Cond>, onFlip: (Cond) -> Unit) {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val set = listOf(Cond.NOT_BREATHING, Cond.HEAVY_BLEEDING).filter { it in cond }
    LaunchedEffect(set) { open = false }
    if (open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tile(t("Not breathing", "Hindi humihinga"), Cond.NOT_BREATHING in cond, Modifier.weight(1f)) { onFlip(Cond.NOT_BREATHING) }
        Tile(t("Bleeding badly", "Malakas dumugo"), Cond.HEAVY_BLEEDING in cond, Modifier.weight(1f)) { onFlip(Cond.HEAVY_BLEEDING) }
        Box(
            Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, c.outline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = t("Close", "Isara")) { open = false },
            contentAlignment = Alignment.Center,
        ) { Text("×", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    } else Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).border(1.5.dp, c.outline, CircleShape)
            .clickable(role = Role.Button) { open = true }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.tertiary)
        Text(
            if (set.isEmpty()) t("Someone not breathing or bleeding?", "May hindi humihinga o malakas dumugo?")
            else set.map { condLabel(it) }.joinToString(" · ") + t(" · change", " · palitan"),
            Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// Compact tile for the emergency chip (56 dp: thumb-sized but leaves room for the conversation).
@Composable
fun Tile(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 56.dp), shape = CircleShape,
        border = BorderStroke(1.5.dp, if (on) c.onPrimaryContainer else c.outline), contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) c.onPrimaryContainer else c.surfaceContainerHigh, contentColor = if (on) c.onPrimary else c.onSurface,
        ),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

// Silent when all is well. AI state lives in the demo overlay; SOS doesn't depend on it, so the victim isn't told.
// No progress bar: an indeterminate one redraws every frame.
@Composable
fun ModelRow() {
    // "Load AI files" lives in the demo panel: a victim never needs it.
    val line = when (Voice.packs) {
        Voice.Packs.DOWNLOADING -> t("Voice is downloading. Keep Wi-Fi on.", "Dina-download ang boses. Panatilihing naka-Wi-Fi.")
        Voice.Packs.ONLINE_ONLY, Voice.Packs.NONE -> t("Voice won't work without signal. Use the buttons.", "Hindi gagana ang boses nang walang signal. Gamitin ang mga button.")
        else -> return
    }
    Text(line, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- Rescuer: dispatch board (mock R1/R4) and record detail (R2/R3) ----

// Ticks once a second so "heard N s ago" and silence stay current.
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = SystemClock.elapsedRealtime() } }
    return now
}

// This phone's GPS fix (×1e5), or null. Offline GPS works in airplane mode, just slowly indoors.
@SuppressLint("MissingPermission")
@Composable
fun rememberGps(): Pair<Int, Int>? {
    val ctx = LocalContext.current
    var fix by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    DisposableEffect(Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val l = LocationListener { fix = (it.latitude * 1e5).toInt() to (it.longitude * 1e5).toInt() }
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        ) {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { l.onLocationChanged(it) }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, l, Looper.getMainLooper())
        }
        onDispose { lm.removeUpdates(l) }
    }
    return fix
}

// Compass heading in degrees from north, or null if the phone has no rotation sensor.
// Low-passed on sin/cos so the arrow doesn't jitter and doesn't flip through 0°/360°.
@Composable
fun rememberHeading(): Float? {
    val ctx = LocalContext.current
    var deg by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val m = FloatArray(9)
        val o = FloatArray(3)
        var s = Double.NaN
        var co = 0.0
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(m, e.values)
                SensorManager.getOrientation(m, o)
                val a = o[0].toDouble()
                if (s.isNaN()) { s = sin(a); co = cos(a) } else { s += 0.15 * (sin(a) - s); co += 0.15 * (cos(a) - co) } // ponytail: α=0.15 is the lag/jitter knob
                deg = ((Math.toDegrees(atan2(s, co)) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
    return deg
}

fun ago(ms: Long) = (ms / 1000).let { if (it < 60) "$it s" else "${it / 60} min" }

@Composable
fun codeLabel(c: Code) = when (c) {
    Code.COLLAPSE, Code.FLOOD, Code.LANDSLIDE -> hazardLabel(Hazard.valueOf(c.name))
    else -> t("Emergency", "Emergency")
}

@Composable
fun peopleLabel(n: Int) = when (n) {
    0 -> t("? people", "? katao")
    1 -> t("1 person", "1 tao")
    else -> t("$n people", "$n katao")
}

@Composable
fun Tag(text: String, filled: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(
        text,
        Modifier.border(2.dp, c.onSurface, RoundedCornerShape(8.dp))
            .background(if (filled) c.onSurface else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = if (filled) c.surface else c.onSurface, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
    )
}

// Vulnerable people, conditions and place as one line of flags.
@Composable
fun flags(r: Record) = (r.vuln.sorted().map { vulnLabel(it) } + r.cond.sorted().map { condLabel(it) } +
    listOfNotNull(place(r.floor, r.spot).ifEmpty { null })).joinToString(" · ")

@Composable
fun ColumnScope.Board(onOpen: (Int) -> Unit) {
    val now = rememberNow()
    val me = rememberGps()
    val c = MaterialTheme.colorScheme
    val all = Beacon.heard.values
    val ranked = rank(all)
    Column {
        Text(t("Calls for help", "Mga humihingi ng tulong"), fontFamily = Head, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        if (Beacon.scanning) Kicker(
            t("${ranked.size} beacons · scanning", "${ranked.size} beacon · nag-i-scan") + " · " + t("safe: ${safeCount(all)}", "ligtas: ${safeCount(all)}"),
        ) else Text(
            t("Can't scan: turn on Bluetooth and allow Nearby devices and Location.", "Hindi makapag-scan: buksan ang Bluetooth at payagan ang Nearby devices at Location."),
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
    }
    // This phone's own SAFE broadcast runs until stopped here or from the notification.
    if (Beacon.state == Beacon.State.SENDING && Beacon.record?.code == Code.SAFE) { // state is the Compose-observed half
        val ctx = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Sharing that you're safe", "Ibinabahaging ligtas ka"), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Ghost(t("Stop", "Ihinto"), Modifier, min = 56.dp, size = 16.sp) { Beacon.stop(ctx, rescued = false) }
        }
    }
    if (ranked.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text(t("Listening for beacons…", "Nakikinig para sa mga beacon…"), fontFamily = Head, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            t("No beacons nearby yet. Bluetooth reaches about 30–100 m, so silence doesn't mean nobody needs help. Move closer.",
                "Wala pang beacon sa malapit. Mga 30–100 m lang ang abot ng Bluetooth, kaya hindi ibig sabihing walang nangangailangan. Lumapit pa."),
            fontSize = 18.sp,
        )
    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ranked.forEach { h -> BoardRow(h, now, me) { onOpen(h.rec.id) } }
    }
}

@Composable
fun BoardRow(h: Heard, now: Long, me: Pair<Int, Int>?, onClick: () -> Unit) {
    val r = h.rec
    val c = MaterialTheme.colorScheme
    val silent = now - h.at > SILENT_MS
    // Glare-proof: the 2 dp black outline stays; triage is a thick left bar plus its word in a chip.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 96.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, if (silent) c.onSurfaceVariant else c.onSurface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(if (silent) c.surfaceContainerHigh else TriBg.getValue(r.tri)))
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (silent) Kicker(r.tri.name, color = c.onSurface) else TriChip(r.tri)
            val otw = r.id in Beacon.onTheWay
            if (r.worse || silent || otw) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.worse) Tag(t("GETTING WORSE", "LUMALALA"), filled = true)
                if (otw) Tag(t("ON THE WAY", "PAPUNTA NA"), filled = false)
                if (silent) Tag(t("SILENT · ${ago(now - h.at)}", "TAHIMIK · ${ago(now - h.at)}"), filled = false)
            }
            Text(
                peopleLabel(r.people) + (if (r.trapped) t(" · trapped", " · naiipit") else "") + " · " + codeLabel(r.code),
                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
            )
            flags(r).ifEmpty { null }?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant) }
            Text(
                t("REPORTED ${ago(now - reportedAt(h))} · HEARD ${ago(now - h.at)} · ${r.battery}%",
                    "INIULAT ${ago(now - reportedAt(h))} · NARINIG ${ago(now - h.at)} · ${r.battery}%") +
                    (if (r.battery <= LOW_BATTERY) t(" · MAY STOP SOON", " · BAKA MAMATAY NA") else ""),
                fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant,
            )
        }
        Column(Modifier.width(76.dp).padding(8.dp).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            if (me != null && r.lat != NO_FIX) {
                val b = bearingDeg(me.first, me.second, r.lat, r.lon)
                Text(distanceText(distanceM(me.first, me.second, r.lat, r.lon)), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(compass8(b) + if (silent) t(" · last", " · huli") else "", fontSize = 13.sp)
            } else {
                // Indoors and in rubble there's no GPS, but signal strength still says who's closest.
                val lit = if (silent) -1 else smooth(h.samples, now)?.let(::band) ?: -1
                Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(4) { i ->
                        Box(Modifier.width(8.dp).fillMaxHeight((i + 1) / 4f).background(if (i <= lit) c.primary else Color.Transparent, RoundedCornerShape(2.dp))
                            .border(1.5.dp, c.onSurface, RoundedCornerShape(2.dp)))
                    }
                }
                Text(
                    if (lit < 0) t("No GPS", "Walang GPS") else listOf(t("Cold", "Malamig"), t("Warm", "Maligamgam"), t("Hot", "Mainit"), t("Here", "Nandito"))[lit],
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ColumnScope.Detail(h: Heard?, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val now = rememberNow()
    val me = rememberGps()
    val heading = rememberHeading()
    Ghost(t("← Board", "← Listahan"), Modifier, min = 48.dp, size = 16.sp, onClick = onBack)
    if (h == null) { LaunchedEffect(Unit) { onBack() }; return }
    val r = h.rec
    val silent = now - h.at > SILENT_MS
    val brief = briefing(r, (now - reportedAt(h)) / 60_000, lang == Lang.FIL)
    // Read aloud on open (and when the language changes), not on every packet.
    LaunchedEffect(r.id, lang) { Voice.speak(brief, lang) }
    DisposableEffect(Unit) { onDispose { Voice.stop() } }
    // The rescuer is walking with the phone out: don't let the screen time out mid-search.
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    // "On my way" (ours or another rescuer's ACK) = find mode: the victim now beacons 10× faster, so the compass,
    // beeps and auto-ring have data. Before that the screen is for deciding: briefing first.
    val finding = r.id in Beacon.onTheWay
    var wasFinding by remember { mutableStateOf(finding) }
    val findingSay = t("Finding. Turn until the beep goes high, then walk.", "Hinahanap. Umikot hanggang tumaas ang beep, tapos maglakad.")
    LaunchedEffect(finding) {
        if (finding && !wasFinding) Voice.speak(findingSay, lang)
        wasFinding = finding
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TriChip(r.tri, 18.sp)
            Text(codeLabel(r.code) + " · " + peopleLabel(r.people), fontFamily = Head, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (r.worse) Tag(t("GETTING WORSE", "LUMALALA"), filled = true)
        if (finding) {
            Finder(h, silent, heading, me)
            Actions(r.id)
        }
        Text("“$brief”", fontSize = if (finding) 15.sp else 18.sp, lineHeight = if (finding) 21.sp else 25.sp)
        Row(
            Modifier.clip(CircleShape).border(2.dp, c.onSurface, CircleShape).clickable(role = Role.Button) { Voice.speak(brief, lang) }
                .heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(SpeakerIcon, null, Modifier.size(22.dp), tint = c.onSurface)
            Text(t("Repeat", "Ulitin"), fontWeight = FontWeight.Bold)
        }

        // GPS arrow when both phones have a fix (outdoors, e.g. a flood roof). In find mode the Finder dial uses it.
        if (finding) Unit else if (me != null && r.lat != NO_FIX) {
            val b = bearingDeg(me.first, me.second, r.lat, r.lon)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("↑", Modifier.rotate((b - (heading ?: 0f)).toFloat()), fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, color = c.primary)
                Column {
                    Text(distanceText(distanceM(me.first, me.second, r.lat, r.lon)), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text(compass8(b) + if (silent) t(" · last known location", " · huling alam na lokasyon") else "", fontSize = 16.sp)
                    if (heading == null) Text(t("No compass: arrow points north-up", "Walang compass: nakaturo sa hilaga ang arrow"), fontSize = 13.sp)
                }
            }
        } else Kicker(t("No GPS · using signal strength", "Walang GPS · gamit ang lakas ng signal"), color = c.onSurface)

        if (!finding) {
            Text(
                t("Tap \"On my way\" to start finding: their phone sends 10× faster, and the compass, beeps and auto-ring turn on.",
                    "Pindutin ang \"Papunta na ako\" para maghanap: 10× bibilis ang signal nila, at bubuksan ang compass, beep at auto-ring."),
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Actions(r.id)
        }
        Text(
            t("HEARD ${ago(now - h.at)} AGO · REV ${r.rev} · BATTERY ${r.battery}%", "NARINIG ${ago(now - h.at)} NA · REV ${r.rev} · BATERYA ${r.battery}%"),
            fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant,
        )
    }
}

// Find mode: a live compass, signal meter and distance, Geiger beeps with an aim tone, and auto-ring when close.
// Everything is passive, so the rescuer never stops to press anything.
@Composable
fun Finder(h: Heard, silent: Boolean, heading: Float?, me: Pair<Int, Int>?) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val r = h.rec
    val prefs = remember { ctx.getSharedPreferences("rescuer", Context.MODE_PRIVATE) }
    var ref1m by remember { mutableFloatStateOf(prefs.getFloat("ref1m", REF_1M.toFloat())) }
    var sound by rememberSaveable { mutableStateOf(true) }
    val now = SystemClock.elapsedRealtime() // recomposes on every packet
    val avg = if (silent || now - h.at > 3_000) null else smooth(h.samples, now)
    val names = listOf(t("Cold", "Malamig"), t("Warm", "Maligamgam"), t("Hot", "Mainit"), t("Right here", "Nandito na"))

    // Live compass: each packet's RSSI (relative to the 3 s median) paired with where the phone faces right now.
    val headingNow by rememberUpdatedState(heading)
    var pts by remember(r.id) { mutableStateOf(listOf<Triple<Long, Float, Double>>()) }
    var est by remember(r.id) { mutableStateOf<Pair<Float, Long>?>(null) } // strongest heading, and when
    var lastRing by remember(r.id) { mutableLongStateOf(0L) }
    LaunchedEffect(h.samples.lastOrNull()) {
        val (at, rssi) = h.samples.lastOrNull() ?: return@LaunchedEffect
        if (silent) return@LaunchedEffect
        val hd = headingNow
        val m = smooth(h.samples, at)
        if (hd != null && m != null) {
            pts = (pts + Triple(at, hd, rssi - m)).filter { at - it.first <= LIVE_MS }
            bestHeading(pts.map { it.second to it.third })?.let { est = it to at }
        }
        // Auto-ring: close enough → the victim's phone chirps and strobes; again every 40 s while still close.
        if (closeEnough(h.samples, at, ref1m.toDouble()) && (lastRing == 0L || at - lastRing >= AUTO_RING_EVERY_MS)) {
            if (Beacon.command(ctx, Code.RING, r.id)) lastRing = at
        }
    }

    // Outdoors with both GPS fixes and still far: GPS bearing beats signal guessing. Close in, GPS error (~25 m) is too big.
    val gpsM = if (me != null && r.lat != NO_FIX) distanceM(me.first, me.second, r.lat, r.lon) else null
    val gps = gpsM != null && gpsM > 25
    val sig = est?.takeIf { now - it.second <= 20_000 }
    val target = if (gps) bearingDeg(me!!.first, me.second, r.lat, r.lon).toFloat() else sig?.first
    val rel = if (heading != null && target != null) relativeDeg(target, heading) else null
    val fresh = gps || (sig != null && now - sig.second <= 5_000)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(230.dp).border(4.dp, c.onSurface, CircleShape), contentAlignment = Alignment.Center) {
            when {
                heading == null -> Text(t("No compass on this phone", "Walang compass ang phone"), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                rel == null -> Text(t("Look around slowly", "Dahan-dahang tumingin sa paligid"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                else -> Text("↑", Modifier.rotate(rel).alpha(if (fresh) 1f else 0.4f), fontSize = 150.sp, fontWeight = FontWeight.ExtraBold, color = c.primary)
            }
        }
    }
    if (rel != null) {
        val a = kotlin.math.abs(rel)
        Text(
            when {
                a <= 30 -> t("Ahead", "Diretso")
                a >= 150 -> t("Behind you", "Nasa likod mo")
                rel > 0 -> t("Turn right", "Kumanan")
                else -> t("Turn left", "Kumaliwa")
            },
            Modifier.fillMaxWidth(), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        )
    }
    Text(
        if (gps) t("By GPS · ${distanceText(gpsM!!)}", "Ayon sa GPS · ${distanceText(gpsM!!)}")
        else t("By signal · rough, ±45°. Doorways and gaps can bend it.", "Ayon sa signal · tantiya, ±45°. Puwedeng lumihis sa pinto at butas."),
        Modifier.fillMaxWidth(), fontSize = 14.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
    )

    // Hot/cold as 4 segments (film .meter), lit up to the current band; outlined for sunlight.
    val lit = avg?.let(::band) ?: -1
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { i ->
            Box(Modifier.weight(1f).height(52.dp).background(if (i <= lit) c.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .border(2.dp, c.onSurface, RoundedCornerShape(12.dp)))
        }
    }
    Row { Kicker(names[0], Modifier.weight(1f), color = c.onSurface); Kicker(names[3], color = c.onSurface) }
    Text(
        if (avg == null) t("Waiting for signal…", "Hinihintay ang signal…")
        else names[band(avg)] + " · " + t("about", "mga") + " " + distanceBucket(approxMeters(avg, ref1m.toDouble())),
        fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
    )
    Text(
        if (lastRing != 0L && now - lastRing < 30_000) t("Their phone is ringing: listen and look for the light.", "Tumutunog ang phone nila: makinig at hanapin ang ilaw.")
        else t("Their phone rings by itself within ~8 m.", "Kusang tutunog ang phone nila sa loob ng ~8 m."),
        fontSize = 16.sp, fontWeight = FontWeight.Bold,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { sound = !sound }) {
            Text(if (sound) t("Beeps on", "May beep") else t("Beeps off", "Walang beep"), fontWeight = FontWeight.Bold)
        }
        Text(avg?.let { "%.0f dBm".format(it) } ?: "", Modifier.weight(1f), fontSize = 14.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        // Stand 1 m from the victim phone once per hall; walls and bodies shift the path loss a lot. Also moves the auto-ring distance.
        TextButton(onClick = { avg?.let { ref1m = it.toFloat(); prefs.edit().putFloat("ref1m", ref1m).apply() } }, enabled = avg != null) {
            Text(t("Calibrate: 1 m away", "I-calibrate: 1 m"), fontWeight = FontWeight.Bold)
        }
    }

    // Geiger beeps + a vibration tick: faster = closer. High pitch = facing the victim (within ±30°), so the rescuer
    // turns until it goes high, then walks, with eyes on the rubble.
    val latest by rememberUpdatedState(if (silent) null else h)
    val aimed by rememberUpdatedState(rel?.let { kotlin.math.abs(it) <= 30 } == true)
    LaunchedEffect(sound) {
        if (!sound) return@LaunchedEffect
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        val vib = ctx.getSystemService(Vibrator::class.java)
        try {
            while (true) {
                val cur = latest
                val t0 = SystemClock.elapsedRealtime()
                val dbm = cur?.takeIf { t0 - it.at <= 3_000 }?.let { smooth(it.samples, t0) }
                if (dbm == null) { delay(500); continue }
                // ponytail: tones and the 1.5 s → 0.12 s range are the hall-tuning knobs
                tone.startTone(if (aimed) ToneGenerator.TONE_DTMF_D else ToneGenerator.TONE_PROP_BEEP, 60)
                vib?.vibrate(VibrationEffect.createOneShot(if (aimed) 50 else 25, VibrationEffect.DEFAULT_AMPLITUDE))
                delay((1500 - 1380 * level(dbm)).toLong())
            }
        } finally { tone.release() }
    }
}

// Rescuer actions (mock R2/R3). Each one advertises a command with the victim's id for ~70 s.
@Composable
fun Actions(victim: Int) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var sent by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    val failed = t("Couldn't send: turn on Bluetooth and allow Nearby devices.", "Hindi maipadala: buksan ang Bluetooth at payagan ang Nearby devices.")
    val onWay = t("Sent. Their phone shows \"Help is on the way\".", "Naipadala. Makikita nila ang \"Paparating ang tulong\".")
    val ringing = t("Ringing their phone for 30 s. At low battery it can take up to a minute to start.",
        "Pinatutunog ang phone nila nang 30 s. Kung mahina ang baterya, baka abutin ng isang minuto bago magsimula.")
    fun go(code: Code, ok: String) { sent = if (Beacon.command(ctx, code, victim)) ok else failed }

    Filled(if (victim in Beacon.onTheWay) t("On my way ✓ (send again)", "Papunta na ✓ (ipadala ulit)") else t("On my way", "Papunta na ako")) { go(Code.ACK, onWay) }
    OutlinedButton(
        onClick = { go(Code.RING, ringing) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = Btn,
        border = BorderStroke(2.dp, c.onSurface),
    ) { Text(t("Ring phone", "Patunugin ang phone"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
    // Rescued stops the victim's only signal, so it takes a second tap within 3 s.
    OutlinedButton(
        onClick = { if (confirm) { confirm = false; go(Code.RESC, "") } else confirm = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = Btn, border = BorderStroke(2.dp, c.onSurface),
    ) {
        Text(
            if (confirm) t("Tap again, then tap Yes on their phone", "Pindutin ulit, tapos Oo sa phone nila") else t("Rescued", "Nailigtas na"),
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
    sent?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

// Victim side of ACK / RING / RESC, on every screen. "Help is on the way" is the payoff: a big flat green card above
// the content, never an overlay, so a CPR step underneath is never hidden.
@Composable
fun VictimBanners() {
    val lang = LocalLang.current
    val help = t("Help is on the way", "Paparating na ang tulong")
    LaunchedEffect(Beacon.acked) { if (Beacon.acked) Voice.speak(help, lang) }
    if (Beacon.acked) Row(
        Modifier.fillMaxWidth().background(Color(0xFF0F5B36), Btn).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$help.", fontFamily = Head, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(t("A rescuer heard your beacon. Stay where you are.", "Narinig ng rescuer ang beacon mo. Manatili ka riyan."), fontSize = 16.sp, lineHeight = 21.sp, color = Color(0xFFC9F0DA))
        }
        Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
    if (Alert.ringing) Filled(
        t("A rescuer is ringing this phone · tap to stop", "Pinatutunog ng rescuer ang phone · pindutin para ihinto"),
        size = 18.sp, container = Amber, ink = Color(0xFF2A1D00),
    ) { Alert.stop() }
}

// RESC arrived: the beacon keeps sending until someone here answers. A rescue means contact, so the rescuer who
// tapped "Rescued" is next to this phone; a wrong tap on someone who can't answer renews the SOS by itself.
@Composable
fun ColumnScope.ConfirmRescue(onYes: () -> Unit, onNo: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val now = rememberNow()
    val left = ((Beacon.RESCUE_WAIT_MS - (now - Beacon.rescueAt)) / 1000).coerceAtLeast(0)
    val ask = t("A rescuer marked you as rescued. Is a rescuer with you right now?", "Minarkahan ka ng rescuer na nailigtas. May rescuer ba sa tabi mo ngayon?")
    LaunchedEffect(Unit) { Voice.speak(ask, lang) }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Kicker(t("Your SOS is still on", "Naka-on pa ang SOS mo"), color = c.tertiary)
        Text(ask, fontFamily = Head, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            t("Your SOS stays on until you answer. No answer in ${left / 60}:%02d keeps it on.".format(left % 60),
                "Naka-on pa rin ang SOS mo hanggang sumagot ka. Kapag walang sagot sa loob ng ${left / 60}:%02d, mananatili itong naka-on.".format(left % 60)),
            fontSize = 18.sp, lineHeight = 24.sp, color = c.onSurfaceVariant,
        )
    }
    Filled(t("Yes, a rescuer is with me", "Oo, may rescuer na sa tabi ko"), min = 72.dp, onClick = onYes)
    val still = t("Your SOS is still on.", "Naka-on pa rin ang SOS mo.")
    Ghost(t("No, I still need help", "Hindi, kailangan ko pa ng tulong"), min = 72.dp) { onNo(); Voice.speak(still, lang) }
}

// Confirmed rescued (mock V16): the conversation is over, with one clear way back to a working SOS.
@Composable
fun ColumnScope.Rescued(onRestart: () -> Unit, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val say = t("You're marked as rescued. Your beacon has stopped. If you still need help, tap restart SOS.",
        "Minarkahan kang nailigtas. Huminto na ang beacon mo. Kung kailangan mo pa ng tulong, pindutin ang i-SOS ulit.")
    LaunchedEffect(Unit) { Voice.speak(say, lang) }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(80.dp).background(SafeGreen.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 40.sp, fontWeight = FontWeight.Black, color = SafeGreen)
        }
        Pill(t("Beacon stopped", "Huminto ang beacon"), c.onSurfaceVariant)
        Text(t("You're marked as rescued", "Minarkahan kang nailigtas"), fontFamily = Head, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            t("Your beacon has stopped so rescuers can reach others.", "Itinigil na ang beacon mo para makapunta ang rescuers sa iba."),
            fontSize = 19.sp, lineHeight = 26.sp, color = c.onSurfaceVariant,
        )
    }
    val back = t("Your SOS is back on.", "Naka-on na ulit ang SOS mo.")
    Filled(t("I still need help · restart SOS", "Kailangan ko pa ng tulong · i-SOS ulit"), min = 72.dp) { onRestart(); Voice.speak(back, lang) }
    Ghost(t("Done", "Tapos na"), min = 56.dp, size = 18.sp, onClick = onDone)
}

// ---- Survival mode (D7): the screen behind the black layer, shown on a tap ----

fun Context.battery(): Sample = getSystemService(BatteryManager::class.java).let {
    Sample(SystemClock.elapsedRealtime(), it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER), it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
}

// Survival mode keeps listening while the battery allows (knob): the waiting mic holds the screen on (black, near-zero
// brightness), which costs more than letting it sleep. Below this the screen sleeps and only a tap wakes it.
const val STANDBY_MIN_PCT = 30

// A pinned victim can't tap the black screen: once speech is heard, onHeard gets it (back to the conversation, which
// handles it as if said there). Needs Whisper + the VAD, so noise doesn't wake Whisper all night; null = no listening.
@Composable
fun SurvivalListener(pct: Int, charging: Boolean, onHeard: ((String) -> Unit)?) {
    val ctx = LocalContext.current
    val on = onHeard != null && Voice.whisperReady && Voice.vadReady && (charging || pct > STANDBY_MIN_PCT) &&
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val view = LocalView.current
    DisposableEffect(on) { if (on) view.keepScreenOn = true; onDispose { if (on) view.keepScreenOn = false } }
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        var fails = 0
        while (fails < 3) { // a broken mic stops trying; the tap still works
            val (said, failed) = suspendCancellableCoroutine { k ->
                Voice.listen(ctx, standby = true) { s, f -> if (k.isActive) k.resume(s to f) }
                k.invokeOnCancellation { Voice.cancel() }
            }
            if (said != null) return@LaunchedEffect onHeard!!(said)
            fails = if (failed) fails + 1 else 0
            delay(if (failed) 2_000L * fails else 300)
        }
    }
    if (on) Text(t("Speak any time. Saklolo is listening.", "Magsalita kahit kailan. Nakikinig ang Saklolo."), fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ColumnScope.Survival(onExit: () -> Unit, onHeard: ((String) -> Unit)? = null) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    // Drain is measured from entering survival mode (ACK/RING scan windows included, since the service runs them).
    val start = remember { ctx.battery() }
    var now by remember { mutableStateOf(start) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = ctx.battery()
            Log.d("Saklolo", "drain: $start -> $now, ${hoursLeft(start, now)} h left")
        }
    }
    val charging = ctx.getSystemService(BatteryManager::class.java).isCharging
    val left = hoursLeft(start, now)
    val mins = (now.atMs - start.atMs) / 60_000
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Kicker(t("Survival mode", "Survival mode"), color = c.tertiary)
        // The number is the headline: how long rescuers can still hear this phone.
        if (!charging && left != null) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("~${left.roundToInt()}", fontFamily = Head, fontSize = 88.sp, lineHeight = 88.sp, fontWeight = FontWeight.Black)
            Text(t("hours left", "oras pa"), Modifier.padding(bottom = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
        }
        Text(
            when {
                charging -> t("Charging", "Nagcha-charge")
                left != null -> t("Your beacon will keep sending this long.", "Ganito katagal pa magpapadala ang beacon mo.")
                else -> t("Measuring battery use…", "Sinusukat ang gamit ng baterya…")
            },
            fontFamily = Head, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp,
        )
        Kicker(t("Measured on this phone · $mins min · battery ${now.pct}%", "Sinukat sa phone na ito · $mins min · baterya ${now.pct}%"))
        BeaconLine()
        SurvivalListener(now.pct, charging, onHeard)
        Text(t("The screen goes dark to save battery. Tap anywhere to see this again.","Didilim ang screen para makatipid. Pindutin kahit saan para makita ulit."), fontSize = 18.sp, lineHeight = 24.sp, color = c.onSurfaceVariant)
        if (now.pct in 0..30) Text(
            t("Low battery: a rescuer's ring can take up to a minute to reach you.", "Mahina ang baterya: baka abutin ng isang minuto bago marinig ang ring ng rescuer."),
            fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = Amber,
        )
    }
    Ghost(t("Leave survival mode", "Umalis sa survival mode"), size = 18.sp, onClick = onExit)
}

// ---- Eval (D13): every set in assets/eval/, dictionary vs model, on this phone ----

// rules = what the app does without the model (its fallback); dict = the frozen keyword baseline. frozen = never tuned on.
data class EvalRow(val name: String, val n: Int, val dict: Int, val model: Int?, val target: String, val pass: Boolean?, val medianMs: Long?, val note: String = "",
                   val rules: Int = dict, val frozen: Boolean = false)

// Blocking; call off the main thread. model/pass are null when the model isn't loaded.
fun runEval(ctx: Context, protocols: Protocols, progress: (String) -> Unit): List<EvalRow> {
    fun set(f: String) = JSONArray(ctx.assets.open("eval/$f").bufferedReader().use { it.readText() }).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    val on = Llm.state == Llm.State.READY
    val ms = mutableListOf<Long>()
    fun <T> timed(f: () -> T): T { val t0 = SystemClock.uptimeMillis(); return f().also { ms += SystemClock.uptimeMillis() - t0 } }
    fun median(): Long? = ms.sorted().getOrNull(ms.size / 2).also { ms.clear() }
    val rows = mutableListOf<EvalRow>()

    // A report is right only if the whole record matches (hazard, headcount, trapped, floor, spot, vulnerable, conditions).
    fun extraction(name: String, cases: List<JSONObject>, target: String, frozen: Boolean = false, ok: (model: Int, dict: Int) -> Boolean) {
        progress(name)
        val want = cases.map { validate(it.getJSONObject("expect").toString())!! }
        val text = cases.map { it.getString("text") }
        val base = text.map(Dictionary::baseline)
        val rule = text.map(Dictionary::extract)
        val aiDebug = if (on) text.map { timed { Llm.extract(it) } } else null
        val ai = aiDebug?.map { it.first }
        val d = text.indices.count { base[it] == want[it] }
        val r = text.indices.count { rule[it] == want[it] }
        val m = ai?.let { text.indices.count { i -> it[i] == want[i] } }
        text.indices.forEach { i ->
            Log.d("SakloloEval", "$name #$i [${cases[i].optString("lang")}] base=${base[i] == want[i]} rules=${rule[i] == want[i]} ai=${ai?.get(i) == want[i]} \"${text[i]}\"" +
                "\n  want=${want[i]}\n  rules=${rule[i]}" + (aiDebug?.let { "\n  ai=${it[i].first}\n  " + it[i].second.replace("\n", "\n  ") } ?: ""))
        }
        val med = median()
        // Per field, rules alone vs the AI alone: shows where the AI helps even when a whole record misses.
        val alone = if (on) text.map { Llm.understand(it)?.first } else null
        fun pct(got: List<Facts?>, f: (Facts) -> Any?) = got.indices.count { got[it]?.let(f) == f(want[it]) } * 100 / cases.size
        val fields = listOf<Pair<String, (Facts) -> Any?>>("hazard" to { it.hazard }, "trapped" to { it.trapped }, "injuries" to { it.cond }, "vulnerable" to { it.vuln })
        val perField = fields.joinToString("\n") { (f, get) -> "$f: rules ${pct(rule, get)}%" + (alone?.let { " · AI ${pct(it, get)}%" } ?: "") }
        rows += EvalRow(name, cases.size, d, m, target, m?.let { ok(it, d) && med!! < 5000 }, med, perField, rules = r, frozen = frozen)
    }
    // Reports by other people are scored apart: nothing was tuned on them, so they're the honest number.
    val (mine, others) = set("extract.json").partition { it.optString("by") != "others" }
    extraction("Extraction (tuning set)", mine, "≥ 8, beats dictionary by ≥ 3, < 5 s") { m, d -> m >= 8 && m - d >= 3 }
    if (others.isNotEmpty()) extraction("Extraction (written by others)", others, "≥ 80%, < 5 s") { m, _ -> m * 10 >= others.size * 8 }
    // Frozen sets nothing was tuned on: real #RescuePH tweets (CrisisLexT26, anonymized) and hard cases.
    val real = set("real.json")
    extraction("Real #RescuePH tweets", real, "beats rules, < 5 s", frozen = true) { m, _ -> true }
    val hard = set("hard.json")
    extraction("Hard cases", hard, "beats rules, < 5 s", frozen = true) { m, _ -> true }

    progress("Unclear answers")
    val ans = set("answers.json")
    val wantAns = ans.map { Ans.valueOf(it.getString("expect")) }
    val dAns = ans.map { Dictionary.answer(it.getString("a")) }
    // What the app does with the model: combine() keeps only answers the dictionary agrees with.
    val rawAns = if (on) ans.map { c -> timed { Llm.answer(c.getString("q"), c.getString("a")) } } else null
    val mAns = rawAns?.mapIndexed { i, r -> combine(r, dAns[i]) }
    fun right(g: List<Ans>) = g.indices.count { g[it] == wantAns[it] }
    fun noAsYes(g: List<Ans>) = g.indices.count { wantAns[it] == Ans.NO && g[it] == Ans.YES }
    fun saferAll(g: List<Ans>) = g.indices.all { wantAns[it] != Ans.UNSURE || g[it] == Ans.UNSURE }
    ans.indices.forEach { Log.d("SakloloEval", "answer #$it \"${ans[it].getString("a")}\" want=${wantAns[it]} dict=${dAns[it]} model=${rawAns?.get(it)} combined=${mAns?.get(it)}") }
    val medAns = median()
    rows += EvalRow(
        "Unclear answers", ans.size, right(dAns), mAns?.let(::right), "every unclear one safer, 0 \"no\" read as \"yes\", < 2 s",
        mAns?.let { saferAll(it) && noAsYes(it) == 0 && medAns!! < 2000 }, medAns,
        "\"no\" read as \"yes\": dictionary ${noAsYes(dAns)}" + (mAns?.let { ", model ${noAsYes(it)}" } ?: ""),
    )

    progress("Myth-buster")
    val myths = set("myths.json")
    val faqs = myths.map { protocols.all.getValue(it.getString("protocol")).faqs }
    val wantFaq = myths.map { it.getString("expect") }
    val dFaq = myths.indices.map { Dictionary.matchFaq(myths[it].getString("q"), faqs[it]) }
    val mFaq = if (on) myths.indices.map { timed { Llm.matchFaq(myths[it].getString("q"), faqs[it]) } ?: NO_FAQ } else null
    fun hit(g: List<String>) = g.indices.count { g[it] == wantFaq[it] }
    fun wrong(g: List<String>) = g.indices.count { g[it] != wantFaq[it] && g[it] != NO_FAQ } // NONE is never wrong
    myths.indices.forEach { Log.d("SakloloEval", "myth #$it \"${myths[it].getString("q")}\" want=${wantFaq[it]} dict=${dFaq[it]} ai=${mFaq?.get(it)}") }
    val medFaq = median()
    rows += EvalRow(
        "Myth-buster", myths.size, hit(dFaq), mFaq?.let(::hit), "≥ 8, 0 wrong, < 2 s",
        mFaq?.let { hit(it) >= 8 && wrong(it) == 0 && medFaq!! < 2000 }, medFaq,
        "wrong answers: dictionary ${wrong(dFaq)}" + (mFaq?.let { ", model ${wrong(it)}" } ?: ""),
    )
    rows.forEach { Log.d("SakloloEval", "ROW $it") }
    return rows
}

@Composable
fun ColumnScope.Eval(protocols: Protocols) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<EvalRow>?>(null) }
    var running by remember { mutableStateOf<String?>(null) }
    Column {
        Text("Eval", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(t("RUN ON THIS PHONE · ${Llm.name} ${Llm.state}", "PINATAKBO SA PHONE NA ITO · ${Llm.name} ${Llm.state}"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
    }
    Button(
        onClick = {
            running = "…"
            scope.launch {
                rows = withContext(Dispatchers.Default) { runEval(ctx, protocols) { running = it } }
                running = null
            }
        },
        enabled = running == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
    ) { Text(running?.let { t("Running: $it", "Tumatakbo: $it") } ?: t("Run all sets", "Patakbuhin lahat"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows?.forEach { r ->
            Column(
                Modifier.fillMaxWidth().border(1.5.dp, c.outline, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${r.name} · n=${r.n}", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    if (r.frozen) Pill(t("Never trained on", "Hindi pinag-aralan"), c.tertiary)
                }
                // Same scale for both bars: what the app does without the model, then with it.
                @Composable fun bar(label: String, v: Int?, color: Color) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, Modifier.width(96.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.weight(1f).height(18.dp).background(c.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
                        if (v != null && v > 0) Box(Modifier.fillMaxWidth(v / r.n.toFloat()).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
                    }
                    Text(v?.let { "${it * 100 / r.n}%" } ?: "—", Modifier.width(48.dp), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                }
                bar(t("Rules", "Rules"), r.rules, c.onSurfaceVariant)
                bar(t("Rules + AI", "Rules + AI"), r.model, Amber)
                Text(
                    t("Keyword baseline", "Keyword baseline") + " ${r.dict}/${r.n}" + (r.medianMs?.let { " · " + t("median %.1f s", "median %.1f s").format(it / 1000.0) } ?: ""),
                    fontSize = 13.sp, color = c.onSurfaceVariant,
                )
                if (r.note.isNotEmpty()) Text(r.note, fontSize = 13.sp, color = c.onSurfaceVariant)
                Text(
                    t("TARGET", "TARGET") + " ${r.target} · " + when (r.pass) { true -> "PASS"; false -> "FAIL"; null -> t("NO MODEL", "WALANG MODEL") },
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant,
                )
            }
        }
    }
}
