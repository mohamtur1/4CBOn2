#!/usr/bin/env python3
"""
preflight_disk_uv.py -- diagnose the Lever-2 "section 5 (Start serving)" failure

The launcher dies on step 2 of 3:

    uv pip install --python <PREFIX>/venv/bin/python --no-index \
        --find-links <DATASET>/wheels -r <DATASET>/requirements.lock

It is rebuilt here from the spec that was recorded in an earlier thread
(original sha256 28d36fb1..., 373 lines, not on GitHub). It answers one
question: is that failure DISK (ENOSPC), a MISSING/UNRESOLVABLE wheel under
--no-index, a PYTHON/UV mismatch, PERMISSION, or a CORRUPT artifact?

It never touches the real install target: it refuses to run if --test-prefix
resolves to /tmp/sgl-intel or /kaggle/working/sgl-intel.

Usage on Kaggle, in a cell BEFORE the launcher cell:

    %run preflight_disk_uv.py --wheelhouse /kaggle/input/pennyroyal-v253 --try-install

or, to probe the relocation target's headroom:

    %run preflight_disk_uv.py --wheelhouse /kaggle/input/pennyroyal-v253 \
         --test-prefix /kaggle/working/sgl-probe --try-install --keep

Exit codes:  0 = clean   1 = blocker found   2 = could not run
"""

import argparse
import os
import re
import shutil
import stat
import subprocess
import sys
import zipfile
from pathlib import Path

GIB = 1024 ** 3
LOW_DISK_GIB = 6.0
FORBIDDEN_PREFIXES = ("/tmp/sgl-intel", "/kaggle/working/sgl-intel")

# environment the launcher forces (offline Kaggle, internet OFF)
LAUNCHER_ENV = {
    "UV_OFFLINE": "1",
    "UV_PYTHON_DOWNLOADS": "never",
    "PYTHONNOUSERSITE": "1",
    "UV_NO_CACHE": "1",
}

VERDICT_DISK = "ENOSPC -- relocate PREFIX"
VERDICT_WHEEL = "missing/unresolvable wheel -- relocating PREFIX will not fix it"
VERDICT_PY = "python/uv mismatch -- not a disk problem"
VERDICT_PERM = "write permission / read-only filesystem"
VERDICT_CORRUPT = "corrupt artifact -- re-upload or re-pin the dataset version"
VERDICT_OK = "install succeeded -- the section-5 failure was environmental to that run"

_signatures = [
    (VERDICT_DISK, ("no space left on device", "enospc", "errno 28",
                    "no space left", "insufficient space", "disk quota exceeded",
                    "edquot", "errno 122")),
    (VERDICT_PERM, ("permission denied", "errno 13", "eacces", "read-only file system",
                    "ero fs", "erofs", "operation not permitted", "errno 1")),
    (VERDICT_PY, ("requires-python", "requires a different python", "unsupported python",
                  "no interpreter found", "does not support python", "incompatible with",
                  "python version", "abi tag", "platform tag", "no python",
                  "requested python", "python interpreter")),
    (VERDICT_WHEEL, ("no solution found", "no matching distribution", "not found",
                     "does not have", "cannot find", "could not find", "no distributions",
                     "package metadata", "unsatisfiable", "no wheel")),
    (VERDICT_CORRUPT, ("bad zip", "not a zip", "invalid wheel", "invalid bdist",
                       "corrupt", "truncated", "unexpected end of", "hash mismatch",
                       "digest mismatch", "invalid", "cannot open", "is not a valid")),
]


def log(msg=""):
    print(msg, flush=True)


def head(title):
    log()
    log("=" * 62)
    log(title)
    log("=" * 62)


def human(nbytes):
    return f"{nbytes / GIB:7.2f} GiB"


# --------------------------------------------------------------------- disk
def statvfs_report(paths):
    rows = []
    seen = set()
    for p in paths:
        try:
            rp = str(Path(p).resolve())
        except OSError:
            rp = str(p)
        if rp in seen:
            continue
        seen.add(rp)
        st = Path(p)
        if not st.exists():
            rows.append({"path": p, "exists": False})
            continue
        try:
            v = os.statvfs(p)
        except OSError as e:
            rows.append({"path": p, "exists": True, "error": str(e)})
            continue
        total = v.f_blocks * v.f_frsize
        free = v.f_bfree * v.f_frsize
        avail = v.f_bavail * v.f_frsize
        rows.append({
            "path": p, "exists": True, "total": total, "free": free,
            "avail": avail,
            "pct_used": (100.0 * (total - free) / total) if total else 0.0,
            "low": avail < LOW_DISK_GIB * GIB,
            "writable": os.access(p, os.W_OK),
        })
    return rows


def print_disk(rows):
    head("1. DISK (statvfs) -- anything under %.0f GiB avail is flagged" % LOW_DISK_GIB)
    low = []
    for r in rows:
        if not r.get("exists"):
            log(f"  [  n/a ] {r['path']}  (does not exist)")
            continue
        if "error" in r:
            log(f"  [  err ] {r['path']}  statvfs: {r['error']}")
            continue
        flag = "LOW " if r["low"] else " ok "
        mark = "  <-- FLAG" if r["low"] else ""
        wr = "" if r["writable"] else "  [NOT WRITABLE]"
        log(f"  [ {flag}] {r['path']}")
        log(f"          avail {human(r['avail'])}  free {human(r['free'])}"
            f"  total {human(r['total'])}  used {r['pct_used']:.1f}%{wr}{mark}")
        if r["low"]:
            low.append(r["path"])
    return low


# ------------------------------------------------------------ find_unique
def find_unique(root, kind, name):
    """kind='dir' or 'file'; returns (path|None, all_matches)."""
    hits = []
    root = Path(root)
    if not root.exists():
        return None, []
    for dirpath, dirnames, filenames in os.walk(root):
        if kind == "dir" and name in dirnames:
            hits.append(Path(dirpath) / name)
        elif kind == "file" and name in filenames:
            hits.append(Path(dirpath) / name)
    return (hits[0] if len(hits) == 1 else None), hits


def norm(name):
    """PEP 503 name normalisation."""
    return re.sub(r"[-_.]+", "-", name).strip().lower()


def wheel_identity(filename):
    """'foo_bar-1.2.3-py3-none-any.whl' -> ('foo-bar', '1.2.3')"""
    stem = filename[:-4] if filename.endswith(".whl") else filename
    parts = stem.split("-")
    if len(parts) < 2:
        return None, None
    return norm(parts[0]), parts[1]


def audit_wheelhouse(wheelhouse):
    """Replicates the launcher's find_unique + one-of-each invariants."""
    head("2. WHEELHOUSE AUDIT (launcher find_unique replication)")
    log(f"  wheelhouse root: {wheelhouse}")
    problems = []
    wheels_dir, wd_hits = find_unique(wheelhouse, "dir", "wheels")
    lock, lock_hits = find_unique(wheelhouse, "file", "requirements.lock")

    if len(wd_hits) != 1:
        problems.append(f"expected exactly 1 'wheels/' dir, found {len(wd_hits)}")
        log(f"  [FAIL] wheels/ dirs found: {len(wd_hits)} (expected 1)")
    else:
        log(f"  [ ok ] wheels/ -> {wheels_dir}")
    if len(lock_hits) != 1:
        problems.append(f"expected exactly 1 requirements.lock, found {len(lock_hits)}")
        log(f"  [FAIL] requirements.lock found: {len(lock_hits)} (expected 1)")
    else:
        log(f"  [ ok ] requirements.lock -> {lock}")

    if wheels_dir is None:
        return None, None, problems

    all_whl = sorted(wheels_dir.glob("*.whl"))
    total = sum(f.stat().st_size for f in all_whl)
    log(f"  [info] wheel count: {len(all_whl)}   total wheelhouse size: {human(total)}")

    sglang = sorted(wheels_dir.glob("sglang-*.whl"))
    uvwhl = sorted(wheels_dir.glob("uv-*.whl"))
    for label, hits in (("sglang-*.whl", sglang), ("uv-*.whl", uvwhl)):
        if len(hits) != 1:
            problems.append(f"expected exactly 1 {label}, found {len(hits)}")
            log(f"  [FAIL] {label}: {len(hits)} (expected 1)")
        else:
            log(f"  [ ok ] {label}: {hits[0].name}  ({hits[0].stat().st_size / 1e6:.1f} MB)")
    return (wheels_dir, lock), {"wheels": all_whl, "total": total,
                                "sglang": sglang, "uv": uvwhl}, problems


# --------------------------------------------------------------- lock check
def parse_lock(lock_path):
    reqs = []
    if lock_path is None:
        return reqs
    for raw in lock_path.read_text(errors="replace").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line or line.startswith("-"):
            continue
        line = line.split(";", 1)[0].strip()          # drop env marker
        if "==" not in line:
            continue
        name, ver = line.split("==", 1)
        name = re.sub(r"\[.*?\]", "", name).strip()   # drop extras
        ver = ver.strip()
        if name:
            reqs.append((norm(name), ver, line))
    return reqs


def cross_check_lock(reqs, wheels):
    head("3. LOCK vs WHEELHOUSE (missing-wheel check BEFORE uv runs)")
    by_name = {}
    for w in wheels:
        n, v = wheel_identity(w.name)
        if n:
            by_name.setdefault(n, []).append((v, w.name))
    missing, version_gap, ok = [], [], 0
    for name, ver, raw in reqs:
        hits = by_name.get(name)
        if not hits:
            missing.append((name, ver, raw))
        elif all(v != ver for v, _ in hits):
            version_gap.append((name, ver, [h[0] for h in hits], raw))
        else:
            ok += 1
    log(f"  lock pins: {len(reqs)}   satisfied by a wheel: {ok}")
    if missing:
        log(f"  [FAIL] {len(missing)} pin(s) with NO wheel in --find-links:")
        for n, v, raw in missing[:25]:
            log(f"         - {raw}   (looking for {n}=={v})")
        if len(missing) > 25:
            log(f"         ... and {len(missing) - 25} more")
    else:
        log("  [ ok ] every lock pin has a wheel in the wheelhouse")
    if version_gap:
        log(f"  [FAIL] {len(version_gap)} pin(s) present under a DIFFERENT version:")
        for n, v, av, raw in version_gap[:25]:
            log(f"         - {raw}   (wheelhouse has {n} {', '.join(av)})")
    return missing, version_gap


# --------------------------------------------------------------- uv extract
def extract_uv(uv_wheel, dest_dir):
    head("4. BUNDLED uv (extracted the way the launcher does it)")
    dest_dir.mkdir(parents=True, exist_ok=True)
    try:
        return _extract_uv_inner(uv_wheel, dest_dir)
    except OSError as e:
        # ENOSPC / EROFS / EACCES while materialising the 40 MB binary is the
        # same diagnosis as failing later -- it must not traceback.
        log(f"  [FAIL] could not materialise the uv binary: {e}")
        return None, str(e)
    except (zipfile.BadZipFile, KeyError, EOFError) as e:
        log(f"  [FAIL] uv wheel is not readable: {e}")
        return None, str(e)


def _extract_uv_inner(uv_wheel, dest_dir):
    with zipfile.ZipFile(uv_wheel) as z:
        elf_member = None
        for info in z.infolist():
            if info.is_dir():
                continue
            with z.open(info) as fh:
                if fh.read(4) == b"\x7fELF":
                    elf_member = info
                    break
        if elf_member is None:
            log("  [FAIL] no \\x7fELF member inside the uv wheel -- corrupt artifact")
            return None, "no ELF member in uv wheel"
        log(f"  [ ok ] ELF magic found in: {elf_member.filename} "
            f"({elf_member.file_size / 1e6:.1f} MB)")
        out = dest_dir / "uv"
        with z.open(elf_member) as src, open(out, "wb") as dst:
            shutil.copyfileobj(src, dst, length=1024 * 1024)
    out.chmod(out.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    env = launcher_env()
    r = subprocess.run([str(out), "-V"], capture_output=True, text=True, env=env)
    log(f"  [info] uv -V -> {r.stdout.strip() or r.stderr.strip()}  (rc={r.returncode})")
    if r.returncode != 0:
        log("  [FAIL] extracted uv will not execute -- corrupt artifact or wrong arch")
        return None, (r.stdout + r.stderr)
    return out, ""


def launcher_env():
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    env.pop("VIRTUAL_ENV", None)
    env.update(LAUNCHER_ENV)
    return env


# --------------------------------------------------------------- venv + try
def uv_venv(uv, venv_dir):
    head("5. uv venv --python sys.executable (proves/disproves a uv-python mismatch)")
    env = launcher_env()
    cmd = [str(uv), "venv", "--python", sys.executable, str(venv_dir)]
    log(f"  $ {' '.join(cmd)}")
    r = subprocess.run(cmd, capture_output=True, text=True, env=env)
    tail = (r.stdout + r.stderr).strip().splitlines()[-12:]
    for ln in tail:
        log(f"    | {ln}")
    log(f"  rc={r.returncode}")
    if r.returncode != 0:
        return None, (r.stdout + r.stderr)
    py = venv_dir / "bin" / "python"
    r2 = subprocess.run([str(py), "-V"], capture_output=True, text=True, env=env)
    log(f"  [ ok ] venv interpreter: {r2.stdout.strip()}  ({py})")
    return py, ""


def try_install(uv, venv_py, wheels_dir, lock):
    head("6. REPLAY of the failing launcher command (throwaway venv, FULL stderr)")
    env = launcher_env()
    cmd = [str(uv), "pip", "install",
           "--python", str(venv_py),
           "--no-index",
           "--find-links", str(wheels_dir),
           "-r", str(lock)]
    log(f"  $ {' '.join(cmd)}")
    log("  " + "-" * 58)
    buf = []
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, env=env, bufsize=1)
    assert proc.stdout is not None
    for line in proc.stdout:
        buf.append(line)
        log(f"    | {line.rstrip()}")
    rc = proc.wait()
    log("  " + "-" * 58)
    log(f"  rc={rc}   (untruncated: {len(buf)} lines)")
    return rc, "".join(buf)


# --------------------------------------------------------------- diagnosis
def classify(text, low_disks):
    """Signature-match a captured stderr against the five known causes."""
    low = (text or "").lower()
    for verdict, keys in _signatures:
        if any(k in low for k in keys):
            return verdict
    if low_disks:
        return VERDICT_DISK + f" (low-disk paths: {', '.join(low_disks)})"
    return (VERDICT_WHEEL + " / UNCLASSIFIED -- no known signature matched; "
            "read the streamed stderr above")


def diagnose(rc, text, missing, version_gap, problems, low_disks):
    head("VERDICT")
    if rc != 0:
        # our own prechecks are stronger evidence than stderr grepping
        if missing or version_gap:
            return VERDICT_WHEEL
        return classify(text, low_disks)
    if problems:
        return "wheelhouse invariants broken: " + "; ".join(problems)
    return VERDICT_OK


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--wheelhouse", default="/kaggle/input/pennyroyal-v253")
    ap.add_argument("--working", default="/kaggle/working")
    ap.add_argument("--test-prefix", default="/tmp/uv-preflight")
    ap.add_argument("--try-install", action="store_true",
                    help="replay the failing command, stream untruncated stderr")
    ap.add_argument("--keep", action="store_true", help="keep the scratch dir")
    args = ap.parse_args()

    wheelhouse = Path(args.wheelhouse)
    test_prefix = Path(args.test_prefix)

    # ---- safety: never operate on the real install target
    resolved = str(test_prefix.expanduser().resolve())
    for bad in FORBIDDEN_PREFIXES:
        if resolved == bad or resolved.startswith(bad + os.sep) or \
           str(test_prefix) == bad:
            log(f"REFUSING to run: --test-prefix resolves to the real install "
                f"target ({bad}). Pick a throwaway path.")
            sys.exit(2)

    head("0. SETUP")
    log(f"  python : {sys.executable}  ({sys.version.split()[0]})")
    log(f"  cwd    : {os.getcwd()}")
    log(f"  scratch: {test_prefix}   (keep={args.keep})")

    if not wheelhouse.exists():
        log(f"\nERROR: wheelhouse not found: {wheelhouse}")
        log("  check the dataset is attached as an input to this notebook")
        sys.exit(2)

    # ---- 1. disk
    paths = ["/", "/tmp", args.working, "/kaggle/input",
             str(wheelhouse), str(test_prefix.parent)]
    low_disks = print_disk(statvfs_report(paths))

    # ---- 2. wheelhouse audit
    (wheels_dir, lock), wh, problems = audit_wheelhouse(wheelhouse)

    if wheels_dir is None or lock is None or not wh.get("uv"):
        head("VERDICT")
        log("  could not run: wheelhouse layout does not match the launcher's "
            "expectations")
        for p in problems:
            log(f"    - {p}")
        sys.exit(2)

    # ---- 3. lock vs wheels
    missing, version_gap = cross_check_lock(parse_lock(lock), wh["wheels"])
    if missing or version_gap:
        problems.append("lock pins not covered by the wheelhouse")

    # ---- 4. uv
    if test_prefix.exists():
        shutil.rmtree(test_prefix, ignore_errors=True)
    test_prefix.mkdir(parents=True, exist_ok=True)
    uv, uv_err = extract_uv(wh["uv"][0], test_prefix / "bin")
    if uv is None:
        head("VERDICT")
        log("  " + classify(uv_err, low_disks) + " (failed extracting the uv binary)")
        if not args.keep:
            shutil.rmtree(test_prefix, ignore_errors=True)
        sys.exit(1)

    # ---- 5. venv
    venv_py, venv_err = uv_venv(uv, test_prefix / "venv")
    if venv_py is None:
        head("VERDICT")
        log("  " + classify(venv_err, low_disks) + " (failed creating the venv)")
        if not args.keep:
            shutil.rmtree(test_prefix, ignore_errors=True)
        sys.exit(1)

    # ---- 6. optional replay
    rc, text = 0, ""
    if args.try_install:
        rc, text = try_install(uv, venv_py, wheels_dir, lock)
    else:
        head("6. REPLAY skipped (pass --try-install to replay the failing command)")

    verdict = diagnose(rc, text, missing, version_gap, problems, low_disks)
    log(f"  {verdict}")
    if low_disks:
        log(f"  low-disk paths: {', '.join(low_disks)}")

    if not args.keep:
        shutil.rmtree(test_prefix, ignore_errors=True)
        log(f"\n  scratch removed: {test_prefix}")
    else:
        log(f"\n  scratch kept: {test_prefix}")

    if rc != 0 or missing or version_gap or problems:
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()
