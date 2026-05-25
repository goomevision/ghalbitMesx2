# fix_clean_voip.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membersihkan referensi VoIP dari ChatActivity..." -ForegroundColor Cyan

# Baca file ChatActivity.kt
$chatPath = "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt"
$content = Get-Content $chatPath -Raw

# 1. Hapus import VoipEngine (jika masih ada)
$content = $content -replace "import com.ghalbitnet.meshx2.chat.VoipEngine", ""
$content = $content -replace "`r`n`r`n", "`r`n"  # bersihkan baris kosong

# 2. Ganti blok btnCall.setOnClickListener dengan yang aman
$oldCallBlock = "btnCall.setOnClickListener \{ startVoipCall\(\) \}"
$newCallBlock = 'btnCall.setOnClickListener {
            Toast.makeText(this, "Fitur Telepon akan hadir di versi berikutnya", Toast.LENGTH_SHORT).show()
        }'

$content = $content -replace [regex]::Escaped($oldCallBlock), $newCallBlock

# 3. Hapus deklarasi variabel isCallActive
$content = $content -replace "private var isCallActive = false", ""

# 4. Hapus metode startVoipCall secara keseluruhan
$content = $content -replace "(?s)private fun startVoipCall\(\).*?^\s*\}\s*", ""

# 5. Hapus VoipEngine.stop() di onDestroy
$content = $content -replace "VoipEngine.stop\(\)", ""

# Tulis ulang file
Write-FileWithoutBOM $chatPath $content

# ================================================
# Build & Install
# ================================================
Write-Host "Build ulang..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal ke perangkat..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "VoIP dihapus total. Chat sekarang stabil." -ForegroundColor Cyan
    } else {
        Write-Host "ADB tidak ditemukan. Install manual APK." -ForegroundColor Yellow
    }
} else {
    Write-Host "Build gagal. Periksa error di atas." -ForegroundColor Red
}