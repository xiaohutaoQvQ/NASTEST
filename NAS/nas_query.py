#!/usr/bin/env python3
"""Read-only SSH query helper for the NAS."""
import sys
import paramiko

HOST, PORT, USER, PASS = "123.56.65.45", 18441, "root", "13161312286z"

def run(cmd: str, timeout: int = 120) -> str:
    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(HOST, port=PORT, username=USER, password=PASS, timeout=20)
    try:
        stdin, stdout, stderr = cli.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        rc = stdout.channel.recv_exit_status()
        result = out
        if err.strip():
            result += ("\n[stderr]\n" + err)
        result += f"\n[exit={rc}]"
        return result
    finally:
        cli.close()

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "uname -a"
    timeout = int(sys.argv[2]) if len(sys.argv) > 2 else 120
    print(run(cmd, timeout))
