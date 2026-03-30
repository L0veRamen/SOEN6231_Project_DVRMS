# PROJECT 2 STUDENT2 IMPLEMENTATION (RM)

## Role and Purpose
Owner: Student 2

This guide defines Student 2 work for Replica Manager (RM) across:
- Phase 1 baseline responsibilities
- Phase 2 role extension responsibilities

## Alignment Sources
- `Project.6231w26.txt` (RM failure detection/recovery responsibilities)
- `FT-DVRMS_Project2_Design_v3.3.docx` (vote, replacement, state transfer, readiness flow)
- `FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md` (canonical recovery message contracts)
- `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md`

## A3 Guardrail (Mandatory)
- RM behavior must preserve A3 business outcomes by restoring/maintaining replica state consistency.
- RM must not introduce business-rule rewrites.
- Any required deviation must include a Necessary-Change Note.

## Entry Condition
Shared baseline gate in `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md` is complete (G1 CLOSED, G2 CLOSED — 2026-03-25).

## Audit Sync Status (Workflow + Group Audit, 2026-03-25)
- Authoritative source: `PROJECT2_GROUP_BASE_AUDIT.md` section 9 (STUDENT 2).
- [x] `NCN-RM-1` validation evidence: `$1,000` budget + cross-office invariants verified via code inspection (`ReplicaLauncher:263-268` routes to home office → A3 remote path) + unit test `executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit` (passing).
- [x] `NCN-RM-2` validation evidence: FIFO waitlist + 5-check auto-assignment verified via code inspection (`addToWaitListLocal` FIFO ArrayList + `processWaitlistCandidate` 5 sequential checks).
- [x] G3 (RM channels): reliable send/ACK coverage applied to `VOTE_*`, `STATE_REQUEST`/`STATE_TRANSFER`, `INIT_STATE` retry+ACK loop, and `REPLICA_READY` fan-out.
- [x] Mark Student 2 Phase-2 done — all 3 items above are complete.

## Phase 1 Baseline Responsibilities (RM)
Target file: `src/main/java/server/ReplicaManager.java`

- Launch and monitor co-located replica.
- Perform heartbeat checks and crash suspicion handling.
- Participate in vote flow for Byzantine/crash replacement decisions.
- Replace faulty replica and coordinate state restoration.
- Broadcast readiness (`REPLICA_READY`) after replacement.

Phase 1 RM done criteria:
Phase 1 RM criteria are complete (see Group Audit section 9).

## Phase 2 Extension Responsibilities (RM)
- Harden vote-window behavior and malformed message handling.
- Finalize replacement safety around concurrent fault reports (`replacementInProgress` guard).
- Ensure replacement readiness and replay handoff stays consistent.
- Document and preserve state-transfer payload completeness (snapshot must include `vehicleDB`, `reservations`, `waitList`, `budgets`, `crossOfficeCount`, `lastSeqNum`).
- Verify state-transfer sequencing semantics: `resetNextExpectedSeq(lastSeq+1)` clears stale holdback entries before replay (`holdbackQueue.clear()` behavior is intentional for correctness).
- **G3 APPLIED (owner: Student 2):** RM recovery channels now use reliable send/ACK semantics:
  - `VOTE_BYZANTINE` / `VOTE_CRASH` peer broadcasts (async reliable)
  - `STATE_REQUEST` (RM->RM and RM->Replica) and `STATE_TRANSFER` relay
  - `INIT_STATE` retry + ACK confirmation loop
  - `REPLICA_READY` reliable fan-out to Sequencer, FE, and peer RMs
  Residual raw channels are now outside Student 2’s RM ownership (tracked in shared doc §3.3).
- **G5 APPLIED:** `launchReplica()` and `killReplica()` are now `synchronized`; public `stop()` added for teardown access (`ReplicaManager.java:105,120,128`). Monitor for any new unsynchronized access paths to `replicaProcess`.
- **Heartbeat scope:** `heartbeatLoop()` is monitoring-only — it logs failures but does NOT autonomously trigger replacement. Crash replacement is always initiated by an FE `CRASH_SUSPECT` notification. This is intentional per design §3.2 ("when the FE sends CRASH_SUSPECT, each RM verifies via heartbeat"). See NCN-RM-6 below.
- Maintain/extend RM unit tests in:
  - `src/test/java/unit/ReplicaManager/*`

Phase 2 RM done criteria:
- [x] NCN-RM-1 validation evidence complete: code inspection confirms `ReplicaLauncher:263-268` routes `*_EXECUTE` to customer home office → A3 remote path preserves budget ($1,000 via `BudgetManager`) + cross-office slot (`acquireRemoteOfficeSlot`/`releaseRemoteOfficeSlot`). Unit test `executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit` asserts deduction, limit, refund, and slot release.
- [x] NCN-RM-2 validation evidence complete: code inspection confirms `addToWaitListLocal` (line 965-982) uses `synchronizedList(ArrayList)` for FIFO. `processWaitlistCandidate` (line 1157-1198) performs 5 sequential checks: vehicle exists, valid dates, no duplicate, cross-office slot, budget+capacity eligibility.
- [x] G3 Phase 2 RM scope complete: `VOTE_*`, state-transfer paths, and `REPLICA_READY` fan-out wrapped with reliable behavior
- [x] RM unit tests remain enabled and passing — `mvn test` 0 failures; RM tests not @Disabled
- [x] RM output contracts are stable for Sequencer/Integration dependencies — all wire contracts verified PASS in group audit §3.2

## Cross-Student Handoff (Inputs/Outputs)
Inputs Student 2 needs:
- FE fault notification message contracts (Student 1)
- Replica readiness replay expectation (Student 3)

Outputs Student 2 provides:
- Stable replacement + readiness behavior for Student 3 replay logic
- Reliable crash/byzantine recovery behavior for Student 4 failure tests

## Necessary-Change Notes (A3 Parity — Required for Demo Sign-Off)

Per design §2 A3 guardrail, behavior differences from A3 must be justified here.

### NCN-RM-1: RESERVE/CANCEL/UPDATE local execute-path parity (budget + cross-office invariants)

| Field | Content |
|---|---|
| Source clause | Design §1.3 ("A3 business rules unchanged"); §3.2 state transfer |
| Change summary | Workflow/group audits classify this as a parity-risk area: replicated local execute path for `RESERVE`/`CANCEL`/`ATOMIC_UPDATE` must preserve A3 invariants (budget + cross-office behavior). Documentation exists; validation evidence is still open. |
| Affected operations/files | `VehicleReservationWS.java` RESERVE, CANCEL, ATOMIC_UPDATE cases |
| Why unavoidable | In active replication, the home-server cross-office UDP call of A3 would create inter-replica replication loops. Local execution avoids this. |
| Validation evidence | [x] **Code inspection verified (2026-03-25):** `ReplicaLauncher:263-268` routes `RESERVE_EXECUTE`/`CANCEL_EXECUTE`/`ATOMIC_UPDATE_EXECUTE` to customer's home office, which uses A3's cross-office remote path. Budget: `BudgetManager` with `DEFAULT_BUDGET=1000.0`; `canAfford()` → `deduct()` on reserve, `refund()` on cancel. Cross-office slot: `acquireRemoteOfficeSlot()` on reserve, `releaseRemoteOfficeSlot()` on cancel (line 814). [x] **Unit test:** `executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit` passes — asserts budget deduction, cross-office limit (max 1 per office), refund to $1,000, and slot release after cancel. |
| Risk check | If budget is not enforced, customers can make unlimited reservations — violates A3 §1.3 invariant. Must be fixed before demo. |

### NCN-RM-2: Replica receives WAITLIST via addToWaitListLocal (local-only path)

| Field | Content |
|---|---|
| Source clause | Design §1.3 ("A3 business rules unchanged"); §3.5 replica modifications |
| Change summary | A3 `addToWaitList` calls a cross-office UDP sub-request when a vehicle is freed and triggers auto-assignment across offices. P2 replica executes `addToWaitListLocal(...)` which runs entirely locally within the replica. |
| Affected operations/files | `VehicleReservationWS.java` WAITLIST case |
| Why unavoidable | In active replication, cross-office UDP calls from within a replica would create inter-replica replication loops. Each replica holds all 3 offices locally, so local waitlist processing is deterministic. |
| Validation evidence | [x] **Code inspection verified (2026-03-25):** `addToWaitListLocal` (line 965-982) appends to `Collections.synchronizedList(new ArrayList<>())` — FIFO insertion order preserved. `processWaitList` (line 1110-1139) iterates from index 0 (FIFO). `processWaitlistCandidate` (line 1157-1198) performs 5 sequential checks: (1) vehicle exists, (2) valid dates, (3) no duplicate reservation, (4) cross-office slot via `acquireRemoteOfficeSlot`, (5) assignment eligibility via `assignReservationIfEligible` (budget + capacity). Each replica holds all 3 offices locally — equivalent to A3. |
| Risk check | If auto-assignment skips cross-office vehicles, waitlist customers may not be served. Verify the local path covers all 3 offices since each replica holds the full office set. |

### NCN-RM-3: VOTE_ACK / VOTE_NACK not implemented

| Field | Content |
|---|---|
| Source clause | Design §4.4 lists `VOTE_ACK / VOTE_NACK` as `RM→RM` response messages for vote requests |
| Change summary | Design §4.4 specifies explicit ACK/NACK responses to vote messages. Current implementation collects votes into `voteCollector` keyed by sender RM ID and evaluates after a bounded `VOTE_WINDOW_MS = 2000 ms` timeout. No per-vote ACK is sent or expected. |
| Affected operations/files | `ReplicaManager.java` — `handleVote()`, `evaluateVoteWindow()` |
| Why unavoidable | Vote-window timeout provides equivalent quorum safety: after 2 s, all reachable RMs have had opportunity to vote. Explicit ACK/NACK per vote would require a second round-trip per RM per fault event, adding latency to the replacement critical path. |
| Validation evidence | Verified: `evaluateVoteWindow()` correctly uses `agreeCount > totalVotes / 2` on received votes — sufficient for strict majority. |
| Risk check | Vote channels now use reliable send/ACK (peer broadcasts), while vote-window timeout still governs quorum closure when a peer RM is truly unreachable. |

### NCN-RM-4: SHUTDOWN message not sent before Byzantine kill

| Field | Content |
|---|---|
| Source clause | Design §4.4 lists `SHUTDOWN` as `RM → Faulty Replica` — "Graceful shutdown (Byzantine replacement)" |
| Change summary | `killReplica()` calls `replicaProcess.destroyForcibly()` directly without first sending a `SHUTDOWN` UDP message to the replica. `UDPMessage.Type.SHUTDOWN` exists in the enum but is never sent. |
| Affected operations/files | `ReplicaManager.java:120-124` — `killReplica()` |
| Why unavoidable | A Byzantine replica by definition may ignore or corrupt messages, including SHUTDOWN. A graceful shutdown message has no reliability guarantee to a faulty process. `destroyForcibly()` achieves the same effect immediately and is safe. |
| Validation evidence | Verified: `killReplica()` terminates the process reliably; subsequent `launchReplica()` starts a clean fresh process. No state corruption observed. |
| Risk check | No risk — forceful termination is strictly stronger than graceful shutdown for a Byzantine process. |

### NCN-RM-5: Replica-originated READY replaced by RM-originated REPLICA_READY

| Field | Content |
|---|---|
| Source clause | Design §4.4 lists two distinct messages: `READY:replicaID:lastSeqNum` (Replica → Sequencer) and `REPLICA_READY:replicaID:address` (RM → Seq/FE/RMs) |
| Change summary | Current implementation has only RM-originated `REPLICA_READY:replicaID:localhost:port:lastSeqNum` sent by `notifyReplicaReady()`. There is no Replica-originated `READY` message. The replica does not notify the Sequencer directly; the RM owns the recovery completion signal. |
| Affected operations/files | `ReplicaManager.java:548-563` — `notifyReplicaReady()`; `ReplicaLauncher.java` — no READY send |
| Why unavoidable | The RM coordinates the entire recovery lifecycle (kill → launch → state transfer → notify). Only the RM knows when state transfer is complete and the new replica has loaded its state. The replica cannot self-announce readiness before the RM has finished INIT_STATE. |
| Validation evidence | Verified: `notifyReplicaReady()` sends to Sequencer, FE, and all RMs after INIT_STATE ACK received. Sequencer replays from `lastSeqNum+1` correctly. |
| Risk check | RM is co-located with replica and owns the process lifecycle. Single notification source (RM) is simpler and safer than dual-source notification. |

### NCN-RM-6: heartbeatLoop failure is monitoring-only (no autonomous replacement)

| Field | Content |
|---|---|
| Source clause | Design §3.2 "monitors health via heartbeats" |
| Change summary | `heartbeatLoop()` runs every 3000 ms, sends `HEARTBEAT_CHECK` to own replica, and logs failure. It does NOT send `CRASH_SUSPECT` to other RMs or FE. Crash replacement is exclusively triggered by `FE → CRASH_SUSPECT`. |
| Affected operations/files | `ReplicaManager.java:136-149` — `heartbeatLoop()` |
| Why unavoidable | Design §3.2 explicitly states crash handling begins "when the FE sends CRASH_SUSPECT." The FE is the authoritative timeout-based detector (2 × slowestResponseTime). RM-autonomous detection would create parallel replacement races without FE coordination. |
| Validation evidence | Verified: Crash detection path tested via FE timeout → CRASH_SUSPECT → RM heartbeat verify → VOTE_CRASH → replaceReplica(). |
| Risk check | Gap: if no client request is in flight when a replica crashes, detection is delayed until the next request triggers FE timeout. Acceptable for the project fault model (LAN, active clients). |

## Traceability Checklist
| Item | Source |
|---|---|
| RM detects/replaces failed replica | Requirement Project 2 RM bullet |
| RM crash vote and byzantine vote | Design recovery sections |
| RM state transfer and readiness message | Design recovery + addendum contracts |
| Reachable-majority handling | Design crash/byzantine consensus rationale |
