# S0d test 1: laptop listens for a phone advertising "hello". Run: python scan.py
import asyncio
from bleak import BleakScanner

def seen(dev, adv):
    blobs = list(adv.service_data.values()) + list(adv.manufacturer_data.values())
    if any(b"hello" in b for b in blobs):
        print("GOT HELLO from", dev.address, "signal", adv.rssi)

async def main():
    print("Listening for 60 seconds...")
    async with BleakScanner(seen):
        await asyncio.sleep(60)
    print("Done.")

asyncio.run(main())
