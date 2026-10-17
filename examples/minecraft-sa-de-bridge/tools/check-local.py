#!/usr/bin/env python3
"""Compile the real core for Java 17 and exercise it against the actual JS script.

Requires Java with jdk.compiler (or a JDK), Node and Python 3.10+. No game stubs
are used to claim a Forge build. Results are explicitly synthetic.
"""
import pathlib
import subprocess
import tempfile
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run(args, check=True, cwd=ROOT)


def main():
    with tempfile.TemporaryDirectory(prefix="umsa-check-") as temp:
        base = pathlib.Path(temp)
        classes = base / "classes"
        classes.mkdir()
        core = ROOT / "mc/src/main/java/dev/universalmodder/sabridge/BridgeCore.java"
        run("java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(classes),
            str(core), str(ROOT / "tools/CoreChecks.java"), str(ROOT / "tools/BridgeHarness.java"))
        run("java", "-cp", str(classes), "CoreChecks", str(base / "unit"))
        run("node", "--check", str(ROOT / "cleo/umsa-bridge[fs].js"))

        mailbox = base / "integration"
        script = ROOT / "cleo/umsa-bridge[fs].js"
        # Java outlives CLEO by >3 seconds to exercise disappearance and stale-file detection.
        with (base / "java.log").open("w+") as java_log:
            java = subprocess.Popen(["java", "-cp", str(classes), "BridgeHarness", str(mailbox), "12"],
                                    stdout=java_log, stderr=subprocess.STDOUT)
            try:
                js = subprocess.run(["node", str(ROOT / "tools/cleo-harness.cjs"), str(script),
                                     str(mailbox), "4", "true"], capture_output=True, text=True,
                                    encoding="utf-8", timeout=10, check=True)
                assert "state=roundtrip" in js.stdout, js.stdout
                # Restart JS while Java stays alive; random session must be acknowledged again.
                js2 = subprocess.run(["node", str(ROOT / "tools/cleo-harness.cjs"), str(script),
                                      str(mailbox), "3"], capture_output=True, text=True,
                                     encoding="utf-8", timeout=10, check=True)
                assert "state=roundtrip" in js2.stdout, js2.stdout
                first_session = re.search(r"state=roundtrip session=(\d+)", js.stdout).group(1)
                second_session = re.search(r"state=roundtrip session=(\d+)", js2.stdout).group(1)
                assert first_session != second_session, "restart must change sender session"
                assert java.wait(timeout=12) == 0
                java_log.seek(0)
                evidence = java_log.read()
                assert "state=roundtrip" in evidence, evidence
                assert "state=stale" in evidence, evidence
                assert "|1|1234|-4567|890" in evidence, evidence
                assert "|1|100|640|-200" in js.stdout, js.stdout
                print("PASS cross-process Java core <-> actual JS with simulated CLEO APIs")
                print("PASS telemetry both ways, CLEO restart, stale peer timeout")
                print("NOT TESTED: Forge adapter, Win32 IniFiles, CLEO runtime, retail games")
            finally:
                if java.poll() is None:
                    java.terminate()
                    java.wait(timeout=5)


if __name__ == "__main__":
    main()
