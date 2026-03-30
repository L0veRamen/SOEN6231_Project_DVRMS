# DVRMS Project 2 — Setup and Run Guide

## Prerequisites

- JDK 8 or higher (`java`, `javac`, `wsgen`, `wsimport` on PATH)
- Apache Maven 3.x
- `nc` (netcat) — used for fault injection commands
- `lsof` — used for port checks and crash simulation

## Preflight

Verify required tools:

```bash
for cmd in java mvn wsimport nc lsof; do
  command -v "$cmd" >/dev/null && echo "OK   $cmd" || echo "MISS $cmd"
done
```

Kill stale processes from previous runs:

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" || true
```

Verify ports are free (should return empty):

```bash
lsof -nP \
  -iTCP:8080 \
  -iUDP:9000 -iUDP:9100 \
  -iUDP:6001 -iUDP:6002 -iUDP:6003 -iUDP:6004 \
  -iUDP:7001 -iUDP:7002 -iUDP:7003 -iUDP:7004
```

## Build

```bash
mvn clean compile
```

Without Maven, compile manually then use `java -cp bin` instead of `java -cp target/classes`:

```bash
mkdir -p bin
javac -d bin src/main/java/model/*.java src/main/java/server/*.java
```

## Run Tests

```bash
mvn test
```

## Start the System

Open 7 terminals. Start each component in order — wait for the expected log before moving to the next.

### Terminal 1 — Sequencer

```bash
java -cp target/classes server.Sequencer
```

Expected: `Sequencer listening on port 9100`

### Terminal 2 — Replica Manager 1

```bash
java -cp target/classes server.ReplicaManager 1
```

Expected: `RM1: Replica launched on port 6001`

### Terminal 3 — Replica Manager 2

```bash
java -cp target/classes server.ReplicaManager 2
```

Expected: `RM2: Replica launched on port 6002`

### Terminal 4 — Replica Manager 3

```bash
java -cp target/classes server.ReplicaManager 3
```

Expected: `RM3: Replica launched on port 6003`

### Terminal 5 — Replica Manager 4

```bash
java -cp target/classes server.ReplicaManager 4
```

Expected: `RM4: Replica launched on port 6004`

### Terminal 6 — Front End

```bash
java -cp target/classes server.FrontEnd
```

Expected: `FrontEnd published at http://localhost:8080/fe`

SOAP endpoint: `http://localhost:8080/fe?wsdl`

### Terminal 7 — Client

Build client stubs against the FE WSDL:

```bash
./build-client.sh --wsdl http://localhost:8080/fe?wsdl
```

Run the manager client:

```bash
java -cp bin client.ManagerClient --wsdl http://localhost:8080/fe?wsdl
```

Run the customer client:

```bash
java -cp bin client.CustomerClient --wsdl http://localhost:8080/fe?wsdl
```

The client prompts for an ID at startup. The first 3 characters choose the office, the 4th character chooses the role (`M` = manager, `U` = customer):

| Role | MTL | WPG | BNF |
|---|---|---|---|
| Manager | MTLM1000 | WPGM1000 | BNFM1000 |
| Customer | MTLU1000 | WPGU1000 | BNFU1000 |

To run multiple clients at once, open additional terminals and repeat the `java -cp bin ...` command with a different ID each time.

Run menu option **3** (List Available Vehicles) once as a baseline sanity check.

### Alternative — One-Command Startup

Instead of opening 7 terminals manually, use the startup script:

```bash
./demo-start.sh
```

This launches all 6 server components in the background, builds the client, and prints ready status. Logs go to `logs/`. Stop everything with:

```bash
./demo-stop.sh
```

## Failure Scenarios (Quick Reference)

Full system must be running (Sequencer + 4 RMs + FE) before each scenario.

For detailed step-by-step demo scenarios with narration cues and expected logs, see [FAILURE_DEMO_SETUP.md](FAILURE_DEMO_SETUP.md).

### Byzantine — enable on Replica 3

```bash
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003
```

### Byzantine — disable on Replica 3

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003
```

### Crash — kill Replica 2

```bash
kill $(lsof -ti udp:6002 | head -n1)
```

### Simultaneous — crash Replica 2 + Byzantine Replica 3

```bash
kill $(lsof -ti udp:6002 | head -n1)
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003
```

### Reset between scenarios

Minimal reset:

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003 || true
```

Full reset (then restart from Terminal 1):

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" || true
```

### Test Scenario Summary

| Test | How to trigger |
|---|---|
| T1–T5 (Normal) | Send SOAP requests via client |
| T6–T10 (Byzantine) | `SET_BYZANTINE:true` on target replica, send requests |
| T11–T14 (Crash) | Kill replica process, send requests |
| T15–T17 (Simultaneous) | Kill one replica + Byzantine another, send requests |
| T18–T21 (Edge cases) | Concurrent clients, cross-office operations |

## Port Assignment

| Component | Port | Protocol |
|---|---|---|
| Sequencer | 9100 | UDP |
| FE (SOAP) | 8080 | TCP |
| FE (results) | 9000 | UDP |
| Replica 1 / RM 1 | 6001 / 7001 | UDP |
| Replica 2 / RM 2 | 6002 / 7002 | UDP |
| Replica 3 / RM 3 | 6003 / 7003 | UDP |
| Replica 4 / RM 4 | 6004 / 7004 | UDP |

Per-replica office ports (inter-office UDP):

| Replica | MTL | WPG | BNF |
|---|---|---|---|
| 1 | 5001 | 5002 | 5003 |
| 2 | 5011 | 5012 | 5013 |
| 3 | 5021 | 5022 | 5023 |
| 4 | 5031 | 5032 | 5033 |

## Troubleshooting

**Port already in use:**

```bash
lsof -i :6001
```

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager"
```

**WSDL not reachable:** Make sure FE (Terminal 6) is running first. `build-client.sh` auto-waits up to 30s.

**UDP bind errors in tests:** The `Address already in use` messages during `mvn test` are expected and harmless — test stubs pass regardless.

## Appendix — A3 Standalone Mode (dev/debug)

To test business logic without the replication layer:

```bash
./start-server.sh
```

In another terminal:

```bash
./build-client.sh --wsdl http://localhost:8081/mtl?wsdl
```

```bash
java -cp bin client.CustomerClient
```

This starts three standalone offices (MTL:8081, WPG:8082, BNF:8083) without Sequencer, RMs, or FE.
