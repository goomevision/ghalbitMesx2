package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import java.io.File

class ImageViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_TITLE = "title"

        fun createIntent(
            context: Context,
            filePath: String,
            title: String
        ): Intent {
            return Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val filePath =
            intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val title =
            intent.getStringExtra(EXTRA_TITLE).orEmpty()

        val btnClose =
            findViewById<ImageButton>(R.id.btnCloseImage)
        val txtTitle =
            findViewById<TextView>(R.id.txtImageTitle)
        val imageView =
            findViewById<ImageView>(R.id.ivFullImage)

        txtTitle.text = title
        btnClose.setOnClickListener { finish() }

        val file = File(filePath)
        if (!file.exists()) {
            finish()
            return
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            finish()
            return
        }

        imageView.setImageBitmap(bitmap)
    }
}
