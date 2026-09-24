#!/usr/bin/env python3
import os
import re
import shutil
import subprocess
import sys
import traceback
from pathlib import Path

def run(cmd, *, check=True, env=None):
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=check, env=env)

def which(x): return shutil.which(x) is not None

def read_text(p: Path) -> str:
    try:
        return p.read_text(encoding="utf-8", errors="ignore")
    except FileNotFoundError:
        return ""

def detect_android_settings(repo: Path):
    # best-effort parsing; falls back to known-good defaults used by F-Droid build of RSAF
    compile_sdk = None
    ndk_version = None
    build_tools = None

    candidates = [
        repo / "app" / "build.gradle.kts",
        repo / "app" / "build.gradle",
        repo / "build.gradle.kts",
        repo / "build.gradle",
    ]

    for f in candidates:
        txt = read_text(f)
        if not txt:
            continue

        m = re.search(r"\bcompileSdk(?:Version)?\s*=?\s*(\d+)", txt)
        if m and compile_sdk is None:
            compile_sdk = int(m.group(1))

        m = re.search(r"\bndkVersion\s*=\s*['\"]([^'\"]+)['\"]", txt)
        if m and ndk_version is None:
            ndk_version = m.group(1).strip()

        m = re.search(r"\bbuildToolsVersion\s*=\s*['\"]([^'\"]+)['\"]", txt)
        if m and build_tools is None:
            build_tools = m.group(1).strip()

    # Fallbacks aligned with RSAF builds seen in the wild (F-Droid uses NDK r29).
    if ndk_version is None:
        ndk_version = "29.0.14206865"
    if compile_sdk is None:
        # If you later bump compileSdk, the sdkmanager install below will still try to install it.
        compile_sdk = 36
    if build_tools is None:
        # Keep the fallback aligned with app/build.gradle.
        build_tools = "36.0.0"

    return compile_sdk, ndk_version, build_tools

def ensure_apt_deps():
    if not which("apt-get"):
        return

    # Minimal packages to fetch/unzip SDK and run builds
    pkgs = [
        "ca-certificates", "curl", "wget", "unzip", "git",
        "bash", "zip", "make", "python3", "python3-venv",
    ]
    run(["sudo", "apt-get", "update"])
    run(["sudo", "apt-get", "install", "-y"] + pkgs)

def ensure_java21():
    # Prefer system JDK21 if available, else stop with a clear message.
    if which("java"):
        try:
            out = subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT).decode("utf-8", "ignore")
            if "21." in out or "openjdk 21" in out:
                return
        except Exception:
            pass

    if which("apt-get"):
        # Ubuntu 24.04+/Debian variants typically have openjdk-21-jdk
        run(["sudo", "apt-get", "install", "-y", "openjdk-21-jdk-headless"])
        return

    raise RuntimeError("JDK 21 not found and no supported package manager detected.")

def ensure_go(go_version: str, install_dir: Path):
    # Use existing go if it matches major.minor; otherwise install tarball to install_dir/go
    if which("go"):
        try:
            out = subprocess.check_output(["go", "version"]).decode("utf-8", "ignore")
            if f"go{go_version.split('.')[0]}.{go_version.split('.')[1]}" in out:
                return Path(
                    subprocess.check_output(["go", "env", "GOROOT"])
                    .decode("utf-8", "ignore")
                    .strip()
                )
        except Exception:
            pass

    install_dir.mkdir(parents=True, exist_ok=True)
    go_root = install_dir / "go"
    if go_root.exists():
        shutil.rmtree(go_root)

    # Standard official URL pattern
    arch = "amd64"
    url = f"https://dl.google.com/go/go{go_version}.linux-{arch}.tar.gz"
    tar = install_dir / f"go{go_version}.tar.gz"

    run(["bash", "-lc", f"curl -L --fail '{url}' -o '{tar}'"])
    run(["bash", "-lc", f"tar -C '{install_dir}' -xzf '{tar}'"])
    return go_root

def install_android_cmdline_tools(sdk_root: Path, tools_zip_url: str):
    # Download and install cmdline-tools into sdk_root/cmdline-tools/latest
    cmdline_latest = sdk_root / "cmdline-tools" / "latest"
    if (cmdline_latest / "bin" / "sdkmanager").exists():
        return

    sdk_root.mkdir(parents=True, exist_ok=True)
    tmp = sdk_root / "_tmp_cmdline"
    if tmp.exists():
        shutil.rmtree(tmp)
    tmp.mkdir(parents=True, exist_ok=True)

    zip_path = tmp / "cmdline-tools.zip"
    run(["bash", "-lc", f"curl -L --fail '{tools_zip_url}' -o '{zip_path}'"])
    run(["bash", "-lc", f"unzip -q '{zip_path}' -d '{tmp}'"])

    # Google zip contains "cmdline-tools/"; place it as "cmdline-tools/latest/"
    (sdk_root / "cmdline-tools").mkdir(parents=True, exist_ok=True)
    extracted = tmp / "cmdline-tools"
    if not extracted.exists():
        raise RuntimeError("Unexpected cmdline-tools zip layout (missing cmdline-tools/).")

    if cmdline_latest.exists():
        shutil.rmtree(cmdline_latest)
    shutil.move(str(extracted), str(cmdline_latest))

def sdkmanager_path(sdk_root: Path) -> Path:
    return sdk_root / "cmdline-tools" / "latest" / "bin" / "sdkmanager"

def accept_licenses(sdk_root: Path):
    sm = sdkmanager_path(sdk_root)
    env = os.environ.copy()
    env["ANDROID_SDK_ROOT"] = str(sdk_root)
    env["ANDROID_HOME"] = str(sdk_root)

    # yes | sdkmanager --licenses
    run(["bash", "-lc", f"yes | '{sm}' --sdk_root='{sdk_root}' --licenses"], env=env)

def install_sdk_packages(sdk_root: Path, packages):
    sm = sdkmanager_path(sdk_root)
    env = os.environ.copy()
    env["ANDROID_SDK_ROOT"] = str(sdk_root)
    env["ANDROID_HOME"] = str(sdk_root)

    for pkg in packages:
        run([str(sm), f"--sdk_root={sdk_root}", "--install", pkg], env=env)

def write_env_file(env_file: Path, sdk_root: Path, ndk_version: str, go_home: Path):
    ndk_home = sdk_root / "ndk" / ndk_version
    env_file.parent.mkdir(parents=True, exist_ok=True)

    # Keep PATH additions minimal and explicit.
    content = f"""#!/usr/bin/env bash
set -e

export ANDROID_SDK_ROOT="{sdk_root}"
export ANDROID_HOME="{sdk_root}"
export ANDROID_NDK_HOME="{ndk_home}"
export ANDROID_NDK_ROOT="{ndk_home}"

export GOROOT="{go_home}"
export PATH="$GOROOT/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

# Keep Gradle caches inside repo (faster + reproducible)
export GRADLE_USER_HOME="${{PWD}}/.cache/gradle"

# Gradle stability in CI-ish environments
export JAVA_TOOL_OPTIONS="${{JAVA_TOOL_OPTIONS:-}} -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true"
# Robolectric resolves android-all runtime artifacts lazily during tests. These
# defaults keep Codex/local runs deterministic while still allowing callers to
# point at an internal mirror if the default Maven endpoint is unavailable.
export ROBOLECTRIC_DEPENDENCY_REPO_URL="${{ROBOLECTRIC_DEPENDENCY_REPO_URL:-https://repo1.maven.org/maven2}}"
export ROBOLECTRIC_DEPENDENCY_REPO_ID="${{ROBOLECTRIC_DEPENDENCY_REPO_ID:-central}}"

export GRADLE_OPTS="${{GRADLE_OPTS:-}} -Dorg.gradle.daemon=false -Dkotlin.incremental=false -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true -Drobolectric.dependency.repo.url=$ROBOLECTRIC_DEPENDENCY_REPO_URL -Drobolectric.dependency.repo.id=$ROBOLECTRIC_DEPENDENCY_REPO_ID"
"""
    env_file.write_text(content, encoding="utf-8")
    env_file.chmod(0o755)



def _escape_gradle_properties_path(p: Path) -> str:
    # local.properties uses Java .properties escaping rules.
    s = str(p)
    # Gradle accepts forward slashes even on Windows.
    if os.name == "nt":
        s = s.replace("\\", "/")
    # Escape backslashes/spaces for safety.
    s = s.replace("\\", "\\\\")
    s = s.replace(" ", "\\ ")
    return s

def write_local_properties(repo: Path, sdk_root: Path, ndk_home: Path):
    # Make Gradle happy even if ANDROID_HOME isn't exported in the parent shell.
    lp = repo / "local.properties"
    lines = [
        f"sdk.dir={_escape_gradle_properties_path(sdk_root)}",
    ]
    # Do not write ndk.dir; AGP now prefers android.ndkVersion in module build files
    # and environment variables (ANDROID_NDK_HOME/ANDROID_NDK_ROOT).
    lp.write_text("\n".join(lines) + "\n", encoding="utf-8")

def export_to_github_env(sdk_root: Path, ndk_home: Path):
    # Persist env vars across GitHub Actions steps (if running there).
    github_env = os.environ.get("GITHUB_ENV")
    if not github_env:
        return
    with open(github_env, "a", encoding="utf-8") as f:
        f.write(f"ANDROID_SDK_ROOT={sdk_root}\n")
        f.write(f"ANDROID_HOME={sdk_root}\n")
        f.write(f"ANDROID_NDK_HOME={ndk_home}\n")
        f.write(f"ANDROID_NDK_ROOT={ndk_home}\n")
def main():
    repo = Path.cwd()

    # Configurable knobs
    sdk_root = Path(os.environ.get("ANDROID_SDK_ROOT", str(repo / ".cache" / "android-sdk"))).resolve()
    tools_zip_url = os.environ.get(
        "ANDROID_CMDLINE_TOOLS_URL",
        # A commonly referenced "latest" package URL (update if you pin differently).
        "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
    )
    go_version = os.environ.get("GO_VERSION", "1.26.5")
    go_install_dir = Path(os.environ.get("GO_INSTALL_DIR", str(repo / ".cache" / "go"))).resolve()
    env_out = Path(os.environ.get("ENV_OUT", str(repo / ".codex" / "env.sh"))).resolve()

    compile_sdk, ndk_version, build_tools = detect_android_settings(repo)
    print(f"Detected/Default: compileSdk={compile_sdk}, ndkVersion={ndk_version}, buildTools={build_tools}")

    # System deps (optional)
    if which("sudo") and which("apt-get"):
        ensure_apt_deps()
        ensure_java21()

    # Android cmdline-tools
    install_android_cmdline_tools(sdk_root, tools_zip_url)
    accept_licenses(sdk_root)

    sdk_packages = [
        "platform-tools",
        "cmake;3.22.1",
        "ndk;" + ndk_version,
        f"platforms;android-{compile_sdk}",
        f"build-tools;{build_tools}",
    ]
    install_sdk_packages(sdk_root, sdk_packages)

    # Go toolchain
    go_home = ensure_go(go_version, go_install_dir)

    # Emit env file for Codex (or any CI shell)
    write_env_file(env_out, sdk_root, ndk_version, go_home)
    print(f"Wrote env: {env_out}")

    # Write Gradle local.properties so ./gradlew works even if env vars are not propagated
    write_local_properties(repo, sdk_root, sdk_root / "ndk" / ndk_version)
    export_to_github_env(sdk_root, sdk_root / "ndk" / ndk_version)
    print("Wrote local.properties")

    # Optional smoke build (enable by env var)
    if os.environ.get("SMOKE_BUILD", "0") == "1":
        run(["bash", "-lc", "chmod +x ./gradlew && ./gradlew --no-daemon :app:assembleDebug"])

if __name__ == "__main__":
    try:
        main()
    except Exception:
        traceback.print_exc()
        sys.exit(1)
