import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import publish as p
import dispatch as d


def plan_fixture(loader="fabric"):
    return {
        "tag": f"v2.4.2-{loader}-1.20.1-adapter.1", "halo_sha": "a" * 40,
        "core_sha": "b" * 40, "version": "2.4.2", "revision": "1", "loader": loader,
        "game_versions": ["1.20.1"], "filename": f"halo-1.20.1-{loader}-2.4.2+adapter.1.jar",
        "version_type": "release", "name": "Halo", "changelog": "Release notes",
        "sha256": "c" * 64, "sha512": "d" * 128, "release_id": 1, "source_run_id": 2,
        "source_run_attempt": 1, "modrinth_project": "k2fEt5RO", "curseforge_project": "1582156",
        "build_game": "1.20.1", "minimum_java": 17, "java_versions": [17], "include_sources": False,
    }


def jar_fixture(plan, **changes):
    provenance = {
        "haloCommit": plan["halo_sha"], "coreCommit": plan["core_sha"], "coreLock": plan["core_sha"],
        "coreVersion": plan["version"], "adapterRevision": plan["revision"], "development": False,
        **changes,
    }
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w") as jar:
        jar.writestr("network/azusake/halo/Example.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x3d")
        jar.writestr("halo-build.json", json.dumps(provenance))
        if plan["loader"] == "fabric":
            jar.writestr("fabric.mod.json", json.dumps({"id": "halo", "version": "2.4.2+adapter.1", "depends": {"fabric-api": "*"}}))
        else:
            name = "META-INF/mods.toml" if plan["loader"] == "forge" else "META-INF/neoforge.mods.toml"
            jar.writestr(name, '[[mods]]\nmodId = "halo"\nversion = "2.4.2+adapter.1"\n')
    return result.getvalue()


class FakeGitHub:
    def __init__(self):
        self.records = {}
        self.deletes = []
        self.fail_receipt = False
        self.body = "Release notes"

    def assets(self, release_id):
        return [{"name": name, "id": index} for index, name in enumerate(self.records, 10)]

    def download(self, tag, name):
        return p.canonical(self.records[name])

    def record(self, release_id, name, value):
        if self.fail_receipt and not name.endswith(".pending.json"):
            raise p.PublishError("GitHub receipt unavailable")
        if name in self.records:
            raise p.ApiError("POST", "/assets", 422)
        self.records[name] = value
        return {"id": 10}

    def api(self, path, method="GET"):
        if method == "GET" and path == "/releases/1":
            return {"tag_name": plan_fixture()["tag"], "draft": False, "body": self.body}
        self.deletes.append((method, path))
        if method == "DELETE":
            self.records = {name: value for name, value in self.records.items() if not name.endswith(".pending.json")}


class ReleaseValidationTests(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((p.ROOT / ".github/release-targets.json").read_text())

    def test_all_nine_targets_and_26_1_alias(self):
        self.assertEqual(len(self.config["targets"]), 9)
        for target in self.config["targets"]:
            tag = f'v2.4.2-{target["loader"]}-{target["tag_game"]}-adapter.1'
            _, found = p.target_for(self.config, tag)
            self.assertEqual(found, target)
        self.assertEqual(p.target_for(self.config, "v2.4.2-fabric-26.1-adapter.1")[1]["game"], "26.1.2")

    def test_rejects_frozen_unknown_and_injected_tags(self):
        for tag in ["v2.4.2", "v2.4.2-fabric-26.2-adapter.1-flash", "v2.4.2-forge-26.3-adapter.1", "v2.4.2-fabric-26.2-adapter.1\n", "$(echo hello)"]:
            with self.subTest(tag=tag), self.assertRaises(p.PublishError):
                p.target_for(self.config, tag)

    def test_run_trust_boundary(self):
        run = {"repository": {"full_name": "AzusaKe/Halo"}, "head_repository": {"full_name": "AzusaKe/Halo"}, "event": "push", "status": "completed", "conclusion": "success", "path": ".github/workflows/gradle.yml", "head_sha": "a" * 40}
        p.validate_run(run, "AzusaKe/Halo")
        for key, value in [("event", "pull_request"), ("conclusion", "failure"), ("status", "in_progress"), ("path", ".github/workflows/other.yml"), ("head_repository", {"full_name": "fork/Halo"})]:
            with self.subTest(key=key), self.assertRaises(p.PublishError):
                p.validate_run({**run, key: value}, "AzusaKe/Halo")

    def test_loader_metadata_and_provenance(self):
        for loader in ["fabric", "forge", "neoforge"]:
            plan = plan_fixture(loader)
            p.inspect_jar(jar_fixture(plan), plan)
            for changes in [{"development": True}, {"development": "false"}, {"haloCommit": "e" * 40}, {"coreLock": "e" * 40}, {"coreCommit": "e" * 40}, {"coreVersion": "2.4.1"}, {"adapterRevision": "2"}]:
                with self.subTest(loader=loader, changes=changes), self.assertRaises(p.PublishError):
                    p.inspect_jar(jar_fixture(plan, **changes), plan)

    def test_source_jar_is_not_a_distribution(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as jar:
            jar.writestr("Example.java", "class Example {}")
        with self.assertRaises(p.PublishError):
            p.inspect_jar(data.getvalue(), plan_fixture())

    def test_identity_ignores_run_attempt_but_locks_payload(self):
        plan = plan_fixture()
        first = p.identity(plan, "curseforge")
        self.assertEqual(first, p.identity({**plan, "source_run_attempt": 2, "source_run_id": 99}, "curseforge"))
        for changes in [{"sha256": "e" * 64}, {"changelog": "changed"}, {"game_versions": ["1.21.1"]}, {"curseforge_project": "other"}]:
            self.assertNotEqual(first, p.identity({**plan, **changes}, "curseforge"))

    def test_modrinth_version_limit_preserves_platform_identity(self):
        numbers = []
        for target in self.config["targets"]:
            plan = {**plan_fixture(), "loader": target["loader"], "build_game": target["game"], "game_versions": [target["game"]]}
            numbers.append(p.modrinth_version_number(plan))
        self.assertEqual(len(set(numbers)), 9)
        self.assertTrue(all(len(number) <= 32 for number in numbers))
        with self.assertRaisesRegex(p.PublishError, "32 characters"):
            p.modrinth_version_number({**plan_fixture(), "version": "2.4.2-alpha.extremely.long.prerelease"})


class PublicationRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.gh = FakeGitHub()
        self.plan = plan_fixture()
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.output = Path(self.tmp.name)

    def publish(self, platform="curseforge"):
        return p.publish_one(self.gh, self.plan, b"jar", platform, "fake-token", self.output)

    @patch.object(p, "platform_metadata", return_value={})
    @patch.object(p, "upload", return_value={"id": 123, "status": "uploaded_review_may_be_pending"})
    def test_success_is_not_uploaded_twice(self, upload, metadata):
        self.assertEqual(self.publish()["id"], 123)
        self.assertEqual(self.publish()["status"], "receipt_exists")
        upload.assert_called_once()
        self.assertTrue((self.output / "halo-publish-curseforge.json").is_file())

    @patch.object(p, "platform_metadata", return_value={})
    @patch.object(p, "upload", side_effect=p.PublishError("timeout"))
    def test_ambiguous_failure_blocks_duplicate(self, upload, metadata):
        with self.assertRaises(p.PublishError):
            self.publish()
        with self.assertRaisesRegex(p.PublishError, "Unresolved"):
            self.publish()
        upload.assert_called_once()

    @patch.object(p, "platform_metadata", return_value={})
    @patch.object(p, "upload", side_effect=p.ApiError("POST", "/upload", 400))
    def test_definite_rejection_allows_corrected_retry(self, upload, metadata):
        with self.assertRaises(p.ApiError):
            self.publish()
        self.assertNotIn("halo-publish-curseforge.pending.json", self.gh.records)
        self.assertEqual(self.gh.deletes, [("DELETE", "/releases/assets/10")])

    @patch.object(p, "platform_metadata", return_value={})
    @patch.object(p, "upload", return_value={"id": 123, "status": "uploaded"})
    def test_receipt_failure_preserves_local_result_without_reupload(self, upload, metadata):
        self.gh.fail_receipt = True
        with self.assertRaises(p.PublishError):
            self.publish()
        self.assertEqual(json.loads((self.output / "halo-publish-curseforge.json").read_text())["result"]["id"], 123)
        with self.assertRaisesRegex(p.PublishError, "Unresolved"):
            self.publish()
        upload.assert_called_once()

    @patch.object(p, "upload")
    def test_receipt_conflict_blocks_upload(self, upload):
        self.gh.records["halo-publish-curseforge.json"] = {"identity": "wrong", "result": {"id": 5}}
        with self.assertRaisesRegex(p.PublishError, "conflicts"):
            self.publish()
        upload.assert_not_called()

    @patch.object(p, "upload")
    @patch.object(p, "existing_modrinth", return_value={"id": "existing", "status": "already_present"})
    def test_modrinth_hash_recovery_after_interrupted_attempt(self, existing, upload):
        self.gh.records["halo-publish-modrinth.pending.json"] = {"identity": p.identity(self.plan, "modrinth")}
        self.assertEqual(self.publish("modrinth")["id"], "existing")
        upload.assert_not_called()

    @patch.object(p, "request")
    def test_modrinth_same_version_different_bytes_rejected(self, request):
        request.return_value = [{"version_number": self.plan["tag"], "files": [{"hashes": {"sha512": "wrong"}}]}]
        with self.assertRaisesRegex(p.PublishError, "different JAR"):
            p.existing_modrinth(self.plan, "token")

    @patch.object(p, "request")
    def test_curseforge_missing_game_never_falls_back(self, request):
        request.return_value = [{"id": 1, "name": "Fabric"}, {"id": 2, "name": "1.20.2"}]
        with self.assertRaisesRegex(p.PublishError, "not found"):
            p.platform_metadata("curseforge", self.plan, "token")

    def test_curseforge_version_disambiguates_by_slug(self):
        versions = [
            {"id": 10, "name": "1.20.1", "slug": "1-20-1", "gameVersionTypeID": 1},
            {"id": 11, "name": "1.20.1", "slug": "other-1-20-1", "gameVersionTypeID": 1},
            {"id": 20, "name": "Forge", "slug": "forge", "gameVersionTypeID": 512},
            {"id": 30, "name": "Java 17", "slug": "java-17", "gameVersionTypeID": 6},
        ]
        self.assertEqual(p.curseforge_version_id(versions, "1.20.1"), 10)
        self.assertEqual(p.curseforge_version_id(versions, "Forge"), 20)
        self.assertEqual(p.curseforge_version_id(versions, "Java 17"), 30)

    def test_curseforge_version_prefers_java_release_type_id(self):
        versions = [
            {"id": 9990, "name": "1.20.1", "slug": "1-20-1", "gameVersionTypeID": 75125},
            {"id": 9993, "name": "1.20.1", "slug": "1-20-1", "gameVersionTypeID": 615},
            {"id": 9994, "name": "1.20.1", "slug": "1-20-1", "gameVersionTypeID": 1},
        ]
        self.assertEqual(p.curseforge_version_id(versions, "1.20.1"), 9994)

    def test_curseforge_version_accepts_object_type_and_ties_break_by_id(self):
        versions = [
            {"id": 51, "name": "1.20.1", "slug": "a", "gameVersionTypeID": 2},
            {"id": 52, "name": "1.20.1", "slug": "b", "gameVersionTypeID": 2},
        ]
        with self.assertRaisesRegex(p.PublishError, "ambiguous"):
            p.curseforge_version_id(versions, "1.20.1")

    def test_multipart_uses_metadata_and_binary_without_corruption(self):
        raw = b"\x00\xff\r\n\x80"
        body, content_type = p.multipart("metadata", {"changelog": "中文"}, self.plan["filename"], raw)
        self.assertIn(raw, body)
        self.assertIn('中文'.encode(), body)
        self.assertIn('name="metadata"'.encode(), body)
        self.assertTrue(content_type.startswith("multipart/form-data; boundary=halo-"))


class SourceBuildTests(unittest.TestCase):
    def setUp(self):
        self.plan = plan_fixture()
        self.content = jar_fixture(self.plan)
        self.config = json.loads((p.ROOT / ".github/release-targets.json").read_text())
        self.run = {"id": 2, "run_attempt": 1, "repository": {"full_name": "AzusaKe/Halo"}, "head_repository": {"full_name": "AzusaKe/Halo"}, "event": "push", "status": "completed", "conclusion": "success", "path": ".github/workflows/gradle.yml", "head_sha": self.plan["halo_sha"], "head_branch": self.plan["tag"]}
        self.jobs = [{"name": "build", "conclusion": "success", "steps": [{"name": "Create GitHub Release", "conclusion": "success"}]}]
        self.release = {"id": 1, "tag_name": self.plan["tag"], "draft": False, "published_at": "2026-09-20", "body": "Notes", "prerelease": False, "html_url": "https://github.com/AzusaKe/Halo/releases"}
        self.asset = {"name": self.plan["filename"], "state": "uploaded", "size": len(self.content), "digest": "sha256:" + hashlib.sha256(self.content).hexdigest()}
        self.tag_sha = self.plan["halo_sha"]
        self.compare = "ahead"

    def api(self, path):
        if path.startswith("/actions/runs/"):
            return self.run
        if path.startswith("/commits/"):
            return {"sha": self.tag_sha}
        if path.startswith("/compare/"):
            return {"status": self.compare}
        if path.startswith("/releases/tags/"):
            return self.release
        if path.startswith("/git/trees/"):
            return {"tree": [{"path": "core", "mode": "160000", "sha": self.plan["core_sha"]}]}
        raise AssertionError(path)

    def pages(self, path, key):
        return self.jobs

    def assets(self, release_id):
        return [self.asset, {"name": self.plan["filename"].replace(".jar", "-sources.jar")}]

    def file(self, sha, path):
        return "minecraft_version = 1.20.1\nadapter_revision = 1\n"

    def download(self, tag, name):
        self.assertEqual(name, self.plan["filename"])
        return self.content

    def test_full_preflight_selects_binary_not_sources(self):
        plan, content = p.make_plan(self, self.config, 2)
        self.assertEqual(plan["filename"], self.plan["filename"])
        self.assertEqual(content, {"main": self.content})

    def test_moved_tag_and_wrong_branch_rejected(self):
        self.tag_sha = "e" * 40
        with self.assertRaisesRegex(p.PublishError, "Tag moved"):
            p.make_plan(self, self.config, 2)
        self.tag_sha = self.plan["halo_sha"]
        self.compare = "diverged"
        with self.assertRaisesRegex(p.PublishError, "maintained branch"):
            p.make_plan(self, self.config, 2)

    def test_failed_matrix_member_blocks_even_if_release_exists(self):
        self.jobs.append({"name": "build (other)", "conclusion": "failure", "steps": []})
        with self.assertRaisesRegex(p.PublishError, "Not all"):
            p.make_plan(self, self.config, 2)

    def test_missing_release_step_and_draft_rejected(self):
        self.jobs[0]["steps"] = []
        with self.assertRaisesRegex(p.PublishError, "did not successfully"):
            p.make_plan(self, self.config, 2)
        self.jobs[0]["steps"] = [{"name": "Create GitHub Release", "conclusion": "success"}]
        self.release["draft"] = True
        with self.assertRaisesRegex(p.PublishError, "not published"):
            p.make_plan(self, self.config, 2)

    def test_changed_release_bytes_rejected(self):
        self.asset["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(p.PublishError, "digest"):
            p.make_plan(self, self.config, 2)


class CommandTests(unittest.TestCase):
    @patch.object(p, "publish_one")
    @patch.object(p, "make_plan", return_value=(plan_fixture(), b"jar"))
    def test_default_is_read_only(self, make_plan, publish_one):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(p.main(["--run-id", "2", "--output", tmp]), 0)
            publish_one.assert_not_called()
            self.assertEqual(json.loads((Path(tmp) / "results.json").read_text())["curseforge"]["status"], "dry_run")

    @patch.dict(os.environ, {"GH_TOKEN": "fake"})
    @patch.object(p, "publish_one", side_effect=[p.PublishError("Modrinth unavailable"), {"status": "uploaded", "id": 42}])
    @patch.object(p, "make_plan", return_value=(plan_fixture(), b"jar"))
    def test_one_platform_failure_does_not_block_the_other(self, make_plan, publish_one):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(p.main(["--run-id", "2", "--publish", "--notes-sha256", p.notes_digest("Release notes"), "--output", tmp]), 1)
            self.assertEqual(publish_one.call_count, 2)
            results = json.loads((Path(tmp) / "results.json").read_text())
            self.assertEqual(results["modrinth"]["status"], "failed")
            self.assertEqual(results["curseforge"]["id"], 42)


class FinalNotesTests(unittest.TestCase):
    def test_unfinalized_and_old_notes_are_rejected(self):
        plan = plan_fixture()
        for expected in ["", "not-a-hash", p.notes_digest("Old auto-generated notes")]:
            with self.subTest(expected=expected), self.assertRaises(p.PublishError):
                p.validate_final_notes(plan, expected)
        p.validate_final_notes(plan, p.notes_digest(plan["changelog"]))

    @patch.object(p, "publish_one")
    @patch.object(p, "make_plan", return_value=(plan_fixture(), b"jar"))
    def test_publish_without_final_hash_has_no_platform_side_effects(self, make_plan, publish_one):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(p.main(["--run-id", "2", "--publish", "--output", tmp]), 1)
            publish_one.assert_not_called()

    @patch.object(p, "upload")
    def test_edit_after_queued_dispatch_blocks_upload(self, upload):
        gh = FakeGitHub()
        gh.body = "Changed while queued"
        with tempfile.TemporaryDirectory() as tmp, self.assertRaisesRegex(p.PublishError, "changed after finalization"):
            p.publish_one(gh, plan_fixture(), b"jar", "curseforge", "token", Path(tmp))
        self.assertEqual(gh.records, {})
        upload.assert_not_called()

    @patch.object(p, "upload")
    def test_edit_during_platform_preflight_blocks_upload(self, upload):
        gh = FakeGitHub()
        def metadata(*args):
            gh.body = "Changed while resolving game versions"
            return {}
        with patch.object(p, "platform_metadata", side_effect=metadata), tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(p.PublishError, "changed after finalization"):
                p.publish_one(gh, plan_fixture(), b"jar", "curseforge", "token", Path(tmp))
        self.assertEqual(gh.records, {})
        upload.assert_not_called()

    @patch.object(p, "request")
    @patch.object(p, "make_plan", return_value=(plan_fixture(), b"jar"))
    def test_dispatch_binds_edited_file_instead_of_old_body(self, make_plan, request):
        gh = FakeGitHub()
        gh.base = "https://api.github.com/repos/AzusaKe/Halo"
        gh.headers = {}
        config = {"default_branch": "1.20.1-fabric"}
        with self.assertRaisesRegex(p.PublishError, "differ from"):
            d.dispatch(gh, config, 2, "File not yet applied to GitHub", "both", True)
        request.assert_not_called()
        inputs = d.dispatch(gh, config, 2, "Release notes", "both", True)
        payload = json.loads(request.call_args.kwargs["data"])
        self.assertEqual(payload["ref"], "1.20.1-fabric")
        self.assertEqual(inputs["notes_sha256"], p.notes_digest("Release notes"))
        self.assertEqual(payload["inputs"]["dry_run"], "false")

    @patch.object(p, "request")
    def test_both_platform_payloads_use_the_final_body(self, request):
        request.return_value = [{"version": "1.20.1"}]
        self.assertEqual(p.platform_metadata("modrinth", plan_fixture(), "token")["changelog"], "Release notes")
        request.return_value = [{"id": 1, "name": "1.20.1"}, {"id": 2, "name": "Fabric"}, {"id": 3, "name": "Java 17"}]
        self.assertEqual(p.platform_metadata("curseforge", plan_fixture(), "token")["changelog"], "Release notes")


class PublishingOptionsTests(unittest.TestCase):
    def setUp(self):
        self.plan = plan_fixture()
        self.plan["include_sources"] = True
        self.plan["sources"] = {"filename": "halo-1.20.1-fabric-2.4.2+adapter.1-sources.jar", "sha256": "e" * 64, "sha512": "f" * 128}

    def test_explicit_games_java_and_sources(self):
        options = {"game_versions": ["26.1", "26.1.1", "26.1.2"], "java_versions": [25, 26], "include_sources": True}
        self.assertEqual(p.normalize_options(options, {**self.plan, "minimum_java": 25}), options)
        self.assertEqual(p.normalize_options({}, self.plan), {"game_versions": ["1.20.1"], "java_versions": [17], "include_sources": False})

    def test_invalid_or_incompatible_options_rejected(self):
        for options in [[], {"unknown": 1}, {"game_versions": []}, {"game_versions": ["1.20.*"]}, {"game_versions": ["1.20.1", "1.20.1"]}, {"java_versions": [16]}, {"java_versions": [True]}, {"java_versions": [17, 17]}, {"include_sources": "true"}]:
            with self.subTest(options=options), self.assertRaises(p.PublishError):
                p.normalize_options(options, self.plan)

    def test_bytecode_minimum_is_read_from_the_jar(self):
        plan = plan_fixture()
        p.inspect_jar(jar_fixture(plan), plan)
        self.assertEqual(plan["minimum_java"], 17)
        with self.assertRaises(p.PublishError):
            p.normalize_options({"java_versions": [21]}, {**plan, "minimum_java": 25})

    def test_version_identity_does_not_depend_on_compatibility_list_order(self):
        one = p.modrinth_version_number(self.plan)
        two = p.modrinth_version_number({**self.plan, "game_versions": ["1.20.2", "1.20.1"]})
        self.assertEqual(one, two)

    @patch.object(p, "request")
    def test_modrinth_sources_are_secondary_and_typed(self, request):
        request.return_value = [{"version": "1.20.1"}]
        metadata = p.platform_metadata("modrinth", self.plan, "token")
        self.assertEqual(metadata["file_parts"], ["file", "sources"])
        self.assertEqual(metadata["primary_file"], "file")
        self.assertEqual(metadata["file_types"], {"sources": "sources-jar"})
        self.assertNotIn("java_versions", metadata)  # Modrinth has no such field.
        request.return_value = {"id": "version"}
        p.upload("modrinth", self.plan, {"main": b"BINARY", "sources": b"SOURCE"}, metadata, "token")
        body = request.call_args.kwargs["data"]
        self.assertIn(b'name="file";', body)
        self.assertIn(b'name="sources";', body)
        self.assertIn(b"BINARY", body)
        self.assertIn(b"SOURCE", body)

    @patch.object(p, "request")
    def test_curseforge_resolves_minecraft_loader_and_java_tags(self, request):
        request.return_value = [{"id": 1, "name": "1.20.1"}, {"id": 2, "name": "Fabric"}, {"id": 3, "name": "Java 17"}, {"id": 4, "name": "Java 21"}]
        metadata = p.platform_metadata("curseforge", {**self.plan, "java_versions": [17, 21]}, "token")
        self.assertEqual(metadata["gameVersions"], [1, 2, 3, 4])
        with self.assertRaisesRegex(p.PublishError, "Java 25"):
            p.platform_metadata("curseforge", {**self.plan, "java_versions": [25]}, "token")

    @patch.object(p, "upload", return_value={"id": 999, "status": "uploaded"})
    def test_curseforge_sources_link_parent_without_game_versions(self, upload):
        gh = FakeGitHub()
        with tempfile.TemporaryDirectory() as tmp:
            result = p.publish_one(gh, self.plan, {"main": b"jar", "sources": b"source"}, "curseforge", "token", Path(tmp), role="sources", parent_id=123)
            self.assertEqual(result["id"], 999)
            metadata = upload.call_args.args[3]
            self.assertEqual(metadata["parentFileID"], 123)
            self.assertNotIn("gameVersions", metadata)
            self.assertTrue(upload.call_args.kwargs["sources_only"])
            p.publish_one(gh, self.plan, {}, "curseforge", "token", Path(tmp), role="sources", parent_id=123)
            upload.assert_called_once()
            with self.assertRaisesRegex(p.PublishError, "conflicts"):
                p.publish_one(gh, self.plan, {}, "curseforge", "token", Path(tmp), role="sources", parent_id=456)

    @patch.object(p, "upload", side_effect=p.PublishError("timeout"))
    def test_source_timeout_has_its_own_pending_record(self, upload):
        gh = FakeGitHub()
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(p.PublishError):
                p.publish_one(gh, self.plan, {}, "curseforge", "token", Path(tmp), role="sources", parent_id=123)
            self.assertIn("halo-publish-curseforge-sources.pending.json", gh.records)
            self.assertNotIn("halo-publish-curseforge.pending.json", gh.records)
            with self.assertRaisesRegex(p.PublishError, "Unresolved"):
                p.publish_one(gh, self.plan, {}, "curseforge", "token", Path(tmp), role="sources", parent_id=123)
            upload.assert_called_once()

    def test_main_curseforge_receipt_survives_optional_source_selection(self):
        without = {**self.plan, "include_sources": False}
        without.pop("sources")
        self.assertEqual(p.identity(without, "curseforge"), p.identity(self.plan, "curseforge"))
        self.assertNotEqual(p.identity(without, "modrinth"), p.identity(self.plan, "modrinth"))

    def test_sources_archive_cannot_be_a_binary(self):
        with self.assertRaises(p.PublishError):
            p.inspect_sources(jar_fixture(self.plan))
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as jar:
            jar.writestr("network/azusake/halo/Example.java", "class Example {}")
        p.inspect_sources(buf.getvalue())

    @patch.dict(os.environ, {"GH_TOKEN": "fake"})
    @patch.object(p, "publish_one", side_effect=[{"id": 123, "status": "uploaded"}, p.PublishError("sources failed")])
    def test_source_failure_preserves_successful_primary_result(self, publish_one):
        with patch.object(p, "make_plan", return_value=(self.plan, {})), tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(p.main(["--run-id", "2", "--platform", "curseforge", "--publish", "--notes-sha256", p.notes_digest(self.plan["changelog"]), "--output", tmp]), 1)
            results = json.loads((Path(tmp) / "results.json").read_text())
            self.assertEqual(results["curseforge"]["id"], 123)
            self.assertEqual(results["curseforge_sources"]["status"], "failed")


if __name__ == "__main__":
    unittest.main()
