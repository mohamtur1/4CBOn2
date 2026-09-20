# PAYLOAD — what this folder is, and where each file goes

Read this, then do not copy this file into the new repository. It is a manifest, not content.

This folder (`uli_transplant/`) is the **exact payload** for the first commit of
`mohamtur1/Universal_Learner`. It lives in `mohamtur1/4CBOn2` on branch
`arena/01a0bcfe-4cbon2`, which is where the ULI work was authored.

## Mapping

| In this folder | Goes to (new repo) | Notes |
|---|---|---|
| `README.md` | `README.md` | The project's front door. If GitHub auto-created a README when the repo was made, **replace it** — do not merge the two. |
| `.gitignore` | `.gitignore` | Note the leading dot. `cp uli_transplant/.gitignore .` |
| `docs/ULI_GREENLIGHT.md` | `docs/ULI_GREENLIGHT.md` | Frozen snapshot of the reviewed decisions (D1–D10). |
| `docs/ULI_WALKTHROUGH.md` | `docs/ULI_WALKTHROUGH.md` | **The master document.** The whole project arc M0–M11, rulings R1–R17, acceptance tests, glossary. This is the authoritative build contract. |
| `docs/SESSION_START.md` | `docs/SESSION_START.md` | Onboarding contract for every future working session. |
| `PAYLOAD.md` | *(nothing)* | You are reading it. Leave it behind. |

## Copy commands

```bash
# from the root of a clone of Universal_Learner
SRC=/tmp/uli-payload            # wherever the payload was fetched to
cp    "$SRC/README.md"      README.md
cp    "$SRC/.gitignore"     .gitignore
mkdir -p docs
cp    "$SRC/docs/"*.md      docs/
```

## Two things to preserve

1. **The two documents are the record of a real review.** They contain decisions that were argued
   for and against, plus a pre-registered list of the project's own weakest claims. Do not
   summarise, "clean up", or modernise them on the way in — transplant byte-for-byte, then let
   future work amend them through rulings.
2. **The originals stay in `4CBOn2`.** That repository is the record of the session that produced
   them. From the moment of transplant, `Universal_Learner` is the home and the living copy; the
   `4CBOn2` copies are frozen history and should not be edited again.
