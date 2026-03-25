#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: Required command not found: ${cmd}" >&2
    exit 1
  fi
}

check_port_available() {
  local protocol="$1"
  local port="$2"

  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi

  if [[ "${protocol}" == "tcp" ]]; then
    if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "ERROR: TCP port ${port} is already in use." >&2
      echo "Stop the process using port ${port} and retry." >&2
      exit 1
    fi
  else
    if lsof -nP -iUDP:"${port}" >/dev/null 2>&1; then
      echo "ERROR: UDP port ${port} is already in use." >&2
      echo "Stop the process using port ${port} and retry." >&2
      exit 1
    fi
  fi
}

require_cmd javac
require_cmd java
require_cmd wsgen

# Guard against the most common startup failure: busy ports.
check_port_available tcp 8081
check_port_available tcp 8082
check_port_available tcp 8083
check_port_available udp 5001
check_port_available udp 5002
check_port_available udp 5003

# Compile server + model, generate WSDL via wsgen, then publish all 3 endpoints.
echo "=== Compiling server ==="
rm -rf bin generated
mkdir -p bin generated
javac -d bin src/main/java/model/*.java src/main/java/server/*.java

echo "=== Generating WSDL artifacts (wsgen) ==="
if wsgen -Xnocompile -cp bin -wsdl -d generated -r generated server.VehicleReservationWS; then
  echo "wsgen completed."
else
  echo "wsgen failed in this environment; continuing with live Endpoint.publish WSDL."
fi

echo "=== Starting all servers ==="
java -cp bin server.ServerPublisher
