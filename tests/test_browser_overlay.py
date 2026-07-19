from __future__ import annotations

import hashlib
import json
import re
import unittest
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class _StartPageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.scripts: list[dict[str, str | None]] = []
        self.forms: list[dict[str, str | None]] = []
        self.images: list[dict[str, str | None]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag == "script": self.scripts.append(values)
        if tag == "form": self.forms.append(values)
        if tag == "img": self.images.append(values)


class BrowserOverlayTests(unittest.TestCase):
    def test_version_pin_has_exact_source_digest(self) -> None:
        content = (ROOT / "VERSION.env").read_text(encoding="utf-8")
        self.assertRegex(content, r"(?m)^FIREFOX_ESR_VERSION=140\.12\.0esr$")
        digest = re.search(r"(?m)^FIREFOX_SOURCE_SHA512=([0-9a-f]+)$", content)
        self.assertIsNotNone(digest)
        self.assertEqual(len(digest.group(1)), hashlib.sha512().digest_size * 2)

    def test_extension_has_no_privileged_or_host_permissions(self) -> None:
        manifest = json.loads((ROOT / "extension/manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["permissions"], [])
        self.assertEqual(manifest["host_permissions"], [])
        self.assertIn("form-action https://duckduckgo.com", manifest["content_security_policy"]["extension_pages"])
        self.assertIn("object-src 'none'", manifest["content_security_policy"]["extension_pages"])

    def test_start_page_uses_local_scripts_and_explicit_search_only(self) -> None:
        parser = _StartPageParser()
        parser.feed((ROOT / "extension/start/index.html").read_text(encoding="utf-8"))
        self.assertTrue(parser.scripts)
        self.assertTrue(all(not (script.get("src") or "").startswith(("http:", "https:")) for script in parser.scripts))
        self.assertEqual([form.get("action") for form in parser.forms], ["https://duckduckgo.com/"])
        self.assertTrue(all(not (image.get("src") or "").startswith(("http:", "https:")) for image in parser.images))

    def test_privacy_policies_disable_hidden_reporting(self) -> None:
        policies = json.loads((ROOT / "distribution/policies.json").read_text(encoding="utf-8"))["policies"]
        for key in ("DisableFirefoxStudies", "DisablePocket", "DisableTelemetry"):
            self.assertIs(policies[key], True)
        preferences = policies["Preferences"]
        self.assertIs(preferences["toolkit.telemetry.enabled"]["Value"], False)
        self.assertIs(preferences["browser.crashReports.unsubmittedCheck.autoSubmit2"]["Value"], False)

    def test_extension_policy_does_not_silently_block_user_extensions(self) -> None:
        policies = json.loads((ROOT / "distribution/policies.json").read_text(encoding="utf-8"))["policies"]
        self.assertEqual(policies["ExtensionSettings"]["*"]["installation_mode"], "allowed")
        start = policies["ExtensionSettings"]["kixkan-start@macandwindows.local"]
        self.assertEqual(start["installation_mode"], "force_installed")
        self.assertTrue(start["install_url"].startswith("file:///usr/lib/kixkan-browser/"))

    def test_branding_assets_are_present_and_nonempty(self) -> None:
        for path in (ROOT / "branding/kixkan-logo.png", ROOT / "branding/kixkan-wallpaper.png"):
            self.assertGreater(path.stat().st_size, 100_000)

    def test_os_repository_is_not_nested_here(self) -> None:
        self.assertFalse((ROOT / "distro").exists())
        self.assertFalse((ROOT / "iso").exists())


if __name__ == "__main__":
    unittest.main()

