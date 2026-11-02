---
name: safety
description: Run the safety-reviewer agent on the current changes (or on given files) and report what could damage the robot or hurt someone. Runs only when the user types /safety.
argument-hint: [files or branch; empty = changes on this branch vs main]
disable-model-invocation: true
---

Use the `safety-reviewer` agent (`.claude/agents/safety-reviewer.md`) to review:

- If the request below is empty: every file changed on this branch compared to `main` (`git diff --name-only main...HEAD`), plus uncommitted changes (`git status --short`).
- Otherwise: the files, folder or branch named below.

Pass the agent the list of files to check. When it returns, show its findings as it wrote them (most dangerous first, each with `path/File.java:line`, what can happen and the fix). Don't fix anything unless the user asks.

Scope: $ARGUMENTS
