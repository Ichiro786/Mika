---
name: google-clock
display: Google Clock
packages: ["com.google.android.deskclock", "com.android.deskclock"]
aliases: ["Clock", "Google Clock", "Alarm", "Timer", "Stopwatch", "时钟", "闹钟", "计时器", "秒表"]
sites: []
open: Clock
description: Alarms, timers, stopwatch and world clock
---
## What it is
The Clock app on Google Pixel and stock Android, package `com.google.android.deskclock` (some devices use `com.android.deskclock`). It handles alarms, timers, stopwatch, world clocks and bedtime.

## Preferred route: system intents
Standard Android clock intents set an alarm or timer without driving the UI: `AlarmClock.ACTION_SET_ALARM` (extras hour, minutes, message, skip UI) and `AlarmClock.ACTION_SET_TIMER` (extra length in seconds, message, skip UI). Currently `phone_open_link` accepts only uris and Android settings actions and rejects `intent:` links, so these intents are not reachable; use the UI route below unless an intent tool is available.

## Screens
Tabs at the bottom: Alarm, Clock, Timer, Stopwatch, Bedtime (the tab set can vary by version).
- **Alarm**: list of alarms, each with a time and an on/off toggle; the "+" button adds an alarm.
- **Clock**: world clocks.
- **Timer**: a number pad for the duration and a start button.
- **Stopwatch**: start, lap and reset.

## Setting an alarm in the UI
1. Open the Alarm tab and tap "+".
2. The time picker opens in dial mode. Tap the keyboard icon to switch to keyboard input, then type the hour and minutes instead of dragging the dial.
3. Choose AM or PM (not shown on 24-hour clocks).
4. Tap OK. Optionally set the days and label before leaving.
5. Verify: the new alarm appears in the list with the right time and its toggle on. Read the toggle state from the accessibility properties or a screenshot; tapping it switches it.

## Known behavior
- Tapping an alarm's toggle switches it immediately; do not tap just to check the state.
- The picker may open in either dial or keyboard mode, depending on the last one used.
