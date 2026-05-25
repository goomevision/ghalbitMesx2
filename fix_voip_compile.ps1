# fix_voip_compile.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membersihkan VoipEngine yang rusak..." -ForegroundColor Cyan

# 1. Hapus file VoipEngine.kt
$voipFile = "app\src\main\java\com\ghalbitnet\meshx2\chat\VoipEngine.kt"
if (Test-Path $voipFile) {
    Remove-Item $voipFile -Force
    Write-Host "VoipEngine.kt dihapus." -ForegroundColor Yellow
}

# 2. Perbarui ChatActivity.kt: hapus impor VoipEngine & metode startVoipCall
$chatPath = "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt"
$chatContent = Get-Content $chatPath -Raw

# Hapus import baris yang mengandung VoipEngine
$chatContent = $chatContent -replace "import com.ghalbitnet.meshx2.chat.VoipEngine", ""

# Hapus metode startVoipCall (dari "private fun startVoipCall" sampai akhir metode)
$chatContent = $chatContent -replace "(?s)private fun startVoipCall\(\).*?^\s*\}\s*$", ""

# Hapus juga variabel isCallActive jika masih ada
$chatContent = $chatContent -replace "private var isCallActive = false", ""

Write-FileWithoutBOM $chatPath $chatContent

# 3. Build ulang
Write-Host "Build ulang..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "VoIP dihapus, chat stabil kembali." -ForegroundColor Cyan
    } else {
        Write-Host "ADB tidak ditemukan, install manual APK." -ForegroundColor Yellow
    }
} else {
    Write-Host "Build gagal. Periksa error di atas." -ForegroundColor Red
}