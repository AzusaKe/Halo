"""Publish verified Halo GitHub Releases. Python 3.11+, standard library only.

Default mode is read-only. No code from a release tag is executed. GitHub
Release assets store immutable upload intents and receipts so an ambiguous
CurseForge response cannot cause an automatic duplicate on the next run.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
USER_AGENT = "AzusaKe/Halo release-publisher (https://github.com/AzusaKe/Halo)"
TAG = re.compile(r"v(?P<version>\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)-(?P<loader>fabric|forge|neoforge)-(?P<game>[0-9.]+)-adapter\.(?P<revision>[1-9]\d*)")


class PublishError(Exception):
    pass


class ApiError(PublishError):
    def __init__(self, method, url, status):
        self.status = status
        # Do not include response bodies, authorization headers or query values.
        super().__init__(f"{method} {urllib.parse.urlsplit(url).path}: HTTP {status}")


def require(condition, message):
    if not condition:
        raise PublishError(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()


def notes_digest(body):
    return hashlib.sha256(body.encode("utf-8")).hexdigest()


def validate_final_notes(plan, expected):
    require(bool(re.fullmatch(r"[0-9a-f]{64}", expected)), "Final Release notes SHA-256 is required; edit notes first, then use scripts/release/dispatch.py")
    require(notes_digest(plan["changelog"]) == expected, "Release notes differ from the finalized body; no upload is allowed")


def verify_current_notes(gh, plan):
    # Always publish the authorized snapshot. Recheck before each destination so
    # a queued run or later edit cannot silently select another body.
    release = gh.api(f'/releases/{plan["release_id"]}')
    require(release["tag_name"] == plan["tag"] and not release["draft"], "Release identity/status changed after finalization")
    require(release.get("body") == plan["changelog"], "Release notes changed after finalization; stop and finalize again")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(method, url, *, headers=None, data=None, binary=False, public_download=False):
    headers = {"User-Agent": USER_AGENT, **(headers or {})}
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    # Only unauthenticated public asset downloads may follow redirects.
    opener = urllib.request.build_opener() if public_download else urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(req, timeout=60) as response:
            raw = response.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024, "Response exceeds the 64 MiB publisher limit")
        return raw if binary else (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raise ApiError(method, url, error.code) from None
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise PublishError(f"{method} {urllib.parse.urlsplit(url).path}: network failure ({type(error).__name__}); no automatic retry") from None


def multipart(field, metadata, filename, content, file_field="file", attachments=()):
    boundary = "halo-" + uuid.uuid4().hex
    require(re.fullmatch(r"[A-Za-z0-9.+_-]+\.jar", filename), "Unsafe JAR filename")
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\nContent-Type: application/json\r\n\r\n'.encode()
        + canonical(metadata)
    )
    for part, name, data in [(file_field, filename, content), *attachments]:
        require(re.fullmatch(r"[A-Za-z0-9.+_-]+\.jar", name), "Unsafe attachment filename")
        require(re.fullmatch(r"[a-z_]+", part), "Unsafe multipart field")
        body += f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="{part}"; filename="{name}"\r\nContent-Type: application/java-archive\r\n\r\n'.encode() + data
    body += f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


class GitHub:
    def __init__(self, repository, token):
        require(repository == "AzusaKe/Halo", "Publishing is restricted to AzusaKe/Halo")
        self.repository = repository
        self.base = f"https://api.github.com/repos/{repository}"
        self.headers = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
        if token:
            self.headers["Authorization"] = f"Bearer {token}"

    def api(self, path, method="GET"):
        return request(method, self.base + path, headers=self.headers)

    def pages(self, path, key=None):
        items = []
        separator = "&" if "?" in path else "?"
        for page in range(1, 101):
            result = self.api(f"{path}{separator}per_page=100&page={page}")
            batch = result[key] if key else result
            items.extend(batch)
            if len(batch) < 100:
                return items
        raise PublishError("GitHub pagination limit exceeded")

    def file(self, sha, path):
        data = self.api(f"/contents/{path}?ref={sha}")
        require(data.get("encoding") == "base64", "Unexpected GitHub source encoding")
        return base64.b64decode(data["content"]).decode()

    def assets(self, release_id):
        return self.pages(f"/releases/{release_id}/assets")

    def download(self, tag, name):
        url = f"https://github.com/{self.repository}/releases/download/{urllib.parse.quote(tag, safe='')}/{urllib.parse.quote(name, safe='')}"
        return request("GET", url, binary=True, public_download=True)

    def record(self, release_id, name, value):
        # GitHub rejects duplicate asset names. Never use --clobber or overwrite.
        url = f"https://uploads.github.com/repos/{self.repository}/releases/{release_id}/assets?name={urllib.parse.quote(name, safe='')}"
        return request("POST", url, headers={**self.headers, "Content-Type": "application/json"}, data=canonical(value))


def target_for(config, tag):
    match = TAG.fullmatch(tag)
    require(match is not None, "Not an allowed Halo platform release tag")
    values = match.groupdict()
    matches = [t for t in config["targets"] if t["loader"] == values["loader"] and t["tag_game"] == values["game"]]
    require(len(matches) == 1, "Tag is not in the maintained platform allowlist")
    return values, matches[0]


def validate_run(run, repository):
    require(run.get("repository", {}).get("full_name") == repository, "CI repository mismatch")
    require(run.get("head_repository", {}).get("full_name") == repository, "Fork builds cannot publish")
    require(run.get("event") == "push", "Only tag push builds can publish")
    require(run.get("status") == "completed" and run.get("conclusion") == "success", "CI has not completed successfully")
    require(run.get("path") == ".github/workflows/gradle.yml", "Unexpected source workflow")
    require(re.fullmatch(r"[0-9a-f]{40}", run.get("head_sha", "")), "Invalid CI commit SHA")


def properties(text):
    return dict(line.split("=", 1) for line in (line.strip() for line in text.splitlines()) if line and not line.startswith("#") and "=" in line)


def modrinth_version_number(plan):
    # Modrinth limits version_number to 32 characters; full Halo tags can exceed
    # that limit. Keep loader/game/revision in compact SemVer build metadata.
    value = f'{plan["version"]}+{plan["loader"]}.{plan.get("build_game", plan["game_versions"][0])}.a{plan["revision"]}'
    require(len(value) <= 32, "Modrinth version number exceeds 32 characters; choose a shorter version")
    return value


def inspect_jar(content, plan):
    expected_version = f'{plan["version"]}+adapter.{plan["revision"]}'
    with zipfile.ZipFile(io.BytesIO(content)) as jar:
        def read(name):
            require(jar.namelist().count(name) == 1, f"Missing or duplicate JAR entry: {name}")
            require(jar.getinfo(name).file_size < 1024 * 1024, f"Oversized JAR metadata: {name}")
            return jar.read(name).decode()
        provenance = json.loads(read("halo-build.json"))
        require(provenance.get("development") is False, "Development JAR cannot publish")
        for field, expected in {"haloCommit": plan["halo_sha"], "coreCommit": plan["core_sha"], "coreLock": plan["core_sha"], "coreVersion": plan["version"]}.items():
            require(provenance.get(field) == expected, f"JAR provenance mismatch: {field}")
        require(str(provenance.get("adapterRevision")) == plan["revision"], "Adapter revision mismatch")
        if plan["loader"] == "fabric":
            mod = json.loads(read("fabric.mod.json"))
            require(mod.get("id") == "halo" and mod.get("version") == expected_version, "Fabric mod identity/version mismatch")
            requires_fabric = "fabric-api" in mod.get("depends", {})
            require(requires_fabric, "Fabric dependency contract changed; review publisher config")
        else:
            location = "META-INF/mods.toml" if plan["loader"] == "forge" else "META-INF/neoforge.mods.toml"
            mods = tomllib.loads(read(location)).get("mods", [])
            require(any(m.get("modId") == "halo" and m.get("version") == expected_version for m in mods), "Forge/NeoForge mod identity/version mismatch")
        minimum_java = 0
        for entry in jar.infolist():
            if entry.filename.startswith("network/azusake/halo/") and entry.filename.endswith(".class"):
                with jar.open(entry) as stream:
                    header = stream.read(8)
                require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe", "Invalid Halo class header")
                require(header[4:6] != b"\xff\xff", "Preview Java bytecode cannot be published by this workflow")
                minimum_java = max(minimum_java, int.from_bytes(header[6:8], "big") - 44)
        require(minimum_java >= 17, "No supported Halo bytecode found in the distribution")
        plan["minimum_java"] = minimum_java
    return provenance


def normalize_options(options, plan):
    require(isinstance(options, dict), "Release options must be a JSON object")
    require(not (set(options) - {"game_versions", "java_versions", "include_sources"}), "Unknown release option")
    games = options.get("game_versions", [plan["build_game"]])
    java = options.get("java_versions", [plan["minimum_java"]])
    sources = options.get("include_sources", False)
    require(isinstance(games, list) and 0 < len(games) <= 100 and all(isinstance(v, str) and re.fullmatch(r"[A-Za-z0-9_.+-]+", v) for v in games), "Specify exact Minecraft version names, not ranges or wildcards")
    require(len(set(games)) == len(games), "Duplicate Minecraft version")
    require(isinstance(java, list) and 0 < len(java) <= 20 and all(type(v) is int and plan["minimum_java"] <= v <= 99 for v in java), f'Java versions must be integers >= the JAR bytecode minimum {plan["minimum_java"]}')
    require(len(set(java)) == len(java), "Duplicate Java version")
    require(type(sources) is bool, "include_sources must be a boolean")
    return {"game_versions": games, "java_versions": sorted(java), "include_sources": sources}


def download_asset(gh, release, filename):
    found = [asset for asset in gh.assets(release["id"]) if asset["name"] == filename]
    require(len(found) == 1 and found[0]["state"] == "uploaded", f"Expected release JAR is not fully uploaded: {filename}")
    require(0 < found[0]["size"] <= 64 * 1024 * 1024, "Invalid JAR asset size")
    content = gh.download(release["tag_name"], filename)
    sha256 = hashlib.sha256(content).hexdigest()
    require(len(content) == found[0]["size"], "Release JAR size mismatch")
    require(found[0].get("digest") == f"sha256:{sha256}", "Missing or mismatching GitHub asset digest")
    return content, {"filename": filename, "sha256": sha256, "sha512": hashlib.sha512(content).hexdigest()}


def inspect_sources(content):
    with zipfile.ZipFile(io.BytesIO(content)) as jar:
        names = jar.namelist()
        require(any(name.endswith(".java") for name in names), "Sources JAR contains no Java sources")
        require(not any(name.endswith(".class") for name in names), "Sources attachment contains compiled classes")


def make_plan(gh, config, run_id, options=None):
    run = gh.api(f"/actions/runs/{run_id}")
    validate_run(run, config["repository"])
    tag = run["head_branch"]
    values, target = target_for(config, tag)
    sha = run["head_sha"]
    # Peel annotated tags through the commits API; never trust target_commitish.
    require(gh.api(f"/commits/{urllib.parse.quote(tag, safe='')}")["sha"] == sha, "Tag moved since this CI build")
    comparison = gh.api(f'/compare/{sha}...{target["branch"]}')
    require(comparison["status"] in ("ahead", "identical"), "Release commit is not on its maintained branch")
    jobs = gh.pages(f'/actions/runs/{run_id}/attempts/{run["run_attempt"]}/jobs', "jobs")
    builds = [job for job in jobs if job["name"] == "build" or job["name"].startswith("build (")]
    require(builds and all(job["conclusion"] == "success" for job in builds), "Not all platform build jobs passed")
    require(any(step["name"] == "Create GitHub Release" and step["conclusion"] == "success" for job in jobs for step in job.get("steps", [])), "CI did not successfully create a GitHub Release")
    release = gh.api(f"/releases/tags/{urllib.parse.quote(tag, safe='')}")
    require(not release["draft"] and release.get("published_at"), "Release is not published")
    require(release["tag_name"] == tag, "Release tag mismatch")
    require(bool((release.get("body") or "").strip()), "Release changelog is empty")
    tree = gh.api(f"/git/trees/{sha}")["tree"]
    core = [entry for entry in tree if entry["path"] == "core" and entry["mode"] == "160000"]
    require(len(core) == 1, "Release does not lock a core submodule")
    props = {key.strip(): value.strip() for key, value in properties(gh.file(sha, "gradle.properties")).items()}
    require(props.get("minecraft_version") == target["game"], "Build Minecraft version changed; review target config")
    require(props.get("adapter_revision") == values["revision"], "Tag revision does not match source")
    filename = f'halo-{target["game"]}-{target["loader"]}-{values["version"]}+adapter.{values["revision"]}.jar'
    content, asset = download_asset(gh, release, filename)
    version_type = "release"
    if release["prerelease"]:
        version_type = "alpha" if re.search(r"(?:^|[.-])alpha(?:[.-]|$)", values["version"]) else "beta"
    require(release["prerelease"] or "-" not in values["version"], "Prerelease version must be marked prerelease on GitHub")
    plan = {
        "schema": 2, "repository": config["repository"], "source_run_id": int(run_id),
        "source_run_attempt": run["run_attempt"], "tag": tag, "branch": target["branch"],
        "halo_sha": sha, "core_sha": core[0]["sha"], "version": values["version"],
        "revision": values["revision"], "loader": target["loader"], "build_game": target["game"], "game_versions": [target["game"]],
        "version_type": version_type, "filename": filename, "sha256": asset["sha256"],
        "sha512": hashlib.sha512(content).hexdigest(), "release_id": release["id"],
        "release_url": release["html_url"], "name": f'Halo {values["version"]} - {target["game"]} {target["loader"]} (Adapter {values["revision"]})',
        "changelog": release["body"], "modrinth_project": config["modrinth_project"],
        "curseforge_project": config["curseforge_project"],
    }
    plan["modrinth_version"] = modrinth_version_number(plan)
    plan["notes_sha256"] = notes_digest(plan["changelog"])
    require(len(plan["name"]) <= 64 and len(plan["changelog"]) <= 65536, "Release title or changelog exceeds platform limits")
    inspect_jar(content, plan)
    plan.update(normalize_options({} if options is None else options, plan))
    files = {"main": content}
    if plan["include_sources"]:
        sources_name = filename.removesuffix(".jar") + "-sources.jar"
        files["sources"], plan["sources"] = download_asset(gh, release, sources_name)
        inspect_sources(files["sources"])
    return plan, files


def mr_headers(token):
    return {"Authorization": token} if token else {}


def existing_modrinth(plan, token):
    versions = request("GET", f'https://api.modrinth.com/v2/project/{plan["modrinth_project"]}/version', headers=mr_headers(token))
    for version in versions:
        matching_file = any(file["hashes"].get("sha512") == plan["sha512"] for file in version["files"])
        if version["version_number"] in (modrinth_version_number(plan), plan["tag"]) or matching_file:
            require(matching_file, "Modrinth version number exists with a different JAR")
            require(set(version["loaders"]) == {plan["loader"]} and set(version["game_versions"]) == set(plan["game_versions"]), "Existing Modrinth file has different compatibility metadata; review instead of duplicating")
            require(version["version_type"] == plan["version_type"], "Existing Modrinth release type differs")
            require(version.get("changelog") == plan["changelog"], "Existing Modrinth notes differ; update platform metadata instead of duplicating the JAR")
            require(version.get("status") in ("listed", "archived", "unlisted"), "Existing Modrinth version is not published")
            expected_dependencies = {"P7dR8mSH"} if plan["loader"] == "fabric" else set()
            actual_dependencies = {dep["project_id"] for dep in version["dependencies"] if dep["dependency_type"] == "required"}
            require(actual_dependencies == expected_dependencies, "Existing Modrinth required dependencies differ")
            if plan.get("include_sources"):
                require(any(file["hashes"].get("sha512") == plan["sources"]["sha512"] and file.get("file_type") == "sources-jar" and not file.get("primary") for file in version["files"]), "Existing Modrinth version lacks the selected sources attachment; do not duplicate the main version")
            return {"id": version["id"], "url": f'https://modrinth.com/mod/{plan["modrinth_project"]}/version/{version["id"]}', "status": "already_present"}
    return None


def platform_metadata(platform, plan, token):
    if platform == "modrinth":
        versions = request("GET", "https://api.modrinth.com/v2/tag/game_version", headers=mr_headers(token))
        require(set(plan["game_versions"]) <= {v["version"] for v in versions}, "Modrinth has not registered this game version")
        return {
            "name": plan["name"], "version_number": modrinth_version_number(plan), "changelog": plan["changelog"],
            "dependencies": ([{"project_id": "P7dR8mSH", "dependency_type": "required"}] if plan["loader"] == "fabric" else []),
            "game_versions": plan["game_versions"], "version_type": plan["version_type"],
            "loaders": [plan["loader"]], "featured": False, "project_id": plan["modrinth_project"],
            "file_parts": ["file", "sources"] if plan.get("include_sources") else ["file"], "primary_file": "file",
            "file_types": {"sources": "sources-jar"} if plan.get("include_sources") else {},
        }
    # Resolve exact platform version IDs; never label 26.3 as a neighbouring release.
    versions = request("GET", "https://minecraft.curseforge.com/api/game/versions", headers={"X-Api-Token": token})
    names = plan["game_versions"] + [{"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}[plan["loader"]]] + [f"Java {version}" for version in plan.get("java_versions", [])]
    ids = []
    for name in names:
        ids.append(curseforge_version_id(versions, name))
    return {
        "changelog": plan["changelog"], "changelogType": "markdown", "displayName": plan["name"],
        "gameVersions": ids, "releaseType": plan["version_type"],
        "relations": {"projects": ([{"slug": "fabric-api", "projectID": "306612", "type": "requiredDependency"}] if plan["loader"] == "fabric" else [])},
    }


def curseforge_version_id(versions, name):
    """Pick one CurseForge game-version id; names can collide across game types."""
    matches = [v for v in versions if v.get("name") == name]
    require(matches, f"CurseForge game/loader version not found: {name}")
    if len(matches) > 1:
        slug = name.replace(".", "-").replace(" ", "-").lower()
        by_slug = [v for v in matches if v.get("slug") == slug]
        if len(by_slug) == 1:
            matches = by_slug
        else:
            typed = [v for v in matches if v.get("gameVersionType") in ("Release", "Add-on", "Java")]
            if len(typed) == 1:
                matches = typed
            else:
                release = [v for v in matches if v.get("gameVersionType") == "Release"]
                if len(release) == 1:
                    matches = release
    require(len(matches) == 1, f"CurseForge game/loader version not found or ambiguous: {name}")
    return matches[0]["id"]


def upload(platform, plan, content, metadata, token, *, sources_only=False):
    field = "data" if platform == "modrinth" else "metadata"
    filename = plan["sources"]["filename"] if sources_only else plan["filename"]
    raw = content["sources" if sources_only else "main"]
    attachments = [("sources", plan["sources"]["filename"], content["sources"])] if platform == "modrinth" and plan.get("include_sources") else []
    body, content_type = multipart(field, metadata, filename, raw, attachments=attachments)
    if platform == "modrinth":
        response = request("POST", "https://api.modrinth.com/v2/version", headers={**mr_headers(token), "Content-Type": content_type}, data=body)
        require(response.get("id"), "Modrinth response did not contain a version ID")
        return {"id": response["id"], "url": f'https://modrinth.com/mod/{plan["modrinth_project"]}/version/{response["id"]}', "status": "uploaded"}
    response = request("POST", f'https://minecraft.curseforge.com/api/projects/{plan["curseforge_project"]}/upload-file', headers={"X-Api-Token": token, "Content-Type": content_type}, data=body)
    require(isinstance(response.get("id"), int) and response["id"] > 0, "CurseForge response did not contain a file ID")
    return {"id": response["id"], "url": f'https://authors.curseforge.com/#/projects/{plan["curseforge_project"]}/files/{response["id"]}', "status": "uploaded_review_may_be_pending"}


def identity(plan, platform, role="main", parent_id=None):
    # CI run IDs/attempts can change on reruns; immutable payload identity cannot.
    keys = ["tag", "halo_sha", "core_sha", "filename", "sha256", "loader", "game_versions", "version_type", "name", "changelog"]
    value = {key: plan[key] for key in keys}
    value["project"] = plan[f"{platform}_project"]
    value["java_versions"] = plan.get("java_versions", [])
    if platform == "modrinth":
        value["version_number"] = modrinth_version_number(plan)
        value["sources"] = plan.get("sources")
    if role == "sources":
        value["sources"] = plan["sources"]
        value["parent_id"] = parent_id
    return hashlib.sha256(canonical(value)).hexdigest()


def publish_one(gh, plan, content, platform, token, output, *, role="main", parent_id=None):
    verify_current_notes(gh, plan)
    require(role in ("main", "sources") and (role == "main" or platform == "curseforge"), "Invalid upload role")
    if role == "sources":
        require(type(parent_id) is int and parent_id > 0, "Sources upload requires a confirmed CurseForge parent file ID")
    fingerprint = identity(plan, platform, role, parent_id)
    record_key = platform if role == "main" else f"{platform}-sources"
    receipt_name = f"halo-publish-{record_key}.json"
    pending_name = f"halo-publish-{record_key}.pending.json"
    assets = {asset["name"]: asset for asset in gh.assets(plan["release_id"])}
    if receipt_name in assets:
        receipt = json.loads(gh.download(plan["tag"], receipt_name))
        require(receipt.get("identity") == fingerprint and receipt.get("result", {}).get("id"), "Existing publication receipt conflicts with this release")
        return {**receipt["result"], "status": "receipt_exists"}
    require(bool(token), f"Missing {platform.upper()}_TOKEN")
    if pending_name in assets:
        pending = json.loads(gh.download(plan["tag"], pending_name))
        require(pending.get("identity") == fingerprint, "Existing upload intent conflicts with this release")
    if platform == "modrinth":
        existing = existing_modrinth(plan, token)
        if existing:
            receipt = {"identity": fingerprint, "result": existing}
            gh.record(plan["release_id"], receipt_name, receipt)
            return existing
    # An interrupted POST may have succeeded. CurseForge's documented Upload
    # API offers no file-hash lookup: stop for reconciliation rather than repeat.
    require(pending_name not in assets, f"Unresolved {platform} upload intent. Inspect the platform and recover the receipt; do not blindly rerun the upload. See docs/release-automation.md")
    metadata = ({"parentFileID": parent_id, "displayName": plan["name"] + " (sources)", "changelog": plan["changelog"], "changelogType": "markdown", "releaseType": plan["version_type"]} if role == "sources" else platform_metadata(platform, plan, token))
    verify_current_notes(gh, plan)
    selected_file = plan["sources"] if role == "sources" else plan
    intent = gh.record(plan["release_id"], pending_name, {"identity": fingerprint, "tag": plan["tag"], "filename": selected_file["filename"], "sha256": selected_file["sha256"], "source_run_id": plan["source_run_id"]})
    try:
        result = upload(platform, plan, content, metadata, token, sources_only=role == "sources")
    except ApiError as error:
        # A definite request rejection is retryable after fixing its cause.
        if error.status in (400, 401, 403, 404, 413, 422):
            gh.api(f'/releases/assets/{intent["id"]}', "DELETE")
        raise
    receipt = {"identity": fingerprint, "result": result}
    # Save locally before uploading the receipt, so a GitHub failure leaves a
    # recoverable record in the Actions artifact. Never re-upload the mod here.
    (output / receipt_name).write_bytes(canonical(receipt))
    gh.record(plan["release_id"], receipt_name, receipt)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True, type=int)
    parser.add_argument("--platform", choices=["both", "modrinth", "curseforge"], default="both")
    parser.add_argument("--publish", action="store_true", help="Actually upload; omitted means read-only preview")
    parser.add_argument("--notes-sha256", default="", help="SHA-256 of the final edited Release body; required with --publish")
    options_group = parser.add_mutually_exclusive_group()
    options_group.add_argument("--options-json", default="{}", help="Exact game_versions, java_versions and include_sources options")
    options_group.add_argument("--options-file", type=Path, help="Read release options from a UTF-8 JSON file")
    parser.add_argument("--output", type=Path, default=Path(".local/halo-publish"))
    args = parser.parse_args(argv)
    args.output.mkdir(parents=True, exist_ok=True)
    config = json.loads((ROOT / ".github/release-targets.json").read_text())
    gh = GitHub(config["repository"], os.environ.get("GH_TOKEN", ""))
    results = {}
    try:
        options = json.loads(args.options_file.read_text(encoding="utf-8") if args.options_file else args.options_json)
        plan, content = make_plan(gh, config, args.run_id, options)
        (args.output / "plan.json").write_bytes(canonical(plan))
        print(f'Verified {plan["tag"]}: {plan["filename"]} (SHA-256 {plan["sha256"]})')
        print(json.dumps({key: plan.get(key) for key in ("game_versions", "java_versions", "include_sources")}, ensure_ascii=False))
        platforms = ["modrinth", "curseforge"] if args.platform == "both" else [args.platform]
        if not args.publish:
            results = {platform: {"status": "dry_run", "project": plan[f"{platform}_project"]} for platform in platforms}
            print("Read-only preview complete. No upload, platform credential validation or moderation check was performed.")
        else:
            validate_final_notes(plan, args.notes_sha256)
            require(bool(os.environ.get("GH_TOKEN")), "GH_TOKEN is required to persist publication receipts")
            for platform in platforms:
                try:
                    results[platform] = publish_one(gh, plan, content, platform, os.environ.get(f"{platform.upper()}_TOKEN", ""), args.output)
                except (PublishError, ValueError, KeyError) as error:
                    results[platform] = {"status": "failed", "error": str(error)}
                if platform == "curseforge" and plan.get("include_sources"):
                    if results[platform]["status"] == "failed":
                        results["curseforge_sources"] = {"status": "blocked", "error": "Main file did not complete"}
                    else:
                        try:
                            results["curseforge_sources"] = publish_one(gh, plan, content, platform, os.environ.get("CURSEFORGE_TOKEN", ""), args.output, role="sources", parent_id=results[platform]["id"])
                        except (PublishError, ValueError, KeyError) as error:
                            results["curseforge_sources"] = {"status": "failed", "error": str(error)}
    except (PublishError, ValueError, KeyError, zipfile.BadZipFile) as error:
        results["preflight"] = {"status": "failed", "error": str(error)}
    (args.output / "results.json").write_bytes(canonical(results))
    print(json.dumps(results, ensure_ascii=False, indent=2))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write("## Halo platform publication\n\n```json\n" + json.dumps(results, ensure_ascii=False, indent=2) + "\n```\n")
    return 1 if any(result["status"] == "failed" for result in results.values()) else 0


if __name__ == "__main__":
    sys.exit(main())
