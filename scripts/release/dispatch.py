"""Dispatch publishing only after GitHub's Release body matches the edited file.

First edit the Release with gh release edit --notes-file. This command verifies
the exact UTF-8 body, binds its hash, and dispatches the trusted default-branch
workflow. Without --publish it dispatches only a read-only preview.
"""
import argparse
import json
import os
from pathlib import Path
import sys

import publish as p


def dispatch(gh, config, run_id, notes, platform, publish, options=None):
    plan, _ = p.make_plan(gh, config, run_id, options)
    expected = p.notes_digest(notes)
    p.validate_final_notes(plan, expected)
    p.verify_current_notes(gh, plan)
    # Send resolved choices, not a local filename or mutable defaults, to CI.
    selected = {key: plan[key] for key in ("game_versions", "java_versions", "include_sources")}
    inputs = {"run_id": str(run_id), "platform": platform, "dry_run": "false" if publish else "true", "notes_sha256": expected, "options_json": p.canonical(selected).decode()}
    p.request("POST", gh.base + "/actions/workflows/publish-platforms.yml/dispatches",
              headers={**gh.headers, "Content-Type": "application/json"},
              data=p.canonical({"ref": config["default_branch"], "inputs": inputs}))
    return inputs


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True, type=int)
    parser.add_argument("--notes-file", required=True, type=Path)
    parser.add_argument("--platform", choices=["both", "modrinth", "curseforge"], default="both")
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--options-file", type=Path, help="JSON file selecting game_versions, java_versions and include_sources")
    args = parser.parse_args(argv)
    try:
        token = os.environ.get("GH_TOKEN", "")
        p.require(bool(token), "GH_TOKEN is required to dispatch the workflow")
        config = json.loads((p.ROOT / ".github/release-targets.json").read_text())
        # Preserve line endings: hash exactly what gh uploaded, not a normalized
        # or subsequently re-read/generated version of the notes.
        notes = args.notes_file.read_bytes().decode("utf-8")
        options = json.loads(args.options_file.read_text(encoding="utf-8")) if args.options_file else None
        result = dispatch(p.GitHub(config["repository"], token), config, args.run_id, notes, args.platform, args.publish, options)
        print(json.dumps(result, indent=2))
        print("Dispatched. Follow the run at https://github.com/AzusaKe/Halo/actions/workflows/publish-platforms.yml")
        return 0
    except (p.PublishError, ValueError, KeyError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
