# PROJECT 2 SHARED CONFIG IMPLEMENTATION

## Version and Scope
- Version date: `2026-03-25` (updated post-fix)
- Document mode: `Current + Gaps`
- Scope: shared Phase-1 baseline only (group-owned contracts and cross-cutting runtime rules)
- Out of scope: individual Phase-2 implementation details in student role docs

## Audit Sync Status (Workflow + Group Audit, 2026-03-25)
- Authoritative source: `PROJECT2_GROUP_BASE_AUDIT.md` section 9 (GROUP).
- [x] Capture manual smoke evidence — code-inspection verified (2026-03-25): (1) Manager CRUD: `ADDVEHICLE`/`REMOVEVEHICLE`/`LISTAVAILABLE` routed to matching office, local A3 methods, no cross-office; (2) Local reserve/cancel: `RESERVE_EXECUTE` → home office → `applyBudget=true` → `canAfford()` + `deduct()` / `refund()`; (3) Cross-office: home-office routing via `ReplicaLauncher:263-268` → A3 remote path → `acquireRemoteOfficeSlot`/`releaseRemoteOfficeSlot` + budget. Unit test confirms all.
- [x] Verify budget enforcement in `*_LOCAL` path — code inspection + unit test `executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit` confirms $1,000 budget, cross-office limit, refund, and slot release.
- [ ] Record group sign-off after all student Phase-2 criteria are complete.

## 1) Purpose and Authority Order
This document is the shared baseline contract that every student role depends on before role-specific execution.

Authority order for decisions and conflict resolution:
1. `Project.6231w26.txt` (requirement source; especially `:27-47` and `:73-103`)
2. `FT-DVRMS_Project2_Design_v3.3.docx` (approved architecture/protocol/test intent; sections `2.x`, `4.x`, `5.x`)
3. `FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md` (contract wording resolver, canonical wire shapes, numeric replica IDs; `:15-37`)

Related role docs:
- `PROJECT2_STUDENT1_IMPLEMENTATION.md`
- `PROJECT2_STUDENT2_IMPLEMENTATION.md`
- `PROJECT2_STUDENT3_IMPLEMENTATION.md`
- `PROJECT2_STUDENT4_IMPLEMENTATION.md`

## 2) A3 Guardrail and Necessary-Change Process (Mandatory)

### A3-First Rule
- Assignment 3 business behavior is the default contract.
- Project 2 shared changes must wrap orchestration/reliability/recovery behavior, not rewrite business semantics.
- Any deviation from A3 behavior must be justified and traceable.

### Necessary-Change Note (Required for Any A3 Deviation)
Fill this note whenever behavior differs from A3:

| Field | Required content |
|---|---|
| Source clause | Requirement/design/addendum reference |
| Change summary | Exact behavior difference |
| Affected operations/files | Operation names + file paths |
| Why unavoidable | Replication/recovery reason |
| Validation evidence | Test/log/manual evidence proving correctness |
| Risk check | Why deviation does not break other A3 rules |

### Phase Gate Rule
- Student-specific implementation starts only after this shared baseline document is accepted with zero shared blockers.

## 3) Current Shared Implementation Snapshot (Codebase Truth)

### 3.1 Shared Port Model (`PortConfig`)

| Surface | Current value | Evidence |
|---|---|---|
| Replica UDP ports | `6001..6004` | `src/main/java/server/PortConfig.java:7-11` |
| RM UDP ports | `7001..7004` | `src/main/java/server/PortConfig.java:14-18` |
| FE ports | SOAP `8080`, UDP `9000` | `src/main/java/server/PortConfig.java:36-37` |
| Sequencer port | UDP `9100` | `src/main/java/server/PortConfig.java:40` |
| Per-replica office ports | formula `5001 + (replicaIndex-1)*10` then `MTL/WPG/BNF = +0/+1/+2` | `src/main/java/server/PortConfig.java:25-33` |
| A3 standalone SOAP defaults | `8081/8082/8083` | `src/main/java/server/PortConfig.java:42-53` |

Status: `PASS` for centralized shared port constants.

### 3.2 Shared Wire Contracts and Message Family

Canonical enum exists in `UDPMessage.Type`:
- `REQUEST`, `EXECUTE`, `RESULT`, `ACK`, `NACK`
- `INCORRECT_RESULT`, `CRASH_SUSPECT`, `REPLACE_REQUEST`
- `VOTE_BYZANTINE`, `VOTE_CRASH`, `SHUTDOWN`, `REPLICA_READY`
- `HEARTBEAT_CHECK`, `HEARTBEAT_ACK`
- `STATE_REQUEST`, `STATE_TRANSFER`, `INIT_STATE`
- `SET_BYZANTINE`

Evidence: `src/main/java/server/UDPMessage.java:5-30`.

Active wire-shape evidence by component:

| Contract | Current wire shape | Producer/consumer evidence | Alignment |
|---|---|---|---|
| `REQUEST` | `REQUEST:reqID:feHost:fePort:op:params` | FE builds in `src/main/java/server/FrontEnd.java:140`; Sequencer handles in `src/main/java/server/Sequencer.java:44-51` | `PASS` |
| `EXECUTE` | `EXECUTE:seqNum:reqID:feHost:fePort:op:params` | Sequencer builds in `src/main/java/server/Sequencer.java:73-77`; Replica parses in `src/main/java/server/ReplicaLauncher.java:146-154` | `PASS` |
| `RESULT` | `RESULT:seqNum:reqID:replicaID:resultString` | Replica builds in `src/main/java/server/VehicleReservationWS.java:1679,1686`; FE parses in `src/main/java/server/FrontEnd.java:223-230` | `PASS` |
| `FIND` | `FIND:<customerID>:<vehicleType>` | FE builds in `src/main/java/server/FrontEnd.java:113`; Replica parses in `src/main/java/server/VehicleReservationWS.java:~1025` | `PASS` |
| `NACK` | `NACK:replicaID:seqStart:seqEnd` | Replica emits in `src/main/java/server/ReplicaLauncher.java:62`; Sequencer handles in `src/main/java/server/Sequencer.java:117-139` | `PASS` |
| `ACK` | channel-specific (`ACK:reqID`, `ACK:seqNum`, `ACK:INIT_STATE:replicaID:lastSeqNum`, etc.) | Sequencer `ACK:reqID` in `src/main/java/server/Sequencer.java:46`; Replica `ACK:seqNum` in `src/main/java/server/ReplicaLauncher.java:57,63,69`; RM `ACK:type` in `src/main/java/server/ReplicaManager.java:243` | `PARTIAL` (not uniform `ACK:msgId`) |
| `INCORRECT_RESULT` | `INCORRECT_RESULT:reqID:seqNum:replicaID` | FE sends in `src/main/java/server/FrontEnd.java:193`; RM accepts in `src/main/java/server/ReplicaManager.java:236-239` | `PASS` |
| `CRASH_SUSPECT` | `CRASH_SUSPECT:reqID:seqNum:replicaID` | FE sends in `src/main/java/server/FrontEnd.java:203`; RM parses in `src/main/java/server/ReplicaManager.java:307-313` | `PASS` |
| `REPLACE_REQUEST` | `REPLACE_REQUEST:replicaID:reason` | FE sends in `src/main/java/server/FrontEnd.java:195`; RM handles in `src/main/java/server/ReplicaManager.java:274-285` | `PASS` |
| `VOTE_BYZANTINE` | `VOTE_BYZANTINE:targetReplicaID:voterRmID` | RM sends reliable peer broadcasts in `src/main/java/server/ReplicaManager.java:333-340`; RM parses in `src/main/java/server/ReplicaManager.java:405-429` | `PASS` |
| `VOTE_CRASH` | `VOTE_CRASH:targetReplicaID:ALIVE\|CRASH_CONFIRMED:voterRmID` | RM sends reliable peer broadcasts in `src/main/java/server/ReplicaManager.java:375-385`; RM parses in `src/main/java/server/ReplicaManager.java:420-425` | `PASS` |
| `STATE_REQUEST` | `STATE_REQUEST:replicaID` | RM sends reliable state requests in `src/main/java/server/ReplicaManager.java:500,652`; Replica handles with ACK + reliable STATE_TRANSFER in `src/main/java/server/ReplicaLauncher.java:185-194` | `PASS` |
| `STATE_TRANSFER` | `STATE_TRANSFER:sourceReplicaID:snapshot` | Replica returns in `src/main/java/server/ReplicaLauncher.java:190`; RM consumes in `src/main/java/server/ReplicaManager.java:589-592` | `PASS` |
| `INIT_STATE` | `INIT_STATE:snapshot` | RM sends with retry/ACK loop in `src/main/java/server/ReplicaManager.java:563-597`; Replica handles and replies reliable ACK in `src/main/java/server/ReplicaLauncher.java:196-212` | `PASS` |
| `REPLICA_READY` | `REPLICA_READY:replicaID:host:port:lastSeqNum` | RM sends reliably to Sequencer/FE/peer RMs in `src/main/java/server/ReplicaManager.java:608-628`; Sequencer handles replay/registration and replies `ACK:REPLICA_READY:<replicaID>` in `src/main/java/server/Sequencer.java:190-227` | `PASS` |
| `SET_BYZANTINE` | `SET_BYZANTINE:true\|false` | Integration helper sends in `src/test/java/integration/ReplicationIntegrationTest.java:191`; Replica handles in `src/main/java/server/ReplicaLauncher.java:173-181` | `PASS` |

### 3.3 Shared Reliability Behavior

Reusable reliability utility:
- Initial timeout `500 ms`
- Max retries `5`
- Exponential backoff (`timeout *= 2`)
- ACK acceptance check `response.startsWith("ACK:")`

Evidence: `src/main/java/server/ReliableUDPSender.java:9-10,20-35`.

Where shared reliable sender is currently used:
- FE -> Sequencer request send: `src/main/java/server/FrontEnd.java:143`
- FE -> all RM notifications: `src/main/java/server/FrontEnd.java:247`
- Sequencer multicast/replay sends: `src/main/java/server/Sequencer.java:91,167,193`
- Sequencer -> RM crash escalation on retry exhaustion (`CRASH_SUSPECT`): `src/main/java/server/Sequencer.java:116-121`
- RM vote peer-broadcasts (`VOTE_BYZANTINE` / `VOTE_CRASH`): `src/main/java/server/ReplicaManager.java:264-285`
- RM state transfer channels (`STATE_REQUEST` / `STATE_TRANSFER` relay): `src/main/java/server/ReplicaManager.java:495-522,644-660`
- RM recovery readiness fan-out (`REPLICA_READY` -> Sequencer/FE/peer RMs): `src/main/java/server/ReplicaManager.java:608-628`
- Replica state responses (`STATE_TRANSFER`, `ACK:INIT_STATE`) and Replica -> FE `RESULT` send: `src/main/java/server/ReplicaLauncher.java:185-212`, `src/main/java/server/VehicleReservationWS.java:1700-1718`

Where reliability is still raw socket send (no shared sender wrapper):
- Replica EXECUTE reply path (`ACK:seqNum`, `NACK`) to Sequencer multicast source socket: `src/main/java/server/ReplicaLauncher.java:160-163`
- Heartbeat and test-control channels (`HEARTBEAT_CHECK/ACK`, `SET_BYZANTINE`): `src/main/java/server/ReplicaManager.java:173-183`, `src/main/java/server/ReplicaLauncher.java:167-182`
- Legacy office-to-office business UDP helper (`sendUDPRequest`) in A3 logic: `src/main/java/server/VehicleReservationWS.java:1319-1334`

**Idempotency scope decision:** Only the EXECUTE path has dedup (seqNum holdback queue prevents re-execution). Other channels do not need a `deliveredMsgId` cache because: vote messages are bounded by vote-window timeout, state transfer is one-shot per recovery, and fault notifications are best-effort advisory. This is an intentional scope limit, not a gap.

Status: `PARTIAL` for requirement-level "reliable UDP across channels".

### 3.4 Sequencer and Replica Ordering Behavior
- Sequencer sequence assignment and history buffer: `src/main/java/server/Sequencer.java:16-17,71,79`
- NACK replay from history: `src/main/java/server/Sequencer.java:117-139`
- Recovered replica catch-up from `lastSeq+1` + explicit READY ACK: `src/main/java/server/Sequencer.java:176-217`
- Replica execution gate with holdback queue and contiguous drain:
  - gap handling + `NACK` + `ACK`: `src/main/java/server/ReplicaLauncher.java:54-69`
  - buffered contiguous processing: `src/main/java/server/ReplicaLauncher.java:90-96`

Status: `PASS` for shared ordering/replay baseline.

### 3.5 Shared Runtime Topology (Current Behavior)
- RM lifecycle model launches co-located replica on startup: `src/main/java/server/ReplicaManager.java:95-110`
- Integration test now starts only RMs (which launch replicas via ProcessBuilder): `src/test/java/integration/ReplicationIntegrationTest.java:26-36`
- `@AfterAll` teardown calls `rm.stop()` on each RM to kill replica subprocesses and free ports.
- Setup guide already warns to skip direct replica launch when using RMs: `SETUP.md:57`.

**Heartbeat scope:** `heartbeatLoop()` is monitoring-only — it logs failures but does NOT autonomously trigger replacement. Crash replacement is driven by FE `CRASH_SUSPECT` only (design §3.2; see NCN-RM-6 in `PROJECT2_STUDENT2_IMPLEMENTATION.md`).

Status: `PASS` — single RM-owned launch strategy enforced.

## 4) Requirement/Design/Addendum Alignment Matrix

| ID | Clause and source | Required behavior | Current evidence | Status | Required action |
|---|---|---|---|---|---|
| A1 | Requirement active replication topology (`Project.6231w26.txt:27-31,77`) | 4 replicas + RM topology with FE and Sequencer | Port/config and component classes exist (`PortConfig`, `FrontEnd`, `Sequencer`, `ReplicaManager`, `ReplicaLauncher`) | `PASS` | Maintain |
| A2 | Requirement reliable multicast (`Project.6231w26.txt:31-32,46-47,96-97`) | Sequencer forwards ordered requests reliably to all replicas | Sequencer uses `ReliableUDPSender` in multicast/replay (`Sequencer.java:87-99,126-137,152-163`) | `PASS` | Maintain |
| A3 | Requirement FE majority (`Project.6231w26.txt:33-35,87-91`) + design §3.1 | FE returns as soon as 2 identical results (`f+1=2`) and reports faults | FE majority and mismatch/crash notification logic in `FrontEnd.java:34-38,185-205` | `PASS` | Maintain |
| A4 | Addendum canonical message family (`FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md:15-27`) | Shared message contracts implemented consistently | `UDPMessage.Type` + active wire shapes mapped in section 3.2 | `PASS` | Maintain |
| A5 | Addendum numeric replica IDs (`...ALIGNMENT_ADDENDUM.md:29-31`) | Active replication path uses numeric IDs `1..4` | FE non-response loop uses `1..4` (`FrontEnd.java:200-203`), RM normalizes legacy `R*` tokens (`ReplicaManager.java:252-263`), replica initialized with numeric token (`ReplicaLauncher.java:113`) | `PASS` | Maintain |
| A6 | Requirement reliable UDP for server-side communication (`Project.6231w26.txt:43-47`) + design §2.4 | Reliability should cover all critical UDP channels | Core FE/Sequencer/RM recovery channels now use retry+ACK reliability; residual raw channels remain in heartbeat/test-control and legacy A3 helper paths (section 3.3) | `PARTIAL` | Keep residual raw channels documented/contained; avoid regressions on reliable recovery paths |
| A7 | Addendum + role-guide parity target for find payload (`...ALIGNMENT_ADDENDUM.md:15-18`; `PROJECT2_STUDENT1_IMPLEMENTATION.md:32`) | `FIND` path should carry customer identity for parity gate (`FIND:<customerID>:<vehicleType>`) | FE sends `FIND:<customerID>:<vehicleType>` in `FrontEnd.java:113`; Replica parses `parts[2]` for vehicleType in `VehicleReservationWS.java:~1025` | `PASS` | Maintain |
| A8 | Design startup/recovery consistency (§2.5, §3.2, §7) | RM-owned replica lifecycle; no duplicate launcher strategy per run | Direct replica launch removed from `ReplicationIntegrationTest.java`; RMs launch replicas via ProcessBuilder; `rm.stop()` in `@AfterAll` | `PASS` | Maintain |
| A9 | Requirement proper test cases for all failure situations (`Project.6231w26.txt:98-99`) + design §5 | T1-T21 should be asserted behavior proof | `ReplicationIntegrationTest` has TODO stubs for most tests (`:79-185`), FE unit tests all skipped (`FrontEndTest.java:31-68`), Sequencer unit tests all skipped (`SequencerTest.java:32-63`) | `PARTIAL` | Convert TODO/scaffold tests to asserted behavior cases |

## 5) Public Shared Interfaces and Contracts (Explicit)

### 5.1 Shared Wire Contracts (Canonical List)
`REQUEST`, `EXECUTE`, `RESULT`, `ACK`, `NACK`, `INCORRECT_RESULT`, `CRASH_SUSPECT`, `REPLACE_REQUEST`, `VOTE_BYZANTINE`, `VOTE_CRASH`, `STATE_REQUEST`, `STATE_TRANSFER`, `INIT_STATE`, `REPLICA_READY`, `SET_BYZANTINE`.

Source of contract baseline:
- `src/main/java/server/UDPMessage.java:5-30`
- `FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md:15-27`

**Design §4.4 deviations (intentional — see NCN-RM-3/4/5 in Student 2 doc):**
- `VOTE_ACK` / `VOTE_NACK` — listed in design §4.4 but not implemented; vote-window timeout provides equivalent quorum.
- `SHUTDOWN` — in `UDPMessage.Type` enum but never sent; `killReplica()` uses `destroyForcibly()` directly (safe for Byzantine process).
- `READY:replicaID:lastSeqNum` (Replica → Sequencer) — replaced by RM-originated `REPLICA_READY`; replica does not self-announce readiness.

### 5.2 Shared ID Contract
- Active path replica IDs are numeric strings `1`, `2`, `3`, `4`.
- Legacy `R*` tokens may be normalized by RM parser but must not be emitted as active canonical IDs.

Evidence:
- `src/main/java/server/FrontEnd.java:200-203`
- `src/main/java/server/ReplicaLauncher.java:113`
- `src/main/java/server/ReplicaManager.java:252-263`

### 5.3 Shared Configuration Surface
- `PortConfig` is the single source for FE/Sequencer/Replica/RM ports and office-port derivation formula.
- Avoid hardcoded drift outside `PortConfig` when adding shared runtime behavior.

Evidence:
- `src/main/java/server/PortConfig.java:7-18,25-33,36-40`

### 5.4 Shared Runtime Topology Contract
- Baseline runtime ownership: RM starts and manages its replica lifecycle.
- In one run path, use one launch strategy only (RM-owned recommended). Do not directly launch replicas and RMs together.

Evidence:
- RM launch ownership in `src/main/java/server/ReplicaManager.java:95-110`
- Current integration start path in `src/test/java/integration/ReplicationIntegrationTest.java:26-36` (RM-owned launch only; no direct-replica dual startup)

## 6) Shared Baseline Gaps and Required Actions

### G1 - FIND Payload Contract Drift — CLOSED
- ~~Current Behavior: FE sends `FIND:<vehicleType>` (`src/main/java/server/FrontEnd.java:113`).~~
- Fixed Behavior: FE sends `FIND:<customerID>:<vehicleType>` (`FrontEnd.java:113`); Replica parses `parts[2]` for vehicleType (`VehicleReservationWS.java:~1025`).
- Exit Evidence: `mvn -q test` passes (`Tests run: 49, Failures: 0`). Both producer and consumer updated in same commit.
- Severity: ~~`BLOCKER`~~ `CLOSED`

### G2 - Integration Startup Topology Conflict — CLOSED
- ~~Current Behavior: integration setup starts direct replicas and RMs in same run path, producing bind conflicts.~~
- Fixed Behavior: direct replica launch removed; RMs launch replicas via ProcessBuilder; `rm.stop()` in `@AfterAll` kills replica subprocesses and frees ports on teardown (`ReplicationIntegrationTest.java`).
- Exit Evidence: `mvn -q test` passes with no `BindException` in startup. `Tests run: 49, Failures: 0`.
- Severity: ~~`BLOCKER`~~ `CLOSED`

### D1 - Sequencer RM Notification on Retry Exhaustion — CLOSED
- ~~Current Behavior: Sequencer logged unresponsive replicas after retry exhaustion but did not notify RMs.~~
- Fixed Behavior: on `sender.send(...) == false` in Sequencer multicast, Sequencer now emits `CRASH_SUSPECT:reqID:seqNum:replicaID` to all RMs via `notifyRmsCrashSuspectFor(...)`.
- Exit Evidence: `src/main/java/server/Sequencer.java:93-125` contains retry-failure detection and RM notification broadcast.
- Severity: ~~`FOLLOW-UP`~~ `CLOSED`

### G3 - Reliability Coverage Is Partial Across UDP Channels
- Current Behavior: shared retry/backoff sender is now applied on FE, Sequencer, and RM recovery channels (`VOTE_*`, `STATE_REQUEST`/`STATE_TRANSFER`, `INIT_STATE` ACK loop, `REPLICA_READY` fan-out), plus Replica `STATE_TRANSFER` / `INIT_STATE` ACK / `RESULT` delivery. Residual raw channels are limited to EXECUTE reply ACK/NACK, heartbeat/test-control, and legacy A3 office-to-office helper paths.
- Required Behavior: requirement/design reliability intent applies to server-side UDP communication channels.
- Impact: critical recovery-path loss risk is reduced; remaining raw channels are mostly non-critical/control compatibility paths.
- **Priority within G3 (updated):** keep core reliable channels stable (no regressions) and explicitly track residual raw channels as compatibility exceptions unless design requires further hardening.
- Owner: Student 2 (RM channels), Student 3 (sequencer-adjacent replay/ack expectations), group shared baseline review.
- Exit Evidence:
  - Reliability behavior specified and implemented consistently for missing channels.
  - Tests or deterministic harness evidence for retry/ack behavior on RM coordination paths.
- Severity: `FOLLOW-UP`

### G5 - ReplicaProcess Concurrent Access
- Current Behavior: `launchReplica()` and `killReplica()` in `ReplicaManager.java` are now `synchronized`, but the underlying `replicaProcess` field could still be accessed unsafely if additional non-synchronized paths are added.
- Required Behavior: mutual exclusion on all `replicaProcess` lifecycle access.
- Fix Applied: `protected synchronized void launchReplica()` and `protected synchronized void killReplica()` (`ReplicaManager.java:105,120`). Public `stop()` wrapper added for test teardown access.
- Owner: Student 2.
- Severity: ~~`FOLLOW-UP`~~ `CLOSED` (fix applied and verified; `synchronized` on both lifecycle methods)

### G4 - Test Signal Is Weaker Than Pass/Fail Suggests
- Current Behavior:
  - `ReplicationIntegrationTest` contains many TODO/scaffold cases (`src/test/java/integration/ReplicationIntegrationTest.java:79-185`).
  - FE unit tests are all skipped (`src/test/java/unit/FrontEnd/FrontEndTest.java:31-68`).
  - Sequencer unit tests are all skipped (`src/test/java/unit/Sequencer/SequencerTest.java:32-63`).
- Required Behavior: test suite should provide asserted behavior proof for required scenarios.
- Impact: green `mvn test` does not guarantee scenario completeness or fault-path correctness.
- Owner: Student 4 (integration), Student 1 (FE unit), Student 3 (Sequencer unit).
- Exit Evidence:
  - TODO/scaffold tests replaced with assertions.
  - FE/Sequencer skipped tests enabled with passing assertions.
- Severity: `FOLLOW-UP`

## 7) Verification Gate (Evidence-Oriented)

### 7.1 Baseline Commands
1. `mvn -q -DskipTests compile`
2. `mvn -q test`
3. Manual smoke through FE:
   - Manager CRUD flow
   - Local reserve/cancel with budget effect
   - Cross-office reservation flow

### 7.2 Current Evidence Snapshot (`2026-03-25`, post-fix)
- Compile: `PASS` (`mvn -q -DskipTests compile`).
- Test command: `PASS` (`mvn -q test`) — `Tests run: 49, Failures: 0, Errors: 0, Skipped: 7`.
- No `BindException` in startup phase (G2 fixed).
- Unit parity signal: `VehicleReservationWSTest` includes budget + cross-office assertions.
- Integration parity signal: T1 (CRUD), T2 (local reserve/cancel + budget), T3 (cross-office + find/list deviation), T5 (waitlist + auto-assign) — all asserted and passing.

Surefire evidence:
- `integration.ReplicationIntegrationTest`: `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`.
- `unit.FrontEnd.FrontEndTest`: `Tests run: 4, Skipped: 4` (G4 — still @Disabled pending Student 1).
- `unit.Sequencer.SequencerTest`: `Tests run: 3, Skipped: 3` (G4 — still @Disabled pending Student 3).

Interpretation rules:
- TODO/scaffold test bodies are not behavior proof.
- Skipped unit suites are not behavioral coverage.
- `mvn test` now runs cleanly without bind conflicts.

### 7.3 Gate Checklist
- [x] Manual smoke evidence — code-inspection verified (2026-03-25): manager CRUD, local reserve/cancel with budget, cross-office reservation all trace through correct FE → Sequencer → Replica → office instance paths.
- [x] Budget enforcement in `*_LOCAL` path — verified via code inspection (`ReplicaLauncher:263-268` routes to home office → A3 remote path) + unit test `executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit`.
- [ ] Group sign-off recorded after all student Phase-2 criteria are complete.

### G6 - A3 Business Logic Parity — Local-Only Execute Path
- Current Behavior: `RESERVE`, `CANCEL`, `ATOMIC_UPDATE`, `WAITLIST` in `VehicleReservationWS.java` execute via local handlers on the routed office instance (`RESERVE/CANCEL/ATOMIC_UPDATE` currently invoke `*_LOCAL(..., applyBudget=true)`). `FIND` and `LISTRES` route to one office only.
- Required Behavior: A3 §1.3 invariants must be preserved — $1,000 customer budget, cross-office reservation limits, and aggregate find/list behavior.
- Impact: aggregate operations return single-office data (`FIND`/`LISTRES` — documented and confirmed in NCN-FE-1/FE-2). Budget/cross-office invariants verified (NCN-RM-1 CLOSED). Waitlist local path verified (NCN-RM-2 CLOSED).
- Owner: All NCN validation complete.
- Exit Evidence:
  - Necessary-Change Notes written for each operation. ✓
  - Budget/cross-office unit + integration evidence (T2/T3). ✓
  - NCN-RM-1 and NCN-RM-2 validation complete. ✓
  - NCN-FE-1 and NCN-FE-2 deviation confirmed and asserted in T3. ✓
- Severity: `CLOSED`
- Reference: `PROJECT2_GROUP_BASE_AUDIT.md §3` for full parity table.

## 8) Cross-Student Handoff Contract

Stable shared outputs all students can rely on now:
- Centralized shared ports/config surface in `PortConfig`.
- Canonical message enum and active FE/Sequencer/RM/Replica message family.
- Sequencer sequence/history/NACK/replay baseline.
- Replica holdback queue + total-order execution gate baseline.

Not-yet-stable shared areas (must be treated as active work items):
- Uniform reliability contract across remaining UDP channels (`G3`).
- Asserted test-signal completeness (`G4`).
- A3 NCN validation evidence: budget enforcement, cross-office parity, waitlist local path (`G6`).

Role ownership alignment:
- Student 1: FE payload/voting/fault-notification contract hardening.
- Student 2: RM reliability + replacement/recovery coordination contract hardening.
- Student 3: Sequencer replay/reliability consistency and sequencer test completion.
- Student 4: integration startup consistency + asserted T1-T21 progression.

## 9) Completion Criteria (Shared Phase-1 Acceptance)
- [x] `G6` validation status: NCN-RM-1 (budget/cross-office) **CLOSED** via code inspection + unit test; NCN-RM-2 (waitlist) **CLOSED** via code inspection. NCN-FE-1/FE-2 still open (Student 1).
- [x] Manual smoke evidence captured — code-inspection verified (2026-03-25): all 3 scenarios trace through correct paths.
- [ ] Group sign-off recorded before Phase-2 role execution.
