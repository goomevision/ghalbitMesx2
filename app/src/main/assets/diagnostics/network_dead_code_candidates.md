# GHALBITNET Network Dead Code Candidates (PHASE 299D)

Catatan: ini audit kandidat. Tidak ada penghapusan pada phase ini.

## Kandidat

| File | Alasan dicurigai | Risiko jika dibiarkan | Rekomendasi |
|---|---|---|---|
| `call/LinphoneVoipEngineAdapter.kt` | Adapter ada, namun pada build aktif sering fallback/stub dan belum ada bukti runtime lintas 2 HP. | Menambah kompleksitas decision engine call. | `REFACTOR` |
| `call/SipVoipEngineAdapter.kt` | Jalur SIP ada tapi belum ada bukti penggunaan lapangan pada log audit terakhir. | State call jadi bercabang banyak. | `REFACTOR` |
| `call/WebRtcVoipEngineAdapter.kt` | Adapter aktif di manager tetapi bukti audio E2E masih minim. | Sulit menentukan jalur gagal karena fallback terlalu banyak. | `KEEP` (audit lanjutan) |
| `call/VoiceChunkTransport.kt` | Interface tersedia, pemakaian konkret terbatas/indirek. | Kebingungan antara chunk pipeline vs packet pipeline. | `DELETE LATER` (setelah call core minimal stabil) |
| `call/AiTranscriptTransport.kt` | Interface siap, namun mode AI tidak jadi jalur utama untuk stabilisasi jaringan. | Menambah noise diagnosa call. | `DELETE LATER` |
| `future/vpn/VpnPolicyManager.kt` | Modul masa depan, bukan jalur komunikasi utama saat ini. | Fokus audit terpecah. | `KEEP` |
| `wireguard/WireGuardMeshManager.kt` | Sudah diinisialisasi, tapi belum jadi jalur pembuktian utama 2 HP pada audit ini. | Potensi salah diagnosa jika dianggap jalur utama. | `KEEP` |

## Duplikasi/tumpang tindih perilaku

1. Banyak mode call (`LIVE_VOICE`, `BUFFERED`, `CAPACITOR`, `AI`, `PTT`) berjalan bersamaan dalam satu activity besar.
2. Signaling bisa lewat local mesh + internet relay, tetapi telemetri belum seragam untuk semua jalur.
3. Route decision chat dan call punya jalur masing-masing; sinkronisasi evidence masih perlu dipastikan runtime.

## File yang log-heavy (perlu throttle tambahan)

| File | Pola log | Risiko |
|---|---|---|
| `chat/AdaptiveRouteManager.kt` | `GHALBIT-ROUTE-LOCK` muncul sangat sering saat lock refresh | log noise tinggi, sulit baca error utama |
| `call/CallSessionActivity.kt` | banyak log mode switch/audio watchdog | sulit isolasi root cause call diam |

## KEEP / REFACTOR / DELETE LATER / DELETE SAFE

- `KEEP`: modul yang langsung dipakai runtime utama (discovery, socket, route, delivery).
- `REFACTOR`: modul call adapter berlapis yang membuat jalur runtime terlalu bercabang.
- `DELETE LATER`: interface/fitur futuristik setelah call core minimal stabil.
- `DELETE SAFE`: belum ada kandidat aman mutlak pada audit ini (butuh bukti referensi nol + uji regresi).

## Catatan penting

Tidak ada file yang direkomendasikan `DELETE SAFE` pada phase ini karena target saat ini adalah stabilitas runtime, bukan pembersihan agresif.

