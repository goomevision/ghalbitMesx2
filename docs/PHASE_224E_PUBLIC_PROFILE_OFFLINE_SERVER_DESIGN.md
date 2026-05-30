# PHASE 224E — Public Profile Offline Server Design

## Tujuan

Kartu nama GhalbitNet tidak boleh hanya menjadi buku telepon. Ia harus menjadi profil profesional yang bisa dibuka, dibagikan, dan dipercaya, baik melalui internet maupun jaringan lokal mesh.

Prinsip utama:

```text
Internet ada     → profil publik disimpan di relay/server
Internet hilang  → HP menjadi server kecil lokal
Pengguna offline → server menyimpan profil terakhir yang diizinkan publik
Privasi tetap    → hanya field yang diizinkan publik yang keluar
```

---

## Status Android Saat Ini

Android sudah memiliki pondasi:

- `CommunityProfile`
- `MyProfileActivity`
- `MyNameCardView`
- QR signed profile
- share QR as text
- scan QR contact
- `ProfileSyncManager.uploadMyProfile()`
- `ProfileSyncManager.fetchProfile()`
- `ProfileSyncManager.batchSyncProfiles()`
- `ProfileSyncManager.verifyProfile()`

Endpoint yang sudah dipakai client:

```text
POST /profile/update
GET  /profile/{globalId}
GET  /profile/batch?ids=...
POST /profile/verify
```

---

## Arsitektur Target

```text
                 Internet tersedia
HP Pengguna ─────────────────────────► Relay/Profile Server
   ▲                                      │
   │                                      ▼
   └──────────── profile publik tersimpan ◄──────── Web umum


                 Internet tidak tersedia
HP Pengguna A ───── Mesh / Hotspot / WiFi Direct ───── HP Pengguna B
      │                                                   │
      └────────────── mini profile server lokal ──────────┘
```

---

## Mode Penyimpanan Profil

### 1. Private Local

Data hanya ada di HP pengguna.

Untuk:

- catatan pribadi
- alias lokal
- data kontak privat
- detail yang tidak boleh dibagikan

### 2. Community Visible

Data terlihat di komunitas mesh/lokal.

Untuk:

- nama
- peran
- keahlian
- komunitas
- status bantuan

### 3. Public Web

Data dapat dibuka oleh siapa pun melalui link publik.

Untuk:

- nama profesional
- bio publik
- profesi/peran
- organisasi
- portofolio/proyek
- skill publik
- QR publik

### 4. Emergency Visible

Data terbuka sementara saat SOS/darurat.

Untuk:

- status darurat
- wilayah umum
- kemampuan relawan
- jalur kontak darurat yang diizinkan

---

## Field Visibility Policy

Server dan HP lokal wajib menghormati izin publik.

| Field | Default | Publik jika |
|---|---|---|
| globalId | Publik | Selalu untuk identitas kartu |
| publicKeyHash | Publik | Selalu untuk verifikasi |
| displayName | Publik | Profil publik aktif |
| nickname | Publik | Profil publik aktif |
| roleTitle | Publik | Profil publik aktif |
| bio | Publik | Profil publik aktif |
| communityName | Komunitas/Publik | Profil publik aktif |
| organization | Publik opsional | Diisi dan profil publik aktif |
| skillTags | Publik | Profil publik aktif |
| avatarUri | Publik opsional | `avatarSyncEnabled=true` dan URI http/https |
| region | Tersembunyi | `showRegion=true` |
| statusMessage | Tersembunyi | `showStatus=true` |
| phone/email | Tersembunyi | Tidak dipublikasi di fase ini |
| localAlias/localNote | Privat | Tidak pernah keluar |
| private keys | Privat | Tidak pernah keluar |

---

## Relay/Profile Server Contract

### POST /profile/update

Menyimpan profil publik terakhir dari pengguna.

Request:

```json
{
  "senderGlobalId": "GX-123",
  "senderPublicKey": "base64-public-key",
  "publicKeyHash": "hash",
  "profileVersion": 3,
  "updatedAt": 1700000000000,
  "signature": "signed-profile",
  "visibility": "PUBLIC",
  "profileJson": "{...public profile json...}"
}
```

Response:

```json
{
  "ok": true,
  "status": "PROFILE_STORED",
  "globalId": "GX-123",
  "profileVersion": 3,
  "publicUrl": "https://profile.ghalbit.net/GX-123",
  "updatedAt": 1700000000000
}
```

Server behavior:

- Validasi `globalId`
- Validasi signature bila public key tersedia
- Simpan versi terbaru saja
- Tolak versi lama jika `profileVersion` lebih kecil
- Simpan payload publik terakhir agar profil tetap bisa dibuka saat HP pengguna offline

Required logs:

```text
GHALBIT-PROFILE-SERVER update globalId=<id> version=<version>
GHALBIT-PROFILE-SERVER stored globalId=<id> publicUrl=<url>
GHALBIT-PROFILE-SERVER rejected globalId=<id> reason=<reason>
```

---

### GET /profile/{globalId}

Mengambil profil publik.

Response:

```json
{
  "ok": true,
  "globalId": "GX-123",
  "profile": {
    "globalId": "GX-123",
    "displayName": "Rafly Kande",
    "nickname": "Rafly",
    "roleTitle": "Pengembang GhalbitNet",
    "bio": "Membangun jaringan komunitas untuk komunikasi darurat dan ekonomi lokal.",
    "communityName": "GhalbitNet Aceh",
    "organization": "Rafly Kande Peduli",
    "skillTags": ["Mesh Network", "Blockchain", "Relawan"],
    "visibility": "PUBLIC",
    "updatedAt": 1700000000000,
    "profileVersion": 3
  }
}
```

If not found:

```json
{
  "ok": false,
  "error": "profile_not_found"
}
```

---

### GET /p/{globalId}

Halaman web umum untuk dibuka oleh non-pengguna GhalbitNet.

Output HTML ringan:

```text
Nama
Peran
Bio
Keahlian
Komunitas
QR / tombol hubungi via GhalbitNet
```

Tidak boleh menampilkan field privat.

---

## HP Sebagai Server Kecil Lokal

Saat internet tidak ada tetapi mesh/hotspot aktif, HP dapat melayani profil publik lokal melalui packet mesh, bukan web server penuh.

### Packet Types

```text
PROFILE_QUERY
PROFILE_RESPONSE
PROFILE_BATCH_QUERY
PROFILE_BATCH_RESPONSE
PROFILE_WEB_CARD_REQUEST
PROFILE_WEB_CARD_RESPONSE
```

### PROFILE_QUERY Payload

```json
{
  "globalId": "GX-123",
  "requesterGlobalId": "GX-999",
  "requestId": "profile-query-1700000000000"
}
```

### PROFILE_RESPONSE Payload

```json
{
  "requestId": "profile-query-1700000000000",
  "globalId": "GX-123",
  "profileVersion": 3,
  "visibility": "PUBLIC",
  "profileJson": "{...public profile json...}",
  "signature": "signed-profile",
  "servedBy": "GX-123",
  "servedAt": 1700000000000
}
```

### Behavior

Jika HP menerima `PROFILE_QUERY` untuk dirinya sendiri:

```text
1. Ambil profil lokal
2. Filter hanya field publik
3. Kirim PROFILE_RESPONSE
```

Jika HP menerima `PROFILE_QUERY` untuk profil yang pernah disimpan:

```text
1. Cek cache remote profile lokal
2. Jika ada dan belum terlalu lama, boleh forward response sebagai cache
3. Tandai servedBy sebagai node penyedia cache
```

---

## Local Profile Cache

Setiap HP menyimpan profil publik yang pernah dilihat.

Tujuannya:

```text
Orang A offline
Orang B pernah menyimpan profil A
Orang C minta profil A
Orang B bisa membantu mengirim versi cache
```

Ini membuat profil tetap hidup di komunitas walaupun pemilik sedang offline.

Rules:

- Cache hanya untuk field publik
- Cache memiliki `updatedAt`
- Cache tidak boleh mengubah signature
- Cache tidak boleh menambah data privat
- Cache harus diberi label `cached`

---

## Privacy Guard

Sebelum profil dikirim ke server atau mesh lokal, wajib melewati filter:

```text
PublicProfileExporter
```

Tugas:

- Hapus field privat
- Hapus localAlias
- Hapus localNote
- Hapus phone/email untuk fase ini
- Hapus avatar jika avatarSyncEnabled=false
- Hapus region jika showRegion=false
- Hapus status jika showStatus=false
- Hanya kirim field yang disetujui pengguna
```

Recommended Android class:

```text
com.ghalbitnet.meshx2.profile.PublicProfileExporter
```

---

## Share Modes

Kartu nama harus bisa dibagikan dalam beberapa bentuk:

### QR Signed

Sudah ada.

### Text Payload

Sudah ada melalui share intent.

### Public Link

Target:

```text
https://profile.ghalbit.net/p/{globalId}
```

### Local Mesh Card

Target:

```text
ghalbit://profile/{globalId}
```

Jika dibuka di dalam aplikasi, cari profil melalui:

1. Local cache
2. Mesh profile query
3. Relay server jika internet ada

---

## Sync Rules

### Saat Internet Ada

```text
1. Upload profil publik ke server
2. Server simpan versi terbaru
3. Server sediakan web profile publik
4. Android tetap menyimpan profil lokal
```

### Saat Internet Hilang

```text
1. HP tetap menampilkan profil lokal
2. HP menjawab PROFILE_QUERY dari node lain
3. HP bisa mengirim QR/profile payload langsung via mesh
4. Cache komunitas membantu memperluas distribusi profil publik
```

### Saat Internet Kembali

```text
1. Upload perubahan profil terbaru
2. Sync profil kontak yang tertunda
3. Update public web profile
```

---

## Implementation Phases

### PHASE 224F — PublicProfileExporter

Tambahkan class filter privasi untuk export profil publik.

### PHASE 224G — Profile Mesh Query Packets

Tambahkan handler `PROFILE_QUERY` dan `PROFILE_RESPONSE`.

### PHASE 224H — Profile Local Cache Share

Izinkan node membantu menyebarkan cache publik yang masih valid.

### PHASE 224I — Public Profile Link

Tambahkan share link `https://profile.ghalbit.net/p/{globalId}` jika relay tersedia.

### PHASE 224J — Server Profile Contract

Tambahkan kontrak backend untuk `/profile/update`, `/profile/{globalId}`, `/p/{globalId}`.

---

## Final Principle

GhalbitNet profile harus bekerja seperti ini:

```text
Profil profesional saat internet ada
Profil lokal saat internet tidak ada
Cache komunitas saat pemilik offline
Privasi tetap dikendalikan pengguna
```

Ini membuat kartu nama GhalbitNet menjadi identitas digital komunitas, bukan hanya kontak biasa.
