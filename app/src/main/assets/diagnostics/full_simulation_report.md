# PHASE 300G — Full Simulation Harness Report

Status: SIM_PARTIAL

## World Model
- Peer A: HYBRID
- Peer B: HYBRID (offline/online toggle)
- Peer C: RELAY_ONLY
- Fake operator server: available
- Network condition toggles: internet on/off, relay on/off, server slow, server error

## Scenario Coverage
- Chat send and fallback: PASS
- Pending queue offline -> online: PASS
- Delivery receipt/read receipt: PASS
- Call signaling state transitions: PASS
- Server 500 handling: PASS
- No-internet handling: PASS
- Loop guard spam detection: PASS

## Categories
- SIM_PASS: endpoint basic contract flow, relay queue, receipts, call signal lifecycle
- SIM_PARTIAL: identity sync dedicated payload, media/file transfer body contract, inbox poll cadence under production scheduler
- SIM_FAIL: none in current unit simulation
- NEEDS_REAL_DEVICE_TEST: mesh route behavior, real call audio path, real server auth and timeout behavior

## Loop/Retry Safety
- Retry without limit: guarded in simulation (counter threshold)
- Heartbeat spam: guarded in simulation
- Call stuck ringing forever: prevented in simulated session transitions
- Pending before TTL: preserved by semantic checks, still needs integration proof in runtime scheduler

## Remaining Gaps
- Real backend availability and auth semantics
- Transport-level mesh evidence under live radio/network conditions
- Media/file transfer consistency with pending queue and dedupe in production path

