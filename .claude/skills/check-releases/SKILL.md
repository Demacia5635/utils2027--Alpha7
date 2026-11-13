---
name: check-releases
description: Check whether a newer WPILib 2027 release (alpha, beta, RC or final) or newer Phoenix 6 / REVLib 2027 vendordeps exist than the versions this repo pins, and recommend whether to update. Use when the user types /check-releases or asks about updates, new releases, a new alpha/beta, or whether it's time to bump WPILib or a vendordep.
---

# Check for new 2027 releases

This repo pins WPILib and its vendor libraries on purpose and moves through the 2027 alpha, beta, RC and final releases one step at a time. This skill reports what is newer than the pins and recommends **hold**, **update** or **update and invest**. It changes nothing until the user approves.

## 1. Read the pins

- WPILib: the GradleRIO version in `build.gradle` (`id "org.wpilib.GradleRIO" version "..."`).
- Vendordeps: `version` and `jsonUrl` of each file in `vendordeps/*.json`. `CommandsV2.json` ships with WPILib, has no `jsonUrl`, and follows the WPILib version.
- Other branches: for each remote branch, `git show origin/<branch>:build.gradle | grep GradleRIO`. Say which branches are ahead of `main` (for example `log-infrastructure` on alpha-8).

## 2. Latest WPILib 2027

```bash
git ls-remote --tags --refs https://github.com/wpilibsuite/allwpilib 'v2027*' | sed 's#.*refs/tags/##' | sort -V | tail -n 5
```

`sort -V` orders alpha < beta < rc correctly, but puts a final tag (`v2027.0.0`, no suffix) before its pre-releases: a tag with no `-alpha`/`-beta`/`-rc` suffix is the newest. Read the release notes at `https://github.com/wpilibsuite/allwpilib/releases/tag/<tag>`, and the WPILib blog (https://wpilib.org/blog) for beta/RC announcements.

## 3. Latest vendordeps

For each vendordep with a `jsonUrl`, download it (`curl -sS <jsonUrl>`) and compare its `version` with the pinned one. The URLs already point at the 2027 track (`Phoenix6-frc2027-latest.json`, `REVLib-2027.json`). If a download is blocked (school network, cloud session), say so and give the URL for the user to open.

Changelogs: Phoenix 6 https://api.ctr-electronics.com/changelog, REVLib https://docs.revrobotics.com/revlib. Each vendordep release is built for a specific WPILib version: check its notes before updating either side, so WPILib and the vendordeps stay matched.

## 4. What an update would touch

For a newer WPILib, read the release notes for renamed or removed APIs, then search this repo for each one (`grep -rn "<name>" src`) and list the files that would change. Use frc-mcp for exact signatures. If another branch is already on that version (for example `log-infrastructure`), `git diff main origin/<branch> -- build.gradle vendordeps` shows what that update needed.

## 5. Recommend

| Found | Recommendation |
|---|---|
| Nothing newer | **Hold.** |
| New alpha, same channel | **Update** when convenient, on its own branch. Expect more renames. |
| First beta, RC or final | **Update and invest**: the API is settling. Plan time for renames and update the docs (step 6). |
| Vendordep only | Update only if its notes fix something we use, and only if it matches our WPILib version. |
| Build season (from kickoff in January) | Hold unless there is a fix we need, and update only between events. |

## 6. If the user approves an update

1. Make a new branch from `main`.
2. Change the GradleRIO version in `build.gradle`. For a vendordep, replace its file in `vendordeps/` with the downloaded JSON (Phoenix 6's file name includes the version: rename it and delete the old file).
3. Compile: `./gradlew compileJava` (Windows: `gradlew.bat compileJava` with the 2027 JDK, see CLAUDE.md). If Gradle can't download (cloud session, no internet), say the change wasn't compiled.
4. Fix compile errors with frc-mcp. Add every rename to the table in `.claude/rules/wpilib-2027.md`, and to `docs/MIGRATION-2027.md` when it touches dashboard code.
5. Update the versions in `CLAUDE.md` (header and Libraries table) and in the rule files that name them. Remind the user to run `frc-mcp sync` after the update.
6. Open a PR. Never merge without the user's OK.

## Output

A short report:

- **Pinned:** WPILib, Phoenix 6, REVLib on `main`, plus any branch on a different version.
- **Latest:** each one's newest version and channel, with links.
- **Would change:** files that use renamed or removed APIs, if any.
- **Recommendation:** hold / update / update and invest, in one line with the reason.

Cite every URL you used, and say which sources you couldn't reach.
