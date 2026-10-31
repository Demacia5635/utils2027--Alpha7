---
name: teach
description: Full Feynman-style lesson on one concept as an interactive HTML page. Runs only when the user types /teach.
argument-hint: <concept, e.g. PID, swerve kinematics, feedforward>
disable-model-invocation: true
---

<!-- Based on https://github.com/Arty2001/feynman (MIT License, Copyright (c) 2026 Athavan Thambimuthu). The six beats, craft rules and quality bar below are copied from it; the Demacia section and Delivery are ours. -->


# Teach (Feynman lesson page)

## No concept given

If the concept at the bottom of this file is empty (for example, the user picked `/teach` from the menu and it was sent right away), do not build a page and do not guess. Reply with one short question asking what to teach, in the user's language, and list three or four concepts that exist in this repo as examples (one line each). Tell them they can simply reply with the concept name, no need to type `/teach` again, and build the page from that reply.

Teach exactly one concept per invocation as a self-contained interactive HTML page, the way Feynman taught: start from the problem someone actually had, let the solution get invented in front of the learner, then do the real math honestly — computed with numbers the learner can watch and recompute, never displayed and skipped past.

## The core rule

Never lead with the definition. Every concept exists because something was hard without it. Before writing anything, answer in one sentence: **"What concrete problem forces this concept to exist?"** For natural concepts the problem is everyday (addition: two piles and you need the total; derivatives: how fast right *now*, not on average). For invented machinery the problem is an engineering pain (encoders: a machine must compare a million sentences by meaning, and comparing letters fails — so turn each sentence into a list of numbers where distance means similarity). That problem sentence is your opening scene. If it only makes sense to someone who already knows the concept, dig one level deeper.

If the request is a broad topic, pick the single load-bearing concept inside it, teach that, and say at the top of the page which slice you chose and why.

## Page structure — six beats, in this order

1. **The problem.** Render the concrete situation where the pain is felt — visually (SVG or styled divs), animated or steppable. Zero jargon anywhere in this beat, including every piece of UI chrome: slider/control labels, live numeric readouts, and chip/badge displays use only plain-world words. Do not print the concept's name, formal variable names, abbreviations, or units (V, Ω, mA, etc.) anywhere in beat 1 — even inside a readout chip. No charts, graphs, axes, or abstract grids of labeled boxes here either — even unlabeled ones read as "diagram" rather than "scene"; save them for beat 2 onward.
2. **The naive attempt.** Show the first thing anyone would try, and let the learner watch it fail — interactively, in the same scene. Name the *exact* failure ("same words, opposite meanings — letter-matching can't tell them apart"). This beat is where the concept earns its existence: the learner should feel "okay, so what WOULD work?" before being told.

Any counts, indices, or rates used to build this beat's scene must come from the same explicit constants (or dial defaults) that beat 4 later manipulates — never separate invented numbers — so the failure and the eventual math are visibly the same throughline, not two disconnected demos.
3. **The idea.** The concept emerges as the fix to that exact failure. Same scene, same colors — show it succeeding precisely where the naive attempt broke. Only now give it its real name, in one plain sentence: "this move — X — is called ___."
4. **The math, worked.** The core machinery, derived and computed — not displayed. Use small concrete numbers (2–4 dimensions, 3-item datasets) and let the learner step through each operation with a control, watching intermediate values update. Every symbol wears the same color as the scene element it stands for, so the equation reads as a caption of the picture. Explain *why* each operation is the right one, not just what it does. Every "it can be shown that" is a failure: show it. The learner should leave able to recompute one small example by hand.
5. **Where it breaks.** The edge case that reveals the concept's boundary — as something the learner triggers with a control (slider, drag, step) and watches fail in real time, never a sentence asserting a boundary exists. If the concept has two distinct ways to break (a zero case and a runaway case), render BOTH as triggerable, animated states — not one animated and one described in prose. If you can't build an interactive path to the failure, find a boundary that can be demonstrated.
6. **Recap card.** A boxed, 30-second summary, one line each: the problem, the idea, the math, the breaking case. This is what the learner should be able to reproduce tomorrow.

## The depth contract

This skill is the opposite of explanation-saving: spend the tokens. Depth means more steps of the *same* concrete thread — the same scene deepened from problem to working math — never a jargon dump, a second metaphor, or a survey of related topics. If a step feels too obvious to include, include it; the learner who needed this page cannot skip steps you can.

## Visual craft rules

- Every abstract element gets one visual counterpart with a **stable color** used consistently across all six beats — in the scene, in the notation, in the recap. Color is the thread that ties the problem to the formalism.
- Include at least one **manipulable control** per beat where it makes sense (slider, stepper, toggle, clickable objects) so the learner changes something and watches the concept respond. Prediction before observation: nudge the learner to guess the outcome before moving it. When a prediction control has multiple choice buttons, the feedback for each choice must genuinely differ (confirm if right, correct if wrong) — never the same reveal text regardless of choice.

At least one prediction moment on the page must use committed discrete choice buttons (not a freeform slider or number input) immediately followed by feedback text that differs by choice — a slider whose value is only compared numerically much later does not satisfy this.
- Self-contained HTML only: inline CSS and vanilla JS, no external libraries, fonts, or network requests. SVG for diagrams.
- Prefer motion that can be **stepped** over motion that only autoplays; learners need to pause at the exact frame where the idea clicks.
- Real objects over abstract shapes in beats 1–3: sentences, coins, pipes, people — not circles labeled "a" and "b" (those belong in beat 4, mapped by color to the real objects from beat 1).

## Quality bar (check before delivering)

- Beat 1 contains zero terms of art — would a ten-year-old describe the scene accurately?
- Beat 2 shows a genuine naive attempt failing interactively, and beat 3's idea fixes that exact failure.
- Beat 4 computes the math with real small numbers the learner can step through and recompute — no formula is displayed without being worked.
- Every symbol in beat 4 is color-mapped 1:1 to a scene element.
- There is a control the learner can move, with a prediction nudge and differentiated feedback.
- Beat 5's boundary is triggerable and fails in real time; both failure modes if there are two.
- The recap card fits in one screen and stands alone.

- No dead code: variables, functions, or DOM branches written during drafting but no longer used (e.g. an abandoned tally approach) are deleted before delivery, not left alongside the working version.
- Any chart, axis, or diagram introduced in beat 4 appears only after the worked numeric steps that justify it, so it reads as a visualization of an already-derived result, not a display run in parallel with the derivation.

## Demacia additions

The learners are high-school students on FRC team 5635.

- **Language.** Write all page text in the language the user wrote in. For Hebrew, set `<html lang="he" dir="rtl">`; keep code, class names, numbers and formulas left-to-right (wrap them in `<code>` or `dir="ltr"` elements).
- **Use this repo's code when the concept lives here.** Search the repo first. If the concept is implemented here (kinematics, PID, limits, odometry), beat 4 uses the real class and method names and the real constants from the code, with `path/File.java:line` next to each, so the learner can open the file and see the same numbers. If a constant can't be found, say so on the page instead of inventing one.
- **Robot-world scenes.** Prefer scenes from the robot and the field (a swerve module, an arm reaching a setpoint, a robot driving to a game piece) or from daily life a teenager knows.
- **One real-life comparison** in beat 3, with one line saying where the comparison stops being true.

## Delivery

The deliverable is the complete page itself — a description of a page is not the page.

1. Write the full page to `docs/lessons/<concept-in-kebab-case>.html`, with a `<title>` naming the concept. The file must be complete and standalone: opening it in any browser with no network shows the whole lesson.
2. Open it in the default browser: `start "" "<path>"` on Windows, `open "<path>"` on macOS, `xdg-open "<path>"` on Linux. If that fails, give the path so the user can double-click it.
3. In the chat, write only two or three sentences: the problem in one line, what to play with on the page, and the file path. Do not re-explain the concept in text — the page is the explanation.

Concept to teach: $ARGUMENTS
