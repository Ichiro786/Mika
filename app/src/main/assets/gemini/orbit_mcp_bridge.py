#!/usr/bin/env python3
"""stdio <-> Orbit's phone-tools MCP socket. Antigravity starts this as a stdio MCP server; every line
goes to Orbit unchanged, and Orbit applies its own approvals and guards to each tool call."""
import os, socket, sys, threading

path = os.environ.get("ORBIT_MCP_SOCKET", "/run/orbit/mcp.sock")
s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
s.connect(path)

def pump_out():
    f = s.makefile("rb")
    out = sys.stdout.buffer
    for line in f:
        out.write(line); out.flush()
    os._exit(0)

threading.Thread(target=pump_out, daemon=True).start()
for line in sys.stdin.buffer:
    s.sendall(line)
s.shutdown(socket.SHUT_WR)
threading.Event().wait(5)
