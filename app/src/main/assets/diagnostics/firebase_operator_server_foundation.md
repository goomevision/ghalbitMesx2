# Firebase Operator Server Foundation

Fondasi ini dibuat agar GhalbitNet bisa memakai **Firebase sebagai operator server HTTP**, bukan sekadar storage pasif.

## Tujuan

Menyediakan endpoint yang sudah sesuai dengan kontrak app Android:

- `GET /health`
- `POST /presence/heartbeat`
- `GET /presence/{globalId}`
- `POST /relay/send`
- `GET /relay/inbox/{globalId}`
- `POST /receipt/delivered`
- `POST /receipt/read`
- `POST /session/start`
- `POST /session/ringing`
- `POST /session/accept`
- `POST /session/reject`
- `POST /session/end`

## Struktur

- `.firebaserc`
- `firebase.json`
- `functions/package.json`
- `functions/index.js`

## Model server

Cloud Functions menulis ke Firestore:

- `operator_presence`
- `operator_inbox`
- `operator_receipts`
- `operator_sessions`

## Status saat fase ini dibuat

- Fondasi operator server Firebase **sudah ada di repo**
- Android client **sudah siap** memanggil endpoint operator server
- Tetapi deploy nyata **belum dilakukan dari mesin ini** karena:
  - Firebase CLI belum tersedia
  - credential admin/deploy belum tersedia di sesi ini

## Langkah deploy saat kredensial siap

1. install Firebase CLI
2. login / set service account
3. dari root repo:

```powershell
cd C:\project\Ghalbitnet\ghalbitMesx2
cd functions
npm install
cd ..
firebase deploy --only functions
```

## Setelah deploy

Isi `local.properties`:

```properties
GHALBIT_RELAY_URL=https://asia-southeast1-maritime-link-aceh.cloudfunctions.net/operator
GHALBIT_PRESENCE_URL=https://asia-southeast1-maritime-link-aceh.cloudfunctions.net/operator
```

Lalu rebuild app dan ulangi:

- `RUN_SERVER_PRESENCE_CHECK`
- virtual peer lookup
- SOS virtual B -> HP A
