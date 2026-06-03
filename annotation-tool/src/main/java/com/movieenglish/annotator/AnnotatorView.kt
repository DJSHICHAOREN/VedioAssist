package com.movieenglish.annotator

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class AnnotatorView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
    }
    private val cornerPaint = Paint().apply {
        color = 0xFFEF4444.toInt(); style = Paint.Style.FILL
    }
    private val handleRadius = 15f

    private var imageBitmap: Bitmap? = null
    private var boxRect: RectF? = null
    private var isDrawing = false
    private var startX = 0f; private var startY = 0f
    private var activeHandle: Int = -1
    private var lastTouchX = 0f; private var lastTouchY = 0f

    var onBoxChanged: ((RectF?) -> Unit)? = null

    fun setImage(bitmap: Bitmap) { imageBitmap = bitmap; boxRect = null; invalidate() }
    fun getBoxRect(): RectF? = boxRect
    fun setBoxRect(rect: RectF) { boxRect = rect; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        imageBitmap?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
        boxRect?.let { rect ->
            canvas.drawRect(rect, boxPaint)
            drawHandle(canvas, rect.left, rect.top)
            drawHandle(canvas, rect.right, rect.top)
            drawHandle(canvas, rect.left, rect.bottom)
            drawHandle(canvas, rect.right, rect.bottom)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = findHandle(x, y)
                if (activeHandle >= 0) { lastTouchX = x; lastTouchY = y }
                else { isDrawing = true; startX = x; startY = y; boxRect = null }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle >= 0) {
                    val dx = x - lastTouchX; val dy = y - lastTouchY
                    moveHandle(activeHandle, dx, dy)
                    lastTouchX = x; lastTouchY = y
                    invalidate()
                } else if (isDrawing) {
                    boxRect = RectF(minOf(startX, x), minOf(startY, y), maxOf(startX, x), maxOf(startY, y))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> { isDrawing = false; activeHandle = -1; onBoxChanged?.invoke(boxRect) }
        }
        return true
    }

    private fun findHandle(x: Float, y: Float): Int {
        val rect = boxRect ?: return -1
        val handles = listOf(rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom)
        return handles.indexOfFirst { (hx, hy) -> hypot((x - hx).toDouble(), (y - hy).toDouble()) < handleRadius * 2 }
    }

    private fun moveHandle(handle: Int, dx: Float, dy: Float) {
        val rect = boxRect ?: return
        when (handle) { 0 -> { rect.left += dx; rect.top += dy }; 1 -> { rect.right += dx; rect.top += dy }; 2 -> { rect.left += dx; rect.bottom += dy }; 3 -> { rect.right += dx; rect.bottom += dy } }
        rect.sort()
    }
}
