#!/usr/bin/env python3
"""Measure a Python install the way Vercel's bundle limit sees it.

Vercel hard-fails a build above 500 MB per function, and it bundles what is
*shipped*, not what a local `pip install` leaves behind. A fresh venv carries
~112 MB of __pycache__ bytecode that never reaches a lambda; counting it
overstates the bundle by roughly 20% and produces a false "over budget" alarm.

Usage:
    python3 tools/measure_bundle.py <path-to-site-packages>

Prints the shipped estimate, the cap, and the margin. Exit 1 if over budget.
"""
import os
import sys

CAP_MB = 500.0
# The header of vercel/requirements.txt records one real Vercel build:
# a 400.3 MB shipped install reported as ~421 MB by Vercel's block accounting.
BLOCK_ACCOUNTING_RATIO = 421.0 / 400.3

# Directories and files that exist after `pip install` but are not shipped.
EXCLUDE_DIR_NAMES = {"__pycache__", "tests", "test", "typing_extensions-stubs"}
EXCLUDE_SUFFIXES = (".pyc", ".pyo")


def measure(root):
    """Classify every file. Nothing is pruned from the walk, so the counts add
    up and `total` really is everything on disk."""
    total = shipped = pycache = excluded_other = 0
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            path = os.path.join(dirpath, fn)
            try:
                size = os.path.getsize(path)
            except OSError:
                continue
            total += size
            parts = os.path.relpath(path, root).split(os.sep)
            if "__pycache__" in parts or fn.endswith(EXCLUDE_SUFFIXES):
                pycache += size
            elif any(part in EXCLUDE_DIR_NAMES for part in parts[:-1]):
                excluded_other += size
            else:
                shipped += size
    return total, shipped, pycache, excluded_other


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    root = sys.argv[1]
    if not os.path.isdir(root):
        print(f"error: {root} is not a directory")
        return 2

    total, shipped, pycache, excluded_other = measure(root)
    shipped_mb = shipped / 1e6
    projected_mb = shipped_mb * BLOCK_ACCOUNTING_RATIO
    margin_mb = CAP_MB - projected_mb
    mb = lambda x: f"{x:7.1f} MB"

    print(f"install as it sits on disk   {mb(total / 1e6)}")
    print(f"  __pycache__ / .pyc         {mb(pycache / 1e6)}   (never shipped)")
    print(f"  tests, stubs               {mb(excluded_other / 1e6)}   (never shipped)")
    print(f"shipped estimate             {mb(shipped_mb)}")
    print(f"Vercel block accounting      {mb(projected_mb)}   (x{BLOCK_ACCOUNTING_RATIO:.3f})")
    print(f"cap                          {mb(CAP_MB)}")
    print(f"margin                       {mb(margin_mb)}"
          f"   {'OK' if margin_mb > 0 else 'OVER BUDGET'}")
    if margin_mb <= 0:
        print("\nThe build will fail. Trim a dependency — see the header of")
        print("vercel/requirements.txt for what is banned and why.")
    return 1 if margin_mb <= 0 else 0


if __name__ == "__main__":
    sys.exit(main())
