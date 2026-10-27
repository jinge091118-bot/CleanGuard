package com.cleanguard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the Play feature graphic on the device.
 *
 * The graphic needs the Android type stack for Chinese glyphs, which a
 * host-side rasterizer does not have. Rendering it here keeps the headline
 * and the subheadline identical to how the app draws its own text, and it
 * avoids a font dependency on the build machine.
 *
 * The output is written to the app's external files directory so the capture
 * tool can pull it with adb.
 */
@RunWith(AndroidJUnit4::class)
class FeatureGraphicRenderTest {

    @Test
    fun renderFeatureGraphic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = 1024
        val height = 500

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x17, 0x6B, 0x5B))

        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x8F, 0xDC, 0xCB)
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 60f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subheadline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xCF, 0xE8, 0xE0)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        canvas.drawText("CleanGuard", 64f, 92f, brand)
        canvas.drawText("看懂你的存储占用", 64f, 192f, headline)
        canvas.drawText("只处理你确认的内容", 64f, 264f, subheadline)
        canvas.drawText("不联网 · 不上传 · 全部在本机完成", 64f, 322f, subheadline)

        // A shield mark that matches the launcher icon.
        val shield = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x2C, 0x91, 0x7B)
            style = Paint.Style.FILL
        }
        val shieldPath = Path().apply {
            moveTo(880f, 86f)
            lineTo(958f, 118f)
            lineTo(958f, 224f)
            cubicTo(958f, 284f, 924f, 328f, 880f, 348f)
            cubicTo(836f, 328f, 802f, 284f, 802f, 224f)
            lineTo(802f, 118f)
            close()
        }
        canvas.drawPath(shieldPath, shield)

        val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 15f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val mark = Path().apply {
            moveTo(839f, 220f)
            lineTo(868f, 249f)
            lineTo(923f, 177f)
        }
        canvas.drawPath(mark, check)

        // Write into the public download directory so the file survives the
        // test package uninstall that Gradle performs when the run finishes.
        val directory = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS,
        )
        directory.mkdirs()
        val output = File(directory, "cleanguard-feature-graphic.png")
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()

        assertTrue("The feature graphic was not written.", output.exists() && output.length() > 0)
        println("FEATURE_GRAPHIC_PATH=${output.absolutePath}")
        println("FEATURE_GRAPHIC_BYTES=${output.length()}")
    }
}
