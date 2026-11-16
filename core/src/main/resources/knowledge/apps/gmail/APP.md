---
name: gmail
display: Gmail
packages: ["com.google.android.gm"]
aliases: ["Gmail", "谷歌邮箱", "Google 邮箱", "gmail", "Google Mail"]
sites: ["mail.google.com"]
open: Gmail
description: Google's email app: send, organize by tab and label, Meet
---
## What it is
Gmail is Google's email app, package `com.google.android.gm`. Can also manage other email accounts.

## Screens
Two tabs at the bottom: "Mail" and "Meet".
- **Mail**: top left is the menu button ("Open navigation drawer"), in the middle the search box ("Search in mail"), on the right the profile picture ("Account and settings"). The list starts with the current inbox name (e.g. "Primary"), then summary rows for categories such as "Updates" and "Promotions" (with new-mail counts), then the emails; bottom right is the "Compose" button.
- **Drawer**: inbox categories (Primary, Promotions, Social, Updates); under "All labels": Starred, Snoozed, Important, Sent, Scheduled, Outbox, Drafts, All mail, Spam, Trash, Manage subscriptions, Create new, plus your own labels; under "Google apps": Calendar, Contacts; at the very bottom "Settings" and "Help & feedback", which you must scroll down to see.
- **Meet**: has "New meeting" and "Join with a code".

## Features
- **Mail list**: one row per email; the element name joins "Unread", sender, subject and snippet; the date and star are separate elements. Tapping the sender's avatar selects the email, it does not open it.
- **Search**: tap the search box at the top; there is a back button, an input box and filter buttons: label, from, to, attachments.
- **Accounts**: the profile picture opens the account sheet: Manage your Google Account, switch/add account, manage accounts on this device, storage usage.
- **Settings**: reached from the bottom of the drawer; the first level is "General settings", one row per account, and "Add account".

## Known behavior
- On first launch or after an update these appear in turn: the "Gmail has a new look" intro page ("Got it"), a page about managing all your email addresses ("Go to Gmail"), the notification-permission prompt ("Don't allow" / "Allow"), a card introducing Meet ("Learn more" / "Got it"), and a tip card at the top of the list ("Dismiss").
- The system notification-permission prompt exposes no elements and the Back key does not close it; tap its on-screen buttons.
- The first time you open the "Meet" tab, "Allow Gmail to make and manage phone calls" appears ("Cancel" / "Allow"); elements unreadable.
- The account sheet exposes no elements; read the screen.
- While the drawer or search page is open, the mail list behind it is still in the element list, and the drawer items come after the emails.
- Back in the mail list exits the app.
