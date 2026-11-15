#!/usr/bin/env python3
"""Orbit: prepare Google's Antigravity ACP server ON THE PHONE from Google's own download.

Orbit downloads the linux-arm64 zip named in the ACP registry from dl.google.com and checks its sha256
before this runs. Here we take Google's helper binary and the server's Python sources out of it (the
bundled .so files need TCMalloc, so they are dropped), install public wheels for the native parts into a
venv, and smoke-test ACP initialize. Nothing is re-hosted. Approach after openbot-local's agy-hybrid,
slimmed: the PAR is read inside the zip (never written out), tests are skipped, pip is removed after
use, and nothing is precompiled.

Usage: orbit_prepare.py --zip FILE --version X.Y.Z [--root /root/agy] [--python /opt/python/bin/python3]
Progress on stderr: "[orbit] STAGE <name> [pct]". Exit 0 = ready; <root>/current points at it.
"""
import argparse, json, os, re, shutil, subprocess, sys, tempfile, threading, zipfile

ACP_MAIN = "google3/cloud/developer_experience/antigravity_extensions/acp_server"
TP = "google3/third_party/py"
# Bundled packages replaced by public wheels (native parts, or metadata checks). name -> (pypi, pin?)
REPLACE = {"pydantic": ("pydantic", True), "pydantic_core": (None, False),
           "PIL": ("pillow", True), "psutil": ("psutil", True), "cryptography": ("cryptography", False)}
ALWAYS = ["protobuf", "certifi"]
TOOLS = "/opt/orbit"
SMOKE_TIMEOUT = 180
SKIP_DIR = re.compile(r"/(tests|testdata)/")


def log(*a): print("[orbit]", *a, file=sys.stderr, flush=True)
def stage(name, pct=None): log("STAGE", name, *([] if pct is None else [int(pct)]))


def bundled_version(src, top):
    for cand in ("version.py", "__init__.py", "_version.py", "__about__.py"):
        p = os.path.join(src, TP, top, cand)
        if os.path.exists(p):
            m = re.search(r"^(?:__version__|VERSION|version)\s*[:=][^'\"\n]*['\"]([0-9][^'\"]*)['\"]",
                          open(p, errors="ignore").read(), re.M)
            if m: return m.group(1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", required=True)
    ap.add_argument("--version", required=True)
    ap.add_argument("--root", default="/root/agy")
    ap.add_argument("--python", default="/opt/python/bin/python3")
    a = ap.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+", a.version): sys.exit(f"bad version {a.version!r}")
    os.makedirs(a.root, exist_ok=True)
    for old in os.listdir(a.root):
        if old.startswith("."): shutil.rmtree(os.path.join(a.root, old), ignore_errors=True)
    work = tempfile.mkdtemp(prefix=f".{a.version}.", dir=a.root)
    try:
        stage("extract", 0)
        bin_dir = os.path.join(work, "bin"); os.makedirs(bin_dir)
        src = os.path.join(work, "src")
        with zipfile.ZipFile(a.zip) as outer:
            outer.extract("localharness_external", bin_dir)
            # The PAR is an ELF launcher with a ZIP appended; zipfile reads it from inside the outer zip.
            with outer.open("agy_acp_server.par") as par_file, zipfile.ZipFile(par_file) as par:
                items = [i for i in par.infolist() if i.filename.startswith("google3/") and not i.filename.endswith("/")
                         and not (i.filename.endswith(".so") or ".so." in i.filename)
                         and not SKIP_DIR.search("/" + i.filename)]
                for n, i in enumerate(items, 1):
                    par.extract(i, src)
                    if n % 500 == 0: stage("extract", n * 100 // len(items))
        os.chmod(os.path.join(bin_dir, "localharness_external"), 0o755)
        os.remove(a.zip)  # Google's download is not kept
        vfile = os.path.join(src, ACP_MAIN, "_version.py")
        real = re.search(r"__version__\s*=\s*['\"]([\d.]+)['\"]", open(vfile).read()).group(1)

        stage("install", 0)
        pins = list(ALWAYS)
        for top, (pypi, pin) in REPLACE.items():
            v = bundled_version(src, top) if pin else None
            if pypi: pins.append(f"{pypi}=={v}" if v else pypi)
            shutil.rmtree(os.path.join(src, TP, top), ignore_errors=True)
        venv = os.path.join(work, "venv")
        subprocess.run([a.python, "-m", "venv", venv], check=True, stdout=sys.stderr)
        vpy = os.path.join(venv, "bin", "python")
        stage("install", 30)
        pip = [vpy, "-m", "pip", "install", "-q", "--no-cache-dir", "--disable-pip-version-check", "--only-binary=:all:"]
        if subprocess.run([*pip, *pins], stdout=sys.stderr).returncode:
            subprocess.run([*pip, *[p.split("==")[0] for p in pins]], check=True, stdout=sys.stderr)
        subprocess.run([vpy, "-m", "pip", "uninstall", "-y", "-q", "pip"], stdout=sys.stderr, stderr=sys.stderr)
        stage("install", 100)

        for f in ("orbit_agy.py", "agy_acp_server"):
            shutil.copy(os.path.join(TOOLS, f), os.path.join(bin_dir, f))
        os.chmod(os.path.join(bin_dir, "agy_acp_server"), 0o755)

        stage("smoke")
        req = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": 1, "clientInfo": {"name": "orbit", "title": "Orbit", "version": "0"},
            "clientCapabilities": {}}})
        home = os.path.join(work, ".smokehome")
        env = dict(os.environ, HOME=home, GEMINI_HOME=os.path.join(home, ".gemini"))
        p = subprocess.Popen([os.path.join(bin_dir, "agy_acp_server"), "--uid="], stdin=subprocess.PIPE,
                             stdout=subprocess.PIPE, stderr=sys.stderr, env=env)
        timer = threading.Timer(SMOKE_TIMEOUT, p.kill); timer.start()
        try:
            p.stdin.write((req + "\n").encode()); p.stdin.flush()
            line = p.stdout.readline()
        finally:
            timer.cancel(); p.kill(); p.wait()
        if not line: sys.exit(f"smoke test: no answer to ACP initialize within {SMOKE_TIMEOUT}s")
        got = json.loads(line)["result"]["agentInfo"]["version"]
        if got != real: sys.exit(f"smoke test: server reports {got}, expected {real}")
        shutil.rmtree(home, ignore_errors=True)
        shutil.rmtree(os.path.join(work, "pycache"), ignore_errors=True)
        open(os.path.join(work, ".ready"), "w").write(real + "\n")

        dest = os.path.join(a.root, real)
        if os.path.exists(dest): shutil.rmtree(dest)
        os.rename(work, dest)
        cur = os.path.join(a.root, "current"); tmp = cur + ".new"
        if os.path.lexists(tmp): os.remove(tmp)
        os.symlink(real, tmp); os.replace(tmp, cur)
        for old in os.listdir(a.root):   # only one runtime is kept
            if old not in (real, "current"): shutil.rmtree(os.path.join(a.root, old), ignore_errors=True)
        log("ready", dest); stage("done", 100)
    except BaseException:
        shutil.rmtree(work, ignore_errors=True); raise


if __name__ == "__main__":
    main()
