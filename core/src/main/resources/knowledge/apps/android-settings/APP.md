---
name: android-settings
display: Android Settings
packages: ["com.android.settings"]
aliases: ["系统设置", "手机设置", "Android Settings", "Settings", "设置", "system settings", "phone settings"]
sites: []
open: Settings
description: Android system settings: network, display, permissions
---
## What it is
The settings app built into Android, package `com.android.settings`.

## Screens
The home page is a list of categories with a search box at the top; tapping a category leads through sub-pages and detail pages. Names, categories and hierarchy of settings differ by Android version and phone manufacturer, and some manufacturers add their own entries. The text and look of the Back button change per page; judge whether you are back at the home page by the page title and structure.

## Features
- **Search settings**: the search box at the top finds a setting directly by name.
- **Toggle settings**: Wi-Fi, Bluetooth, Airplane mode, Location, etc. A toggle's state is sometimes shown only by color and position, so the UI tree may have no explicit "On/Off" text.
- **About phone**: model, system version, etc.; the same page may also show serial number, IMEI, device name and other identifiers.
- **Permissions, Developer options, accounts, security, system update, install sources, reset**: changing these really changes the device state.

## Known behavior
Tapping a toggle switches it immediately, so when you only want to know the current state, read the accessibility properties or look at a screenshot; do not tap.
