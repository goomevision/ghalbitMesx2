package com.ghalbitnet.meshx2.access

object JoinCommunityPage {

    fun render(
        gatewayUrl: String,
        proxyHost: String,
        proxyPort: Int,
        portalPort: Int,
        status: NetworkAccessPolicy.AuthStatus,
        detail: String
    ): String {
        val statusLabel =
            when (status) {
                NetworkAccessPolicy.AuthStatus.AUTHORIZED -> "AUTHORIZED"
                NetworkAccessPolicy.AuthStatus.AUTH_PENDING -> "AUTH_PENDING"
                NetworkAccessPolicy.AuthStatus.UNAUTHORIZED -> "UNAUTHORIZED"
                NetworkAccessPolicy.AuthStatus.EXPIRED -> "EXPIRED"
                NetworkAccessPolicy.AuthStatus.BLOCKED -> "BLOCKED"
                NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH -> "UNKNOWN_NO_HELLO_AUTH"
                NetworkAccessPolicy.AuthStatus.UNKNOWN_DEVICE -> "UNKNOWN_DEVICE"
            }

        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>GhalbitMesh X2 Community Access</title>
              <style>
                body { font-family: sans-serif; background:#08111b; color:#edf3fb; margin:0; padding:24px; }
                .wrap { max-width:720px; margin:0 auto; }
                .panel { background:#0f1d2d; border:1px solid #22364f; border-radius:10px; padding:20px; margin-bottom:16px; }
                h1 { margin:0 0 10px 0; font-size:28px; }
                h2 { font-size:18px; margin:0 0 10px 0; }
                .status { display:inline-block; padding:6px 10px; background:#16324d; border-radius:999px; font-weight:700; }
                a.button { display:inline-block; margin-top:12px; background:#1d8f57; color:white; padding:10px 14px; text-decoration:none; border-radius:8px; }
                code { background:#122131; padding:2px 5px; border-radius:4px; }
                ul { padding-left:18px; }
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="panel">
                  <h1>GhalbitMesh X2</h1>
                  <p>Jaringan komunitas yang mensyaratkan aplikasi resmi, node identity valid, dan akses handshake yang sah.</p>
                  <div class="status">Status: $statusLabel</div>
                  <p>$detail</p>
                </div>
                <div class="panel">
                  <h2>Syarat bergabung</h2>
                  <ul>
                    <li>Wajib install aplikasi GhalbitMesh X2.</li>
                    <li>Wajib punya node identity yang sah.</li>
                    <li>Wajib mengirim HELLO_AUTH yang valid.</li>
                    <li>Perangkat tanpa aplikasi tidak dianggap peserta jaringan.</li>
                  </ul>
                </div>
                <div class="panel">
                  <h2>Cara lanjut</h2>
                  <p>Jika kamu belum punya aplikasi, unduh dulu lalu buka kembali jaringan ini dari dalam GhalbitMesh X2.</p>
                  <p>Untuk memakai internet komunitas, install GhalbitMesh X2 atau gunakan konfigurasi proxy Ghalbit.</p>
                  <a class="button" href="https://ghalbit.example/download">Unduh aplikasi GhalbitMesh X2</a>
                  <p>Proxy host: <code>$proxyHost</code></p>
                  <p>Proxy port: <code>$proxyPort</code></p>
                  <p>Portal port: <code>$portalPort</code></p>
                  <p>Fallback manual: buka <code>$gatewayUrl</code> dari browser perangkat yang terhubung.</p>
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
