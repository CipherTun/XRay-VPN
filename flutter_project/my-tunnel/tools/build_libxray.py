#!/usr/bin/env python3
"""Build the latest published upstream libXray Android AAR, with strict checks.

Set LIBXRAY_REF to an exact tag to reproduce a build. The default `latest` resolves
GitHub's latest non-prerelease release, then records the selected tag and commit.
No placeholder AARs are created or accepted.
"""
from __future__ import annotations
import io
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_LIBS = ROOT / "android" / "app" / "libs"
ASSETS = ROOT / "assets"
REQUESTED_REF = os.environ.get("LIBXRAY_REF", "latest").strip()
WORK = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "libXray-upstream-build"
REPO_URL = "https://github.com/XTLS/libXray.git"


def run(*args: str, cwd: Path | None = None, capture: bool = False) -> str:
    print("+", " ".join(args), flush=True)
    result = subprocess.run(args, cwd=cwd, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ""


def latest_release_tag() -> str:
    request = urllib.request.Request(
        "https://api.github.com/repos/XTLS/libXray/releases/latest",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "XRay-VPN-build"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        release = json.load(response)
    tag = release.get("tag_name", "")
    if not tag or release.get("draft") or release.get("prerelease"):
        raise SystemExit("ERROR: GitHub did not return a published stable libXray release")
    return tag


def main() -> None:
    ref = latest_release_tag() if REQUESTED_REF.lower() == "latest" else REQUESTED_REF
    if not ref or ref.startswith("-") or any(c.isspace() for c in ref):
        raise SystemExit(f"ERROR: invalid LIBXRAY_REF: {ref!r}")
    if WORK.exists():
        shutil.rmtree(WORK)
    run("git", "clone", "--depth", "1", "--branch", ref, REPO_URL, str(WORK))
    commit = run("git", "rev-parse", "HEAD", cwd=WORK, capture=True)
    run("python3", "build/main.py", "android", cwd=WORK)

    aar = WORK / "libXray.aar"
    if not aar.is_file() or aar.stat().st_size < 100_000:
        raise SystemExit(f"ERROR: upstream build did not produce a plausible AAR: {aar}")
    with zipfile.ZipFile(aar) as z:
        names = z.namelist()
        native = [n for n in names if n.startswith("jni/") and n.endswith(".so")]
        if "classes.jar" not in names:
            raise SystemExit("ERROR: libXray AAR has no classes.jar")
        with zipfile.ZipFile(io.BytesIO(z.read("classes.jar"))) as classes:
            class_names = set(classes.namelist())
            required_classes = (
                "libXray/LibXray.class",
                "libXray/DialerController.class",
            )
            for required_class in required_classes:
                if required_class not in class_names:
                    raise SystemExit(f"ERROR: libXray Java binding is missing {required_class}")
            # The gomobile support classes are part of the runtime. Ensure the
            # AAR is not later stripped of them by the Android patcher.
            if not any(name.startswith("go/Seq") and name.endswith(".class") for name in class_names):
                raise SystemExit("ERROR: libXray AAR lacks gomobile go.Seq runtime classes")
        # Verify the exact Java-facing API consumed by MainActivity and the VPN service.
        classes_jar = WORK / "libXray-classes.jar"
        classes_jar.write_bytes(z.read("classes.jar"))
        api = subprocess.run(
            ["javap", "-classpath", str(classes_jar), "libXray.LibXray", "libXray.DialerController"],
            check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        ).stdout
        for method in ("invoke(", "registerDialerController(", "registerListenerController(", "setDNS(", "resetDNS(", "protectFd("):
            if method not in api:
                raise SystemExit(f"ERROR: libXray Java API does not expose required method {method}")
        required_abis = {"arm64-v8a", "armeabi-v7a"}
        present = {n.split("/")[1] for n in native if len(n.split("/")) > 2}
        if not required_abis.issubset(present):
            raise SystemExit(f"ERROR: libXray AAR ABI inventory incomplete. Present={sorted(present)}")
        for abi in sorted(required_abis):
            if not any(n.startswith(f"jni/{abi}/") and n.endswith(".so") for n in native):
                raise SystemExit(f"ERROR: no native libraries for {abi}")
        print("Verified upstream native libraries:")
        for name in native:
            print(" -", name)

    ANDROID_LIBS.mkdir(parents=True, exist_ok=True)
    target = ANDROID_LIBS / "libXray.aar"
    shutil.copy2(aar, target)
    built_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    info = {
        "engine": "XTLS/libXray",
        "release": ref,
        "commit": commit,
        "builtAtUtc": built_at,
        "requestedRef": REQUESTED_REF,
        "abis": ["armeabi-v7a", "arm64-v8a"],
        "appBuildNumber": int(os.environ.get("GITHUB_RUN_NUMBER", "0")),
        "sourceCommit": os.environ.get("GITHUB_SHA", "local"),
    }
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "build-info.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    (ROOT / "libxray-release.txt").write_text(
        f"release={ref}\ncommit={commit}\nbuiltAtUtc={built_at}\n", encoding="utf-8")
    print(f"Installed official upstream AAR: {target} ({target.stat().st_size} bytes)")
    print(f"Resolved libXray release: {ref} ({commit})")
    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with open(output_path, "a", encoding="utf-8") as output:
            output.write(f"libxray_ref={ref}\nlibxray_commit={commit}\n")
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(
                f"### libXray build\n\n- Release: `{ref}`\n- Commit: `{commit}`\n"
                f"- ABIs: `armeabi-v7a`, `arm64-v8a`\n- AAR size: `{target.stat().st_size}` bytes\n"
            )


if __name__ == "__main__":
    main()
