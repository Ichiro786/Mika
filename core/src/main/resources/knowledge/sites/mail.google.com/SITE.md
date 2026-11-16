---
name: mail.google.com
display: Gmail
description: Gmail on the web: search, read, compose and labels
domains: ["mail.google.com"]
aliases: ["Gmail", "Google Mail", "谷歌邮箱"]
apps: ["gmail"]
open: mail.google.com
mode: desktop
---
## What it is
Google's web mailbox; requires signing in to a Google account.

## Pages and URLs
Entry `https://mail.google.com/`. Layout: a search box at the top; on the left Compose and Inbox, Sent, Drafts, Spam, Trash and custom labels; in the middle the mail list. The UI language follows the account settings, so button names are not necessarily English. Appending `#search/<encoded query>` to the current account's address searches directly; the account index in the address (e.g. `u/0`) corresponds to the signed-in account. For search syntax see `references/gmail-search-syntax.md`.

## Features
- **List and search**: each row has sender, subject, date and whether it is unread, with no body. The list is virtually scrolled: rows above are removed as you scroll down, so the number of rows scrolled past is not the total number of emails in the mailbox.
- **Message body**: opening an email shows sender, subject, body, time and attachments. Opening an email may change its unread state.
- **Compose, reply, forward**: use Compose for a new email; for reply and forward, open the email first. On reply, recipient and subject are filled in automatically. After sending, the email appears in Sent; the compose window closing does not prove it was sent.

## Known behavior
Signing in to a Google account and "Verify it's you" are two different checks.
