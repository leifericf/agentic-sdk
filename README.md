# agentic-sdk

A runtime-agnostic system of skills, agents, and a deterministic spine for
software development across a bounded stack (C, Zig, Clojure, Elixir). Claude
Code is the master format; an OpenCode projection is generated from it.

> **Refresh in progress.** This skill system is being homogenized from several
> predecessor systems into the architecture described in
> [docs/design.md](docs/design.md). The documentation below describes the
> previous three-layer edition and is being replaced section by section.

A set of composable skills for software development, organized into three layers for maximum flexibility and parallel execution.

## Quick Start

### Most Common: Implement a Feature

```bash
/implement-feature
```

One command handles the complete implementation workflow:
1. Pick feature from backlog
2. Create implementation plan
3. Implement with quality gates
4. Validate the feature

### Other High-Level Workflows

```bash
/design-system        # Design a system from problem to backlog
/incident-response     # Handle incidents from triage to follow-up
/setup-project         # Set up a new project
/merge-to-trunk        # Merge a feature branch to trunk
/prepare-release       # Prepare a versioned release
/audit-codebase        # Audit for bugs, security, and UX/UI issues
/remediate-issues      # Fix all discovered issues across all tracks
```

### Fine-Grained Control

Need more control? Run individual workflows directly:

```bash
# Planning workflows
/describe-problem
/define-requirements
/design-ux
/design-technical
/create-backlog

# Implementation workflows
/pick-feature
/plan-feature
/execute-plan
/review-plan
/validate-feature
/triage-backlog

# Issue discovery and remediation workflows
/find-issues
/fix-issues

# Operations workflows
/triage-logs
/review-incident
/analyze-root-cause
/assess-risk
```

## Installation

```bash
# Clone the toolkit somewhere on your machine:
git clone https://github.com/leifericf/claude-code-toolkit.git ~/claude-code-toolkit

# Symlink into your project (macOS, Linux, WSL):
mkdir -p /path/to/your/project/.claude
ln -s ~/claude-code-toolkit/skills /path/to/your/project/.claude/skills
ln -s ~/claude-code-toolkit/agents /path/to/your/project/.claude/agents

# Or copy if you prefer a standalone snapshot:
cp -r ~/claude-code-toolkit/skills /path/to/your/project/.claude/skills
cp -r ~/claude-code-toolkit/agents /path/to/your/project/.claude/agents
```

**Note:** Symlinks work on macOS, Linux, and WSL. Native Windows does not reliably support symlinks — use WSL or copy instead.

**Note:** Claude Code's sandbox may block symlinks that point outside the project directory. If you encounter sandbox errors, use the copy approach instead.

Then run `/setup-project` in Claude Code to initialize the project's artifact structure.

## Architecture: Three Layers

```
┌─────────────────────────────────────────────────────────────┐
│ LAYER 1: Orchestrators (High-Level Intent)                  │
│ "I want to accomplish X"                                     │
│ Examples: /implement-feature, /design-system                 │
└─────────────────────────────────────────────────────────────┘
                           ↓ compose
┌─────────────────────────────────────────────────────────────┐
│ LAYER 2: Workflows (Declarative Processes)                  │
│ "I need to do this specific thing"                            │
│ Examples: /plan-feature, /execute-plan, /design-technical    │
└─────────────────────────────────────────────────────────────┘
                           ↓ compose
┌─────────────────────────────────────────────────────────────┐
│ LAYER 3: Primitives (Atomic Assessments)                    │
│ Internal units launched by workflows (do not invoke directly)│
│ Examples: assess-observability, check-format                │
└─────────────────────────────────────────────────────────────┘
```

### Layer 1: Orchestrators (`disable-model-invocation: true`)

**High-level entry points you invoke directly.** Each orchestrator composes multiple workflows into complete end-to-end flows.

- **Most of the time**: You'll run an orchestrator
- **Benefit**: One command handles everything automatically
- **Count**: 8 orchestrators

### Layer 2: Workflows

**Composable workflows you can run standalone or composed into orchestrators.** Each workflow focuses on a specific aspect of software development.

- **Sometimes**: You'll run a workflow directly for fine-grained control
- **Benefit**: Each workflow is independently useful and reusable
- **Count**: 22 workflows

### Layer 3: Primitives (`user-invocable: false`)

**Internal atomic units launched automatically by workflows.** Hidden from the `/` menu — do not invoke these directly.

- **Never**: You'll invoke primitives directly
- **Benefit**: Enable parallel execution, reusable components
- **Count**: 11 primitives

All 41 skills live in a flat `skills/` directory. Layers are distinguished by frontmatter fields, not directory structure.

## Design Principles

1. **Decompose**: Complex workflows → small focused primitives
2. **Compose**: Recombine primitives into higher-level orchestrators
3. **Reuse**: Same primitive in multiple contexts
4. **Parallelize**: Independent primitives run concurrently
5. **Simplicity**: Each component does one thing well
6. **Directness**: Clear entry points; no ambiguity
7. **Agnosticism**: High-level, declarative, works with any tech stack

## Tech Stack Agnostic

This toolkit captures **general values, principles, and concepts** that apply to software development using **any** tech stack.

**What we focus on**:
- What to build (outcomes, requirements)
- Why it matters (problems, user needs)
- How we'll know it works (acceptance criteria, testing)
- What could go wrong (risks, edge cases)

**What we avoid**:
- Package managers (npm vs cargo vs pip)
- Test runners (jest vs pytest vs cargo test)
- Frameworks (React vs Django vs Phoenix)
- Cloud providers (AWS vs GCP vs Azure)

## Parallel Execution

Workflows launch primitives in parallel for faster execution:

**Sequential** (old): 4 assessments take 4 minutes
```
assess-observability (1 min)
  → wait
assess-testing (1 min)
  → wait
assess-data (1 min)
  → wait
assess-rollout (1 min)
Total: 4 minutes
```

**Parallel** (new): 4 assessments take 1 minute
```
assess-observability (1 min)
assess-testing (1 min)
assess-data (1 min)
assess-rollout (1 min)
  → all run concurrently
Total: 1 minute
```

## Directory Structure

```
claude-code-toolkit/
└── skills/                          # All skills (flat, auto-discovered)
    ├── implement-feature/SKILL.md   # Orchestrator
    ├── design-system/SKILL.md       # Orchestrator
    ├── incident-response/SKILL.md   # Orchestrator
    ├── setup-project/SKILL.md       # Orchestrator
    ├── merge-to-trunk/SKILL.md      # Orchestrator
    ├── prepare-release/SKILL.md     # Orchestrator
    ├── audit-codebase/SKILL.md      # Orchestrator
    ├── remediate-issues/SKILL.md    # Orchestrator
    ├── bootstrap-project/SKILL.md   # Workflow
    ├── capture-preferences/SKILL.md # Workflow
    ├── describe-problem/SKILL.md    # Workflow
    ├── define-requirements/SKILL.md # Workflow
    ├── design-ux/SKILL.md           # Workflow
    ├── design-technical/SKILL.md    # Workflow
    ├── review-risks/SKILL.md        # Workflow
    ├── create-backlog/SKILL.md      # Workflow
    ├── pick-feature/SKILL.md        # Workflow
    ├── plan-feature/                # Workflow (with supporting file)
    │   ├── SKILL.md
    │   └── plan-template.md
    ├── execute-plan/SKILL.md        # Workflow
    ├── review-plan/SKILL.md         # Workflow
    ├── validate-feature/SKILL.md    # Workflow
    ├── triage-backlog/SKILL.md      # Workflow
    ├── quality-gate/SKILL.md        # Workflow
    ├── assess-quality-gates/SKILL.md# Workflow
    ├── triage-logs/SKILL.md         # Workflow
    ├── review-incident/SKILL.md     # Workflow
    ├── analyze-root-cause/SKILL.md  # Workflow
    ├── assess-risk/SKILL.md         # Workflow
    ├── find-issues/SKILL.md         # Workflow
    ├── fix-issues/SKILL.md          # Workflow
    ├── assess-observability/SKILL.md# Primitive (hidden)
    ├── assess-testing/SKILL.md      # Primitive (hidden)
    ├── assess-data/SKILL.md         # Primitive (hidden)
    ├── assess-rollout/SKILL.md      # Primitive (hidden)
    ├── check-format/SKILL.md        # Primitive (hidden)
    ├── check-lint/SKILL.md          # Primitive (hidden)
    ├── check-tests/SKILL.md         # Primitive (hidden)
    ├── check-build/SKILL.md         # Primitive (hidden)
    ├── discover-bugs/SKILL.md       # Primitive (hidden)
    ├── discover-security-issues/SKILL.md # Primitive (hidden)
    └── discover-ux-issues/SKILL.md  # Primitive (hidden)
```

## How to Use

### For Most Users

Run an orchestrator:
```bash
/implement-feature
```

The orchestrator will:
1. Guide you through the complete workflow
2. Invoke the right workflows at the right time
3. Launch primitives in parallel when beneficial
4. Report progress clearly

### For Power Users

Run workflows directly:
```bash
/plan-feature
```

You get fine-grained control over each step.

### For Developers (Composing New Workflows)

Launch primitives via the Task tool:
```yaml
Launch 4 primitives in parallel:
  → Task: Invoke /assess-observability
  → Task: Invoke /assess-testing
  → Task: Invoke /assess-data
  → Task: Invoke /assess-rollout
Wait for all to complete
Merge results
```

## Artifact Structure

Created by `/setup-project` inside your project's `.claude/` directory:

```
.claude/
  skills/                  # Skills (symlinked or copied from this repo)
  agents/                  # Custom subagent types (symlinked or copied from this repo)
  artifacts/
    planning/              # Backlog, requirements, problem description, designs
      tasks/               # Feature implementation plans
    decisions/             # Decision log, open questions
    project/               # Project meta, release tracking
    ops/                   # Incident reviews, RCAs, risk assessments, issue lists
```

Commit `skills`, `agents`, and `artifacts` to your repo so the team shares them. Add a `.claude/.gitignore` to exclude personal files:

```gitignore
# Personal settings (not shared)
settings.local.json

# Temporary plans (conversation-scoped)
plans/

# Claude Code memory (per-user, not project docs)
projects/
```

## Workflows

### Full Planning Cycle
```
/setup-project → /describe-problem → /define-requirements →
/review-risks → /design-ux → /design-technical →
/create-backlog
```

### Implementation Loop
```
/design-system → /implement-feature →
/incident-response → (repeat)
```

### Issue Discovery and Remediation
```
/audit-codebase → /remediate-issues → /audit-codebase (verify)
```

### Ship
```
/merge-to-trunk → /prepare-release
```

## Origins

This toolkit is a Claude Code-native port of a prior tool-agnostic edition, replacing the generic file-reference chains with Claude Code's native skill system and adding a layered architecture for composition and parallel execution.

## License

MIT
