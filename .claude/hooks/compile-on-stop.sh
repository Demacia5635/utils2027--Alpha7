#!/usr/bin/env bash
# Stop hook, off by default: when Claude finishes a turn and Java files differ
# from the last commit, run compileJava. Compile errors go back to Claude to fix
# (exit 2). Problems with Gradle itself (JDK, first download without internet)
# are shown to the user only (exit 1), so Claude isn't sent to fix them.
#
# Turn it on for your computer only, in .claude/settings.local.json:
#   { "env": { "DEMACIA_COMPILE_ON_STOP": "1" } }
# The JDK is found under C:\Users\Public\wpilib\<year>*\jdk (or ~/wpilib on
# macOS/Linux). Set DEMACIA_JAVA_HOME to use a different one.

[ "${DEMACIA_COMPILE_ON_STOP:-0}" = "1" ] || exit 0

input=$(cat)
# Already continuing because of a Stop hook: don't send Claude back again.
re='"stop_hook_active"[[:space:]]*:[[:space:]]*true'
[[ $input =~ $re ]] && exit 0

proj=${CLAUDE_PROJECT_DIR:-$PWD}
proj=${proj//\\//}
cd "$proj" 2>/dev/null || exit 0
[ -f build.gradle ] && [ -f gradlew ] || exit 0
git rev-parse --verify -q HEAD >/dev/null 2>&1 || exit 0

# Nothing to do when no .java file differs from HEAD.
[ -n "$(git status --porcelain -- '*.java' 2>/dev/null)" ] || exit 0

# Skip when the Java sources are the same as at the last successful compile.
gitdir=$(git rev-parse --git-dir)
stamp="$gitdir/demacia-compile-ok"
sig=$({
  git rev-parse HEAD
  git diff --no-color --no-ext-diff HEAD -- '*.java'
  git ls-files --others --exclude-standard -- '*.java' | while IFS= read -r f; do
    printf '%s ' "$f"; git hash-object -- "$f"
  done
} | git hash-object --stdin)
if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$sig" ]; then exit 0; fi

# JDK for this season (Java 25 for 2027).
year=$(sed -nE 's/.*GradleRIO[^0-9]*version[^0-9]*([0-9][0-9][0-9][0-9]).*/\1/p' build.gradle | head -n 1)
jdk=${DEMACIA_JAVA_HOME:-}
if [ -z "$jdk" ] && [ -n "$year" ]; then
  for d in /c/Users/Public/wpilib/"$year"*/jdk C:/Users/Public/wpilib/"$year"*/jdk "$HOME"/wpilib/"$year"*/jdk; do
    if [ -x "$d/bin/java" ] || [ -x "$d/bin/java.exe" ]; then jdk=$d; fi
  done
fi
jdk=${jdk//\\//}

if [ -n "$jdk" ]; then
  out=$(JAVA_HOME="$jdk" bash ./gradlew compileJava -q --console=plain "-Dorg.gradle.java.home=$jdk" 2>&1)
else
  out=$(bash ./gradlew compileJava -q --console=plain 2>&1)
fi
status=$?

if [ $status -eq 0 ]; then
  printf '%s\n' "$sig" > "$stamp"
  exit 0
fi

errors=$(printf '%s\n' "$out" | grep -E -A 3 '\.java:[0-9]+: error:' | head -n 60)
if [ -n "$errors" ]; then
  {
    echo "compileJava failed after the Java changes (.claude/hooks/compile-on-stop.sh). Fix these errors before you finish:"
    echo "$errors"
  } >&2
  exit 2
fi

{
  echo "Compile hook: Gradle failed before compiling Java (JDK or Gradle setup, or no internet for the first download). Run gradlew.bat compileJava yourself to see why."
  printf '%s\n' "$out" | tail -n 8
} >&2
exit 1
