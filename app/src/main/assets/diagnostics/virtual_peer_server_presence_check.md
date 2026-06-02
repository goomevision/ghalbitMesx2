# Virtual Peer Server Presence Check

Tujuan fase ini sederhana: membuktikan bahwa **HP A yang sedang online benar-benar terlihat di server**, lalu sebuah peer virtual/debug probe bisa melakukan lookup dan menemukan status itu.

## Trigger debug

- Action ADB:
  - `com.ghalbitnet.meshx2.debug.RUN_SERVER_PRESENCE_CHECK`

## Urutan bukti

1. Probe membaca identitas lokal HP A (`nodeId`, `globalId`, `publicKeyHash`)
2. Probe mencoba `registerOnline`
3. Probe mencoba `heartbeat`
4. Probe mencoba `checkPeerOnline(globalId)`
5. Hasil diklasifikasikan sebagai:
   - `VISIBLE_ON_SERVER`
   - `NOT_VISIBLE_ON_SERVER`
   - `SERVER_NOT_CONFIGURED`

## Log utama

- `GHALBIT-VIRTUAL-PEER PRESENCE_CHECK_START`
- `GHALBIT-VIRTUAL-PEER REGISTER_OK`
- `GHALBIT-VIRTUAL-PEER REGISTER_FAIL`
- `GHALBIT-VIRTUAL-PEER HEARTBEAT_OK`
- `GHALBIT-VIRTUAL-PEER HEARTBEAT_FAIL`
- `GHALBIT-VIRTUAL-PEER LOOKUP_OK`
- `GHALBIT-VIRTUAL-PEER LOOKUP_FAIL`
- `GHALBIT-DEBUG-TRIGGER RESULT status=...`

## Makna hasil

- `VISIBLE_ON_SERVER`
  - server menerima kehadiran HP A
  - lookup presence untuk HP A berhasil
  - ini bukti awal bahwa peran operator server hidup

- `NOT_VISIBLE_ON_SERVER`
  - HP A mencoba register/heartbeat tetapi lookup tidak menemukan presence yang otoritatif
  - ada gap di presence server atau sinkronisasi app-server

- `SERVER_NOT_CONFIGURED`
  - base URL relay/presence belum siap atau belum dikonfigurasi dengan benar
