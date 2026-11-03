#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 4CBOn2 — pure-JVM Kotlin verification harness (v0.3)
#
# Why this exists:
#   The canonical Android verification path is `./gradlew :app:testDebugUnitTest`, but a fresh
#   sandbox needs `dl.google.com` for the Android SDK, which some environments block. Kotlin and
#   a JDK are nevertheless available from PyPI / npm, and every 4CBOn2 core module is deliberately
#   free of `android.*` imports. This script therefore compiles those modules plus the real JUnit
#   test sources (against a tiny `org.junit` shim) and executes them on a plain JVM.
#
#   It is a *sandbox convenience*, never a replacement: run Gradle before packaging an APK.
#
# Usage:
#   scripts/verify_pure_kotlin.sh              # compile + run every pure-JVM JUnit test
#   scripts/verify_pure_kotlin.sh --compile    # compile only (type-check the whole core)
# ---------------------------------------------------------------------------
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${HOME}/.cache/4cbon2-pure-kotlin"
OUT="${CACHE}/build"
KOTLIN_VERSION="2.4.21"
JDK4PY_VERSION="25.0.2.1"

MAIN_SRC="${REPO_ROOT}/android/app/src/main/java/io/github/mohamtur1/fourcbon2"
TEST_SRC="${REPO_ROOT}/android/app/src/test/java/io/github/mohamtur1/fourcbon2"
TOOL_SRC="${REPO_ROOT}/tools/pure_jvm_verify"

mkdir -p "${CACHE}" "${OUT}"

# --- 1. JDK (JRE image is enough: kotlinc does not need javac for pure Kotlin) ----
JDK_DIR="${CACHE}/jdk4py/jdk4py/java-runtime"
if [ ! -x "${JDK_DIR}/bin/java" ]; then
    echo "[verify] provisioning JDK ${JDK4PY_VERSION} from PyPI..."
    rm -rf "${CACHE}/jdk4py" "${CACHE}/jdkdl"
    mkdir -p "${CACHE}/jdkdl"
    python3 -m pip download "jdk4py==${JDK4PY_VERSION}" --no-deps -d "${CACHE}/jdkdl" >/dev/null
    unzip -q -o "${CACHE}"/jdkdl/jdk4py-*.whl -d "${CACHE}/jdk4py"
fi
export JAVA_HOME="${JDK_DIR}"
export PATH="${JAVA_HOME}/bin:${PATH}"

# --- 2. Kotlin compiler (from npm; the official tarball lives behind blocked hosts) --
KOTLINC="${CACHE}/kotlinc/package/bin/kotlinc"
if [ ! -x "${KOTLINC}" ]; then
    echo "[verify] provisioning Kotlin ${KOTLIN_VERSION} compiler from npm..."
    rm -rf "${CACHE}/kotlinc" "${CACHE}/pkg"
    mkdir -p "${CACHE}/pkg"
    ( cd "${CACHE}/pkg" && npm pack "kotlin-compiler@${KOTLIN_VERSION}" --silent >/dev/null )
    mkdir -p "${CACHE}/kotlinc"
    tar -xzf "${CACHE}"/pkg/kotlin-compiler-*.tgz -C "${CACHE}/kotlinc"
    chmod +x "${CACHE}"/kotlinc/package/bin/* || true
fi
STDLIB="${CACHE}/kotlinc/package/lib"

# --- 3. Source selection: every Kotlin file that never imports android.* ----------
echo "[verify] selecting pure-JVM sources..."
python3 - "$MAIN_SRC" "$TEST_SRC" "$TOOL_SRC" "${OUT}" <<'PY'
import pathlib, sys

main_src, test_src, tool_src, out = map(pathlib.Path, sys.argv[1:5])

def pure(path: pathlib.Path) -> bool:
    return "import android." not in path.read_text(encoding="utf-8")

# FourCBOn2WorkspaceContainer.kt imports android.* only inside its Android factory companion,
# which the compile-only stubs in tools/pure_jvm_verify satisfy; its pure constructor is what the
# real tests drive, so it belongs in the harness build.
EXTRA = {
    "FourCBOn2WorkspaceContainer.kt",
    "ScopedWorkspaceFileManager.kt",
}
main = sorted(
    p for p in main_src.glob("*.kt")
    if pure(p) or p.name in EXTRA
)
# LocalDatabase.kt is pure after the v0.3 split; AndroidSqliteBackend.kt is excluded automatically.
tests = sorted(p for p in test_src.glob("*.kt") if pure(p))
tools = sorted(p for p in tool_src.glob("*.kt"))

skipped = [p.name for p in main_src.glob("*.kt") if not pure(p) and p.name not in EXTRA]
(out / "sources.rsp").write_text("\n".join(str(p) for p in main + tests + tools) + "\n", encoding="utf-8")
(out / "main_count.txt").write_text(f"{len(main)} {len(tests)} {len(tools)}", encoding="utf-8")
print(f"        {len(main)} core modules, {len(tests)} test classes, {len(tools)} harness files")
if skipped:
    print(f"        skipped (android.*): {', '.join(skipped)}")
PY

# --- 4. Compile ----------------------------------------------------------------
echo "[verify] compiling..."
rm -rf "${OUT}/classes"
mkdir -p "${OUT}/classes"
"${KOTLINC}" -nowarn -jvm-target 17 \
    -classpath "${OUT}/classes" \
    -d "${OUT}/classes" \
    @"${OUT}/sources.rsp" 2>&1 | grep -v "^warning:" || true

if [ ! -d "${OUT}/classes/io" ]; then
    echo "[verify] COMPILATION FAILED" >&2
    exit 1
fi
echo "[verify] compilation OK"

if [ "${1:-}" = "--compile" ]; then
    exit 0
fi

# --- 5. Execute real @Test methods --------------------------------------------
echo "[verify] running tests..."
java -cp "${OUT}/classes:${STDLIB}/kotlin-stdlib.jar:${STDLIB}/kotlin-stdlib-jdk8.jar:${STDLIB}/kotlin-stdlib-jdk7.jar" \
    io.github.mohamtur1.fourcbon2.tooling.PureJvmTestRunnerKt "${OUT}/classes" \
    | tee "${OUT}/test-report.txt"
