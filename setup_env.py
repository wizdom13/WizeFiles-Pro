#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
import os
import sys
import subprocess
import platform
from pathlib import Path

# --- Configuration ---
# SecureCloud requires Go (golang), Android SDK, and Android NDK.
TARGET_SDK_VERSION = "platforms;android-36"
BUILD_TOOLS_VERSION = "build-tools;36.0.0"

def print_status(message, status="INFO"):
    colors = {
        "INFO": "\033[94m",  # Blue
        "SUCCESS": "\033[92m", # Green
        "WARNING": "\033[93m", # Yellow
        "ERROR": "\033[91m",   # Red
        "RESET": "\033[0m"
    }
    prefix = f"[{status}]"
    print(f"{colors.get(status, '')}{prefix} {message}{colors['RESET']}")

def check_command(command, args=["--version"]):
    try:
        subprocess.run([command] + args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False

def get_env_var(var_name, default=None):
    return os.environ.get(var_name, default)

def setup_go_environment():
    print_status("Checking Go environment...", "INFO")
    
    if not check_command("go", ["version"]):
        print_status("Go (golang) is not installed or not in PATH.", "ERROR")
        print_status("Please install Go from https://go.dev/dl/", "INFO")
        sys.exit(1)
    
    # Check for gomobile
    if not check_command("gomobile", ["version"]):
        print_status("gomobile tool not found. Installing...", "WARNING")
        try:
            # Install gomobile
            subprocess.run(["go", "install", "golang.org/x/mobile/cmd/gomobile@latest"], check=True)
            
            # Add GOPATH/bin to PATH for the current session if needed
            go_path = subprocess.check_output(["go", "env", "GOPATH"], text=True).strip()
            go_bin = os.path.join(go_path, "bin")
            os.environ["PATH"] += os.pathsep + go_bin
            
            # Initialize gomobile
            print_status("Initializing gomobile (this may take a minute)...", "INFO")
            subprocess.run([os.path.join(go_bin, "gomobile"), "init"], check=True)
            print_status("gomobile installed and initialized.", "SUCCESS")
        except subprocess.CalledProcessError as e:
            print_status(f"Failed to install gomobile: {e}", "ERROR")
            sys.exit(1)
    else:
        print_status("gomobile is already installed.", "SUCCESS")

def accept_licenses(android_home):
    """
    Writes official license hashes to the SDK directory to avoid 'License not accepted' errors.
    """
    print_status("Accepting Android SDK licenses...", "INFO")
    licenses_dir = os.path.join(android_home, "licenses")
    os.makedirs(licenses_dir, exist_ok=True)

    # Known hashes for Android SDK licenses
    licenses = {
        "android-sdk-license": [
            "8933bad161af4178b1185d1a37fbf41ea5269c55",
            "d56f5187479451eabf01fb78af6dfcb131a6481e",
            "24333f8a63b6825ea9c5514f83c2829b004d1fee",
        ],
        "android-sdk-preview-license": [
            "84831b9409646a918e30573bab4c9c91346d8abd",
        ],
        "intel-android-extra-license": [
            "d975f751698a77b662f1254ddbeed3901e976f5a",
        ],
    }

    for license_name, hashes in licenses.items():
        license_path = os.path.join(licenses_dir, license_name)
        with open(license_path, "w") as f:
            f.write("\n".join(hashes))
    
    print_status("Licenses accepted successfully.", "SUCCESS")

def install_sdk_packages(android_home):
    """
    Uses sdkmanager to install specific versions required by build.gradle.kts
    """
    # Verify the path based on the structure we enforced in setup_android_environment
    cmdline_tools_path = os.path.join(android_home, "cmdline-tools", "latest", "bin", "sdkmanager")
    
    if not os.path.exists(cmdline_tools_path):
        # Fallback check for older SDK layouts
        cmdline_tools_path = os.path.join(android_home, "tools", "bin", "sdkmanager")
    
    if os.path.exists(cmdline_tools_path):
        print_status(f"Installing {TARGET_SDK_VERSION} and {BUILD_TOOLS_VERSION}...", "INFO")
        try:
            # Prepare the command
            cmd = [cmdline_tools_path, TARGET_SDK_VERSION, BUILD_TOOLS_VERSION]
            
            # Pure Python "yes" piping:
            # Create a byte string of "y\n" repeated many times to answer any prompts.
            # This avoids relying on the external 'yes' CLI tool.
            yes_input = b"y\n" * 50 
            
            subprocess.run(
                cmd,
                input=yes_input,       # Pipe "y" to stdin
                check=True,
                stdout=subprocess.DEVNULL, # Silence standard output
                stderr=subprocess.PIPE     # Capture error output just in case
            )
            print_status("SDK packages installed.", "SUCCESS")
            
        except subprocess.CalledProcessError as e:
            # Decode the error message if possible
            error_msg = e.stderr.decode().strip() if e.stderr else "Unknown error"
            print_status(f"Failed to install SDK packages: {error_msg}", "WARNING")
            print_status("You may need to install them manually via Android Studio.", "INFO")
    else:
        print_status("sdkmanager not found. Skipping automatic package installation.", "WARNING")


def setup_android_environment():
    print_status("Checking Android environment...", "INFO")
    
    # 1. Locate Android SDK
    android_home = get_env_var("ANDROID_HOME") or get_env_var("ANDROID_SDK_ROOT")
    
    # Try default locations if env var is not set
    if not android_home:
        home = str(Path.home())
        system = platform.system()
        possible_paths = []
        if system == "Windows":
            possible_paths.append(os.path.join(home, "AppData", "Local", "Android", "Sdk"))
        elif system == "Darwin": # macOS
            possible_paths.append(os.path.join(home, "Library", "Android", "sdk"))
        elif system == "Linux":
            possible_paths.append(os.path.join(home, "Android", "Sdk"))
            possible_paths.append(os.path.join(home, "android-sdk"))
        
        for path in possible_paths:
            if os.path.exists(path):
                android_home = path
                break
    
    # --- AUTO-INSTALLATION LOGIC START ---
    if not android_home or not os.path.exists(android_home):
        print_status("Android SDK not found. Installing automatically...", "WARNING")
        
        # Define installation path in user's home directory
        home = str(Path.home())
        android_home = os.path.join(home, "android-sdk")
        os.makedirs(android_home, exist_ok=True)
        
        # URL for Linux Command Line Tools (compatible with Jules/CI environments)
        # Note: If running on Mac/Windows locally, you might need conditional URLs here.
        # This defaults to Linux for the cloud environment.
        cmdline_url = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        zip_path = os.path.join(android_home, "cmdline-tools.zip")
        
        try:
            print_status(f"Downloading Command Line Tools from {cmdline_url}...", "INFO")
            subprocess.run(["wget", "-q", cmdline_url, "-O", zip_path], check=True)
            
            print_status("Extracting...", "INFO")
            # Unzip
            subprocess.run(["unzip", "-q", zip_path, "-d", android_home], check=True)
            
            # Reorganize for sdkmanager requirements: cmdline-tools/latest/bin
            # The zip usually extracts a folder named "cmdline-tools"
            base_cmdline = os.path.join(android_home, "cmdline-tools")
            latest_dir = os.path.join(base_cmdline, "latest")
            
            # If 'latest' doesn't exist, we need to create it and move content
            if not os.path.exists(latest_dir):
                # Move everything from cmdline-tools/* into cmdline-tools/latest/*
                # We rename the extracted 'cmdline-tools' to 'temp', create 'cmdline-tools/latest', then move temp inside.
                temp_dir = os.path.join(android_home, "cmdline-tools_temp")
                os.rename(base_cmdline, temp_dir)
                os.makedirs(latest_dir, exist_ok=True)
                
                # Move contents
                for item in os.listdir(temp_dir):
                    os.rename(os.path.join(temp_dir, item), os.path.join(latest_dir, item))
                os.rmdir(temp_dir)

            # Cleanup zip
            os.remove(zip_path)
            
            print_status(f"Android SDK installed at: {android_home}", "SUCCESS")
            
            # Set environment variables for the current process so subsequent steps work
            os.environ["ANDROID_HOME"] = android_home
            # Add sdkmanager to PATH for this run
            sdk_bin = os.path.join(latest_dir, "bin")
            os.environ["PATH"] += os.pathsep + sdk_bin

        except Exception as e:
            print_status(f"Failed to auto-install Android SDK: {e}", "ERROR")
            sys.exit(1)
    # --- AUTO-INSTALLATION LOGIC END ---
        
    print_status(f"Using Android SDK at: {android_home}", "INFO")
    
    # 2. Accept Licenses (Fixes 'License not accepted' error)
    accept_licenses(android_home)

    # 3. Install required versions (Fixes missing platform-36 error)
    install_sdk_packages(android_home)

    # 4. Locate Android NDK
    ndk_home = get_env_var("ANDROID_NDK_HOME")
    
    # If not set, try to find it inside SDK/ndk
    if not ndk_home:
        ndk_root = os.path.join(android_home, "ndk")
        if os.path.exists(ndk_root):
            # Pick the latest version found
            versions = sorted(os.listdir(ndk_root))
            if versions:
                ndk_home = os.path.join(ndk_root, versions[-1])

    if not ndk_home or not os.path.exists(ndk_home):
        print_status("Could not locate Android NDK.", "WARNING")
        print_status("SecureCloud requires the NDK to build the 'scelink' component.", "INFO")
        print_status("You may need to run: sdkmanager --install \"ndk;27.0.12077973\"", "INFO")
    else:
        print_status(f"Found Android NDK at: {ndk_home}", "SUCCESS")

    return android_home, ndk_home

def create_local_properties(android_home, ndk_home):
    print_status("Generating local.properties...", "INFO")
    
    prop_file = Path("local.properties")
    
    content = f"## This file is automatically generated by setup_env.py\n"
    content += f"sdk.dir={android_home.replace(os.sep, '/')}\n"
    if ndk_home:
        content += f"ndk.dir={ndk_home.replace(os.sep, '/')}\n"
    
    try:
        with open(prop_file, "w") as f:
            f.write(content)
        print_status("local.properties created successfully.", "SUCCESS")
    except IOError as e:
        print_status(f"Failed to write local.properties: {e}", "ERROR")

def main():
    print_status("Starting SecureCloud Development Environment Setup for Jules...", "INFO")
    
    # 1. Setup Go
    setup_go_environment()
    
    # 2. Setup Android SDK/NDK and Licenses
    sdk, ndk = setup_android_environment()
    
    # 3. Create Gradle properties
    create_local_properties(sdk, ndk)
    
    print_status("-" * 40, "INFO")
    print_status("Setup Complete!", "SUCCESS")
    print_status("To build the app for testing, run:", "INFO")
    print_status("  1. ./gradlew assembleDebug", "INFO")
    print_status("-" * 40, "INFO")

if __name__ == "__main__":
    main()