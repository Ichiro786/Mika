#!/usr/bin/env python3
"""Orbit: start Google's Antigravity ACP server frontend on a stock CPython.

Google's agy_acp_server.par links TCMalloc for a 48-bit address space and aborts on 39-bit Android
kernels. Its Python frontend, extracted on the phone by orbit_prepare.py from Google's own download,
runs fine on python-build-standalone CPython. Nothing from Google ships with Orbit.
Approach after openbot-local's engine/agy-hybrid.
"""
import importlib, os, runpy, sys, types

here = os.path.dirname(os.path.abspath(__file__))
src = os.environ.get("ORBIT_AGY_SRC") or os.path.join(os.path.dirname(here), "src")
tp = os.path.join(src, "google3", "third_party", "py")

# The registry starts the Linux build with an empty --uid=; init_google is not used here.
sys.argv = [a for a in sys.argv if a != "--uid="]
# main.py looks for localharness_external next to argv[0].
sys.argv[0] = os.path.join(here, "agy_acp_server")

sys.path.insert(0, src)
sys.path.append(tp)

# `google` is a regular package in the PAR and a namespace in the venv: merge both.
venv_google = [os.path.join(p, "google") for p in sys.path
               if p not in (src, tp) and os.path.isdir(os.path.join(p, "google"))]
g = types.ModuleType("google")
g.__path__ = venv_google + [os.path.join(tp, "google")]
g.__package__ = "google"
sys.modules["google"] = g

# The official entrypoint turns this check off before loading generated code too.
import google.protobuf.runtime_version as _rv
_rv.ValidateProtobufRuntimeVersion = lambda *a, **k: None

MAIN = "google3.cloud.developer_experience.antigravity_extensions.acp_server.main"
if os.environ.get("ORBIT_AGY_IMPORT_ONLY") == "1":
    importlib.import_module(MAIN)
    print("IMPORT_OK", file=sys.stderr)
    sys.exit(0)
runpy.run_module(MAIN, run_name="__main__", alter_sys=True)
