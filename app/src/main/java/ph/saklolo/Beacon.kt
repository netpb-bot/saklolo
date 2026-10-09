package ph.saklolo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.random.Random

// ponytail: unregistered 16-bit service UUID (a 128-bit one doesn't fit next to 22 bytes); register one before real use
val SERVICE_UUID: ParcelUuid = ParcelUuid(UUID.fromString("00005a10-0000-1000-8000-00805f9b34fb"))

// Victim side: what to advertise, and commands addressed to it. Rescuer side: what was heard, and commands to send.
object Beacon {
    enum class State { OFF, SENDING, NO_BLUETOOTH, NO_PERMISSION, STOPPED }

    var state by mutableStateOf(State.OFF)
    var bytes by mutableStateOf<ByteArray?>(null) // last advertised, for the demo overlay
    var scanning by mutableStateOf(false)
    val heard = mutableStateMapOf<Int, Heard>()

    // Victim: a rescuer answered (ACK) → "Help is on the way" and low-latency advertising.
    var acked by mutableStateOf(false)
    // Rescuer: records someone is on the way to (ACK heard or sent), and ids marked rescued (never re-added).
    val onTheWay = mutableStateListOf<Int>()
    private val rescued = mutableSetOf<Int>()

    @Volatile var record: Record? = null // facts part; the service adds id, rev, location, battery, age
        private set
    private lateinit var app: Context
    private val lock = Object()
    private val main = Handler(Looper.getMainLooper())
    private val handled = mutableMapOf<Code, Int>() // last command rev acted on, per code

    // Starts the service on first call (SOS confirm or "I'm safe"), then hands it the new facts. Each change bumps the revision.
    fun send(ctx: Context, facts: Record) {
        app = ctx.applicationContext
        val prefs = ctx.getSharedPreferences("beacon", Context.MODE_PRIVATE)
        // One id per install, so a later SAFE record replaces this phone's SOS; revision keeps rising across restarts.
        val id = prefs.getInt("id", 0).takeIf { it != 0 } ?: Random.nextInt().also { prefs.edit().putInt("id", it).apply() }
        val old = record
        if (old != null && old.copy(rev = 0) == facts.copy(id = id, rev = 0)) return
        val rev = (prefs.getInt("rev", 0) % 255) + 1
        prefs.edit().putInt("rev", rev).apply()
        record = facts.copy(id = id, rev = rev)
        if (facts.code == Code.SAFE) acked = false // a SAFE phone has no rescuer coming
        wake()
        if (old == null) state = State.OFF
        // Every change, not just the first: the running service rebuilds its notification (SOS → SAFE).
        ctx.startForegroundService(Intent(ctx, BeaconService::class.java))
    }

    internal fun wake() = synchronized(lock) { lock.notifyAll() }
    internal fun sleep(ms: Long) = synchronized(lock) { lock.wait(ms) }

    fun canScan(ctx: Context) = ctx.has(Manifest.permission.ACCESS_FINE_LOCATION) &&
        (Build.VERSION.SDK_INT < 31 || ctx.has(Manifest.permission.BLUETOOTH_SCAN))

    // Everything the beacon and board need, asked once at app start so an emergency never waits on a dialog.
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_SCAN) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val rec = r.scanRecord?.getServiceData(SERVICE_UUID)?.let { Record.decode(it) } ?: return
            main.post {
                when (rec.code) {
                    // Commands carry the victim's id: never store them as records.
                    Code.ACK, Code.RING, Code.RESC -> onCommand(rec)
                    else -> {
                        val old = heard[rec.id]
                        if (rec.id !in rescued && supersedes(rec, old?.rec)) {
                            val now = SystemClock.elapsedRealtime()
                            val samples = old?.samples.orEmpty().filter { now - it.first <= SAMPLE_MS } + (now to r.rssi)
                            heard[rec.id] = Heard(rec, samples, now)
                        }
                    }
                }
            }
        }
        override fun onScanFailed(code: Int) { Log.w("Saklolo", "scan failed $code") }
    }

    private fun onCommand(c: Record) {
        // Any rescuer's board: someone is on the way / the record is done.
        if (c.code == Code.ACK && c.id !in onTheWay) onTheWay += c.id
        if (c.code == Code.RESC) { rescued += c.id; heard.remove(c.id) }
        // The victim phone it's addressed to. A command repeats for ~70 s; act once per (code, rev).
        val mine = record ?: return
        if (c.id != mine.id || mine.code == Code.SAFE || handled[c.code] == c.rev) return
        handled[c.code] = c.rev
        Log.d("Saklolo", "command ${c.code} rev ${c.rev}")
        when (c.code) {
            Code.ACK -> { acked = true; wake() } // the service switches to low-latency on its next pass
            Code.RING -> Alert.ring(app)
            // Not a stop: the phone asks "Is a rescuer with you?" first. A rescue means contact, so whoever is there taps
            // Yes; a wrong tap on someone who can't answer renews the SOS by itself (RESCUE_WAIT_MS).
            Code.RESC -> if (!rescuePending) {
                rescueAt = SystemClock.elapsedRealtime(); rescuePending = true; main.postDelayed(autoRenew, RESCUE_WAIT_MS)
            }
            else -> {}
        }
    }

    // Victim: a rescuer sent RESC and nobody has answered "Is a rescuer with you?" yet. The beacon keeps sending.
    var rescuePending by mutableStateOf(false)
        private set
    var rescueAt = 0L // elapsedRealtime of that RESC, for the countdown
        private set
    const val RESCUE_WAIT_MS = 120_000L
    private val autoRenew = Runnable { renew(app) }

    // "Yes, a rescuer is with me": stop for good. The id is dropped because rescuer phones that saw RESC ignore it
    // forever; the next SOS or "I'm safe" gets a fresh one.
    fun confirmRescued(ctx: Context) {
        forgetId(ctx)
        stop(ctx, rescued = true)
    }

    // "No, I still need help", or no answer in time: same facts under a fresh id, so every board shows it again.
    fun renew(ctx: Context) {
        main.removeCallbacks(autoRenew)
        rescuePending = false
        forgetId(ctx)
        record?.let { send(ctx, it) }
    }

    private fun forgetId(ctx: Context) = ctx.getSharedPreferences("beacon", Context.MODE_PRIVATE).edit().remove("id").apply()

    // Rescued (confirmed), or the user stopped their SAFE broadcast: the beacon stops until a new SOS or "I'm safe".
    fun stop(ctx: Context, rescued: Boolean) {
        main.removeCallbacks(autoRenew)
        rescuePending = false
        record = null
        acked = false
        ctx.stopService(Intent(ctx, BeaconService::class.java))
        state = if (rescued) State.STOPPED else State.OFF
    }

    // Service-data filter on our UUID: needed for scans to keep running with the screen off, and keeps other BLE noise out.
    // Rescuer board: low latency. Victim listening for ACK/RING/RESC: low power.
    @SuppressLint("MissingPermission")
    fun startScan(ctx: Context, lowPower: Boolean = false): Boolean {
        val scanner = ctx.bt()?.bluetoothLeScanner ?: return false
        if (!canScan(ctx)) return false
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceData(SERVICE_UUID, ByteArray(0)).build()),
            ScanSettings.Builder().setScanMode(if (lowPower) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCb,
        )
        scanning = true
        return true
    }

    @SuppressLint("MissingPermission")
    fun stopScan(ctx: Context) {
        scanning = false
        if (canScan(ctx)) runCatching { ctx.bt()?.bluetoothLeScanner?.stopScan(scanCb) }
    }

    // Rescuer → victim: advertise ACK/RING/RESC with the victim's id for ~70 s, long enough to reach a victim
    // in low-battery mode (it only listens in its 10 s on-window every 60 s). Separate from this phone's own beacon.
    private val cmdCb = object : AdvertiseCallback() {
        override fun onStartFailure(code: Int) { Log.w("Saklolo", "command advertise failed $code") }
    }
    private var cmdRev = Random.nextInt(1, 255)
    private val endCommand = Runnable { runCatching { stopCommand() } }

    @SuppressLint("MissingPermission")
    private fun stopCommand() { app.bt()?.bluetoothLeAdvertiser?.stopAdvertising(cmdCb) }

    @SuppressLint("MissingPermission")
    fun command(ctx: Context, code: Code, victim: Int): Boolean {
        app = ctx.applicationContext
        val adv = ctx.bt()?.bluetoothLeAdvertiser ?: return false
        if (Build.VERSION.SDK_INT >= 31 && !ctx.has(Manifest.permission.BLUETOOTH_ADVERTISE)) return false
        cmdRev = cmdRev % 255 + 1
        runCatching { adv.stopAdvertising(cmdCb) }
        adv.startAdvertising(
            AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(false)
                .setTimeout(0).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build(),
            AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
                .addServiceData(SERVICE_UUID, Record(victim, code, rev = cmdRev).encode()).build(),
            cmdCb,
        )
        main.removeCallbacks(endCommand)
        main.postDelayed(endCommand, 70_000)
        if (code == Code.ACK && victim !in onTheWay) onTheWay += victim
        if (code == Code.RESC) { rescued += victim; heard.remove(victim) }
        return true
    }
}

private fun Context.has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
private fun Context.bt() = getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }

// Foreground service (connectedDevice) so the beacon survives the screen going off.
class BeaconService : Service() {
    @Volatile private var running = true
    private var looping = false
    private var lat = NO_FIX
    private var lon = NO_FIX
    private val startedAt = SystemClock.elapsedRealtime()

    private val advCb = object : AdvertiseCallback() {
        override fun onStartFailure(code: Int) { Log.w("Saklolo", "advertise failed $code") }
    }
    private val gps = LocationListener { l: Location ->
        lat = (l.latitude * 1e5).toInt(); lon = (l.longitude * 1e5).toInt(); Beacon.wake()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == "stop") { Beacon.stop(this, rescued = false); return START_NOT_STICKY }
        val safe = Beacon.record?.code == Code.SAFE
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("beacon", "SOS beacon", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "beacon")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
        // SAFE gets a Stop button; SOS doesn't, so a pocket tap can't silence a trapped victim ("I'm safe" in the app is the way out).
        if (safe) b.setContentTitle("Saklolo: sharing that you're safe")
            .setContentText("Nearby rescuers see you as safe.")
            .addAction(Notification.Action.Builder(null, "Stop",
                PendingIntent.getService(this, 1, Intent(this, BeaconService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)).build())
        else b.setContentTitle("Saklolo: SOS beacon is on")
            .setContentText("Nearby rescuers can find this phone, even without signal.")
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, n)
        // ponytail: an OS kill restarts us with no facts; stop rather than show a beacon that isn't sending.
        // The beacon is off until the app reopens; persist the record in the "beacon" prefs if that matters.
        if (Beacon.record == null) { stopSelf(); return START_NOT_STICKY }
        if (!looping) {
            looping = true
            startGps()
            thread(name = "beacon") { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        Beacon.wake()
        getSystemService(LocationManager::class.java).removeUpdates(gps)
    }

    // Duty cycle by battery: above 30% advertise continuously (low power), else 10 s on / 50 s off.
    // Every pass re-reads facts, battery, location and age, so a change goes out within one cycle.
    // After an ACK: continuous low-latency, so the rescuer's hot/cold meter updates fast.
    // The victim listens for ACK/RING/RESC only while advertising (PRD: a ring can take up to ~60 s at low battery).
    private fun loop() {
        while (running) {
            val battery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val low = battery in 0..30 && !Beacon.acked
            advertise(
                battery,
                when {
                    Beacon.acked -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                    low -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
                    else -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                },
            )
            listen(Beacon.record?.code != Code.SAFE) // a SAFE phone has nothing to be answered
            if (low) {
                Thread.sleep(10_000)
                stopAdvertising()
                listen(false)
                Beacon.sleep(50_000) // a new fact or an ACK wakes us early
            } else Beacon.sleep(60_000) // age ticks in minutes
        }
        stopAdvertising()
        listen(false)
        if (Beacon.state != Beacon.State.STOPPED) Beacon.state = Beacon.State.OFF
    }

    private var listening = false
    private fun listen(on: Boolean) {
        if (on == listening) return
        listening = if (on) Beacon.startScan(this, lowPower = true) else { Beacon.stopScan(this); false }
    }

    @SuppressLint("MissingPermission")
    private fun advertise(battery: Int, mode: Int) {
        val adv = bt()?.bluetoothLeAdvertiser ?: return run { Beacon.state = Beacon.State.NO_BLUETOOTH }
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_ADVERTISE)) return run { Beacon.state = Beacon.State.NO_PERMISSION }
        val rec = Beacon.record ?: return
        val bytes = rec.copy(
            lat = lat, lon = lon, battery = battery,
            age = ((SystemClock.elapsedRealtime() - startedAt) / 60_000).toInt(),
        ).encode()
        runCatching { adv.stopAdvertising(advCb) }
        adv.startAdvertising(
            AdvertiseSettings.Builder().setAdvertiseMode(mode).setConnectable(false).setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build(),
            // No device name, no TX power: only our 22 bytes.
            AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
                .addServiceData(SERVICE_UUID, bytes).build(),
            advCb,
        )
        Beacon.bytes = bytes
        Beacon.state = Beacon.State.SENDING
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() { runCatching { bt()?.bluetoothLeAdvertiser?.stopAdvertising(advCb) } }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val lm = getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { gps.onLocationChanged(it) }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30_000L, 0f, gps, Looper.getMainLooper())
    }
}
