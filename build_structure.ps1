$folders = @(
    "app",
    "app\src",
    "app\src\main",
    "app\src\main\java",
    "app\src\main\java\com",
    "app\src\main\java\com\ghalbitnet",
    "app\src\main\java\com\ghalbitnet\meshx2",
    "app\src\main\java\com\ghalbitnet\meshx2\core",
    "app\src\main\java\com\ghalbitnet\meshx2\core\utils",
    "app\src\main\java\com\ghalbitnet\meshx2\core\network",
    "app\src\main\java\com\ghalbitnet\meshx2\wireguard"
)

foreach ($folder in $folders) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    Write-Host "Created: $folder"
}