#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

PID_FILE="${SCRIPT_DIR}/logs/demo.pids"

echo "=== Stopping DVRMS P2 system ==="

if [[ -f "${PID_FILE}" ]]; then
  while read -r pid; do
    if kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null && echo "Killed PID ${pid}" || true
    fi
  done < "${PID_FILE}"
  rm -f "${PID_FILE}"
  echo "PID file cleaned."
else
  echo "No PID file found at ${PID_FILE}."
fi

pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" 2>/dev/null || true

echo "All DVRMS processes stopped."
