#!/usr/bin/env python3
"""Capture Mika's primary screens on an already booted, clean Android emulator."""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args: str) -> bytes:
    return subprocess.check_output(["adb", *args], timeout=60)


def nodes() -> list[ET.Element]:
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    return list(ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml")).iter("node"))


def find_text(label: str) -> list[ET.Element]:
    return [node for node in nodes() if label in (node.get("text"), node.get("content-desc"))]


def tap_tab(label: str) -> None:
    matches = find_text(label)
    if not matches:
        raise RuntimeError(f"Could not find navigation tab {label!r}")
    positions = []
    for node in matches:
        coords = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
        if len(coords) == 4:
            positions.append(((coords[0] + coords[2]) // 2, (coords[1] + coords[3]) // 2))
    if not positions:
        raise RuntimeError(f"Navigation tab {label!r} has no bounds")
    x, y = max(positions, key=lambda position: position[1])
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(3)


def capture(destination: Path, expected: str) -> None:
    for _ in range(15):
        if find_text(expected):
            destination.write_bytes(adb("exec-out", "screencap", "-p"))
            return
        time.sleep(2)
    destination.write_bytes(adb("exec-out", "screencap", "-p"))
    raise RuntimeError(f"Expected {expected!r} on {destination.name}; saved capture for diagnosis")


def main() -> None:
    apk, output = Path(sys.argv[1]), Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    adb("install", "-r", str(apk))
    adb("shell", "pm", "clear", "com.wangzan101.mika")
    adb("shell", "pm", "grant", "com.wangzan101.mika", "android.permission.POST_NOTIFICATIONS")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "wm", "dismiss-keyguard")
    for theme, night in (("dark", "yes"), ("light", "no")):
        adb("shell", "cmd", "uimode", "night", night)
        adb("shell", "am", "force-stop", "com.wangzan101.mika")
        adb("shell", "am", "start", "-n", "com.wangzan101.mika/.ui.MainActivity",
            "--es", "open_conversation", "visual-capture")
        time.sleep(12)
        capture(output / f"{theme}-chat.png", "RECOMMENDED TASKS")
        tap_tab("Tasks")
        capture(output / f"{theme}-tasks.png", "Your work.")
        tap_tab("Make")
        capture(output / f"{theme}-make.png", "Teach Orbit")


if __name__ == "__main__":
    main()
