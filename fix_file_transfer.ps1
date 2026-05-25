# fix_file_transfer.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Perbaiki FileTransfer & crash prevention..." -ForegroundColor Cyan

# 1. Tambahkan startFileServer di MainActivity.startMesh()
$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw

$startServerLine = 'FileTransferManager.startFileServer(this)'
if ($mainContent -notmatch [regex]::Escape($startServerLine)) {
    # Import dulu
    if ($mainContent -notmatch "import com.ghalbitnet.meshx2.network.FileTransferManager") {
        $mainContent = $mainContent -replace "import com.ghalbitnet.meshx2.network.MeshSocketServer", "import com.ghalbitnet.meshx2.network.MeshSocketServer`nimport com.ghalbitnet.meshx2.network.FileTransferManager"
    }
    # Sisipkan setelah TokenManager.init
    $mainContent = $mainContent -replace "(TokenManager.init\(this\))", "`$1`n        $startServerLine"
    Write-FileWithoutBOM $mainPath $mainContent
}

# 2. Perkuat ChatActivity terhadap null storage
$chatPath = "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt"
$chatContent = Get-Content $chatPath -Raw

# Pastikan pengecekan null di sendFile dan toggleRecording
if ($chatContent -notmatch "getExternalFilesDir\(null\) \?: return") {
    # Ganti metode sendFile untuk validasi
    $chatContent = $chatContent -replace "private fun sendFile\(uri: Uri\) \{", 'private fun sendFile(uri: Uri) {
        if (getExternalFilesDir(null) == null) {
            Toast.makeText(this, "Penyimpanan tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }'
    # toggleRecording juga dicek
    $chatContent = $chatContent -replace 'val dir = getExternalFilesDir\(null\)', 'val dir = getExternalFilesDir(null) ?: run { Toast.makeText(this, "Penyimpanan tidak siap", Toast.LENGTH_SHORT).show(); return }'
}

Write-FileWithoutBOM $chatPath $chatContent

# 3. Build & Install
.\gradlew assembleDebug
if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "FileServer sekarang aktif. Kirim file lagi." -ForegroundColor Green
} else {
    Write-Host "Build gagal" -ForegroundColor Red
}