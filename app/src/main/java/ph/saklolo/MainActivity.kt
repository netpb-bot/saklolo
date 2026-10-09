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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Hinahon tokens: victim dark (Saklolo 1 Language and Tokens.dc.html)
private val VictimDark = darkColorScheme(
    primary = Color(0xFF9CC2FF), onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF12407F), onPrimaryContainer = Color(0xFFD9E6FF),
    background = Color(0xFF000000), surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF12161B), surfaceContainerHigh = Color(0xFF1C2128),
    onSurface = Color(0xFFF1F3F6), onSurfaceVariant = Color(0xFFB9C1CC),
    outline = Color(0xFF5B6573), outlineVariant = Color(0xFF2E353E),
    error = Color(0xFFFF7A73), // 911 only
)

// Rescuer · Sunlight: pure white, black ink, outlines not shadows (tokens mock).
private val RescuerLight = lightColorScheme(
    primary = Color(0xFF002D72), onPrimary = Color.White,
    primaryContainer = Color(0xFFC9DBFF), onPrimaryContainer = Color(0xFF001233),
    secondaryContainer = Color(0xFFC9DBFF), onSecondaryContainer = Color(0xFF001233),
    background = Color.White, surface = Color.White,
    surfaceContainer = Color(0xFFEDEFF2), surfaceContainerHigh = Color(0xFFDDE1E6),
    onSurface = Color.Black, onSurfaceVariant = Color(0xFF1F252C),
    outline = Color.Black, outlineVariant = Color(0xFF1F252C),
    error = Color(0xFFB3141B), // 911 only
)

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

    // Injected as if spoken: same model, validator and dictionary path, only the mic is skipped.
    const val REPORT = "Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo. Tatlo kami, ay hindi, apat pala, " +
        "kasama si lola. Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!"
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
                facts = f(old)
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
                Demo.base = Dictionary.baseline(said); Demo.rules = dict; Demo.result = null
                val at = q
                busy = true
                lifecycleScope.launch {
                    val (model, raw) = withContext(Dispatchers.Default) { Llm.extract(said) }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        t("Report", "Ulat") to { inject(Demo.REPORT) },
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
            MaterialTheme(colorScheme = if (rescuing) RescuerLight else VictimDark) {
                CompositionLocalProvider(LocalLang provides lang) {
                    Shell(lang, onLang = { lang = it; prefs.edit().putString("lang", it.name).apply() }, menu) {
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
                                onActive = { lastTouch = SystemClock.elapsedRealtime() }, onSafe = ::safe,
                            ) else Home(
                                sos, onSos = { sos = it; if (it == Sos.CONFIRMED && asked.isEmpty()) nextQuestion() },
                                onSafe = ::safe,
                            )
                            Screen.BOARD -> Board(onOpen = { openId = it; screen = Screen.DETAIL })
                            Screen.DETAIL -> Detail(Beacon.heard[openId], onBack = { screen = Screen.BOARD })
                            // Speech wakes it only after SOS: inject() would otherwise confirm an SOS nobody sent.
                            Screen.SURVIVAL -> Survival(onExit = { screen = Screen.HOME }, onHeard = if (sos == Sos.CONFIRMED) { said -> lastTouch = SystemClock.elapsedRealtime(); inject(said) } else null)
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
fun Shell(lang: Lang, onLang: (Lang) -> Unit, menu: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("DEMO · not medically reviewed", "DEMO · hindi pa nasuri ng doktor"),
                    Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { Demo.on = !Demo.on }) },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow {
                    Lang.entries.forEachIndexed { i, l ->
                        SegmentedButton(
                            selected = lang == l, onClick = { onLang(l) },
                            shape = SegmentedButtonDefaults.itemShape(i, Lang.entries.size),
                            icon = {},
                        ) { Text(l.name) }
                    }
                }
            }
            if (Demo.on) DemoPanel(menu)
            VictimBanners()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(t("Call 911", "Tumawag sa 911"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("  " + t("if there's signal", "kung may signal"), fontSize = 14.sp)
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

// Keywords vs Saklolo for the last report (the demo beat), sized to read on a projector.
@Composable
fun Compare(base: Facts, rules: Facts?, result: Facts?) {
    val c = MaterialTheme.colorScheme
    val names = listOf(t("What", "Ano"), t("People", "Ilan"), t("Trapped", "Naiipit"), t("Where", "Saan"), t("Who", "Sino"), t("Hurt", "Pinsala"))
    val k = factRows(base)
    val s = factRows(result)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("", Modifier.width(64.dp))
        Text(t("Keywords", "Keywords"), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
        Text("Saklolo", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Amber)
        result?.let { triage(it) }?.let { tri ->
            Text(
                tri.name, Modifier.background(TriBg.getValue(tri), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                color = if (tri == Tri.RED) Color.White else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
    }
    names.indices.forEach { i ->
        val diff = result != null && k[i] != s[i]
        Row(Modifier.fillMaxWidth().background(if (diff) Amber.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(6.dp)).padding(vertical = 2.dp)) {
            Text(names[i], Modifier.width(64.dp).padding(start = 4.dp), fontSize = 13.sp, color = c.onSurfaceVariant)
            Text(k[i], Modifier.weight(1f), fontSize = 15.sp, color = if (diff) c.onSurfaceVariant else c.onSurface)
            Text(if (result == null) "…" else s[i], Modifier.weight(1f), fontSize = 15.sp, fontWeight = if (diff) FontWeight.ExtraBold else FontWeight.Normal)
        }
    }
    // Honest credit: only what the model found beyond the rules counts as AI.
    if (result != null && rules != null) Text(
        if (Llm.state != Llm.State.READY) t("AI not loaded · rules only", "Walang AI · rules lang")
        else (listOfNotNull(result.hazard?.takeIf { rules.hazard == null }?.let { hazardLabel(it) }) +
            (result.cond - rules.cond).map { condLabel(it) }).joinToString(", ").ifEmpty { null }?.let { t("AI added: $it", "Dinagdag ng AI: $it") }
            ?: t("AI added nothing · rules covered it", "Walang dinagdag ang AI · sakop ng rules"),
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Amber,
    )
}

@Composable
fun DemoPanel(menu: @Composable () -> Unit) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    var scores by remember { mutableStateOf(false) }
    // Bounded so the screen underneath and Call 911 always keep their space.
    Column(
        Modifier.fillMaxWidth().heightIn(max = 300.dp).border(1.dp, Amber, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Llm.name} · on device · ${Llm.state}", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber)
            if (Demo.ai.isNotEmpty()) Text(
                if (scores) t("Scores ▾", "Scores ▾") else t("Scores ▸", "Scores ▸"),
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { scores = !scores }.padding(horizontal = 8.dp, vertical = 6.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber,
            )
        }
        menu()
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Demo.base?.let { Compare(it, Demo.rules, Demo.result) }
        if (scores) Text(Demo.ai, fontSize = 11.sp, fontFamily = mono, lineHeight = 14.sp)
        Beacon.bytes?.let { b ->
            Text("beacon · ${b.size} bytes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                hexFields(b).forEachIndexed { i, (name, hex) ->
                    Column(Modifier.background(Cells[i % Cells.size], RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(hex, fontSize = 11.sp, fontFamily = mono, color = Color.Black)
                        Text(name, fontSize = 9.sp, color = Color.Black)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun ColumnScope.Home(sos: Sos, onSos: (Sos) -> Unit, onSafe: () -> Unit) {
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
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
            Sos.CANCELLING -> CancelBar(onDone = { onSos(Sos.CONFIRMED) }, onCancel = { onSos(Sos.IDLE) })
            Sos.CONFIRMED, Sos.RESCUED -> {} // the chat or the Rescued screen takes over
        }
    }
    ModelRow()
    if (sos == Sos.IDLE) OutlinedButton(
        onClick = onSafe,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(t("I'm safe", "Ligtas ako"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
}

// Hold 1.5 s to confirm; releasing early snaps the ring back.
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
        Modifier.size(248.dp).clip(CircleShape).background(c.outlineVariant)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressing = true; tryAwaitRelease(); pressing = false }) },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator({ fill }, Modifier.fillMaxSize(), color = c.onSurface, strokeWidth = 8.dp, trackColor = Color.Transparent)
        // Dark fill, light ink: on OLED a 224 dp light-blue disc is the biggest drain on this screen.
        Column(
            Modifier.size(224.dp).clip(CircleShape).background(c.primaryContainer).border(3.dp, c.primary, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("SOS", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
            Text(t("Press and hold", "Pindutin nang matagal"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer)
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
    Text(t("Sending SOS…", "Ipinapadala ang SOS…"), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
    LinearProgressIndicator({ left.value }, Modifier.fillMaxWidth().height(8.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp)) {
        Text(t("Cancel", "Kanselahin"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
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
// Hands-free (from the first trigger on): a pinned victim may never reach the screen again, so the mic waits for speech
// with no time limit and reopens by itself after silence, noise or a reply. Only a broken mic (3 times in a row, with a
// growing pause) gives up and calls onMiss, so the screen offers typing. The screen stays on meanwhile: Android
// silences the mic of an app in the background.
@Composable
fun MicButton(label: String, enabled: Boolean = true, trigger: Int = 0, onMiss: () -> Unit = {}, onText: (String) -> Unit) {
    val ctx = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(false) }
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
            when {
                // The reply's spoken line reopens the mic; this is the fallback if no line follows.
                said != null -> { onText(said); if (handsFree) later(10_000) }
                handsFree && fails >= 3 -> { handsFree = false; missed = true; onMiss() }
                handsFree -> later(if (failed) 2_000L * fails else 300)
                else -> { missed = true; onMiss() }
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) hear(handsFree) else { denied = true; missed = false; handsFree = false; onMiss() } }
    var asked by rememberSaveable { mutableStateOf(false) }
    // Each new trigger (Saklolo finished speaking) starts or keeps the waiting mic. The first one asks for the mic
    // right after SOS, so hands-free works without ever touching the mic button.
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        handsFree = true
        if (granted()) hear(standby = true) else if (!asked) { asked = true; askMic.launch(Manifest.permission.RECORD_AUDIO) }
    }
    LaunchedEffect(reopen) { if (reopen.first > 0) { delay(reopen.second); if (handsFree && granted()) hear(standby = true) } }
    val view = LocalView.current
    DisposableEffect(handsFree) { if (handsFree) view.keepScreenOn = true; onDispose { if (handsFree) view.keepScreenOn = false } }
    DisposableEffect(Unit) { onDispose { Voice.cancel() } } // leaving the screen closes the mic; no late result
    // Always on screen after SOS, so a dark container rather than a full-width light fill.
    Button(
        onClick = {
            if (granted()) hear(standby = false)
            else askMic.launch(Manifest.permission.RECORD_AUDIO)
        },
        enabled = enabled && !listening && !Alert.ringing, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(
            if (listening) t("Listening… speak now", "Nakikinig… magsalita na") else label,
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        )
    }
    if (denied) Text(t("The microphone is off. Use the buttons or type it.", "Naka-off ang mikropono. Gamitin ang mga button o i-type."), fontSize = 16.sp)
    if (missed) Text(t("I didn't catch that. Tap the mic, use the buttons, or type it.", "Hindi ko narinig. Pindutin ang mic, gamitin ang mga button, o i-type."), fontSize = 16.sp)
}

data class FollowUpQ(val n: Int, val id: String, val text: Txt, val hazard: Hazard?)

// Follow-up question (mocks V7, V9). Every question carries its own answer buttons, so a victim who can't or won't
// speak is never stuck. On the protocol screen it waits below the step: "answer when you can".
@Composable
fun FollowUp(f: FollowUpQ, waiting: Boolean, onAns: (Ans) -> Unit, onFacts: ((Facts) -> Facts) -> Unit, onSkip: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().border(2.dp, if (waiting) c.outline else c.primary, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (waiting) t("Answer when you can", "Sagutin kapag kaya mo") else t("Question ${f.n} of $MAX_FOLLOWUPS", "Tanong ${f.n} sa $MAX_FOLLOWUPS"),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.primary,
        )
        Text(f.text.of(LocalLang.current), fontSize = if (waiting) 22.sp else 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
        Text(t("Say it, or tap one.", "Sabihin, o pindutin ang isa."), fontSize = 16.sp, color = c.onSurfaceVariant)
        when (f.id) {
            "exit" -> Answers(null, onAns)
            // No Skip: "Something else" is the way out, so the hazard is never left unknown.
            "what" -> listOf(
                listOf(Hazard.COLLAPSE to t("Collapse", "Gumuho"), Hazard.FLOOD to t("Flood", "Baha")),
                listOf(Hazard.LANDSLIDE to t("Landslide", "Landslide"), Hazard.OTHER to t("Something else", "Iba pa")),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (h, label) -> Big(label, Modifier.weight(1f)) { onFacts { it.copy(hazard = h) } } }
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
                OutlinedButton(onClick = onSkip, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(t("No one is hurt", "Walang nasaktan"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
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
                OutlinedButton(onClick = onSkip, Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(t("Skip", "Laktawan"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> OutlinedButton(onClick = onSkip, Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                Text(t("Skip", "Laktawan"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
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
fun SurviveButton(onClick: () -> Unit) = OutlinedButton(
    onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp),
) { Text(t("Save battery", "Tipirin ang baterya"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }

// Triage colours live outside the M3 scheme and only ever mean triage (tokens mock).
private val TriBg = mapOf(Tri.RED to Color(0xFFC9252C), Tri.YELLOW to Color(0xFFFFC53D), Tri.GREEN to Color(0xFF2FB36A))

// Answers "is my SOS out?" first, in words. A beacon that isn't running is the worst silent failure, so that state
// says NOT and carries the one button that fixes it. Hidden once answered or stopped: VictimBanners says those.
@Composable
fun SosStrip() {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    if (Beacon.acked || Beacon.state == Beacon.State.STOPPED) return
    val bt = Beacon.state == Beacon.State.NO_BLUETOOTH
    val off = bt || Beacon.state == Beacon.State.NO_PERMISSION
    val ink = if (off) c.onSurface else c.onPrimaryContainer
    Column(
        Modifier.fillMaxWidth()
            .background(if (off) c.surface else c.primaryContainer, RoundedCornerShape(16.dp))
            .border(3.dp, if (off) c.onSurface else c.primaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            off -> {
                Text(t("Your SOS is NOT on", "HINDI pa naka-on ang SOS mo"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                Text(
                    if (bt) t("Turn on Bluetooth so rescuers can find you.", "Buksan ang Bluetooth para mahanap ka ng rescuers.")
                    else t("Allow \"Nearby devices\" so rescuers can find you.", "Payagan ang \"Nearby devices\" para mahanap ka ng rescuers."),
                    fontSize = 18.sp, lineHeight = 24.sp, color = ink,
                )
                Button(
                    onClick = {
                        ctx.startActivity(
                            if (bt) Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp),
                ) { Text(if (bt) t("Turn on Bluetooth", "Buksan ang Bluetooth") else t("Open settings", "Buksan ang settings"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Beacon.state == Beacon.State.OFF ->
                Text(t("Starting your SOS…", "Sinisimulan ang SOS mo…"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ink)
            else -> {
                Text(t("Your SOS is on", "Naka-on ang SOS mo"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                Text(
                    t("Rescuers nearby can find you, even with no signal. No one has answered yet.",
                        "Mahahanap ka ng rescuers sa malapit, kahit walang signal. Wala pang sumasagot."),
                    fontSize = 18.sp, lineHeight = 24.sp, color = ink,
                )
            }
        }
    }
}

// What went out in the beacon, in words, known facts only. Unknowns are asked by the question card, not shown as "?".
@Composable
fun Sent(f: Facts) {
    val parts = listOfNotNull(
        f.hazard?.let { hazardLabel(it) },
        f.people.takeIf { it > 0 }?.let { peopleLabel(it) },
        f.trapped?.let { if (it) t("Trapped", "Naiipit") else t("Can get out", "Makakalabas") },
        place(f.floor, f.spot).ifEmpty { null },
    ) + f.vuln.sorted().map { vulnLabel(it) } + f.cond.sorted().map { condLabel(it) }
    if (parts.isEmpty()) return
    Text(
        t("Rescuers see: ", "Nakikita ng rescuers: ") + parts.joinToString(" · "),
        fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        Text(line, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (ok) c.primary else c.onSurface)
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
fun Big(label: String, modifier: Modifier = Modifier, on: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp), // five count buttons share one 360 dp row
        border = BorderStroke(2.dp, if (on) c.onPrimaryContainer else c.outline),
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
    Column(Modifier.fillMaxWidth().border(2.dp, c.outlineVariant, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        m.label?.let { Text(it.of(lang), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary) }
        Text(m.text.of(lang), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    }
}

// Only the current step, sized for arm's length (CPR runs for minutes with the phone on the floor). Past and future
// steps are not shown: one thing at a time.
@Composable
fun StepCard(proto: Protocol, n: Int, cur: Node, aloud: Boolean) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxWidth().border(2.dp, c.primary, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            proto.title.of(lang) + " · " + t("Step $n", "Hakbang $n") + if (aloud) " · " + t("reading aloud", "binabasa") else "",
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.primary,
        )
        Text(cur.text.of(lang), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
        cur.detail?.let { Text(it.of(lang), fontSize = 20.sp, lineHeight = 26.sp) }
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
    val node = id?.let { proto?.node(it) }
    // Like a dispatcher: one safety tip first, then the questions, then the rest of the steps.
    // CPR and bleeding control never pause for questions.
    var tipGiven by remember(plan) { mutableStateOf(false) }
    val lifeThreat = plan.any { it.first.id == "NOT_BREATHING" || it.first.id == "BLEEDING" }
    val asking = followUp != null && tipGiven && !lifeThreat
    fun go(next: String?) {
        last = null // a new step clears the old reply; callers set the "I understood" line after this
        tipGiven = true
        id?.let { done = done + it }
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
    LaunchedEffect(Demo.inject) { Demo.inject?.let { Demo.inject = null; handle(it) } }

    val scroll = rememberScrollState()
    // New content goes to the top now (no log to scroll), so jump back up to the step.
    LaunchedEffect(last, node, followUp) { scroll.scrollTo(0) }
    // Fixed order, one main card: is my SOS out → what rescuers see → what I do now (step, else question, else rest).
    Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SosStrip()
        Sent(facts)
        last?.let { Reply(it) }
        if (proto != null && node != null && !asking) {
            StepCard(proto, done.size + 1, node, aloud)
            if (node.isQuestion) Answers(null) { a -> stepAnswer(node, a) }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { replay++ }, modifier = Modifier.heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, c.outline),
                ) { Text(t("Repeat", "Ulitin"), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                // CPR keeps the screen lit for minutes: dark fill, light border and ink.
                Button(
                    onClick = { go(node.next) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer),
                    border = BorderStroke(2.dp, c.primary),
                ) { Text(t("Next", "Susunod"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
            }
        } else if (followUp == null) Column(
            // Nothing to do but wait: the only action offered is saving battery.
            Modifier.fillMaxWidth().border(2.dp, c.outline, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text((if (facts.hazard == null) GREETING else STAY).of(lang), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
            SurviveButton(onSurvive)
        }
        // Follow-ups wait below the first step, and always below a life-threat protocol.
        followUp?.let { FollowUp(it, waiting = node != null && !asking,onAns = { a -> onExit(a, false) }, onFacts = onFacts, onSkip = onSkip) }
        var typed by rememberSaveable { mutableStateOf("") }
        // Typing is the fallback for when voice fails, not a third input to choose from.
        if (!auto || Voice.packs != Voice.Packs.READY) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), placeholder = { Text(t("Or type it here", "O i-type dito")) })
            OutlinedButton(
                onClick = { if (typed.isNotBlank()) { handle(typed); typed = "" } }, Modifier.heightIn(min = 56.dp),
                enabled = !busy && !thinking, shape = RoundedCornerShape(16.dp),
            ) { Text(t("Send", "Ipadala"), fontWeight = FontWeight.Bold) }
        }
        // False alarm or out of danger: SAFE replaces this phone's SOS on rescuer boards (PRD risks). It stops the only
        // signal, so it takes a second tap within 3 s (same pattern as the rescuer's "Rescued").
        var confirmSafe by remember { mutableStateOf(false) }
        LaunchedEffect(confirmSafe) { if (confirmSafe) { delay(3000); confirmSafe = false } }
        TextButton(
            onClick = { if (confirmSafe) onSafe() else confirmSafe = true },
            modifier = Modifier.align(Alignment.End).heightIn(min = 56.dp),
        ) {
            Text(
                if (confirmSafe) t("Tap again: stop my SOS", "Pindutin ulit: ihinto ang SOS ko") else t("I'm safe now", "Ligtas na ako"),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
    // Life threats can start at any moment, so these two stay on screen; hazard and trapped are answered in the card.
    fun flip(x: Cond) = onFacts { it.copy(cond = if (x in it.cond) it.cond - x else it.cond + x) }
    Text(t("Tap if someone is:", "Pindutin kung may:"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(t("Not breathing", "Hindi humihinga"), Cond.NOT_BREATHING in facts.cond) { flip(Cond.NOT_BREATHING) }
        Tile(t("Bleeding badly", "Malakas dumugo"), Cond.HEAVY_BLEEDING in facts.cond) { flip(Cond.HEAVY_BLEEDING) }
    }
    // The mic carries the busy state (no floating status text); buttons stay usable while it's busy.
    MicButton(
        if (busy || thinking) t("Understanding…", "Inuunawa…") else t("Tap to talk", "Pindutin para magsalita"),
        enabled = !busy && !thinking, trigger = listenKey, onMiss = { auto = false },
    ) { auto = true; handle(it) }
}

// Compact tile for the always-visible row (48 dp: thumb-sized but leaves room for the conversation).
@Composable
fun Tile(label: String, on: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, if (on) c.onPrimaryContainer else c.outline), contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) c.onPrimaryContainer else c.surfaceContainerHigh, contentColor = if (on) c.onPrimary else c.onSurface,
        ),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

// Silent when all is well. AI state lives in the demo overlay; SOS doesn't depend on it, so the victim isn't told.
// No progress bar: an indeterminate one redraws every frame.
@Composable
fun ModelRow() {
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
        Modifier.border(2.dp, c.onSurface, RoundedCornerShape(6.dp))
            .background(if (filled) c.onSurface else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (filled) c.surface else c.onSurface, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
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
        Text(t("Calls for help", "Mga humihingi ng tulong"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (Beacon.scanning) t("${ranked.size} BEACONS · SCANNING", "${ranked.size} BEACON · NAG-I-SCAN") + " · " + t("SAFE: ${safeCount(all)}", "LIGTAS: ${safeCount(all)}")
            else t("Can't scan: turn on Bluetooth and allow Nearby devices and Location.", "Hindi makapag-scan: buksan ang Bluetooth at payagan ang Nearby devices at Location."),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant,
        )
    }
    // This phone's own SAFE broadcast runs until stopped here or from the notification.
    if (Beacon.state == Beacon.State.SENDING && Beacon.record?.code == Code.SAFE) { // state is the Compose-observed half
        val ctx = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Sharing that you're safe", "Ibinabahaging ligtas ka"), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { Beacon.stop(ctx, rescued = false) }, shape = RoundedCornerShape(12.dp)) {
                Text(t("Stop", "Ihinto"), fontWeight = FontWeight.Bold)
            }
        }
    }
    if (ranked.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text(t("Listening for beacons…", "Nakikinig para sa mga beacon…"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
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
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 96.dp)
            .border(2.dp, if (silent) c.onSurfaceVariant else c.onSurface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.width(76.dp).fillMaxHeight().background(if (silent) Color.Transparent else TriBg.getValue(r.tri)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                r.tri.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = if (silent) c.onSurface else if (r.tri == Tri.RED) Color.White else Color.Black,
            )
        }
        Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val otw = r.id in Beacon.onTheWay
            if (r.worse || silent || otw) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.worse) Tag(t("GETTING WORSE", "LUMALALA"), filled = true)
                if (otw) Tag(t("ON THE WAY", "PAPUNTA NA"), filled = false)
                if (silent) Tag(t("SILENT · ${ago(now - h.at)}", "TAHIMIK · ${ago(now - h.at)}"), filled = false)
            }
            Text(
                peopleLabel(r.people) + (if (r.trapped) t(" · trapped", " · naiipit") else "") + " · " + codeLabel(r.code),
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            )
            flags(r).ifEmpty { null }?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Text(
                t("REPORTED ${ago(now - reportedAt(h))} · HEARD ${ago(now - h.at)} · ${r.battery}%",
                    "INIULAT ${ago(now - reportedAt(h))} · NARINIG ${ago(now - h.at)} · ${r.battery}%") +
                    (if (r.battery <= LOW_BATTERY) t(" · MAY STOP SOON", " · BAKA MAMATAY NA") else ""),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant,
            )
        }
        Column(Modifier.width(76.dp).padding(8.dp).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            if (me != null && r.lat != NO_FIX) {
                val b = bearingDeg(me.first, me.second, r.lat, r.lon)
                Text(distanceText(distanceM(me.first, me.second, r.lat, r.lon)), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(compass8(b) + if (silent) t(" · last", " · huli") else "", fontSize = 13.sp)
            } else Text(t("No GPS", "Walang GPS"), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, c.onSurface)) {
        Text(t("← Board", "← Listahan"), fontWeight = FontWeight.Bold)
    }
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
            Text(
                r.tri.name, Modifier.background(TriBg.getValue(r.tri), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (r.tri == Tri.RED) Color.White else Color.Black, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(codeLabel(r.code) + " · " + peopleLabel(r.people), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (r.worse) Tag(t("GETTING WORSE", "LUMALALA"), filled = true)
        if (finding) {
            Finder(h, silent, heading, me)
            Actions(r.id)
        }
        Text("“$brief”", fontSize = if (finding) 15.sp else 18.sp, lineHeight = if (finding) 21.sp else 25.sp)
        OutlinedButton(onClick = { Voice.speak(brief, lang) }, shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, c.onSurface)) {
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
        } else Text(t("NO GPS · using signal strength", "WALANG GPS · gamit ang lakas ng signal"), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)

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
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant,
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

    Box(Modifier.fillMaxWidth().height(40.dp).border(2.dp, c.onSurface, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((avg?.let(::level) ?: 0.0).toFloat()).background(c.primary))
    }
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

    Button(
        onClick = { go(Code.ACK, onWay) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp),
    ) { Text(if (victim in Beacon.onTheWay) t("On my way ✓ (send again)", "Papunta na ✓ (ipadala ulit)") else t("On my way", "Papunta na ako"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
    OutlinedButton(
        onClick = { go(Code.RING, ringing) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, c.onSurface),
    ) { Text(t("Ring phone", "Patunugin ang phone"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
    // Rescued stops the victim's only signal, so it takes a second tap within 3 s.
    OutlinedButton(
        onClick = { if (confirm) { confirm = false; go(Code.RESC, "") } else confirm = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, c.onSurface),
    ) {
        Text(
            if (confirm) t("Tap again, then tap Yes on their phone", "Pindutin ulit, tapos Oo sa phone nila") else t("Rescued", "Nailigtas na"),
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
    sent?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

// Victim side of ACK / RING / RESC, on every screen.
@Composable
fun VictimBanners() {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val help = t("Help is on the way", "Paparating na ang tulong")
    LaunchedEffect(Beacon.acked) { if (Beacon.acked) Voice.speak(help, lang) }
    if (Beacon.acked) Text(
        help, Modifier.fillMaxWidth().background(c.primary, RoundedCornerShape(16.dp)).padding(16.dp),
        color = c.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
    )
    if (Alert.ringing) Button(
        onClick = { Alert.stop() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp),
    ) { Text(t("A rescuer is ringing this phone · tap to stop", "Pinatutunog ng rescuer ang phone · pindutin para ihinto"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
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
        Text(ask, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            t("Your SOS stays on until you answer. No answer in ${left / 60}:%02d keeps it on.".format(left % 60),
                "Naka-on pa rin ang SOS mo hanggang sumagot ka. Kapag walang sagot sa loob ng ${left / 60}:%02d, mananatili itong naka-on.".format(left % 60)),
            fontSize = 18.sp, lineHeight = 24.sp, color = c.onSurfaceVariant,
        )
    }
    Button(onClick = onYes, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp)) {
        Text(t("Yes, a rescuer is with me", "Oo, may rescuer na sa tabi ko"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
    val still = t("Your SOS is still on.", "Naka-on pa rin ang SOS mo.")
    OutlinedButton(
        onClick = { onNo(); Voice.speak(still, lang) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, c.onSurface),
    ) { Text(t("No, I still need help", "Hindi, kailangan ko pa ng tulong"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface) }
}

// Confirmed rescued (mock V16): the conversation is over, with one clear way back to a working SOS.
@Composable
fun ColumnScope.Rescued(onRestart: () -> Unit, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val say = t("You're marked as rescued. Your beacon has stopped. If you still need help, tap restart SOS.",
        "Minarkahan kang nailigtas. Huminto na ang beacon mo. Kung kailangan mo pa ng tulong, pindutin ang i-SOS ulit.")
    LaunchedEffect(Unit) { Voice.speak(say, lang) }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(64.dp).border(3.dp, c.onSurface, CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(t("You're marked as rescued", "Minarkahan kang nailigtas"), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            t("Your beacon has stopped so rescuers can reach others.", "Itinigil na ang beacon mo para makapunta ang rescuers sa iba."),
            fontSize = 19.sp, lineHeight = 26.sp,
        )
    }
    val back = t("Your SOS is back on.", "Naka-on na ulit ang SOS mo.")
    Button(
        onClick = { onRestart(); Voice.speak(back, lang) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp),
    ) { Text(t("I still need help · restart SOS", "Kailangan ko pa ng tulong · i-SOS ulit"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
    OutlinedButton(
        onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, c.outline),
    ) { Text(t("Done", "Tapos na"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.onSurface) }
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
        Text(t("SURVIVAL MODE", "SURVIVAL MODE"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
        Text(
            when {
                charging -> t("Charging", "Nagcha-charge")
                left != null -> t("Beacon will last ~${left.roundToInt()} more hours", "Tatagal pa ang beacon nang ~${left.roundToInt()} oras")
                else -> t("Measuring battery use…", "Sinusukat ang gamit ng baterya…")
            },
            fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp,
        )
        Text(
            t("Measured on this phone over $mins min · battery ${now.pct}%", "Sinukat sa phone na ito sa loob ng $mins min · baterya ${now.pct}%"),
            fontSize = 16.sp, color = c.onSurfaceVariant,
        )
        BeaconLine()
        SurvivalListener(now.pct, charging, onHeard)
        Text(t("The screen goes dark to save battery. Tap anywhere to see this again.","Didilim ang screen para makatipid. Pindutin kahit saan para makita ulit."), fontSize = 18.sp)
        if (now.pct in 0..30) Text(
            t("Low battery: a rescuer's ring can take up to a minute to reach you.", "Mahina ang baterya: baka abutin ng isang minuto bago marinig ang ring ng rescuer."),
            fontSize = 16.sp,
        )
    }
    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp)) {
        Text(t("Leave survival mode", "Umalis sa survival mode"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Eval (D13): every set in assets/eval/, dictionary vs model, on this phone ----

data class EvalRow(val name: String, val n: Int, val dict: Int, val model: Int?, val target: String, val pass: Boolean?, val medianMs: Long?, val note: String = "")

// Blocking; call off the main thread. model/pass are null when the model isn't loaded.
fun runEval(ctx: Context, protocols: Protocols, progress: (String) -> Unit): List<EvalRow> {
    fun set(f: String) = JSONArray(ctx.assets.open("eval/$f").bufferedReader().use { it.readText() }).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    val on = Llm.state == Llm.State.READY
    val ms = mutableListOf<Long>()
    fun <T> timed(f: () -> T): T { val t0 = SystemClock.uptimeMillis(); return f().also { ms += SystemClock.uptimeMillis() - t0 } }
    fun median(): Long? = ms.sorted().getOrNull(ms.size / 2).also { ms.clear() }
    val rows = mutableListOf<EvalRow>()

    // A report is right only if the whole record matches (hazard, headcount, trapped, floor, spot, vulnerable, conditions).
    fun extraction(name: String, cases: List<JSONObject>, target: String, ok: (model: Int, dict: Int) -> Boolean) {
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
        rows += EvalRow(name, cases.size, d, m, target, m?.let { ok(it, d) && med!! < 5000 }, med, "rules only: $r/${cases.size}\n$perField")
    }
    // Reports by other people are scored apart: nothing was tuned on them, so they're the honest number.
    val (mine, others) = set("extract.json").partition { it.optString("by") != "others" }
    extraction("Extraction (tuning set)", mine, "≥ 8, beats dictionary by ≥ 3, < 5 s") { m, d -> m >= 8 && m - d >= 3 }
    if (others.isNotEmpty()) extraction("Extraction (written by others)", others, "≥ 80%, < 5 s") { m, _ -> m * 10 >= others.size * 8 }
    // Frozen sets nothing was tuned on: real #RescuePH tweets (CrisisLexT26, anonymized) and hard cases.
    val real = set("real.json")
    extraction("Real #RescuePH tweets", real, "beats rules, < 5 s") { m, _ -> true }
    val hard = set("hard.json")
    extraction("Hard cases", hard, "beats rules, < 5 s") { m, _ -> true }

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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${r.name} · n=${r.n}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    t("Keywords", "Keywords") + " ${r.dict}/${r.n}   ·   AI " + (r.model?.let { "$it/${r.n}" } ?: t("not loaded", "hindi naka-load")),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
                r.medianMs?.let { Text(t("Median latency %.1f s", "Median na tagal %.1f s").format(it / 1000.0), fontSize = 14.sp) }
                if (r.note.isNotEmpty()) Text(r.note, fontSize = 14.sp)
                Text(
                    t("TARGET", "TARGET") + " ${r.target} · " + when (r.pass) { true -> "PASS"; false -> "FAIL"; null -> t("NO MODEL", "WALANG MODEL") },
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant,
                )
            }
        }
    }
}
