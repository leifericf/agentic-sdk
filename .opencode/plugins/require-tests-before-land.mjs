// OpenCode plugin: require-tests-before-land adapter.
//
// The policy lives in hooks/require-tests-before-land.clj (Babashka). This
// module is the runtime adapter OpenCode auto-loads: it watches every bash
// invocation through the tool.execute.before hook, and when a command looks
// like a land op it shells out to bb and blocks the call (by throwing) if the
// policy denies.
//
// Why tool.execute.before: OpenCode exposes no hook that resolves a permission
// "ask" programmatically. permission.asked and permission.replied are
// notification events only. The documented way for a plugin to block a tool is
// tool.execute.before, inspecting input.tool and output.args, and throwing to
// deny (the pattern OpenCode's own .env-protection example uses).
//
// Fail-safe: any error or uncertainty in the plumbing allows. Only an explicit
// {allow: false} from the policy throws and blocks, so a wrong field name or a
// missing bb never breaks work.
//
// Wiring: drop this file in .opencode/plugins/ (auto-loaded at startup; no
// opencode.json entry or permission rule required). Requires bb on PATH.

import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
// .opencode/plugins/x.mjs -> repo root -> hooks/require-tests-before-land.clj
const script = path.join(here, "..", "..", "hooks", "require-tests-before-land.clj");

// Cheap pre-filter so most bash calls never spawn bb. Mirrors the land
// patterns in hooks/require-tests-before-land.clj; keep aligned.
const LAND_OPS = /git +push|jj +git +push|jj +bookmark +(move|set) +main|git +merge/;

function runPolicy({ command, cwd }) {
  return new Promise((resolve) => {
    const payload = JSON.stringify({ command: command || "", cwd: cwd || "" });
    const child = execFile("bb", [script], { cwd: cwd || process.cwd() }, (err, stdout) => {
      if (err) return resolve({ allow: true });
      try {
        resolve(JSON.parse(stdout.trim()));
      } catch {
        resolve({ allow: true });
      }
    });
    child.stdin.end(payload);
  });
}

export default async ({ directory }) => ({
  "tool.execute.before": async (input, output) => {
    if (input?.tool !== "bash") return;
    const command = output?.args?.command || "";
    if (!command || !LAND_OPS.test(command)) return;
    const cwd = directory || process.cwd();
    let decision;
    try {
      decision = await runPolicy({ command, cwd });
    } catch {
      return; // fail-safe: allow
    }
    if (decision && decision.allow === false) {
      // Throw to block, outside the fail-safe try/catch above.
      throw new Error(decision.reason || "require-tests-before-land: denied");
    }
  },
});
