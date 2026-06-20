package com.baroness.app.components.wishlist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

@Composable
fun TrashIcon(size: Dp, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size
        val stroke = Stroke(
            width = 2f * s.width / 24f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val path = Path().apply {
            moveTo(3f * s.width / 24f, 6f * s.height / 24f)
            lineTo(21f * s.width / 24f, 6f * s.height / 24f)
            moveTo(8f * s.width / 24f, 6f * s.height / 24f)
            lineTo(8f * s.width / 24f, 4f * s.height / 24f)
            cubicTo(8f * s.width / 24f, 3f * s.height / 24f, 9f * s.width / 24f, 2f * s.height / 24f, 10f * s.width / 24f, 2f * s.height / 24f)
            lineTo(14f * s.width / 24f, 2f * s.height / 24f)
            cubicTo(15f * s.width / 24f, 2f * s.height / 24f, 16f * s.width / 24f, 3f * s.height / 24f, 16f * s.width / 24f, 4f * s.height / 24f)
            lineTo(16f * s.width / 24f, 6f * s.height / 24f)
            moveTo(19f * s.width / 24f, 6f * s.height / 24f)
            lineTo(19f * s.width / 24f, 20f * s.height / 24f)
            cubicTo(19f * s.width / 24f, 21f * s.height / 24f, 18f * s.width / 24f, 22f * s.height / 24f, 17f * s.width / 24f, 22f * s.height / 24f)
            lineTo(7f * s.width / 24f, 22f * s.height / 24f)
            cubicTo(6f * s.width / 24f, 22f * s.height / 24f, 5f * s.width / 24f, 21f * s.height / 24f, 5f * s.width / 24f, 20f * s.height / 24f)
            lineTo(5f * s.width / 24f, 6f * s.height / 24f)
            close()
            moveTo(10f * s.width / 24f, 11f * s.height / 24f)
            lineTo(10f * s.width / 24f, 17f * s.height / 24f)
            moveTo(14f * s.width / 24f, 11f * s.height / 24f)
            lineTo(14f * s.width / 24f, 17f * s.height / 24f)
        }
        drawPath(path, color = color, style = stroke)
    }
}

@Composable
fun CheckIcon(size: Dp, color: Color = Color(0xFF4CAF50)) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size
        val stroke = Stroke(
            width = 2f * s.width / 24f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val path = Path().apply {
            moveTo(20f * s.width / 24f, 6f * s.height / 24f)
            lineTo(9f * s.width / 24f, 17f * s.height / 24f)
            lineTo(4f * s.width / 24f, 12f * s.height / 24f)
        }
        drawPath(path, color = color, style = stroke)
    }
}

@Composable
fun SmileyIcon(size: Dp, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size
        val stroke = Stroke(
            width = 2f * s.width / 24f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawCircle(
            color = color,
            radius = 10f * s.width / 24f,
            center = Offset(12f * s.width / 24f, 12f * s.height / 24f),
            style = stroke
        )
        val path = Path().apply {
            moveTo(8f * s.width / 24f, 14f * s.height / 24f)
            cubicTo(9.5f * s.width / 24f, 16f * s.height / 24f, 14.5f * s.width / 24f, 16f * s.height / 24f, 16f * s.width / 24f, 14f * s.height / 24f)
        }
        drawPath(path, color = color, style = stroke)
        drawCircle(center = Offset(9f * s.width / 24f, 9f * s.height / 24f), radius = 1.5f * s.width / 24f, color = color)
        drawCircle(center = Offset(15f * s.width / 24f, 9f * s.height / 24f), radius = 1.5f * s.width / 24f, color = color)
    }
}

@Composable
fun DownloadIcon(size: Dp, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size
        val stroke = Stroke(
            width = 2f * s.width / 24f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val path = Path().apply {
            moveTo(21f * s.width / 24f, 15f * s.height / 24f)
            lineTo(21f * s.width / 24f, 19f * s.height / 24f)
            cubicTo(21f * s.width / 24f, 20.1f * s.height / 24f, 20.1f * s.width / 24f, 21f * s.height / 24f, 19f * s.width / 24f, 21f * s.height / 24f)
            lineTo(5f * s.width / 24f, 21f * s.height / 24f)
            cubicTo(3.9f * s.width / 24f, 21f * s.height / 24f, 3f * s.width / 24f, 20.1f * s.height / 24f, 3f * s.width / 24f, 19f * s.height / 24f)
            lineTo(3f * s.width / 24f, 15f * s.height / 24f)
            moveTo(7f * s.width / 24f, 10f * s.height / 24f)
            lineTo(12f * s.width / 24f, 15f * s.height / 24f)
            lineTo(17f * s.width / 24f, 10f * s.height / 24f)
            moveTo(12f * s.width / 24f, 15f * s.height / 24f)
            lineTo(12f * s.width / 24f, 3f * s.height / 24f)
        }
        drawPath(path, color = color, style = stroke)
    }
}