# Firebase Admin Tools

Tool lokal ini dipakai untuk menulis policy control plane ke Firebase dengan akses admin penuh.

## Fungsi server yang sekarang didukung

Tool ini sekarang mewakili tugas server control-plane:

- `Bootstrap Server`
- `Registry Node`
- `Policy Controller`
- `Gateway Directory`
- `Trust & Reputation`
- `Blockchain Sync Summary`
- `Emergency Recovery Snapshot`

## 1. Simpan service account key

Simpan file admin JSON ke salah satu lokasi berikut:

- `C:\project\Ghalbitnet\ghalbitMesx2\firebase-admin-key.json`
- atau lokasi lain, lalu set env var `FIREBASE_SERVICE_ACCOUNT`

Contoh PowerShell:

```powershell
$env:FIREBASE_SERVICE_ACCOUNT="C:\Users\Hp\Downloads\maritime-link-aceh-firebase-adminsdk-fbsvc-bb693a6e97.json"
```

## 2. Install dependency

```powershell
cd C:\project\Ghalbitnet\ghalbitMesx2\firebase-admin-tools
npm install
```

## 3. Tulis policy awal

```powershell
npm run set-policies
```

Tool ini akan menulis:

- `bridgePolicies/default`
- `economyPolicies/default`

ke Firestore project `maritime-link-aceh`.

## 4. Verifikasi dan audit cepat

```powershell
npm run check
npm run show-policies
npm run show-network-state
```

## 4a. Siapkan fondasi control-plane server

```powershell
npm run seed-control-plane
```

Ini akan menulis:

- `bridgePolicies/default`
- `economyPolicies/default`
- `bootstrapConfig/default`
- `trustConfig/default`

## 4b. Bangun ulang state jaringan server

```powershell
npm run rebuild-network-state
```

Ini akan memperbarui:

- `nodeRegistry/{globalId}`
- `gatewayDirectory/{globalId}`
- `bootstrapState/default`
- `networkState/default`
- `blockchainSync/default`

## 4c. Ambil daftar peer bootstrap

```powershell
npm run bootstrap-peers -- --globalId GX-USER-001
```

Tool ini menampilkan:

- peer rekomendasi
- gateway rekomendasi

untuk node baru yang ingin masuk mesh.

## 5. Top-up wallet server

```powershell
npm run top-up-wallet -- --globalId GX-USER-001 --amount 50 --reason SYSTEM_TOPUP
```

Tool ini akan:

- membuat / memperbarui `wallets/{globalId}`
- menambah log ke `walletTransactions`

Lihat isi wallet:

```powershell
npm run show-wallet -- --globalId GX-USER-001
```

## 6. Atur peer policy dari server

Atur tier dan kuota peer:

```powershell
npm run set-peer-policy -- --globalId GX-USER-001 --tier PRIORITY --quotaMb 2048 --note "Tim inti"
```

Blokir peer:

```powershell
npm run block-user -- --globalId GX-USER-001
```

Buka blokir peer:

```powershell
npm run unblock-user -- --globalId GX-USER-001
```

Data ini ditulis ke:

- `peerPolicies/{globalId}`

Semua keputusan policy dan top-up server diperlakukan sebagai:

- `controlledBy = SYSTEM`
- `subjectClass = USER | BUILDER`

## 7. Trust dan abuse

Atur trust score manual:

```powershell
npm run set-trust-score -- --globalId GX-USER-001 --score 82 --note "Node stabil"
```

Catat abuse dan turunkan trust:

```powershell
npm run report-abuse -- --globalId GX-USER-001 --category SPAM --detail "Spam route palsu" --penalty 25
```

Data ini ditulis ke:

- `nodeRegistry/{globalId}`
- `trustReports`
- `abuseReports`

## 8. Emergency recovery

Ambil snapshot recovery:

```powershell
npm run capture-recovery
```

Data ini disimpan ke:

- `recoverySnapshots/{autoId}`

## Catatan

- Jangan commit service account JSON ke repo publik.
- Kunci ini memberi akses admin penuh.
- Trafik internet user tetap tidak lewat Firebase; Firebase hanya dipakai sebagai control plane.
