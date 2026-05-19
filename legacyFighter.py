import os
import json
import re
import subprocess
import time
from datetime import datetime
from pathlib import Path
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass
from google import genai
from google.genai import types

client = genai.Client()

REPO_ROOT = Path(__file__).parent.resolve()
LOG_PATH = REPO_ROOT / "legacy_fighter.log.jsonl"

# Tracks files modified by file_writer/apply_patch so we only stage those
MODIFIED_FILES: set[str] = set()
MAX_FILES_MODIFIED = 8  # scope guardrail — agent cannot grow blast radius beyond this
SCOPE_WARNING_THRESHOLD = 5  # warn agent when approaching limit

# Simple LRU-ish cache so the agent doesn't re-read the same file 8x per run
FILE_READ_CACHE: dict[str, str] = {}
MAX_CACHE_SIZE = 20


def _tracked_files() -> set[str]:
    """Returns set of git-tracked files (ignores untracked, respects .gitignore implicitly)."""
    r = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True)
    return {"./" + line for line in r.stdout.splitlines() if line}


def _check_scope_budget(target_file: str) -> str | None:
    """Returns a scope-related error message if budget exceeded, else None."""
    if target_file in MODIFIED_FILES:
        return None
    if len(MODIFIED_FILES) >= MAX_FILES_MODIFIED:
        return (f"SCOPE LIMIT REACHED: you have already modified {len(MODIFIED_FILES)} files. "
                f"Hard cap is {MAX_FILES_MODIFIED}. Refusing to touch a new file. "
                f"Wrap up and call create_github_pr with the existing changes, or abort.")
    return None


def _scope_status_hint() -> str:
    """Hint appended to mutation tool results so agent stays aware of budget."""
    n = len(MODIFIED_FILES)
    if n >= SCOPE_WARNING_THRESHOLD:
        return f" [Scope: {n}/{MAX_FILES_MODIFIED} files modified. Wrap up.]"
    return f" [Scope: {n}/{MAX_FILES_MODIFIED} files modified.]"


def _log_event(event: dict) -> None:
    event["ts"] = datetime.now().isoformat()
    with open(LOG_PATH, "a", encoding="utf-8") as f:
        f.write(json.dumps(event, default=str) + "\n")


# ==========================================
# TOOLS
# ==========================================

GCP_PROJECT = os.environ.get("GCP_PROJECT")
GCP_SERVICE = os.environ.get("GCP_SERVICE")
GCP_WINDOW = os.environ.get("GCP_WINDOW", "24h")
_GCP_ALERTS_CACHE: str | None = None


def fetch_gcp_alerts() -> str:
    """Fetches REAL production anomalies (errors and slow requests) from Health-Assistant-Server on Google Cloud Run via Cloud Logging. Returns up to 3 most recent errors with full stack traces, or slow requests if no errors. Cached per run."""
    global _GCP_ALERTS_CACHE
    print(f"☁️  [MCP -> GCP] Fetching real production alerts ({GCP_SERVICE}, last {GCP_WINDOW})...", flush=True)
    _log_event({"tool": "fetch_gcp_alerts", "args": {"window": GCP_WINDOW, "service": GCP_SERVICE}})

    if _GCP_ALERTS_CACHE is not None:
        return _GCP_ALERTS_CACHE

    def _gcloud_read(filter_expr: str, limit: int) -> list:
        try:
            r = subprocess.run([
                "gcloud", "logging", "read", filter_expr,
                f"--limit={limit}", f"--freshness={GCP_WINDOW}",
                "--format=json", f"--project={GCP_PROJECT}", "--verbosity=error"
            ], capture_output=True, text=True, timeout=60)
        except subprocess.TimeoutExpired:
            return []
        if r.returncode != 0 or not r.stdout.strip():
            return []
        try:
            return json.loads(r.stdout)
        except json.JSONDecodeError:
            return []

    base = f'resource.type="cloud_run_revision" AND resource.labels.service_name="{GCP_SERVICE}"'

    # ALWAYS query both signal types and combine — repetitive slow requests indicate latency bugs
    # (N+1, missing index, etc.) which are MORE actionable than transient infra errors.
    error_logs = _gcloud_read(f'{base} AND severity>=ERROR', limit=5)
    slow_logs = _gcloud_read(f'{base} AND httpRequest.latency>="1s"', limit=5)

    anomalies = []
    for log in error_logs[:3]:
        payload = log.get("textPayload") or json.dumps(log.get("jsonPayload") or {})[:200]
        anomalies.append({
            "type": "error",
            "timestamp": log.get("timestamp", ""),
            "severity": log.get("severity", ""),
            "message": payload[:3000],
            "request_url": (log.get("httpRequest") or {}).get("requestUrl", ""),
        })

    for log in slow_logs[:3]:
        http = log.get("httpRequest") or {}
        anomalies.append({
            "type": "slow_request",
            "timestamp": log.get("timestamp", ""),
            "url": http.get("requestUrl", ""),
            "latency": http.get("latency", ""),
            "status": http.get("status", ""),
            "method": http.get("requestMethod", ""),
        })

    response = {
        "service": GCP_SERVICE,
        "project": GCP_PROJECT,
        "window": GCP_WINDOW,
        "status": "anomalies_found" if anomalies else "healthy",
        "anomaly_count": len(anomalies),
        "anomalies": anomalies,
    }
    _GCP_ALERTS_CACHE = json.dumps(response, indent=2)
    return _GCP_ALERTS_CACHE


def codebase_search(query: str) -> str:
    """Searches git-TRACKED files only (respects .gitignore, skips untracked WIP). Scans .java, .yml, .yaml, .properties, .groovy, .kts, .ts, .js, .py."""
    print(f"🔍 [MCP -> Git] Searching for keyword: '{query}'", flush=True)
    _log_event({"tool": "codebase_search", "args": {"query": query}})
    extensions = ('.java', '.yml', '.yaml', '.properties', '.groovy', '.kts', '.ts', '.js', '.py')
    results = []
    tracked = _tracked_files()
    for path in tracked:
        if not path.endswith(extensions):
            continue
        try:
            with open(path, 'r', errors='ignore') as f:
                if query in f.read():
                    results.append(path)
        except OSError:
            continue
    return json.dumps({"found_in_files": results[:50], "total": len(results)})


def find_spring_endpoint(http_path: str) -> str:
    """Locates the Java controller class and method that handles the given HTTP path (e.g. '/v1/audit/health-events'). Resolves class-level @RequestMapping prefix automatically. Use this instead of grepping for routes."""
    print(f"🎯 [MCP -> Spring] Locating endpoint: {http_path}", flush=True)
    _log_event({"tool": "find_spring_endpoint", "args": {"http_path": http_path}})
    target = http_path.strip("/")
    matches = []
    method_re = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"')
    class_re = re.compile(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"')
    for root, _, files in os.walk("./src/main/java"):
        for file in files:
            if not file.endswith(".java"):
                continue
            path = os.path.join(root, file)
            try:
                with open(path, 'r', errors='ignore') as f:
                    text = f.read()
            except OSError:
                continue
            if "@RestController" not in text and "@Controller" not in text:
                continue
            cm = class_re.search(text)
            class_prefix = cm.group(1).strip("/") if cm else ""
            for mm in method_re.finditer(text):
                method_path = mm.group(2).strip("/")
                full = "/".join(p for p in [class_prefix, method_path] if p)
                normalized = re.sub(r'\{[^}]+\}', '{*}', full)
                target_normalized = re.sub(r'\{[^}]+\}', '{*}', target)
                if normalized == target_normalized or full == target:
                    line = text[:mm.start()].count("\n") + 1
                    matches.append({"file": path, "line": line, "full_path": "/" + full, "http_verb": mm.group(1).upper()})
    if not matches:
        return json.dumps({"matches": [], "hint": "No exact route match. Try codebase_search with a shorter substring."})
    return json.dumps({"matches": matches})


def file_reader(file_path: str) -> str:
    """Reads and returns the complete text content of a target file. RAISES if the file does not exist (so you know to check the path)."""
    print(f"📖 [MCP -> File] Reading file: {file_path}", flush=True)
    _log_event({"tool": "file_reader", "args": {"file_path": file_path}})
    if file_path in FILE_READ_CACHE:
        return FILE_READ_CACHE[file_path]
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File does not exist: {file_path}")
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    if len(FILE_READ_CACHE) >= MAX_CACHE_SIZE:
        FILE_READ_CACHE.pop(next(iter(FILE_READ_CACHE)))
    FILE_READ_CACHE[file_path] = content
    return content


def file_writer(file_path: str, content: str) -> str:
    """Overwrites an entire file. Prefer apply_patch for small edits — it's safer and less hallucination-prone."""
    print(f"💾 [MCP -> File] Saving changes to: {file_path}", flush=True)
    _log_event({"tool": "file_writer", "args": {"file_path": file_path, "content_size": len(content)}})
    scope_error = _check_scope_budget(file_path)
    if scope_error:
        return scope_error
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    MODIFIED_FILES.add(file_path)
    FILE_READ_CACHE.pop(file_path, None)
    return "File updated successfully. REMINDER: call gradle_compile next." + _scope_status_hint()


def apply_patch(file_path: str, search: str, replace: str) -> str:
    """Surgically replaces an exact `search` string with `replace` in the file. Fails if `search` is not found or not unique. Preferred over file_writer for targeted edits."""
    print(f"✂️  [MCP -> File] Patching: {file_path}", flush=True)
    _log_event({"tool": "apply_patch", "args": {"file_path": file_path, "search_size": len(search), "replace_size": len(replace)}})
    scope_error = _check_scope_budget(file_path)
    if scope_error:
        return scope_error
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File does not exist: {file_path}")
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    occurrences = content.count(search)
    if occurrences == 0:
        return f"Patch failed: search string not found in {file_path}. Read the file again to see current content."
    if occurrences > 1:
        return f"Patch failed: search string occurs {occurrences} times in {file_path}. Provide more surrounding context to make it unique."
    new_content = content.replace(search, replace, 1)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    MODIFIED_FILES.add(file_path)
    FILE_READ_CACHE.pop(file_path, None)
    return "Patch applied successfully. REMINDER: call gradle_compile next." + _scope_status_hint()


def gradle_compile() -> str:
    """Runs `./gradlew compileJava` and returns compilation result. MUST be called after every file_writer/apply_patch. Do not proceed to create_github_pr until this returns BUILD SUCCESS."""
    print("🔨 [MCP -> Gradle] Compiling Java sources...", flush=True)
    _log_event({"tool": "gradle_compile", "args": {}})
    try:
        r = subprocess.run(
            ["./gradlew", "compileJava", "-q", "--console=plain"],
            capture_output=True, text=True, timeout=600
        )
    except subprocess.TimeoutExpired:
        return "BUILD FAILED: compilation timed out after 600s"
    if r.returncode == 0:
        return "BUILD SUCCESS"
    output = (r.stdout + "\n" + r.stderr)[-4000:]
    return f"BUILD FAILED:\n{output}"


def run_tests(test_pattern: str) -> str:
    """Runs Spock integration tests matching the pattern (e.g. '*AuditServiceSpec'). Optional but recommended before opening a PR. Requires Docker for Testcontainers."""
    print(f"🧪 [MCP -> Gradle] Running tests: {test_pattern}", flush=True)
    _log_event({"tool": "run_tests", "args": {"test_pattern": test_pattern}})
    try:
        r = subprocess.run(
            ["./gradlew", ":integration-tests:test", "--tests", test_pattern, "-q", "--console=plain"],
            capture_output=True, text=True, timeout=900
        )
    except subprocess.TimeoutExpired:
        return "TESTS FAILED: timed out after 900s"
    if r.returncode == 0:
        return "TESTS PASSED"
    output = (r.stdout + "\n" + r.stderr)[-4000:]
    return f"TESTS FAILED:\n{output}"


def create_github_pr(branch_name_hint: str, pr_title: str, pr_body: str) -> str:
    """Creates a new branch (auto-named: auto/YYYYMMDD-HHMMSS-<slug>), commits ONLY files modified by file_writer/apply_patch this session, pushes to origin, opens PR via gh CLI against master."""
    if not MODIFIED_FILES:
        return "Aborted: no files were modified this session; refusing to create empty PR."
    slug = re.sub(r'[^a-z0-9-]+', '-', branch_name_hint.lower()).strip('-')[:40] or "fix"
    branch_name = f"auto/{datetime.now():%Y%m%d-%H%M%S}-{slug}"
    print(f"🚀 [MCP -> GitHub] Creating PR on branch: {branch_name}", flush=True)
    _log_event({"tool": "create_github_pr", "args": {"branch_name": branch_name, "pr_title": pr_title}})
    try:
        subprocess.run(["git", "checkout", "-b", branch_name], check=True)
        # Defensive: clear any pre-existing index so only our MODIFIED_FILES get staged
        subprocess.run(["git", "reset", "--quiet"], check=True)
        subprocess.run(["git", "add", "--", *sorted(MODIFIED_FILES)], check=True)
        subprocess.run(["git", "commit", "-m", pr_title], check=True)
        subprocess.run(["git", "push", "-u", "origin", branch_name], check=True)
        result = subprocess.run(
            ["gh", "pr", "create", "--title", pr_title, "--body", pr_body, "--base", "master", "--head", branch_name],
            capture_output=True, text=True
        )
        if result.returncode == 0:
            return f"PR created: {result.stdout.strip()}"
        return f"Branch pushed ({branch_name}) but PR creation failed: {result.stderr.strip()}"
    except subprocess.CalledProcessError as e:
        return f"Git operation failed: {str(e)}"


tools_map = {
    "fetch_gcp_alerts": fetch_gcp_alerts,
    "codebase_search": codebase_search,
    "find_spring_endpoint": find_spring_endpoint,
    "file_reader": file_reader,
    "file_writer": file_writer,
    "apply_patch": apply_patch,
    "gradle_compile": gradle_compile,
    "run_tests": run_tests,
    "create_github_pr": create_github_pr,
}


# ==========================================
# AGENT LOOP
# ==========================================

def _assert_clean_worktree() -> None:
    """Blocks on staged/modified tracked files only. Untracked files are fine — the agent stages only files it modified via MODIFIED_FILES."""
    r = subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True, check=True)
    blocking = [line for line in r.stdout.splitlines() if not line.startswith("??")]
    if blocking:
        print("⚠️  Working tree has staged or modified tracked files:")
        print("\n".join(blocking))
        raise RuntimeError("Stash or commit changes before running the agent.")


def _load_project_rules() -> str:
    rules_path = REPO_ROOT / "CLAUDE.md"
    return rules_path.read_text(encoding="utf-8") if rules_path.exists() else ""


AFC_BUDGET = 50  # max SDK function-calling rounds; each can include parallel calls
RETRY_DELAYS = [5, 10, 15, 30]  # backoff before retry attempts 2..5; transient errors only
TRANSIENT_ERROR_MARKERS = (
    # HTTP / API status codes
    "503", "500", "502", "504", "429",
    "UNAVAILABLE", "DEADLINE_EXCEEDED", "RESOURCE_EXHAUSTED",
    # Socket / network errors
    "Connection reset", "Connection refused", "Connection aborted",
    "ReadError", "WriteError", "ReadTimeout", "ConnectTimeout",
    "ConnectionError", "ProtocolError", "RemoteDisconnected",
    "BrokenPipeError", "TimeoutError",
)


def _call_with_retry(fn, label: str = "Gemini API"):
    """Calls fn() with backoff retry on transient (5xx / 429) errors. Re-raises immediately on other errors."""
    attempts = [0, *RETRY_DELAYS]
    total = len(attempts)
    last_err = None
    for i, delay in enumerate(attempts, start=1):
        if delay:
            print(f"⏳ [Retry] Waiting {delay}s before attempt {i}/{total} to {label}...", flush=True)
            _log_event({"event": "retry_wait", "attempt": i, "delay_s": delay})
            time.sleep(delay)
        try:
            return fn()
        except Exception as e:
            last_err = e
            msg = str(e)
            transient = any(marker in msg for marker in TRANSIENT_ERROR_MARKERS)
            print(f"⚠️  [Attempt {i}/{total} failed] {type(e).__name__}: {msg[:160]}", flush=True)
            _log_event({"event": "retry_error", "attempt": i,
                        "error_type": type(e).__name__, "error": msg[:400],
                        "transient": transient})
            if not transient:
                raise
    raise last_err


def _assert_required_env() -> None:
    """Fail fast if required configuration is missing — no hidden defaults."""
    missing = [name for name in ("GCP_PROJECT", "GCP_SERVICE") if not os.environ.get(name)]
    if missing:
        raise RuntimeError(
            f"Missing required environment variables: {missing}. "
            f"Set them as GitHub Variables (Settings → Variables → Actions) "
            f"or in your .env for local runs."
        )


def run_legacy_fighter() -> None:
    _assert_required_env()
    _assert_clean_worktree()
    project_rules = _load_project_rules()
    run_id = datetime.now().strftime("%Y%m%d-%H%M%S")
    _log_event({"run_id": run_id, "event": "start"})

    base_instruction = """You are an elite autonomous Platform Engineer for 'Health-Assistant-Server'.

Operate in three named phases — print the phase banner in your thinking before acting on each:

=== PHASE 1: DIAGNOSE ===
- Call fetch_gcp_alerts to discover the anomaly.
- Use find_spring_endpoint (PREFERRED) to locate the affected controller. Fall back to codebase_search only if find_spring_endpoint returns no match.
- Read ONLY the files directly implicated by the alert. Identify the single root cause (N+1, blocking I/O, resource leak, suboptimal query).

=== PHASE 2: REFACTOR ===
- Prefer apply_patch for surgical edits. Use file_writer only when rewriting a substantial portion of a file.
- NEVER reference a class, method, or field without first verifying it exists (via file_reader or codebase_search).
- MANDATORY: after EVERY file_writer or apply_patch call, immediately call gradle_compile.
- If gradle_compile returns BUILD FAILED — read the error, fix the offending file, recompile. Loop until BUILD SUCCESS.

=== PHASE 3: SHIP ===
- Only when gradle_compile returned BUILD SUCCESS on your last attempt.
- Optionally call run_tests for the affected module before shipping.
- Call create_github_pr with a short branch_name_hint slug and detailed pr_body explaining the bottleneck and the fix.

=== SCOPE DISCIPLINE (CRITICAL) ===
The alert names ONE specific bottleneck. Fix ONLY that one bottleneck. You may touch multiple files when a single fix legitimately spans them (e.g. adding a repository query method to support the controller refactor). But you MUST NOT:
- introduce new DTOs, abstractions, or projections "while you're at it"
- refactor adjacent code that already compiles and behaves correctly
- touch files merely because codebase_search returned them as tangentially related
- "improve" code style or conventions in files outside the immediate fix
A focused fix typically touches 1–4 files. Hard cap is 8 — beyond that the run will refuse new files. After 5, you should be wrapping up, NOT exploring further.

=== HARD RULES — violations end the run ===
- NEVER call create_github_pr before gradle_compile returns BUILD SUCCESS.
- NEVER fabricate class/method names. If unsure, search/read first.
- If a tool returns "TOOL ERROR: ..." or "SCOPE LIMIT REACHED" you must adapt — do not retry blindly.
- The project conventions below are mandatory and override your defaults. Read them before writing any Java.
"""
    system_instruction = base_instruction + "\n\n=== PROJECT CONVENTIONS (CLAUDE.md) ===\n" + project_rules

    # AFC ON: SDK handles the tool-calling loop internally. Agent runs free until natural finish
    # or budget is hit. Tools self-log via _log_event so observability is preserved.
    config = types.GenerateContentConfig(
        tools=list(tools_map.values()),
        system_instruction=system_instruction,
        temperature=0.2,
        automatic_function_calling=types.AutomaticFunctionCallingConfig(maximum_remote_calls=AFC_BUDGET)
    )

    print("🤖 [Legacy Fighter] Initializing Autonomous Engineering Agent...", flush=True)
    print(f"📋 Run ID: {run_id} | AFC budget: {AFC_BUDGET} rounds | File mod cap: {MAX_FILES_MODIFIED}", flush=True)
    print("─" * 70, flush=True)

    t0 = time.time()
    try:
        response = _call_with_retry(
            lambda: client.models.generate_content(
                model=os.environ.get('GEMINI_MODEL', 'gemini-3-pro-preview'),
                contents="Execute the morning proactive system optimization routine.",
                config=config
            ),
            label="generate_content"
        )
        elapsed = time.time() - t0
        print("─" * 70, flush=True)
        print(f"\n🏁 [Agent Finished] (wall time: {elapsed:.1f}s)\n")
        print(response.text or "(no final text)")
        _log_event({"run_id": run_id, "event": "finish",
                    "wall_time_s": round(elapsed, 1),
                    "modified_files": sorted(MODIFIED_FILES),
                    "text": response.text})
    except Exception as e:
        elapsed = time.time() - t0
        print("─" * 70, flush=True)
        print(f"\n⚠️  [Agent Aborted] {type(e).__name__}: {e}", flush=True)
        _log_event({"run_id": run_id, "event": "abort",
                    "wall_time_s": round(elapsed, 1),
                    "error_type": type(e).__name__, "error": str(e),
                    "modified_files": sorted(MODIFIED_FILES)})

    print(f"\n📊 Files modified: {sorted(MODIFIED_FILES) or 'none'}", flush=True)


if __name__ == "__main__":
    run_legacy_fighter()
