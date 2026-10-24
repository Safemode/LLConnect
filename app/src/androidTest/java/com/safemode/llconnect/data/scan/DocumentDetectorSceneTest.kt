package com.safemode.llconnect.data.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Runs [DocumentDetector] against generated receipt scenes on a device or emulator (OpenCV needs
 * the app's native libraries, so this can't be a JVM unit test).
 *
 * Each scene is drawn with a known receipt outline; the test checks the detected box lands on it,
 * or that nothing is detected when there's no receipt. Every scene is also saved with the expected
 * outline (green) and the detected one (red) under the app's external files dir, `detector-test/`,
 * for a visual check:
 *
 *   adb pull /sdcard/Android/data/com.safemode.llconnect/files/detector-test
 */
@RunWith(AndroidJUnit4::class)
class DocumentDetectorSceneTest {

    private val width = 720
    private val height = 960

    /** A scene plus where its receipt is (TL, TR, BR, BL), or null when it has none. */
    private class Scene(val name: String, val bitmap: Bitmap, val truth: FloatArray?)

    @Test
    fun detectsReceiptsInGeneratedScenes() {
        assertTrue("OpenCV failed to load", DocumentDetector.available)

        val scenes = listOf(
            receiptOn("wood", wood(), rotatedQuad(360f, 470f, 300f, 700f, 7f)),
            receiptOn("khaki_pants", fabric(Color.rgb(201, 184, 150)), rotatedQuad(370f, 480f, 330f, 760f, -5f)),
            receiptOn("gray_pants", fabric(Color.rgb(189, 189, 184)), rotatedQuad(350f, 470f, 320f, 740f, 4f)),
            receiptOn("white_counter", flat(Color.rgb(226, 226, 222), 6), rotatedQuad(360f, 480f, 320f, 720f, -3f), shadow = true),
            receiptOn("fills_frame", flat(Color.rgb(85, 85, 85), 14), rotatedQuad(360f, 480f, 640f, 900f, 1.0f)),
            receiptOn("curled", wood(), rotatedQuad(360f, 480f, 310f, 720f, 6f), curl = 7f),
            receiptOn("thumb_on_edge", fabric(Color.rgb(189, 189, 184)), rotatedQuad(360f, 470f, 320f, 740f, -4f), thumb = true),
            // A small receipt on a lap: a pant leg running off the frame over a light floor, the
            // receipt only ~6% of the frame (a real photo failed this way).
            receiptOn("small_on_lap", lap(), rotatedQuad(400f, 420f, 140f, 330f, 2f)),
            // Torn off the roll: both short ends ragged instead of straight.
            receiptOn("torn_ends", fabric(Color.rgb(201, 184, 150)), rotatedQuad(360f, 480f, 300f, 700f, 3f), torn = true),
            Scene("pants_no_receipt", fabric(Color.rgb(201, 184, 150)), null),
        ).map { Scene(it.name, camera(it.bitmap), it.truth) }

        val outDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "detector-test",
        ).apply { deleteRecursively(); mkdirs() }

        val failures = mutableListOf<String>()
        val report = StringBuilder()
        for (scene in scenes) {
            val log = StringBuilder()
            DocumentDetector.trace = { log.appendLine(it) }
            val detected = try {
                DocumentDetector.detectQuad(scene.bitmap)
            } finally {
                DocumentDetector.trace = null
            }
            File(outDir, "${scene.name}.txt").writeText(log.toString())
            val verdict = when {
                scene.truth == null && detected == null -> "ok (nothing detected)"
                scene.truth == null -> "FAIL detected a box with no receipt"
                detected == null -> "FAIL nothing detected"
                else -> {
                    val error = meanCornerDistance(detected, scene.truth) / hypot(width.toFloat(), height.toFloat())
                    if (error < MAX_CORNER_ERROR) "ok (corner error %.1f%%)".format(error * 100)
                    else "FAIL wrong box (corner error %.1f%%)".format(error * 100)
                }
            }
            if (verdict.startsWith("FAIL")) failures += "${scene.name}: $verdict"
            report.appendLine("${scene.name}: $verdict")
            save(scene, detected, File(outDir, "${scene.name}.png"))
        }
        // Per-frame cost at the live analysis size (the preview stream is about 640x480).
        val frame = Bitmap.createScaledBitmap(scenes.first().bitmap, 480, 640, true)
        repeat(3) { DocumentDetector.detectQuad(frame) } // warm up
        val runs = 20
        val start = System.nanoTime()
        repeat(runs) { DocumentDetector.detectQuad(frame) }
        report.appendLine("avg detection time at 480x640: %.1f ms".format((System.nanoTime() - start) / 1e6 / runs))

        File(outDir, "report.txt").writeText(report.toString())
        assertTrue("Detector failed scenes:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Runs the detector on real photos, for tuning. Push photos (kept out of the repo, since real
     * receipts carry personal data) to the app's external files dir, then run this test:
     *
     *   adb push receipt.jpg /sdcard/Android/data/com.safemode.llconnect/files/detector-photos/
     *
     * Each photo is scaled to the live preview's analysis size first, so results match what the
     * scanner sees. Results (outline image + candidate trace) land in `detector-photos-out/`.
     * Skipped when no photos are present.
     */
    @Test
    fun tracesRealPhotos() {
        val files = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val photos = File(files, "detector-photos").listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            .orEmpty()
        Assume.assumeTrue("No photos in detector-photos/", photos.isNotEmpty())
        val outDir = File(files, "detector-photos-out").apply { deleteRecursively(); mkdirs() }

        for (photo in photos.sortedBy { it.name }) {
            val full = BitmapFactory.decodeFile(photo.absolutePath) ?: continue
            val longEdge = maxOf(full.width, full.height)
            val factor = ANALYSIS_LONG_EDGE.toFloat() / longEdge
            val frame = Bitmap.createScaledBitmap(full, (full.width * factor).toInt(), (full.height * factor).toInt(), true)
            val log = StringBuilder("${photo.name} at ${frame.width}x${frame.height}\n")
            DocumentDetector.trace = { log.appendLine(it) }
            DocumentDetector.traceEdges = { edges ->
                File(outDir, "${photo.nameWithoutExtension}_edges.png").outputStream()
                    .use { edges.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val detected = try {
                DocumentDetector.detectQuad(frame)
            } finally {
                DocumentDetector.trace = null
                DocumentDetector.traceEdges = null
            }
            File(outDir, "${photo.nameWithoutExtension}.txt").writeText(log.toString())
            // Rejected outlines (those that got as far as the edge check) in yellow.
            val rejected = log.lines().mapNotNull { line ->
                line.substringAfter("quad=", "").takeIf { it.isNotEmpty() }
                    ?.split(',')?.map { it.toFloat() }?.toFloatArray()
            }
            save(Scene(photo.name, frame, null), detected, File(outDir, "${photo.nameWithoutExtension}.png"), rejected)
        }
    }

    // ---- Scene building ----

    private fun receiptOn(
        name: String,
        background: Bitmap,
        quad: FloatArray,
        shadow: Boolean = false,
        curl: Float = 0f,
        thumb: Boolean = false,
        torn: Boolean = false,
    ): Scene {
        val canvas = Canvas(background)
        if (shadow) {
            // A soft shadow down and to the right, so one side has even less contrast.
            val shadowPath = quadPath(FloatArray(8) { i -> quad[i] + 10f })
            canvas.drawPath(shadowPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(70, 0, 0, 0)
                maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            })
        }
        val paper = if (torn) tear(receiptBitmap()) else receiptBitmap()
        if (curl == 0f) {
            val matrix = Matrix()
            val src = floatArrayOf(
                0f, 0f, paper.width.toFloat(), 0f,
                paper.width.toFloat(), paper.height.toFloat(), 0f, paper.height.toFloat(),
            )
            matrix.setPolyToPoly(src, 0, quad, 0, 4)
            canvas.drawBitmap(paper, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawBitmapMesh(paper, MESH, MESH, curledMesh(quad, curl), 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (thumb) {
            // A thumb holding the receipt, covering part of its left edge.
            val leftMidX = (quad[0] + quad[6]) / 2
            val leftMidY = (quad[1] + quad[7]) / 2 + 120f
            canvas.drawOval(
                leftMidX - 60f, leftMidY - 45f, leftMidX + 40f, leftMidY + 45f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 160, 130) },
            )
        }
        return Scene(name, background, quad)
    }

    /**
     * What the camera's analysis stream actually delivers: soft focus and light falling off across
     * the frame, rather than crisp, evenly lit pixels.
     */
    private fun camera(scene: Bitmap): Bitmap {
        val soft = Bitmap.createScaledBitmap(
            Bitmap.createScaledBitmap(scene, width / 3, height / 3, true), width, height, true,
        ).copy(Bitmap.Config.ARGB_8888, true)
        Canvas(soft).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.argb(0, 0, 0, 0), Color.argb(90, 0, 0, 0),
                android.graphics.Shader.TileMode.CLAMP,
            )
        })
        return soft
    }

    /** Off-white thermal paper with rows of "text". */
    private fun receiptBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 245, 240))
        val ink = Paint().apply { color = Color.rgb(60, 60, 60) }
        val random = Random(7)
        var y = 40f
        while (y < 660f) {
            val lineWidth = 80f + random.nextFloat() * 180f
            canvas.drawRect(24f, y, 24f + lineWidth, y + 9f, ink)
            if (random.nextBoolean()) canvas.drawRect(230f, y, 276f, y + 9f, ink)
            y += 22f + random.nextFloat() * 10f
        }
        return bitmap
    }

    /** Cuts ragged, torn-off-the-roll edges into the top and bottom of [paper]. */
    private fun tear(paper: Bitmap): Bitmap {
        val w = paper.width.toFloat()
        val h = paper.height.toFloat()
        val random = Random(5)
        val depth = 24f
        val outline = Path().apply {
            moveTo(0f, random.nextFloat() * depth)
            var x = 0f
            while (x < w) {
                x = (x + 8f + random.nextFloat() * 18f).coerceAtMost(w)
                lineTo(x, random.nextFloat() * depth)
            }
            lineTo(w, h - random.nextFloat() * depth)
            while (x > 0f) {
                x = (x - 8f - random.nextFloat() * 18f).coerceAtLeast(0f)
                lineTo(x, h - random.nextFloat() * depth)
            }
            close()
        }
        val out = Bitmap.createBitmap(paper.width, paper.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            clipPath(outline)
            drawBitmap(paper, 0f, 0f, null)
        }
        return out
    }

    /** Grid of mesh vertices across [quad], with the long sides bent by a sine wave. */
    private fun curledMesh(quad: FloatArray, amplitude: Float): FloatArray {
        val verts = FloatArray((MESH + 1) * (MESH + 1) * 2)
        var i = 0
        for (row in 0..MESH) {
            val v = row.toFloat() / MESH
            for (col in 0..MESH) {
                val u = col.toFloat() / MESH
                // Bilinear position inside the quad.
                val topX = quad[0] + (quad[2] - quad[0]) * u
                val topY = quad[1] + (quad[3] - quad[1]) * u
                val botX = quad[6] + (quad[4] - quad[6]) * u
                val botY = quad[7] + (quad[5] - quad[7]) * u
                val x = topX + (botX - topX) * v
                val y = topY + (botY - topY) * v
                verts[i++] = x + amplitude * sin(v * Math.PI.toFloat() * 3f)
                verts[i++] = y
            }
        }
        return verts
    }

    private fun wood(): Bitmap {
        val bitmap = flat(Color.rgb(138, 90, 51), 22)
        val canvas = Canvas(bitmap)
        val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(60, 60, 30, 10)
        }
        val random = Random(3)
        repeat(40) {
            val y = random.nextFloat() * height
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(width * 0.3f, y + 20f, width * 0.6f, y - 20f, width.toFloat(), y + 10f)
            }
            canvas.drawPath(path, grain)
        }
        return bitmap
    }

    /** Woven-looking fabric with soft folds, filling the frame like pants under a receipt. */
    private fun fabric(base: Int): Bitmap {
        val bitmap = flat(base, 12)
        val canvas = Canvas(bitmap)
        val fold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            color = Color.argb(70, 40, 30, 20)
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        val random = Random(11)
        repeat(9) {
            val x = random.nextFloat() * width
            val path = Path().apply {
                moveTo(x, 0f)
                cubicTo(x + 120f, height * 0.3f, x - 140f, height * 0.7f, x + 60f, height.toFloat())
            }
            canvas.drawPath(path, fold)
        }
        // A seam, the sort of straight line pants have.
        canvas.drawLine(width * 0.12f, 0f, width * 0.2f, height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4f
            color = Color.argb(90, 30, 20, 10)
        })
        return bitmap
    }

    /** A light floor with a khaki pant leg crossing it diagonally and running off the bottom. */
    private fun lap(): Bitmap {
        val bitmap = flat(Color.rgb(214, 214, 208), 8)
        val leg = Path().apply {
            moveTo(width * 0.42f, height * 0.12f)
            cubicTo(width * 0.85f, height * 0.18f, width * 0.95f, height * 0.6f, width.toFloat(), height * 0.8f)
            lineTo(width.toFloat(), height.toFloat())
            lineTo(width * 0.1f, height.toFloat())
            cubicTo(width * 0.05f, height * 0.6f, width * 0.15f, height * 0.25f, width * 0.42f, height * 0.12f)
            close()
        }
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.clipPath(leg)
        canvas.drawBitmap(fabric(Color.rgb(176, 156, 126)), 0f, 0f, null)
        canvas.restore()
        return bitmap
    }

    /** A solid color with per-pixel noise, like a camera sensor sees it. */
    private fun flat(color: Int, noise: Int): Bitmap {
        val random = Random(color)
        val pixels = IntArray(width * height) {
            val n = random.nextInt(-noise, noise + 1)
            Color.rgb(
                (Color.red(color) + n).coerceIn(0, 255),
                (Color.green(color) + n).coerceIn(0, 255),
                (Color.blue(color) + n).coerceIn(0, 255),
            )
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
    }

    /** Corners (TL, TR, BR, BL) of a [w] x [h] rectangle centered at ([cx], [cy]), rotated. */
    private fun rotatedQuad(cx: Float, cy: Float, w: Float, h: Float, degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val local = floatArrayOf(-w / 2, -h / 2, w / 2, -h / 2, w / 2, h / 2, -w / 2, h / 2)
        return FloatArray(8) { i ->
            val x = local[i - i % 2]
            val y = local[i - i % 2 + 1]
            if (i % 2 == 0) cx + x * c - y * s else cy + x * s + y * c
        }
    }

    // ---- Output ----

    private fun save(scene: Scene, detected: FloatArray?, file: File, rejected: List<FloatArray> = emptyList()) {
        val out = scene.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
        rejected.forEach { canvas.drawPath(quadPath(it), Paint(stroke).apply { color = Color.YELLOW; strokeWidth = 2f }) }
        scene.truth?.let { canvas.drawPath(quadPath(it), stroke.apply { color = Color.GREEN }) }
        detected?.let { canvas.drawPath(quadPath(it), stroke.apply { color = Color.RED }) }
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun quadPath(q: FloatArray) = Path().apply {
        moveTo(q[0], q[1]); lineTo(q[2], q[3]); lineTo(q[4], q[5]); lineTo(q[6], q[7]); close()
    }

    private fun meanCornerDistance(a: FloatArray, b: FloatArray): Float =
        (0 until 4).map { i -> hypot(a[i * 2] - b[i * 2], a[i * 2 + 1] - b[i * 2 + 1]) }.average().toFloat()

    private companion object {
        /** Detected corners must be within this share of the frame diagonal, on average. */
        const val MAX_CORNER_ERROR = 0.03f
        const val MESH = 12

        /** Long edge of the frames the live preview's analysis stream delivers (640x480). */
        const val ANALYSIS_LONG_EDGE = 640
    }
}
