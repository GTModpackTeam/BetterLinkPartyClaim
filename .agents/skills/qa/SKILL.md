---
name: qa
description: Run the QA workflow. Launches code-review, build-test, and doc-check teams in parallel. If issues are found, delegates fixes to the implementer team. Updates documentation before reporting.
disable-model-invocation: true
argument-hint: "[focus]"
---

# QA Workflow

You are the QA lead. Execute the following PDCA cycle.

**IMPORTANT — Role boundaries:**
- QA lead does **NOT** edit source code (`.java` files) directly. All code fixes must be delegated to the **implementer** agent.
- QA lead **MAY** edit documentation files only: `.claude/skills/`, `.claude/rules/`, `CLAUDE.md`.
- QA lead's role is: assess, coordinate, delegate, verify, document, report.

## Phase 1: Plan

Assess the current state:
- Run `git status` and `git diff` to identify changes
- Check recent commit log

Briefly report the scope and impact of changes to the user, and announce that you are launching 3 teams in parallel.

## Phase 2: Do

Launch the following 3 agents **in parallel** (a single message with 3 Agent tool calls):

1. **code-reviewer** — Code quality review
   - Prompt: provide a summary of current changes (git diff overview) and request review

2. **build-tester** — Build and test verification
   - Prompt: request build and test execution

3. **doc-reviewer** — Documentation consistency check
   - Prompt: request consistency check between CLAUDE.md / skills / rules and the current codebase

Include the current branch name and change summary in each agent's prompt.

## Phase 3: Check

After collecting results from all teams:

1. Summarize each team's results
2. Flag any CRITICAL/FAIL findings immediately
3. Produce an integrated quality report

## Phase 3.5: Fix — Only if code issues were found

If Check detected CRITICAL or FAIL in code:

1. List all items requiring fixes
2. Launch the **implementer** agent with specific fix instructions:
   - What to fix (file, line, content)
   - Why it needs fixing (cite review findings)
   - Run `spotlessApply` and `build` after changes
3. Verify implementer results (read output, do NOT edit code yourself)
4. Re-launch **build-tester** if needed to confirm build success

**Never edit `.java` files directly.** Always delegate to the implementer agent.

Skip this phase if no code fixes are required.

## Phase 4: Act — Documentation update then report

### Step 1: Update documentation (BEFORE reporting)

Based on all results (code review + doc review + any fixes applied), update documentation to match the current codebase:

1. **`.claude/skills/blpc-overview/SKILL.md`** — Primary target. Update architecture reference, class lists, patterns, color conventions, panel IDs, etc. to reflect any code changes.
2. **`.claude/skills/` and `.claude/rules/`** — Update skill/rule definitions if workflows or conventions changed.
3. **`CLAUDE.md`** — Only update if project-level information changed (build system, key dependencies, design principles). Keep it concise — detailed information belongs in skills.

**Principle**: CLAUDE.md stays lean (overview only). All detailed architecture, patterns, and conventions go into `.claude/skills/blpc-overview/SKILL.md`.

### Step 2: Final report

Report to the user in the following format:

```
## QA Report

### Overview
- Branch: <branch name>
- Changes: <change summary>

### Team Results
| Team | Verdict | Critical | Warning | Note |
|------|---------|----------|---------|------|
| Code Review | PASS/WARN/FAIL | 0 | 0 | ... |
| Build & Test | PASS/WARN/FAIL | 0 | 0 | ... |
| Documentation | PASS/WARN/FAIL | 0 | 0 | ... |
| Implementation | PASS/SKIP/FAIL | 0 | 0 | ... |

### Documentation Updates
- (list files updated and what changed)

### Critical Issues
- (list if any)

### Warnings
- (list if any)

### Suggestions
- (list if any)

### Overall Verdict: PASS / PASS_WITH_WARNINGS / FAIL
```

If any CRITICAL exists, Overall Verdict must be FAIL.
