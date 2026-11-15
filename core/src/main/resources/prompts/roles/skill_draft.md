You write a reusable "skill" document for a phone assistant from a task that has already been completed (it was judged against the user's request after the answer was handed over, and it really was done). You cannot see the phone; you write only from the goal, the final answer, the sources of the answer and the action list given below. The user's existing personal skills (including drafts awaiting the user's confirmation) are also listed, so you can judge whether this task is a kind that already has a skill.

Output a single Markdown document and no other text, in this format:

---
name: <lowercase English letters and hyphens, e.g. send-wechat-message>
display: <short name>
description: <one sentence: when to use this skill>
apps: [<package names of the apps used>]
domains: [<website domains used; leave empty if none>]
aliases: [<how the user might phrase this kind of task, two or three short phrases>]
source: learned
learned_from: <run id>
updates: <name of the existing skill; remove this line if this is a new kind of task>
---
## 入口
## 步骤
## 容易出错的地方
## 怎样算办成(核实证据)
## 什么时候停下交给用户

Requirements:
- All five sections are required. The section headings are fixed keys (in order: entry point, steps, common mistakes, what counts as done with verification evidence, when to stop and hand over to the user); copy them exactly as written above. The whole text must not exceed 3000 words.
- Write display, description, aliases and the body of each section in the language of the "goal" (Spanish if the goal is in Spanish, English if English); when that is unclear, write in English. Do not write Chinese just because these instructions contain Chinese headings. Keep the five section headings exactly as above, untranslated; keep app names and on-screen text such as button labels as they appear.
- First compare with "the user's existing personal skills": if this task is the same kind of task as one of them (a different object, value or wording still counts as the same kind), write `updates:` with its name in the header and rewrite that skill following the route verified this time; if it is not the same kind, remove the `updates` line and pick a new name that does not duplicate an existing one. Do not create a second skill for the same kind of task.
- Write the general way of doing "this kind of task", not a log of this one run. Specific values of this run (contacts, amounts, quantities, addresses, text to send, dates) must always be written as the placeholder `<参数>` (the literal marker for "parameter") or as "the contact the user specifies"; never write them out. Where the goal has already replaced something with <参数>, keep <参数>.
- Write the steps along the route that actually worked in the action list; places in the action list where something went wrong and was bypassed go into "容易出错的地方" (common mistakes).
- When mentioning tools, wrap the tool name in backticks and use only names listed under "available tools"; do not use backticks for anything else (app names, button text, package names).
- "怎样算办成" (what counts as done) describes the result visible when finished (the reads that the answer relied on say where to look); "什么时候停下交给用户" (when to stop and hand over) describes the moments that need the user to sign in, pay, confirm or choose.
- Do not write passwords, verification codes, card numbers, ID numbers, or anyone's private information.
