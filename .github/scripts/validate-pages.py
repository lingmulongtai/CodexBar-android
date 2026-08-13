#!/usr/bin/env python3
"""Validate the dependency-free GitHub Pages bundle before deployment."""

from __future__ import annotations

import json
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
from xml.etree import ElementTree


class LandingPageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: list[str] = []
        self.references: list[tuple[str, str, int]] = []
        self.images: list[tuple[dict[str, str], int]] = []
        self.json_ld_blocks: list[str] = []
        self._json_ld_parts: list[str] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = {name: value or "" for name, value in attrs}
        line, _ = self.getpos()

        element_id = attributes.get("id")
        if element_id:
            self.ids.append(element_id)

        for attribute in ("href", "src", "data-image"):
            value = attributes.get(attribute)
            if value:
                self.references.append((tag, value, line))

        if tag == "img":
            self.images.append((attributes, line))

        if tag == "script" and attributes.get("type") == "application/ld+json":
            self._json_ld_parts = []

    def handle_endtag(self, tag: str) -> None:
        if tag == "script" and self._json_ld_parts is not None:
            self.json_ld_blocks.append("".join(self._json_ld_parts))
            self._json_ld_parts = None

    def handle_data(self, data: str) -> None:
        if self._json_ld_parts is not None:
            self._json_ld_parts.append(data)


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)


def validate(site_root: Path) -> list[str]:
    errors: list[str] = []
    required_files = {
        ".nojekyll",
        "index.html",
        "assets/site.css",
        "assets/site.js",
        "assets/og.png",
        "favicon.svg",
        "site.webmanifest",
        "robots.txt",
        "sitemap.xml",
    }

    for relative_path in sorted(required_files):
        if not (site_root / relative_path).is_file():
            errors.append(f"missing required file: {relative_path}")

    index_path = site_root / "index.html"
    if not index_path.is_file():
        return errors

    parser = LandingPageParser()
    parser.feed(index_path.read_text(encoding="utf-8"))

    seen_ids: set[str] = set()
    for element_id in parser.ids:
        if element_id in seen_ids:
            errors.append(f"duplicate HTML id: #{element_id}")
        seen_ids.add(element_id)

    root_resolved = site_root.resolve()
    checked_local_paths: set[Path] = set()

    for tag, value, line in parser.references:
        if value.startswith("#"):
            if value[1:] not in seen_ids:
                errors.append(f"index.html:{line}: unresolved anchor {value}")
            continue

        parsed = urlsplit(value)
        if parsed.scheme:
            if parsed.scheme != "https":
                errors.append(f"index.html:{line}: external {tag} URL must use HTTPS: {value}")
            continue

        if value.startswith("//"):
            errors.append(f"index.html:{line}: protocol-relative URL is not allowed: {value}")
            continue

        if value.startswith("/"):
            errors.append(
                f"index.html:{line}: root-relative path breaks project Pages sites: {value}"
            )
            continue

        relative_path = unquote(parsed.path)
        if not relative_path:
            continue

        candidate = (site_root / relative_path).resolve()
        try:
            candidate.relative_to(root_resolved)
        except ValueError:
            errors.append(f"index.html:{line}: local reference escapes docs/: {value}")
            continue

        checked_local_paths.add(candidate)
        if not candidate.exists():
            errors.append(f"index.html:{line}: missing local reference: {value}")

    for attributes, line in parser.images:
        if not attributes.get("alt"):
            errors.append(f"index.html:{line}: image requires non-empty alt text")
        if not attributes.get("width") or not attributes.get("height"):
            errors.append(f"index.html:{line}: image requires width and height")
        if attributes.get("loading") != "lazy" and attributes.get("fetchpriority") != "high":
            errors.append(
                f"index.html:{line}: non-hero image must use loading=lazy or fetchpriority=high"
            )

    if len(parser.json_ld_blocks) != 1:
        errors.append("index.html must contain exactly one JSON-LD block")
    else:
        try:
            json.loads(parser.json_ld_blocks[0])
        except json.JSONDecodeError as error:
            errors.append(f"invalid JSON-LD: {error}")

    try:
        manifest = json.loads((site_root / "site.webmanifest").read_text(encoding="utf-8"))
        if manifest.get("start_url") != "./":
            errors.append("site.webmanifest start_url must remain project-relative (./)")
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"invalid site.webmanifest: {error}")

    try:
        sitemap = ElementTree.parse(site_root / "sitemap.xml")
        namespace = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
        locations = [element.text for element in sitemap.findall("sm:url/sm:loc", namespace)]
        expected = "https://lingmulongtai.github.io/CodexBar-android/"
        if expected not in locations:
            errors.append(f"sitemap.xml must include canonical URL: {expected}")
    except (OSError, ElementTree.ParseError) as error:
        errors.append(f"invalid sitemap.xml: {error}")

    if not errors:
        print(
            "Validated landing page: "
            f"{len(parser.references)} references, {len(checked_local_paths)} local assets, "
            f"{len(parser.ids)} IDs, {len(parser.images)} images."
        )

    return errors


def main() -> int:
    repository_root = Path(__file__).resolve().parents[2]
    site_root = repository_root / "docs"
    errors = validate(site_root)
    for error in errors:
        fail(error)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
