package com.safemode.llconnect.data.scan

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.acos
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

    /**
     * Detects the document quad in [source] and returns its ordered corners
     * (top-left, top-right, bottom-right, bottom-left) as [x0,y0,x1,y1,x2,y2,x3,y3] in the
     * bitmap's coordinate space, or null when none is found. Used for the live preview overlay.
     */
    fun detectQuad(source: Bitmap): FloatArray? {
        if (!available) return null
        val rgba = Mat()
        val safeSource = if (source.config == Bitmap.Config.ARGB_8888) source
        else source.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(safeSource, rgba)
        try {
            val quad = findDocumentQuad(rgba) ?: return null
            val ordered = orderCorners(quad)
            return FloatArray(8) { i ->
                if (i % 2 == 0) ordered[i / 2].x.toFloat() else ordered[i / 2].y.toFloat()
            }
        } finally {
            rgba.release()
        }
    }

    /**
     * Flattens [source] using a caller-supplied set of already-ordered corners (as produced by
     * [detectQuad], scaled into [source]'s coordinate space). Returns null if OpenCV is
     * unavailable or the corner array is malformed.
     */
    fun flattenWithQuad(source: Bitmap, orderedQuad: FloatArray): Bitmap? {
        if (!available || orderedQuad.size != 8) return null
        val rgba = Mat()
        val safeSource = if (source.config == Bitmap.Config.ARGB_8888) source
        else source.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(safeSource, rgba)
        try {
            val ordered = Array(4) { Point(orderedQuad[it * 2].toDouble(), orderedQuad[it * 2 + 1].toDouble()) }
            return warpOrdered(rgba, ordered)
        } finally {
            rgba.release()
        }
    }

    /** A candidate document outline found by one of the detection passes. */
    private class Candidate(val pass: String, val corners: Array<Point>, val score: Double)

    /**
     * Test-only hook: when set, receives a line for every candidate outline considered (accepted
     * or rejected, and why) plus the final pick. Null in the app, so detection pays nothing for it.
     */
    @VisibleForTesting
    internal var trace: ((String) -> Unit)? = null

    /** Test-only hook: when set, receives the edge map that candidates' sides are checked against. */
    @VisibleForTesting
    internal var traceEdges: ((Bitmap) -> Unit)? = null

    /**
     * Locates the most document-like quadrilateral, returned as full-resolution corner points.
     *
     * Receipts are hard for a single edge pass: white paper on a light counter has faint edges,
     * paper on wood can match the surface's brightness, and curled or torn edges rarely simplify
     * to exactly four points. So candidates are gathered from several views of the image, each
     * outline is reduced to its convex hull and fitted to a quad (falling back to the minimum
     * bounding rectangle when the edge is ragged).
     *
     * The brightness and color views can merge the paper with a similar-looking surface around it
     * (light pants, a pale seat), so every candidate must also have a real edge along all four
     * sides and must not run off the frame. The winner is the largest candidate weighted by how
     * rectangular it is and how strongly its sides are backed by edges.
     */
    private fun findDocumentQuad(rgba: Mat): Array<Point>? {
        // Detect on a downscaled copy for speed and noise tolerance, then scale points back up.
        val longEdge = maxOf(rgba.width(), rgba.height())
        val scale = if (longEdge > DETECT_EDGE) DETECT_EDGE.toDouble() / longEdge else 1.0
        val mats = ArrayList<Mat>()
        fun <T : Mat> T.track(): T = also { mats += it }
        try {
            val small = Mat().track()
            Imgproc.resize(rgba, small, Size(), scale, scale, Imgproc.INTER_AREA)
            val width = small.width()
            val height = small.height()

            val gray = Mat().track()
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            // 1. Edges at fixed thresholds (the original detector, so nothing it found is lost).
            val edges = Mat().track()
            Imgproc.Canny(gray, edges, 50.0, 150.0)

            // 2. Brightness: receipt paper is usually lighter than the surface under it.
            val bright = Mat().track()
            Imgproc.threshold(gray, bright, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            cleanMask(bright, mats)

            // 3. Faint edges: equalize local contrast first (so white paper on a white counter, in
            //    soft focus, still has a usable edge), then run a more sensitive edge pass.
            val equalized = Mat().track()
            Imgproc.createCLAHE(CLAHE_CLIP, Size(8.0, 8.0)).apply(gray, equalized)
            val adaptive = Mat().track()
            Imgproc.Canny(equalized, adaptive, FAINT_EDGE_LOW, FAINT_EDGE_HIGH)

            // 4. Low saturation: white paper on a colored surface of similar brightness (wood,
            //    a car seat, a dashboard).
            val rgb = Mat().track()
            Imgproc.cvtColor(small, rgb, Imgproc.COLOR_RGBA2RGB)
            val hsv = Mat().track()
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val saturation = Mat().track()
            Core.extractChannel(hsv, saturation, 1)
            Imgproc.GaussianBlur(saturation, saturation, Size(5.0, 5.0), 0.0)
            val pale = Mat().track()
            Imgproc.threshold(
                saturation, pale, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU,
            )
            cleanMask(pale, mats)

            // Every candidate's sides are checked against the raw edges from both edge passes
            // (see edgeSupport, which also searches a few pixels to either side).
            val support = Mat().track()
            Core.bitwise_or(edges, adaptive, support)
            val thicken = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)).track()
            Imgproc.dilate(support, support, thicken)
            val edgeMap = EdgeMap(width, height, ByteArray(width * height).also { support.get(0, 0, it) })
            traceEdges?.let { sink ->
                val view = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(support, view)
                sink(view)
            }

            closeGaps(edges, mats)
            closeGaps(adaptive, mats)

            val candidates = ArrayList<Candidate>()
            collect("edges", edges, Imgproc.RETR_LIST, solid = false, edgeMap, candidates)
            collect("faint", adaptive, Imgproc.RETR_LIST, solid = false, edgeMap, candidates)
            collect("bright", bright, Imgproc.RETR_EXTERNAL, solid = true, edgeMap, candidates)
            collect("pale", pale, Imgproc.RETR_EXTERNAL, solid = true, edgeMap, candidates)

            val best = candidates.maxByOrNull { it.score }
            trace?.invoke("picked: ${best?.let { "%s score=%.0f".format(it.pass, it.score) } ?: "none"}")
            if (best == null) return null
            return best.corners.map { Point(it.x / scale, it.y / scale) }.toTypedArray()
        } finally {
            mats.forEach { it.release() }
        }
    }

    /** Joins small breaks in an edge map (a soft fold or shadow along one side of the paper). */
    private fun closeGaps(edges: Mat, mats: MutableList<Mat>) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)).also { mats += it }
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.dilate(edges, edges, kernel)
    }

    /** Removes speckle from a mask and smooths its outline. */
    private fun cleanMask(mask: Mat, mats: MutableList<Mat>) {
        val small = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)).also { mats += it }
        val large = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)).also { mats += it }
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, small)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, large)
    }

    /** The thickened edge pixels of the frame, copied out once so sides can be sampled cheaply. */
    private class EdgeMap(val width: Int, val height: Int, private val pixels: ByteArray) {
        fun isEdge(x: Int, y: Int): Boolean =
            pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)].toInt() != 0
    }

    /**
     * Finds contours in [binary] and adds every one that fits a plausible document quad. [solid]
     * marks a filled mask (as opposed to an edge map), whose contour area is the region's real area.
     */
    private fun collect(
        pass: String,
        binary: Mat,
        mode: Int,
        solid: Boolean,
        edgeMap: EdgeMap,
        out: MutableList<Candidate>,
    ) {
        // While tracing, let somewhat smaller outlines through so near misses show up in the log.
        val minArea = (if (trace != null) TRACE_MIN_AREA_FRACTION else MIN_AREA_FRACTION) *
            edgeMap.width * edgeMap.height
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        for (contour in contours) {
            try {
                // Cheap size check first; most contours are text and texture.
                if (Imgproc.boundingRect(contour).area() < minArea) continue
                fitQuad(pass, contour, solid, edgeMap)?.let(out::add)
            } finally {
                contour.release()
            }
        }
    }

    /**
     * Fits a document quad to [contour], or null if it isn't document-like. Works on the convex
     * hull so a broken or ragged edge still yields the paper's outline.
     */
    private fun fitQuad(pass: String, contour: MatOfPoint, solid: Boolean, edgeMap: EdgeMap): Candidate? {
        val width = edgeMap.width
        val height = edgeMap.height
        val frameArea = width.toDouble() * height
        val points = contour.toArray()
        val hullIndices = MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val hull = MatOfPoint2f(*hullIndices.toArray().map { points[it] }.toTypedArray())
        hullIndices.release()
        val bounds = Imgproc.boundingRect(contour)
        fun reject(reason: String): Candidate? {
            trace?.invoke(
                "%s at (%d,%d %dx%d): %s".format(pass, bounds.x, bounds.y, bounds.width, bounds.height, reason),
            )
            return null
        }
        try {
            val hullArea = Imgproc.contourArea(hull)
            if (hullArea < MIN_AREA_FRACTION * frameArea) {
                return reject("too small (%.1f%% of frame)".format(100 * hullArea / frameArea))
            }

            // Simplify progressively harder until exactly four corners remain.
            val perimeter = Imgproc.arcLength(hull, true)
            var corners: Array<Point>? = null
            val approx = MatOfPoint2f()
            for (epsilon in APPROX_EPSILONS) {
                Imgproc.approxPolyDP(hull, approx, epsilon * perimeter, true)
                if (approx.total() == 4L) {
                    corners = approx.toArray()
                    break
                }
            }
            approx.release()
            // A crinkled receipt may never reduce to four points; use its bounding rectangle, but
            // only if the shape fills it closely (see MIN_FILL_RECT below).
            val fromRect = corners == null
            if (corners == null) {
                corners = arrayOfNulls<Point>(4).also { Imgproc.minAreaRect(hull).points(it) }
                    .requireNoNulls()
            }
            corners = corners.map {
                Point(it.x.coerceIn(0.0, width - 1.0), it.y.coerceIn(0.0, height - 1.0))
            }.toTypedArray()

            val quadMat = MatOfPoint2f(*corners)
            val quadArea = Imgproc.contourArea(quadMat)
            quadMat.release()
            val areaPct = 100 * quadArea / frameArea
            if (quadArea < MIN_AREA_FRACTION * frameArea) return reject("quad too small (%.1f%%)".format(areaPct))
            // Almost the whole frame is the background or the frame border, not a document.
            if (quadArea > MAX_AREA_FRACTION * frameArea) return reject("covers the frame (%.1f%%)".format(areaPct))
            // A corner on the frame border means the shape continues out of view: that's the
            // surface the paper is on (pants, a seat), not the paper.
            if (touchesFrameBorder(corners, width, height)) return reject("runs off the frame")

            // How much of the quad the shape actually fills; low means it isn't rectangular. A
            // filled mask is judged by its real area, so a lumpy region (paper merged with the
            // surface beside it) scores low even if its hull happens to look rectangular.
            val shapeArea = if (solid) Imgproc.contourArea(contour) else hullArea
            val fill = (shapeArea / quadArea).coerceAtMost(1.0)
            if (fill < if (fromRect) MIN_FILL_RECT else MIN_FILL) {
                return reject("not rectangular (fill %.2f%s)".format(fill, if (fromRect) ", rect fallback" else ""))
            }
            val convexMat = MatOfPoint(*corners)
            val convex = Imgproc.isContourConvex(convexMat)
            convexMat.release()
            if (!convex) return reject("not convex")
            if (!hasSaneCorners(corners, maxOf(width, height))) return reject("skewed corners or tiny side")

            // Paper has a crisp edge all the way around; require it on every side. Receipts are
            // torn off the roll, so on a receipt-shaped quad the two short ends may be jagged:
            // they're checked against a band deep enough for a tear, with a lower bar. The long
            // sides still need crisp edges, which is what tells paper from fabric.
            val sides = sideSupport(corners, edgeMap)
            val torn = tornEnds(corners)
            if (torn != null) {
                val tornSides = sideSupport(corners, edgeMap, TORN_RADIUS_FRACTION)
                for (i in torn) {
                    // Scale the torn end onto the same 0-1 bar as the long sides.
                    sides[i] = (tornSides[i] / MIN_TORN_SUPPORT * MIN_EDGE_SUPPORT).coerceAtMost(1.0)
                }
            }
            val support = sides.min()
            if (support < MIN_EDGE_SUPPORT) {
                return reject(
                    "weak edges (support %.2f, sides %s) quad=%s".format(
                        support,
                        sides.joinToString("/") { "%.2f".format(it) },
                        corners.joinToString(",") { "%.0f,%.0f".format(it.x, it.y) },
                    ),
                )
            }

            val candidate = Candidate(pass, corners, quadArea * fill * support * support)
            trace?.invoke(
                "%s at (%d,%d %dx%d): ACCEPTED area %.1f%% fill %.2f support %.2f score %.0f".format(
                    pass, bounds.x, bounds.y, bounds.width, bounds.height, areaPct, fill, support, candidate.score,
                ),
            )
            return candidate
        } finally {
            hull.release()
        }
    }

    /**
     * True if any corner of the quad sits on the edge of the frame. That's what a shape the frame
     * cut off looks like (its corners get clipped onto the border), such as the surface under the
     * paper running out of view. A receipt being framed never has a corner exactly on the border.
     */
    private fun touchesFrameBorder(corners: Array<Point>, width: Int, height: Int): Boolean {
        val margin = BORDER_MARGIN_FRACTION * maxOf(width, height)
        return corners.any { p ->
            p.x <= margin || p.x >= width - 1 - margin || p.y <= margin || p.y >= height - 1 - margin
        }
    }

    /**
     * How well each of the quad's sides lines up with real edges: the share of points sampled along
     * the side that have an edge nearby. Callers use the weakest side, so one fabricated side sinks
     * the candidate.
     *
     * Each sample searches a few pixels to either side of the line, because the fitted quad never
     * sits exactly on the paper's edge: outlines are traced around edges that were thickened to
     * join gaps, and a mask's boundary drifts with blur. Points near the corners are skipped,
     * since rounded or curled corners rarely sit on the line.
     */
    private fun sideSupport(
        corners: Array<Point>,
        edgeMap: EdgeMap,
        radiusFraction: Double = SUPPORT_RADIUS_FRACTION,
    ): DoubleArray {
        val radius = maxOf(2, (radiusFraction * maxOf(edgeMap.width, edgeMap.height)).toInt())
        return DoubleArray(4) { i ->
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val length = dist(a, b)
            if (length < 1.0) return@DoubleArray 0.0
            // Unit normal to the side.
            val nx = -(b.y - a.y) / length
            val ny = (b.x - a.x) / length
            var hits = 0
            for (s in 0 until EDGE_SAMPLES) {
                val t = 0.1 + 0.8 * s / (EDGE_SAMPLES - 1)
                val px = a.x + (b.x - a.x) * t
                val py = a.y + (b.y - a.y) * t
                if ((-radius..radius).any { k -> edgeMap.isEdge((px + nx * k).toInt(), (py + ny * k).toInt()) }) {
                    hits++
                }
            }
            hits.toDouble() / EDGE_SAMPLES
        }
    }

    /**
     * For a receipt-shaped quad (clearly longer than wide), the indices of its two short ends
     * (where a receipt is torn off the roll); null for squarer shapes, which get no allowance.
     */
    private fun tornEnds(corners: Array<Point>): IntArray? {
        // Side i runs from corner i to corner i+1; sides 0/2 and 1/3 are opposite pairs.
        val pairA = (dist(corners[0], corners[1]) + dist(corners[2], corners[3])) / 2
        val pairB = (dist(corners[1], corners[2]) + dist(corners[3], corners[0])) / 2
        return when {
            pairB >= pairA * RECEIPT_ASPECT -> intArrayOf(0, 2)
            pairA >= pairB * RECEIPT_ASPECT -> intArrayOf(1, 3)
            else -> null
        }
    }

    /** Rejects slivers and badly skewed shapes: every angle near 90 degrees, no tiny sides. */
    private fun hasSaneCorners(corners: Array<Point>, longEdge: Int): Boolean {
        for (i in corners.indices) {
            val prev = corners[(i + 3) % 4]
            val here = corners[i]
            val next = corners[(i + 1) % 4]
            if (dist(here, next) < MIN_SIDE_FRACTION * longEdge) return false
            val ax = prev.x - here.x
            val ay = prev.y - here.y
            val bx = next.x - here.x
            val by = next.y - here.y
            val cos = (ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by))
            val degrees = Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)))
            if (degrees < MIN_CORNER_DEGREES || degrees > MAX_CORNER_DEGREES) return false
        }
        return true
    }

    /** Orders the quad's corners, then warps it to a front-on rectangle. */
    private fun warpToBitmap(rgba: Mat, quad: Array<Point>): Bitmap =
        warpOrdered(rgba, orderCorners(quad))

    /** Warps already-ordered corners (TL, TR, BR, BL) to a rectangle sized from their edge lengths. */
    private fun warpOrdered(rgba: Mat, ordered: Array<Point>): Bitmap {
        val (tl, tr, br, bl) = ordered
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

    /** Long edge of the downscaled copy detection runs on. */
    private const val DETECT_EDGE = 640

    /** Contrast limit for the local equalization that precedes the faint-edge pass. */
    private const val CLAHE_CLIP = 3.0

    /** Canny thresholds for the faint-edge pass, run on the contrast-equalized image. */
    private const val FAINT_EDGE_LOW = 20.0
    private const val FAINT_EDGE_HIGH = 60.0

    /**
     * Smallest share of the frame a document may cover. A narrow receipt held at a comfortable
     * distance (say, on a lap) can cover under 7%; rectangularity and edge checks keep smaller
     * clutter out.
     */
    private const val MIN_AREA_FRACTION = 0.04

    /** Largest share; anything bigger is the background or the frame itself. */
    private const val MAX_AREA_FRACTION = 0.95

    /** How fully the outline must fill its quad to count as rectangular. */
    private const val MIN_FILL = 0.85

    /** Stricter fill for the bounding-rectangle fallback, which will box any blob otherwise. */
    private const val MIN_FILL_RECT = 0.92

    /**
     * Share of sampled points on the weakest side that must have an edge nearby. Measured: real
     * and generated receipts score 0.88-1.0 (a thumb over one edge is the low end); outlines traced
     * along fabric (a pant leg) score around 0.65.
     */
    private const val MIN_EDGE_SUPPORT = 0.8

    /** Points sampled along each side when measuring edge support. */
    private const val EDGE_SAMPLES = 24

    /** How far to either side of a quad's side to look for its edge (share of the long edge). */
    private const val SUPPORT_RADIUS_FRACTION = 0.012

    /** Search band for a receipt's torn ends, deep enough to cover a ragged tear. */
    private const val TORN_RADIUS_FRACTION = 0.035

    /** Bar a torn end must clear within its wider band (long sides keep [MIN_EDGE_SUPPORT]). */
    private const val MIN_TORN_SUPPORT = 0.5

    /** Length-to-width ratio from which a quad is treated as a receipt with tearable ends. */
    private const val RECEIPT_ASPECT = 1.4

    /**
     * How close to the frame edge (share of the long edge) a side counts as lying on it. Wide
     * enough to catch shapes the frame cut off (their clipped corners land a few pixels in), narrow
     * enough that a receipt nearly filling the frame still passes.
     */
    private const val BORDER_MARGIN_FRACTION = 0.01

    /** Smallest outline reported by [trace] (tests only), to surface near misses. */
    private const val TRACE_MIN_AREA_FRACTION = 0.02

    /** Shortest allowed side, as a share of the frame's long edge. */
    private const val MIN_SIDE_FRACTION = 0.05

    private const val MIN_CORNER_DEGREES = 50.0
    private const val MAX_CORNER_DEGREES = 130.0

    /** Polygon simplification tolerances (share of the perimeter), tried in order. */
    private val APPROX_EPSILONS = doubleArrayOf(0.02, 0.03, 0.04, 0.05)
}
