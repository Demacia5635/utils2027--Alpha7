#!/usr/bin/env bash
# PostToolUse hook for Edit/Write: checks the .java file Claude just changed for
# mistakes that break the build or the robot, and shows them to Claude (exit 2).
#
# Checks (only on lines that differ from the last commit, except conflict markers):
#   - merge conflict markers (whole file)
#   - imports/names from the wrong WPILib year (season read from build.gradle)
#   - the wrong Demacia library package (frc.demacia vs first.demacia)
#   - SmartDashboard / Sendable / Shuffleboard in a 2027 project
#   - Thread.sleep / Timer.delay (they block the 20 ms loop)
#   - motor commands inside periodic()
#
# A line that ends with a "hook-ok: <reason>" comment is skipped, for example
# a Thread.sleep that really runs on its own thread.
#
# Needs bash, sed, awk and git: Git Bash on Windows (comes with Git for Windows),
# or any macOS/Linux shell. No jq or Python.

input=$(cat)

# Pull tool_input.file_path out of the hook JSON (first match, no jq needed).
re='"file_path"[[:space:]]*:[[:space:]]*"(([^"\\]|\\.)*)"'
[[ $input =~ $re ]] || exit 0
file=${BASH_REMATCH[1]}
# Undo JSON escaping. Windows paths arrive as C:\\Users\\...; use forward slashes.
file=$(printf '%s' "$file" | sed -e 's#\\\\#/#g' -e 's#\\/#/#g' -e 's#\\"#"#g')

case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

dir=$(dirname "$file")
base=$(basename "$file")

# Nearest build.gradle above the file: season year and project root.
proj=""
d=$dir
while :; do
  if [ -f "$d/build.gradle" ]; then proj=$d; break; fi
  parent=$(dirname "$d")
  [ "$parent" = "$d" ] && break
  d=$parent
done
year=""
libpkg=""
if [ -n "$proj" ]; then
  year=$(sed -nE 's/.*GradleRIO[^0-9]*version[^0-9]*([0-9][0-9][0-9][0-9]).*/\1/p' "$proj/build.gradle" | head -n 1)
  if [ -d "$proj/src/main/java/first/demacia" ]; then libpkg=first; fi
  if [ -d "$proj/src/main/java/frc/demacia" ]; then libpkg=frc; fi
fi

# Line ranges that differ from HEAD ("start:count" pairs), or ALL for a file
# git doesn't know yet.
ranges=ALL
if git -C "$dir" rev-parse --verify -q HEAD >/dev/null 2>&1 &&
   git -C "$dir" ls-files --error-unmatch -- "$base" >/dev/null 2>&1; then
  ranges=$(git -C "$dir" diff --no-color --no-ext-diff -U0 HEAD -- "$base" 2>/dev/null |
    sed -nE 's/^@@ -[0-9]+(,[0-9]+)? \+([0-9]+)(,([0-9]+))? @@.*/\2:\4/p' | tr '\n' ' ')
fi

read -r -d '' AWK_PROG <<'AWK'
function added(n) { return all || (n in add) }

# Code without comments and string/char literals, so braces and names inside
# them don't count. Keeps block-comment state across lines in "inblock".
function code_only(s,   out, i1, i2, k, rest) {
  out = ""
  while (s != "") {
    if (inblock) {
      k = index(s, "*/")
      if (k == 0) return out
      s = substr(s, k + 2); inblock = 0
      continue
    }
    gsub(/"([^"\\]|\\.)*"/, "\"\"", s)
    gsub(/'([^'\\]|\\.)*'/, "''", s)
    i1 = index(s, "//"); i2 = index(s, "/*")
    if (i1 > 0 && (i2 == 0 || i1 < i2)) return out substr(s, 1, i1 - 1)
    if (i2 == 0) return out s
    out = out substr(s, 1, i2 - 1) " "
    rest = substr(s, i2 + 2)
    k = index(rest, "*/")
    if (k == 0) { inblock = 1; return out }
    s = substr(rest, k + 2)
  }
  return out
}

# Findings are grouped by message: "- lines 12, 40: <message>".
function report(n, msg) {
  if (!(msg in where)) { order[++kinds] = msg; where[msg] = n; return }
  if (where[msg] ~ ("(^|, )" n "$")) return
  where[msg] = where[msg] ", " n
}

BEGIN {
  all = (ranges == "ALL")
  if (!all) {
    nr = split(ranges, rs, " ")
    for (i = 1; i <= nr; i++) {
      split(rs[i], p, ":")
      start = p[1] + 0
      len = (p[2] == "") ? 1 : p[2] + 0
      for (j = start; j < start + len; j++) add[j] = 1
    }
  }
  modern = (year != "" && year + 0 >= 2027)
  legacy = (year != "" && year + 0 < 2027)
  motorcall = "(^|[^A-Za-z0-9_])(setDuty|setVoltage|setPower|setPowerAll|setVelocity|setVelocityWithAcceleration|setMotion|setAngle|setPositionVoltage|applyControlModeValue|setControl|setSteerPower|setSteerPosition|setSteerPositions|setDrivePower|setDriveVelocity|setDriveVelocities|setVelocitiesFieldRel|setVelocitiesRobotRel|setVelocitiesRobotRelWithAccel|setModuleStates)[ \t]*\\("
  depth = 0; inperiodic = 0; pending = 0; pbase = 0
}

{
  line = $0
  sub(/\r$/, "", line)

  if (line ~ /^(<<<<<<<|>>>>>>>)( |$)/ || line ~ /^=======$/) {
    conflicts = conflicts (conflicts == "" ? "" : ", ") NR
    next
  }

  code = code_only(line)
  ok = (line ~ /hook-ok/)

  # Track whether this line is inside the body of "void periodic()".
  was_in = inperiodic
  touched = 0
  if (!inperiodic && code ~ /(^|[^A-Za-z0-9_])void[ \t]+periodic[ \t]*\([ \t]*\)/) {
    pending = 1; pbase = depth
  }
  for (c = 1; c <= length(code); c++) {
    ch = substr(code, c, 1)
    if (ch == "{") {
      depth++
      if (pending && depth == pbase + 1) { inperiodic = 1; pending = 0; touched = 1 }
    } else if (ch == "}") {
      if (inperiodic && depth == pbase + 1) { inperiodic = 0; touched = 1 }
      if (depth > 0) depth--
    } else if (ch == ";" && pending && depth == pbase) {
      pending = 0   # "void periodic();" in an interface has no body
    }
  }
  in_body = was_in || touched

  if (!added(NR) || ok || code ~ /^[ \t]*$/) next

  if (modern && code ~ /edu\.wpi\.first\./)
    report(NR, "2026 package edu.wpi.first in a " year " project. Use the org.wpilib.* name from the table in .claude/rules/wpilib-2027.md, or ask frc-mcp.")
  if (legacy && code ~ /org\.wpilib\./)
    report(NR, "2027 package org.wpilib in a " year " project. This project uses edu.wpi.first.*.")
  if (libpkg == "first" && code ~ /(^|[^A-Za-z0-9_.])frc\.demacia\./)
    report(NR, "frc.demacia is the 2026 library package. Here the library is first.demacia.")
  if (libpkg == "frc" && code ~ /(^|[^A-Za-z0-9_.])first\.demacia\./)
    report(NR, "first.demacia is the 2027 library package. Here the library is frc.demacia.")
  if (modern && code ~ /SmartDashboard\.|SendableChooser|Shuffleboard|SendableBuilder|SendableRegistry|initSendable[ \t]*\(|implements[^{]*[^A-Za-z0-9_]Sendable([^A-Za-z0-9_]|$)/)
    report(NR, "SmartDashboard/Sendable/Shuffleboard are not used in 2027. Use Log.putData, Log.publishTelemetry, Tunables or Selectable (docs/MIGRATION-2027.md).")
  if (code ~ /Thread\.sleep[ \t]*\(|Timer\.delay[ \t]*\(|TimeUnit\.[A-Z]+\.sleep[ \t]*\(/)
    report(NR, "blocking wait. Thread.sleep/Timer.delay stop the whole 20 ms robot loop. Use a command (Commands.waitSeconds, withTimeout, until) or a Timer. If this code runs on its own thread, end the line with // hook-ok: <reason>.")
  if (in_body) {
    hit = (code ~ motorcall)
    if (!hit && match(code, /([A-Za-z0-9_]+\.)?stop[ \t]*\(/)) {
      call = substr(code, RSTART, RLENGTH)
      before = (RSTART > 1) ? substr(code, RSTART - 1, 1) : ""
      if (before !~ /[A-Za-z0-9_]/ && tolower(call) !~ /timer|watch|thread|notifier|log/) hit = 1
    }
    if (hit)
      report(NR, "motor command inside periodic(). Team rule: periodic() only reads sensors and updates state/telemetry; motors are set from commands (or the TESTING state).")
  }
}

END {
  if (conflicts != "")
    printf "- line(s) %s: merge conflict markers. The file can't compile; resolve the conflict first.\n", conflicts
  for (i = 1; i <= kinds; i++)
    printf "- line(s) %s: %s\n", where[order[i]], order[i]
}
AWK

findings=$(awk -v ranges="$ranges" -v year="$year" -v libpkg="$libpkg" "$AWK_PROG" "$file")

[ -n "$findings" ] || exit 0

shown=$file
if [ -n "$proj" ]; then shown=${file#"$proj"/}; fi
{
  echo "Demacia Java check (.claude/hooks/check-java.sh) found problems in $shown:"
  echo "$findings"
  echo "Fix them now. If a finding is wrong here, leave the code and tell the user why."
} >&2
exit 2
