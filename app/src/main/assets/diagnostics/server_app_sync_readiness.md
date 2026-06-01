# Server App Sync Readiness (PHASE 300B)

Status ringkas: **APP_READY_PARTIAL_SERVER_UNPROVEN**

Jika source backend tidak ditemukan di repo:
**BACKEND_SOURCE_NOT_FOUND_IN_REPO**

## 1) Base URL ditemukan

- `BuildConfig.BASE_RELAY_URL` (dari `GHALBIT_RELAY_URL`)
- `BuildConfig.BASE_PRESENCE_URL` (dari `GHALBIT_PRESENCE_URL`, fallback relay)
- `BuildConfig.INTERNET_RELAY_CONFIGURED`

Sumber:
- `app/build.gradle`
- `online/OnlineFallbackTransport.kt`
- `online/OnlinePresenceManager.kt`
- `identity/IdentityServerClient.kt`

## 2) File yang memakai server

- `online/OnlineFallbackTransport.kt`
- `online/OnlinePresenceManager.kt`
- `identity/IdentityServerClient.kt`
- `routing/CallRouteDiscoveryManager.kt`
- `chat/ChatDeliveryManager.kt`
- `call/InternetRelaySignalingChannel.kt`

## 3) Endpoint yang dipakai app

- `/identity/register`
- `/identity/sync`
- `/identity/lookup/{callId}`
- `/identity/copy-reached-internet`
- `/identity/route-hint`
- `/presence/heartbeat`
- `/presence/{targetGlobalId}`
- `/relay/send`
- `/relay/inbox/{globalId}`
- `/relay/ack`
- `/relay/read`
- `/relay/edits/{globalId}`
- `/relay/deletes/{globalId}`
- `/session/prepare-route`
- `/session/validate-route`
- `/session/heartbeat`
- `/relay/media/*` (URL generate untuk fetch media)

## 4) Status endpoint

| Endpoint Group | Status |
|---|---|
| identity register/sync/lookup | READY_IN_APP |
| relay send/inbox/ack/read | READY_IN_APP |
| session prepare/validate/heartbeat | READY_IN_APP |
| health/ping server | CODE_ONLY (tidak ada caller aktif bawaan) |
| session start/accept/end (REST) | CODE_ONLY (call signaling pakai relay/send payload event) |
| media upload endpoint eksplisit | SERVER_NOT_PROVEN (client fetch URL ada, upload path eksplisit tidak terlihat di file audit ini) |

## 5) Kecocokan request/response model

- Secara client-side sudah konsisten JSON.
- Banyak parser memakai `opt*` (toleran field hilang).
- Kontrak penuh server belum bisa diverifikasi tanpa source backend.

## 6) Auth/token

- Signature payload ada pada beberapa endpoint (identity/relay/presence) via `NodeSigningIdentityManager`.
- Auth bearer token umum belum terlihat sebagai mekanisme utama di endpoint audit ini.

## 7) Timeout/retry

- `HttpURLConnection` timeout terlihat (`connectTimeout/readTimeout` sekitar 4–5 detik).
- Retry/backoff ada di layer `ChatDeliveryManager` + `PendingMessageStore`.
- Retry server-side tidak terlihat (karena backend source tidak ada).

## 8) Error handling 401/404/500

- Secara umum ditangani sebagai gagal + fallback (`responseCode` non-2xx -> error stream / false/null).
- Penanganan spesifik per-kode (401/404/500) masih minimal (belum granular).

## 9) Pending queue server

- Ada jalur fetch inbox (`/relay/inbox/{globalId}`) + local pending store.
- Queue semantics server (ordering, TTL server, dedup server) **belum terbukti** dari repo ini.

## 10) Delivery/read receipt server

- Ada client call:
  - `/relay/ack` (delivered)
  - `/relay/read` (read)
- Bukti enforcement server authoritative end-to-end: **SERVER_NOT_PROVEN**.

