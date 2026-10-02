package com.safemode.llconnect.data.scan

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

/**
 * Finds the largest document-like quadrilateral in a photo and flattens it with a
 * perspective transform, giving the "scanned receipt" look. Runs entirely on-device via
 * OpenCV, with no Google Play Services dependency.
 */
object DocumentDetector {

    /** True once OpenCV's native library has loaded. Checked before any detection. */
    val available: Boolean by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    /**
     * Detects a document in [source] and returns a perspective-corrected copy. Returns null
     * when OpenCV is unavailable or no confident quad is found, so the caller can fall back to
     * the original photo.
     */
    fun detectAndFlatten(source: Bitmap): Bitmap? {
        if (!available) return null
        val rgba = Mat()
        val safeSource = if (source.config == Bitmap.Config.ARGB_8888) source
        else source.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(safeSource, rgba)
        try {
            val quad = findDocumentQuad(rgba) ?: return null
            return warpToBitmap(rgba, quad)
        } finally {
            rgba.release()
        }
    }

    /** Locates the best 4-corner convex contour, returned as full-resolution corner points. */
    private fun findDocumentQuad(rgba: Mat): Array<Point>? {
        // Detect on a downscaled copy for speed and noise tolerance, then scale points back up.
        val longEdge = maxOf(rgba.width(), rgba.height())
        val scale = if (longEdge > 800) 800.0 / longEdge else 1.0
        val small = Mat()
        Imgproc.resize(rgba, small, Size(), scale, scale, Imgproc.INTER_AREA)

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        Imgproc.dilate(
            edges, edges,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
        )

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            edges, contours, Mat(),
            Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
        )

        val smallArea = small.width().toDouble() * small.height()
        var best: Array<Point>? = null
        var bestArea = 0.0
        for (contour in contours) {
            val c2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                val area = Imgproc.contourArea(approx)
                // Require the quad to cover a meaningful share of the frame to avoid latching
                // onto small rectangles (logos, text blocks) instead of the document edge.
                if (area > bestArea && area > 0.2 * smallArea) {
                    bestArea = area
                    best = approx.toArray()
                }
            }
            c2f.release()
            approx.release()
        }
        gray.release(); edges.release(); small.release()

        return best?.map { Point(it.x / scale, it.y / scale) }?.toTypedArray()
    }

    /** Warps the quad to a front-on rectangle sized from its own edge lengths. */
    private fun warpToBitmap(rgba: Mat, quad: Array<Point>): Bitmap {
        val (tl, tr, br, bl) = orderCorners(quad)
        val widthTop = dist(tl, tr)
        val widthBottom = dist(bl, br)
        val heightLeft = dist(tl, bl)
        val heightRight = dist(tr, br)
        val outW = maxOf(widthTop, widthBottom).toInt().coerceAtLeast(1)
        val outH = maxOf(heightLeft, heightRight).toInt().coerceAtLeast(1)

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0),
            Point(0.0, outH - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val dst = Mat()
        Imgproc.warpPerspective(rgba, dst, transform, Size(outW.toDouble(), outH.toDouble()))

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, out)
        srcPts.release(); dstPts.release(); transform.release(); dst.release()
        return out
    }

    /** Orders four corners as top-left, top-right, bottom-right, bottom-left. */
    private fun orderCorners(pts: Array<Point>): Array<Point> {
        val bySum = pts.sortedBy { it.x + it.y }
        val tl = bySum.first()
        val br = bySum.last()
        val byDiff = pts.sortedBy { it.y - it.x }
        val tr = byDiff.first()
        val bl = byDiff.last()
        return arrayOf(tl, tr, br, bl)
    }

    private fun dist(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
