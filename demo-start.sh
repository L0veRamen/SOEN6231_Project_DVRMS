#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

LOG_DIR="${SCRIPT_DIR}/logs"
PID_FILE="${LOG_DIR}/demo.pids"
STARTUP_TIMEOUT=20

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "MISS  ${cmd}" >&2
    return 1
  fi
  echo "OK    ${cmd}"
}

check_port() {
  local protocol="$1"
  local port="$2"
  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi
  if [[ "${protocol}" == "tcp" ]]; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1 && return 1
  else
    lsof -nP -iUDP:"${port}" >/dev/null 2>&1 && return 1
  fi
  return 0
}

wait_for_log() {
  local logfile="$1"
  local pattern="$2"
  local label="$3"
  local timeout="${4:-${STARTUP_TIMEOUT}}"
  for ((i = 1; i <= timeout; i++)); do
    if grep -q "${pattern}" "${logfile}" 2>/dev/null; then
      echo "  OK   ${label}"
      return 0
    fi
    sleep 1
  done
  echo "  FAIL ${label} (no '${pattern}' after ${timeout}s)" >&2
  return 1
}

cleanup_on_failure() {
  echo ""
  echo "ERROR: Startup failed. Killing launched processes..." >&2
  if [[ -f "${PID_FILE}" ]]; then
    while read -r pid; do
      kill "${pid}" 2>/dev/null || true
    done < "${PID_FILE}"
    rm -f "${PID_FILE}"
  fi
  pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" 2>/dev/null || true
  exit 1
}

# ── Preflight ────────────────────────────────────────────────

echo "=== Preflight ==="

missing=0
for cmd in java mvn wsimport nc lsof; do
  require_cmd "${cmd}" || missing=1
done
if [[ "${missing}" -ne 0 ]]; then
  echo "ERROR: Install missing tools before continuing." >&2
  exit 1
fi

echo ""
echo "=== Killing stale processes ==="
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" 2>/dev/null || true
sleep 1

echo ""
echo "=== Checking ports ==="
ports_busy=0
for port in 8080; do
  check_port tcp "${port}" || { echo "ERROR: TCP port ${port} in use." >&2; ports_busy=1; }
done
for port in 9000 9100 6001 6002 6003 6004 7001 7002 7003 7004; do
  check_port udp "${port}" || { echo "ERROR: UDP port ${port} in use." >&2; ports_busy=1; }
done
if [[ "${ports_busy}" -ne 0 ]]; then
  echo "ERROR: Free the ports above and retry." >&2
  exit 1
fi
echo "All ports free."

# ── Build ────────────────────────────────────────────────────

echo ""
echo "=== Building (mvn clean compile) ==="
mvn -q clean compile
echo "Build complete."

# ── Launch components ────────────────────────────────────────

mkdir -p "${LOG_DIR}"
rm -f "${PID_FILE}"

echo ""
echo "=== Starting components ==="

java -cp target/classes server.Sequencer > "${LOG_DIR}/sequencer.log" 2>&1 &
echo $! >> "${PID_FILE}"

sleep 1

for id in 1 2 3 4; do
  java -cp target/classes server.ReplicaManager "${id}" > "${LOG_DIR}/rm${id}.log" 2>&1 &
  echo $! >> "${PID_FILE}"
  sleep 1
done

java -cp target/classes server.FrontEnd > "${LOG_DIR}/fe.log" 2>&1 &
echo $! >> "${PID_FILE}"

# ── Wait for readiness ───────────────────────────────────────

echo ""
echo "=== Waiting for startup ==="

wait_for_log "${LOG_DIR}/sequencer.log" "Sequencer listening" "Sequencer"      || cleanup_on_failure
wait_for_log "${LOG_DIR}/rm1.log"       "Replica launched"    "RM 1 + Replica" || cleanup_on_failure
wait_for_log "${LOG_DIR}/rm2.log"       "Replica launched"    "RM 2 + Replica" || cleanup_on_failure
wait_for_log "${LOG_DIR}/rm3.log"       "Replica launched"    "RM 3 + Replica" || cleanup_on_failure
wait_for_log "${LOG_DIR}/rm4.log"       "Replica launched"    "RM 4 + Replica" || cleanup_on_failure
wait_for_log "${LOG_DIR}/fe.log"        "FrontEnd published"  "Front End"      || cleanup_on_failure

# ── Build client stubs ───────────────────────────────────────

echo ""
echo "=== Building client stubs ==="
./build-client.sh --wsdl http://localhost:8080/fe?wsdl

# ── Summary ──────────────────────────────────────────────────

echo ""
echo "========================================"
echo "  DVRMS P2 system is ready"
echo "========================================"
echo ""
echo "PIDs: $(paste -sd ' ' "${PID_FILE}")"
echo "Logs: ${LOG_DIR}/"
echo "  tail -f ${LOG_DIR}/sequencer.log"
echo "  tail -f ${LOG_DIR}/rm1.log  (through rm4.log)"
echo "  tail -f ${LOG_DIR}/fe.log"
echo ""
echo "Run clients (open new terminals):"
echo ""
echo "  Manager:"
echo "    java -cp bin client.ManagerClient --wsdl http://localhost:8080/fe?wsdl"
echo "    IDs: MTLM1000, WPGM1000, BNFM1000"
echo ""
echo "  Customer:"
echo "    java -cp bin client.CustomerClient --wsdl http://localhost:8080/fe?wsdl"
echo "    IDs: MTLU1000, WPGU1000, BNFU1000"
echo ""
echo "Stop everything:"
echo "  ./demo-stop.sh"
echo ""
