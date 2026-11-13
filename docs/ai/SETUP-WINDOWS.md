# Claude Code setup on Windows (2027 repo)

For the VS Code Claude Code extension. Do this once per computer. Everything else (`CLAUDE.md`, rules, skills, `.mcp.json`) comes with the repo.

## 1. frc-mcp (WPILib 2027 docs)

Skip if `frc-mcp --version` already works in PowerShell.

```powershell
# Scoop, if you don't have it
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
irm get.scoop.sh | iex

scoop bucket add fikretyukselit https://github.com/fikretyukselit/scoop-bucket
scoop install frc-mcp
frc-mcp sync
frc-mcp verify "C:\full\path\to\utils2027--Alpha7"   # should say season=2027
```

## 2. codebase-memory-mcp (code graph)

```powershell
Invoke-WebRequest -Uri https://raw.githubusercontent.com/DeusData/codebase-memory-mcp/main/install.ps1 -OutFile install.ps1
Unblock-File .\install.ps1
.\install.ps1 --skip-config
```

`--skip-config` installs only the program (to `%LOCALAPPDATA%\Programs\codebase-memory-mcp`, added to your PATH). The repo's `.mcp.json` already connects it, so the installer doesn't need to change any Claude settings. If PowerShell blocks the script, run `Set-ExecutionPolicy -Scope Process Bypass` first. SmartScreen may warn because the program is unsigned: "More info" → "Run anyway".

Open a **new** PowerShell and check: `codebase-memory-mcp --version`.

Nothing is sent anywhere: indexing and queries run on your computer.

## 3. Mermaid (diagrams in docs)

In VS Code: Extensions → install **Markdown Preview Mermaid Support** (`bierner.markdown-mermaid`). VS Code also suggests it when you open the repo. Then open `docs/ai/ARCHITECTURE.md` and press `Ctrl+Shift+V`.

## 4. Git for Windows (for the hooks)

The repo's hooks (`.claude/hooks/`) run in Git Bash, which comes with [Git for Windows](https://git-scm.com/downloads/win). If VS Code's Source Control works, you most likely have it. Check in PowerShell: `Test-Path "C:\Program Files\Git\bin\bash.exe"` should print `True`. Without it, every edit shows a "hook error" line in Claude Code; install Git for Windows and restart VS Code.

Optional: to have Claude compile after every turn that changed Java, create `.claude/settings.local.json` (it is not committed) with:

```json
{ "env": { "DEMACIA_COMPILE_ON_STOP": "1" } }
```

It finds the JDK in `C:\Users\Public\wpilib\2027...\jdk`. If yours is elsewhere, add `"DEMACIA_JAVA_HOME": "C:/path/to/jdk"` to the same `env`. Each compile takes a few seconds (the first one longer), which is why it's off by default.

## 5. Check in Claude Code

1. Restart VS Code (so it sees the new PATH).
2. In the Claude Code panel type `/mcp`. You should see `frc` and `codebase-memory`, both connected.
3. The first time, ask Claude: "index this repo with codebase-memory". After that it stays up to date while Claude is open.
4. Type `/hooks`. You should see the `PostToolUse` and `Stop` hooks from `.claude/settings.json`.

## Troubleshooting

- `codebase-memory` shows failed → the program isn't on PATH. Close all VS Code windows and open again, or put the full path to `codebase-memory-mcp.exe` in `.mcp.json` on your computer only (don't commit it).
- `frc` shows failed → run `frc-mcp sync` again. frc-mcp doesn't work through a proxy.
- Updating codebase-memory later: run the same `install.ps1` again.
