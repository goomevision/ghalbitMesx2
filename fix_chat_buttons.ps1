# fix_chat_buttons.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Memperbaiki tombol chat..." -ForegroundColor Cyan

# Tulis ulang activity_chat.xml dengan tombol rapi
Write-FileWithoutBOM "app\src\main\res\layout\activity_chat.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#ECE5DD">

    <!-- Header -->
    <TextView
        android:id="@+id/tvChatTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="18sp"
        android:textColor="#FFFFFF"
        android:background="#075E54"
        android:padding="14dp"
        android:textStyle="bold"
        android:gravity="center"/>

    <!-- Daftar chat -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerChat"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="8dp"/>

    <!-- Area input -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#FFFFFF"
        android:padding="6dp"
        android:gravity="center_vertical">

        <!-- Tombol Lampiran -->
        <ImageButton
            android:id="@+id/btnAttach"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_menu_attachment"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Lampiran"
            android:layout_marginEnd="4dp"/>

        <!-- Input teks -->
        <EditText
            android:id="@+id/edtMessage"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Ketik pesan..."
            android:background="@drawable/bg_message_input"
            android:padding="10dp"/>

        <!-- Tombol Rekam -->
        <ImageButton
            android:id="@+id/btnRecord"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_btn_speak_now"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Rekam"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"/>

        <!-- Tombol Kirim -->
        <ImageButton
            android:id="@+id/btnSend"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_menu_send"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Kirim"
            android:layout_marginEnd="4dp"/>

        <!-- Tombol Telepon (nonaktif) -->
        <ImageButton
            android:id="@+id/btnCall"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_menu_call"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Telepon"/>
    </LinearLayout>
</LinearLayout>
'@

# Tambahkan background untuk EditText (rounded)
Write-FileWithoutBOM "app\src\main\res\drawable\bg_message_input.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF"/>
    <stroke android:width="1dp" android:color="#DDDDDD"/>
    <corners android:radius="20dp"/>
</shape>
'@

# Update ChatActivity: ubah findViewById jadi ImageButton (bukan Button)
$chatPath = "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt"
$chatContent = Get-Content $chatPath -Raw

# Ganti import Button dengan import ImageButton
$chatContent = $chatContent -replace "import android.widget.Button", "import android.widget.ImageButton"

# Ganti tipe variabel
$chatContent = $chatContent -replace "private lateinit var btnSend: Button", "private lateinit var btnSend: ImageButton"
$chatContent = $chatContent -replace "private lateinit var btnAttach: Button", "private lateinit var btnAttach: ImageButton"
$chatContent = $chatContent -replace "private lateinit var btnRecord: Button", "private lateinit var btnRecord: ImageButton"
$chatContent = $chatContent -replace "private lateinit var btnCall: Button", "private lateinit var btnCall: ImageButton"

# Update teks btnRecord agar tetap bisa berubah (gunakan tag atau contentDescription)
$chatContent = $chatContent -replace 'btnRecord.text = "Rekam"', 'btnRecord.contentDescription = "Rekam"'
$chatContent = $chatContent -replace 'btnRecord.text = "STOP"', 'btnRecord.contentDescription = "STOP"'

Write-FileWithoutBOM $chatPath $chatContent

# Build & Install
Write-Host "Build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses, menginstal..." -ForegroundColor Green
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "Tombol chat sekarang rapi dan berfungsi." -ForegroundColor Cyan
} else {
    Write-Host "Build gagal." -ForegroundColor Red
}