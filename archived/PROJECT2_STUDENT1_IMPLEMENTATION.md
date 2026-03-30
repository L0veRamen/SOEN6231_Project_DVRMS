# PROJECT 2 STUDENT1 IMPLEMENTATION (FE)

## Role and Purpose
Owner: Student 1

This guide defines Student 1 work for Front End (FE) across:
- Phase 1 baseline responsibilities (required for group readiness)
- Phase 2 role extension responsibilities (individual completion)

## Alignment Sources
- `Project.6231w26.txt` (FE receives client request, forwards to Sequencer, returns correct result)
- `FT-DVRMS_Project2_Design_v3.3.docx` (FE voting and fault reporting behavior)
- `FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md` (canonical message contracts and replica IDs)
- `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md` (shared gate dependency)

## A3 Guardrail (Mandatory)
- Keep A3 client-facing behavior semantics intact.
- FE changes should orchestrate replication behavior, not alter business rules.
- Any non-A3 behavior must include a Necessary-Change Note (source clause + impact + validation evidence).

## Entry Condition
Shared baseline gate in `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md` is complete (G1 CLOSED, G2 CLOSED — 2026-03-25).

## Audit Sync Status (Workflow + Group Audit, 2026-03-25)
- Authoritative source: `PROJECT2_GROUP_BASE_AUDIT.md` section 9 (STUDENT 1).
- [x] `NCN-FE-1` validation evidence: `findVehicle` routes to default office only (MTL sedans returned, WPG excluded). Deviation confirmed and asserted in `t3_crossOfficeReservation`.
- [x] `NCN-FE-2` validation evidence: `listCustomerReservations` routes to home office only; cross-office reservations not visible. Deviation confirmed and asserted in `t3_crossOfficeReservation`.
- [x] G4: 4 FE unit tests in `FrontEndTest.java` are enabled with real assertions.
- [x] Mark Student 1 Phase-2 done after all 3 items above are complete.
- Current verification snapshot: `mvn -q -Dtest=unit.FrontEnd.FrontEndTest test` passed (`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`); `mvn -q -Dtest=integration.ReplicationIntegrationTest#t3_crossOfficeReservation test` passed.

## Phase 1 Baseline Responsibilities (FE)
Target file: `src/main/java/server/FrontEnd.java`

- Preserve A3 SOAP method signatures.
- Build `REQUEST` messages and send to Sequencer.
- Collect `RESULT` responses and return as soon as `f+1 = 2` matching responses exist.
- Report mismatches/timeouts to RMs (`INCORRECT_RESULT`, `CRASH_SUSPECT`, `REPLACE_REQUEST`).
- Keep canonical numeric replica IDs (`1..4`) in FE/RM fault notifications.
- ~~Ensure find payload contract aligns with parity target (`FIND:<customerID>:<vehicleType>`).~~ DONE — `FrontEnd.java:113` sends `FIND:<customerID>:<vehicleType>`; `VehicleReservationWS.java:~1025` parses `parts[2]` for vehicleType. (G1 CLOSED)

Phase 1 FE done criteria:
Phase 1 FE criteria are complete (see Group Audit section 9).

## Phase 2 Extension Responsibilities (FE)
- Harden timeout and majority behavior under mixed crash+Byzantine responses.
- Finalize FE-side correlation and response handling for all operations.
- Complete FE unit tests and remove disabled placeholders in:
  - `src/test/java/unit/FrontEnd/FrontEndTest.java`
- **G4 FOLLOW-UP (owner: Student 1):** CLOSED — 4 FE unit tests in `FrontEndTest.java` are enabled with real assertions.

## Necessary-Change Notes (A3 Parity — Required for Demo Sign-Off)

Per design §2 A3 guardrail, behavior differences from A3 must be justified and documented here.

### NCN-FE-1: findVehicle — single-office path instead of A3 cross-office aggregate

| Field | Content |
|---|---|
| Source clause | Design §1.3 ("A3 business rules unchanged"); §3.1 FIND payload |
| Change summary | A3 `findVehicle` aggregates search results across all 3 offices. P2 FE sends `FIND:<customerID>:<vehicleType>` to Sequencer; each replica routes to ONE office's `findVehicleLocal(vehicleType)`. |
| Affected operations/files | `FrontEnd.java:113`; `VehicleReservationWS.java:~1025` |
| Why unavoidable | In active replication, cross-office UDP sub-requests from within a replica would trigger another round of sequencing/replication, creating circular dependencies. All business state exists within each replica; routing to the single office holding the vehicle is deterministic and sufficient. |
| Validation evidence | [x] Integration: `t3_crossOfficeReservation` asserts `findVehicle("MTLU3333","Sedan")` returns MTL sedans only (WPG excluded). FIND routes to DEFAULT_OFFICE via `extractTargetOffice`. Deviation confirmed. |
| Risk check | Search semantics are preserved: vehicle is found if it exists. Aggregate view (all offices) is reduced to per-office query. If vehicle is uniquely identified by vehicleID prefix (MTL/WPG/BNF), FE can pre-route to correct office. |

### NCN-FE-2: listCustomerReservations — single-office path instead of A3 cross-office aggregate

| Field | Content |
|---|---|
| Source clause | Design §1.3 |
| Change summary | A3 aggregates reservations across all 3 offices. P2 routes to one office's `listCustomerReservationsLocal`. |
| Affected operations/files | `FrontEnd.java:118-121`; `VehicleReservationWS.java` LISTRES case |
| Why unavoidable | Same cross-office recursion avoidance as NCN-FE-1 |
| Validation evidence | [x] Integration: `t3_crossOfficeReservation` asserts `listCustomerReservations("MTLU3333")` does NOT contain WPG1002. LISTRES routes to home office (MTL) only; cross-office reservations stored in remote office instance. Deviation confirmed. |
| Risk check | If customer only ever has reservations at their home office, behavior is unchanged. Cross-office reservation listing requires replica to aggregate locally across its 3 embedded offices. |

Phase 2 FE done criteria:
- [x] FE unit tests are enabled (no role-owned disabled placeholders)
- [x] FE tests cover: (a) majority logic — `matchCount >= 2` returns correct result; (b) mismatch counter increments on `INCORRECT_RESULT`; (c) `REPLACE_REQUEST` fires when `byzantineCount >= 3`; (d) `CRASH_SUSPECT` fires for non-responding replica after timeout
- [x] FE behavior remains compatible with shared contracts and audit gate

## Cross-Student Handoff (Inputs/Outputs)
Inputs Student 1 needs:
- Shared message/port contracts from shared guide
- Sequencer `EXECUTE`/`RESULT` reliability expectations

Outputs Student 1 provides:
- Stable FE request/response behavior for Student 4 integration tests
- Clear FE fault notification behavior for Student 2 RM validation

## Traceability Checklist
| Item | Source |
|---|---|
| FE as single client entry point | Requirement Project 2 FE bullet |
| FE returns correct result on `f+1` | Design voting and fault tolerance rationale |
| FE fault reporting to RM | Requirement/design FE+RM coordination |
| Canonical message/replicaID format | Addendum canonical contracts |
