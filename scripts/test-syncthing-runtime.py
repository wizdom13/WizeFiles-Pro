#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Real-engine contract test. All peer traffic stays on loopback; no public discovery."""
import http.client
import json
import os
from pathlib import Path
import socket
import signal
import select
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET


class UnixHTTP(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX)
        self.sock.settimeout(10)
        self.sock.connect(self.host)


def wait_for(predicate, seconds=45):
    end = time.monotonic() + seconds
    error = None
    while time.monotonic() < end:
        try:
            if predicate():
                return
        except (OSError, ValueError, KeyError) as failure:
            error = failure
        time.sleep(.2)
    raise AssertionError(f"Condition timed out: {error}")


class Engine:
    def __init__(self, binary, root, supervisor):
        self.root = root
        root.mkdir()
        self.socket = str(root / "api.sock")
        self.key = os.urandom(32).hex()
        # Generate certificates/configuration without starting any listeners or discovery.
        subprocess.run([binary, "--home", str(root), "generate"], check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        config = ET.parse(root / "config.xml")
        options = config.getroot().find("options")
        for name, value in {"globalAnnounceEnabled": "false", "localAnnounceEnabled": "false",
            "relaysEnabled": "false", "natEnabled": "false", "startBrowser": "false",
            "urAccepted": "-1", "crashReportingEnabled": "false", "autoUpgradeIntervalH": "0"}.items():
            node = options.find(name)
            if node is None:
                node = ET.SubElement(options, name)
            node.text = value
        for node in list(options.findall("listenAddress")):
            options.remove(node)
        ET.SubElement(options, "listenAddress").text = "tcp://127.0.0.1:0"
        config.write(root / "config.xml", encoding="utf-8", xml_declaration=True)
        log = (root / "engine.log").open("wb")
        self.process = subprocess.Popen([supervisor, str(os.getpid()), binary, "--home", str(root), "serve", "--no-browser",
            "--no-restart", "--no-upgrade", "--paused", "--gui-address=unix://" + self.socket,
            "--log-level=WARN"], env={**os.environ, "STGUIAPIKEY": self.key, "STMONITORED": "yes"},
            stdout=log, stderr=subprocess.STDOUT)
        log.close()
        try:
            wait_for(lambda: self.call("GET", "/system/status"))
        except BaseException:
            print((root / "engine.log").read_text()[-4000:], file=sys.stderr)
            self.stop()
            raise
        self.id = self.call("GET", "/system/status")["myID"]
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            self.address = f"tcp://127.0.0.1:{reservation.getsockname()[1]}"
        options = self.call("GET", "/config/options")
        options.update(listenAddresses=[self.address], globalAnnounceEnabled=False,
            localAnnounceEnabled=False, relaysEnabled=False, natEnabled=False,
            startBrowser=False, urAccepted=-1, crashReportingEnabled=False)
        self.call("PUT", "/config/options", options)

    def call(self, method, path, data=None, key=None):
        connection = UnixHTTP(self.socket)
        try:
            connection.request(method, "/rest" + path,
                json.dumps(data).encode() if data is not None else None,
                {"Host": "localhost", "X-API-Key": key or self.key, "Content-Type": "application/json"})
            response = connection.getresponse()
            body = response.read()
            if response.status >= 400:
                raise ValueError(f"{method} {path}: {response.status}: {body[:150]!r}")
            return json.loads(body) if body else None
        finally:
            connection.close()

    def share(self, other, folder):
        device = self.call("GET", "/config/defaults/device")
        device.update(deviceID=other.id, addresses=[other.address], paused=False,
            introducer=False, autoAcceptFolders=False)
        self.call("PUT", "/config/devices/" + other.id, device)
        config = self.call("GET", "/config/defaults/folder")
        config.update(id="wize-test", path=str(folder), filesystemType="basic", type="sendreceive",
            devices=[{"deviceID": self.id}, {"deviceID": other.id}], paused=True,
            fsWatcherEnabled=True, rescanIntervalS=1, ignorePerms=True,
            versioning={"type": "simple", "params": {"keep": "5"}})
        self.call("PUT", "/config/folders/wize-test", config)
        # Verify the paused-folder ignore API used by Android before enabling writes.
        self.call("POST", "/db/ignores?folder=wize-test", {"ignore": ["*.ignored"]})
        assert "*.ignored" in self.call("GET", "/db/ignores?folder=wize-test")["ignore"]
        config["paused"] = False
        self.call("PUT", "/config/folders/wize-test", config)

    def scan(self):
        self.call("POST", "/db/scan?folder=wize-test")

    def stop(self):
        if self.process.poll() is None:
            try:
                self.call("POST", "/system/shutdown")
                self.process.wait(timeout=10)
            except (OSError, ValueError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait(timeout=5)


def test_supervisor(supervisor):
    # The child retains stdout. EOF proves both supervisor and engine have exited;
    # this also works when /proc is mounted from a different PID namespace.
    child_code = "import time; print('ready', flush=True); time.sleep(60)"
    helper_code = ("import os,subprocess,sys; p=subprocess.Popen([sys.argv[1],"
        "str(os.getpid()),sys.executable,'-c',sys.argv[2]]); p.wait()")
    helper = subprocess.Popen([sys.executable, "-c", helper_code, supervisor, child_code],
        stdout=subprocess.PIPE, start_new_session=True)
    try:
        assert select.select([helper.stdout], [], [], 5)[0], "Engine did not start"
        assert helper.stdout.readline() == b"ready\n"
        helper.kill()
        helper.wait(timeout=5)
        assert select.select([helper.stdout], [], [], 5)[0], "Orphan engine still holds stdout"
        assert helper.stdout.read(1) == b"", "Engine survived its parent"
        print("PASS: supervisor stops engine after parent process death")
    finally:
        try:
            os.killpg(helper.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        helper.wait(timeout=5)


def main():
    test_supervisor(sys.argv[2])
    engines = []
    with tempfile.TemporaryDirectory(prefix="wf-st-") as temp:
        root = Path(temp)
        try:
            first = Engine(sys.argv[1], root / "first", sys.argv[2])
            engines.append(first)
            second = Engine(sys.argv[1], root / "second", sys.argv[2])
            engines.append(second)
            assert first.id != second.id
            assert first.call("GET", "/svc/deviceid?id=" + second.id)["id"] == second.id
            try:
                first.call("GET", "/system/status", key="wrong")
                raise AssertionError("Unauthenticated control request accepted")
            except ValueError as failure:
                assert "403" in str(failure)
            folders = [root / "a", root / "b"]
            for folder in folders:
                folder.mkdir()
            payload = os.urandom(2 * 1024 * 1024)
            (folders[0] / "document.bin").write_bytes(payload)
            (folders[0] / "secret.ignored").write_text("exclude")
            first.share(second, folders[0])
            second.share(first, folders[1])
            first.scan()
            wait_for(lambda: (folders[1] / "document.bin").read_bytes() == payload)
            assert not (folders[1] / "secret.ignored").exists()
            # Completion must include the peer's acknowledged index.
            def complete():
                status = first.call("GET", "/db/completion?folder=wize-test&device=" + second.id)
                return status["remoteState"] == "valid" and status["needItems"] == 0 and status["needBytes"] == 0
            wait_for(complete)
            (folders[1] / "document.bin").write_text("changed by peer")
            second.scan()
            wait_for(lambda: (folders[0] / "document.bin").read_bytes() == b"changed by peer")
            wait_for(lambda: any(p.read_bytes() == payload for p in
                (folders[0] / ".stversions").rglob("document*.bin")))
            (folders[0] / "document.bin").unlink()
            first.scan()
            wait_for(lambda: not (folders[1] / "document.bin").exists())
            second.process.kill()
            second.process.wait(timeout=5)
            def control_stopped():
                try:
                    second.call("GET", "/system/status")
                    return False
                except (OSError, ValueError):
                    return True
            wait_for(control_stopped, seconds=5)
            wait_for(lambda: not first.call("GET", "/system/connections")["connections"][second.id]["connected"])
            print("PASS: private authenticated control, pairing, paused ignores, two-way data, versions, deletion, disconnection")
        finally:
            for engine in reversed(engines):
                engine.stop()


if __name__ == "__main__":
    main()
