# Server Response Proof Audit (PHASE 300B)

Audit basis:
- kode client saat ini,
- log runtime historis yang sudah ada,
- tanpa permintaan log/tes baru.

## Base URL yang ditemukan

- Relay: `BuildConfig.BASE_RELAY_URL`
- Presence: `BuildConfig.BASE_PRESENCE_URL` (fallback ke relay)
- Identity/Session/Media: mengikuti relay base di caller saat ini

## Endpoint proof table

| Endpoint | Caller file | Proof status |
|---|---|---|
| `/identity/register` | `identity/IdentityServerClient.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/identity/sync` | `identity/IdentityServerClient.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/identity/lookup/{callId}` | `identity/IdentityServerClient.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/relay/send` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, PARTIAL_RUNTIME |
| `/relay/inbox/{globalId}` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, PARTIAL_RUNTIME |
| `/relay/ack` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/relay/read` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/session/prepare-route` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/session/validate-route` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/session/heartbeat` | `online/OnlineFallbackTransport.kt` | CODE_PRESENT, SERVER_NOT_PROVEN |
| `/health` or `/ping` | (tidak ada caller aktif) | CODE_ONLY |

## Kategori kesimpulan

- Endpoint terbukti hidup di app (caller ada): banyak.
- Endpoint terbukti hidup di server (response evidence kuat): belum cukup untuk seluruh lifecycle.

Jika server aktif tidak bisa dibuktikan dari evidence saat ini:

**SERVER_NOT_PROVEN**

## Rekomendasi langsung

1. Jadikan server authoritative untuk status presence + receipt semantics.
2. Standarkan response envelope (`ok`, `status`, `error`, `serverTs`, `correlationId`).
3. Tambah observability id lintas chat/call (`messageId`, `packetId`, `callId`, `sessionId`).

