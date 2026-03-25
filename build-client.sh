#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

DEFAULT_WSDL_URL="http://localhost:8081/mtl?wsdl"
WSDL_URL="${WSDL_URL:-${DEFAULT_WSDL_URL}}"
WAIT_SECONDS="${WAIT_SECONDS:-30}"

usage() {
  cat <<EOF
Usage: ./build-client.sh [--wsdl URL] [--wait-seconds N]

Options:
  --wsdl URL          WSDL endpoint to import (default: ${DEFAULT_WSDL_URL})
  --wait-seconds N    Seconds to wait for WSDL availability (default: 30)
EOF
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: Required command not found: ${cmd}" >&2
    exit 1
  fi
}

wsdl_reachable() {
  curl -fsS "${WSDL_URL}" >/dev/null 2>&1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --wsdl)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: --wsdl requires a URL value." >&2
        usage
        exit 1
      fi
      WSDL_URL="$2"
      shift 2
      ;;
    --wait-seconds)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: --wait-seconds requires a numeric value." >&2
        usage
        exit 1
      fi
      WAIT_SECONDS="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if ! [[ "${WAIT_SECONDS}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: --wait-seconds must be a non-negative integer, got: ${WAIT_SECONDS}" >&2
  exit 1
fi

require_cmd wsimport
require_cmd javac

if command -v curl >/dev/null 2>&1; then
  echo "=== Checking WSDL endpoint ==="
  if ! wsdl_reachable; then
    echo "WSDL is not reachable at: ${WSDL_URL}" >&2
    echo "Start the SOAP servers in another terminal and keep it running:" >&2
    echo "  cd ${SCRIPT_DIR}" >&2
    echo "  ./start-server.sh" >&2
    echo "Waiting up to ${WAIT_SECONDS}s for the endpoint to come online..." >&2

    for ((second = 1; second <= WAIT_SECONDS; second++)); do
      if wsdl_reachable; then
        break
      fi
      sleep 1
    done
  fi

  if ! wsdl_reachable; then
    echo "ERROR: Timed out waiting for WSDL: ${WSDL_URL}" >&2
    echo "Troubleshooting:" >&2
    echo "1) Ensure ./start-server.sh is running without errors." >&2
    echo "2) Verify the endpoint manually: curl -sS ${WSDL_URL} | head" >&2
    echo "3) Check port conflicts on 8081/8082/8083 and UDP 5001/5002/5003." >&2
    exit 1
  fi
else
  echo "WARNING: curl is not installed. Skipping WSDL readiness check." >&2
  echo "If wsimport fails with 'Connection refused', start ./start-server.sh first." >&2
fi

# Generate client stubs from live WSDL, then compile client code.
echo "=== Generating client stubs from WSDL (wsimport) ==="
rm -rf src/generated/client
mkdir -p src/generated bin
wsimport -keep -s src/generated -d bin -p client.generated \
    "${WSDL_URL}"

echo "=== Compiling client code ==="
javac -cp bin -d bin src/main/java/client/*.java

echo "=== Done! Run clients with: ==="
echo "  java -cp bin client.CustomerClient"
echo "  java -cp bin client.ManagerClient"
