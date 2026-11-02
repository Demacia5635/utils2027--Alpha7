---
name: teacher
description: Turn teacher mode (Feynman-style explanations with real-life examples) on or off for the rest of this session. Runs only when the user types /teacher.
argument-hint: on | off
disable-model-invocation: true
---

Teacher mode is described in the "Teacher mode" section of the Demacia output style (`.claude/output-styles/demacia.md`).

- Argument `off`: teacher mode is off from now until the end of this session.
- Argument `on`: teacher mode is on again.
- No argument: flip the current mode (it starts on).

Reply with one short line in the user's language saying the new mode, and nothing else.

Argument: $ARGUMENTS
