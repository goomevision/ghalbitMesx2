Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " GHALBIT MESH X2 PROJECT DIAGNOSTICS"
Write-Host "========================================" -ForegroundColor Cyan

$checks = @(
    "app\src\main\AndroidManifest.xml",
    "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\chat\ContactListActivity.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\monitor\NetworkActivity.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\core\runtime\MeshRuntimeState.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\core\runtime\LightweightMeshSupervisor.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\core\recovery\MeshAutoRecovery.kt",
    "app\src\main\java\com\ghalbitnet\meshx2\core\health\MeshHealthReporter.kt"
)

foreach ($file in $checks) {
    if (Test-Path $file) {
        Write-Host "[OK] $file" -ForegroundColor Green
    } else {
        Write-Host "[MISSING] $file" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Checking duplicate app_name..." -ForegroundColor Yellow
findstr /S /I "string name=`"app_name`"" app\src\main\res\values\*.xml

Write-Host ""
Write-Host "Checking activities in manifest..." -ForegroundColor Yellow
findstr /I "MainActivity ContactListActivity ChatActivity NetworkActivity" app\src\main\AndroidManifest.xml

Write-Host ""
Write-Host "Checking APK output..." -ForegroundColor Yellow
if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "[OK] APK exists: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
} else {
    Write-Host "[MISSING] APK not found. Build first." -ForegroundColor Red
}

Write-Host ""
Write-Host "Diagnostics complete." -ForegroundColor Cyan
