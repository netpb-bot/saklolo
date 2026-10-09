# Dev tool: use this PC as the "second phone" for beacon tests (not part of the app).
#   python pc_beacon.py listen   PC is the rescuer: decodes Saklolo beacons from your phone (victim)
#   python pc_beacon.py send     PC is a victim: advertises the demo RED record for your phone's board
#   python pc_beacon.py ack|ring|resc <victim id>   PC is a rescuer: sends a command to your phone (id from `listen`)
# Needs: pip install bleak   Format mirrors Record.encode() in Triage.kt (22 bytes, big-endian).
import asyncio, struct, sys, time

UUID = "00005a10-0000-1000-8000-00805f9b34fb"
FMT = ">BiiiBBBBBBBH"  # ver|hops, id, lat, lon, kind, people, vuln, cond, place, battery, rev, age
NO_FIX = -2**31
CODES = ["COLLAPSE", "FLOOD", "LANDSLIDE", "OTHER", "SAFE", "ACK", "RESC", "RING"]
TRI = ["GREEN", "YELLOW", "RED"]
VULN = ["INFANT", "CHILD", "ELDERLY", "PREGNANT", "PWD"]
COND = ["NOT_BREATHING", "UNCONSCIOUS", "HEAVY_BLEEDING", "PINNED", "INJURED", "WATER_RISING"]
SPOT = ["UNKNOWN", "BATHROOM", "STAIRS", "UNDER_TABLE", "ROOM", "KITCHEN", "HALLWAY", "ATTIC", "OUTSIDE"]
BANDS = [(-60, "Right here"), (-70, "Hot"), (-80, "Warm")]  # same cut-offs as BAND_DBM in Rescuer.kt


def bits(b, names):
    return [n for i, n in enumerate(names) if b >> i & 1]


def decode(b):
    if len(b) != 22:
        return None
    vh, rid, lat, lon, kind, people, vuln, cond, place, batt, rev, age = struct.unpack(FMT, b)
    if vh >> 4 != 1 or kind >> 4 >= len(CODES):
        return None
    floor = place >> 4
    return dict(
        id=f"{rid & 0xFFFFFFFF:08x}", rev=rev, code=CODES[kind >> 4], tri=TRI[kind >> 2 & 3] if kind >> 2 & 3 < 3 else "?",
        trapped=bool(kind & 2), worse=bool(kind & 1), people=people, vuln=bits(vuln, VULN), cond=bits(cond, COND),
        place=("roof" if floor == 15 else f"floor {floor}" if floor else "") + (f" {SPOT[place & 15].lower()}" if place & 15 else ""),
        gps="no fix" if lat == NO_FIX else f"{lat / 1e5:.5f},{lon / 1e5:.5f}", battery=batt, age=age, hops=vh & 15,
    )


async def listen():
    from bleak import BleakScanner
    t0, seen, last = time.monotonic(), {}, {}

    def on(dev, adv):
        raw = adv.service_data.get(UUID)
        r = raw and decode(bytes(raw))
        if not r:
            return
        if r["code"] in ("ACK", "RING", "RESC"):  # a command from your phone (rescuer) to victim r["id"]
            key = (r["id"], r["code"], r["rev"])
            if key not in last:
                last[key] = True
                print(f"\n>>> command {r['code']} to victim {r['id']} (rev {r['rev']})")
            return
        rssi = seen.setdefault(r["id"], [])
        rssi[:] = (rssi + [adv.rssi])[-5:]
        avg = sum(rssi) / len(rssi)
        band = next((n for t, n in BANDS if avg >= t), "Cold")
        if r["id"] not in last:
            print(f"\n*** first heard {r['id']} after {time.monotonic() - t0:.1f} s")
        line = (f"{r['tri']:6} {r['code']:8} rev {r['rev']:3} people {r['people']} trapped={r['trapped']} worse={r['worse']} "
                f"{r['place'] or '-'} | {', '.join(r['vuln'] + r['cond']) or '-'} | batt {r['battery']}% age {r['age']}m gps {r['gps']}")
        if last.get(r["id"]) != line:
            print(line)
            last[r["id"]] = line
        print(f"\r  {r['id']}  {adv.rssi:4} dBm  avg {avg:6.1f}  {band:10}  raw {bytes(raw).hex()}", end="", flush=True)

    print("Listening for Saklolo beacons (Ctrl+C to stop)...")
    async with BleakScanner(on):
        while True:
            await asyncio.sleep(1)


def send(code="SOS", victim=None):
    from winrt.windows.devices.bluetooth.advertisement import (
        BluetoothLEAdvertisementDataSection, BluetoothLEAdvertisementPublisher)
    from winrt.windows.storage.streams import DataWriter

    rev = int(time.time()) % 200 + 1  # fresh revision each run so the phone acts on it
    if code == "SOS":
        # Demo victim: RED collapse, trapped, getting worse, 4 people, child + elderly, pinned + heavy bleeding, floor 2 bathroom.
        kind = 0 << 4 | 2 << 2 | 1 << 1 | 1
        payload = struct.pack(FMT, 0x10, 0x0C0FFEE, NO_FIX, NO_FIX, kind, 4, 0b110, 0b1100, 2 << 4 | 1, 31, rev, 14)
    else:
        # Rescuer command to the phone: id = the victim's id (from `listen`), like Beacon.command() in the app.
        vid = int(victim, 16)
        payload = struct.pack(FMT, 0x10, vid - 2**32 if vid >= 2**31 else vid, NO_FIX, NO_FIX,
                              CODES.index(code) << 4 | 1 << 2, 0, 0, 0, 0, 0, rev, 0)
    w = DataWriter()
    w.write_bytes(bytes([0x10, 0x5A]) + payload)  # 16-bit UUID 0x5A10, little-endian, then our 22 bytes
    pub = BluetoothLEAdvertisementPublisher()
    pub.advertisement.data_sections.append(BluetoothLEAdvertisementDataSection(0x16, w.detach_buffer()))
    pub.start()
    time.sleep(1)
    print(f"Publisher status: {pub.status.name}  (Started = advertising)")
    print(f"Sending {code} {payload.hex()} rev {rev}. Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pub.stop()


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "listen"
    try:
        if mode == "listen":
            asyncio.run(listen())
        elif mode in ("ack", "ring", "resc"):
            send(mode.upper(), sys.argv[2])  # e.g. python pc_beacon.py ring 1a5edca8
        else:
            send()
    except KeyboardInterrupt:
        pass
