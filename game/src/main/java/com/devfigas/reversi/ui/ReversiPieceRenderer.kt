package com.devfigas.reversi.ui

import android.graphics.*
import com.devfigas.gridgame.ui.PieceRenderer
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiPiece

class ReversiPieceRenderer : PieceRenderer<ReversiPiece> {

    private val bitmapCache = mutableMapOf<String, Bitmap>()

    override fun getBitmap(piece: ReversiPiece, squareSize: Int): Bitmap? {
        val key = "${piece.color.name}_$squareSize"
        bitmapCache[key]?.let { return it }

        val size = squareSize
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val padding = size * 0.08f
        val radius = (size - padding * 2) / 2f
        val cx = size / 2f
        val cy = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (piece.color == ReversiColor.BLACK) {
            // Black disc with subtle gradient for 3D effect
            paint.shader = RadialGradient(
                cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.5f,
                intArrayOf(Color.rgb(80, 80, 80), Color.rgb(30, 30, 30), Color.rgb(10, 10, 10)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)

            // Highlight
            paint.shader = null
            paint.color = Color.argb(40, 255, 255, 255)
            canvas.drawCircle(cx - radius * 0.2f, cy - radius * 0.2f, radius * 0.35f, paint)
        } else {
            // White disc with gradient
            paint.shader = RadialGradient(
                cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.5f,
                intArrayOf(Color.rgb(255, 255, 255), Color.rgb(230, 230, 230), Color.rgb(200, 200, 200)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)

            // Border
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.02f
            paint.color = Color.rgb(180, 180, 180)
            canvas.drawCircle(cx, cy, radius, paint)

            // Highlight
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(60, 255, 255, 255)
            canvas.drawCircle(cx - radius * 0.2f, cy - radius * 0.2f, radius * 0.35f, paint)
        }

        bitmapCache[key] = bitmap
        return bitmap
    }

    override fun getSymbol(piece: ReversiPiece): String {
        return if (piece.color == ReversiColor.BLACK) "\u25CF" else "\u25CB"
    }
}
