package com.example.livemedia

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun DoodleBackground(
    modifier: Modifier = Modifier,
    doodleCount: Int = 15
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) // Very subtle
    val seed = remember { System.currentTimeMillis() }
    val doodles = remember(seed) {
        List(doodleCount) { generateRandomDoodle(it, seed) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "doodle_anim")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        doodles.forEach { doodle ->
            // Animation logic: gentle floating + rotation
            val verticalOffset = sin(time + doodle.phase) * 50f 
            val horizontalOffset = cos(time + doodle.phase * 0.5f) * 30f
            val rotation = sin(time * 0.2f + doodle.phase) * 20f

            withTransform({
                translate(
                    left = doodle.x * size.width + horizontalOffset,
                    top = doodle.y * size.height + verticalOffset
                )
                rotate(doodle.rotation + rotation)
                rotate(doodle.rotation + rotation)
                scale(doodle.scale, doodle.scale)
            }) {
                when (doodle.type) {
                    DoodleType.SQUIGGLE -> drawSquiggle(color, 3.dp.toPx())
                    DoodleType.CIRCLE -> drawCircle(color, 20.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                    DoodleType.STAR -> drawStar(color, 2.dp.toPx())
                    DoodleType.TRIANGLE -> drawTriangle(color, 2.dp.toPx())
                    DoodleType.CROSS -> drawCross(color, 2.dp.toPx())
                }
            }
        }
    }
}

private enum class DoodleType { SQUIGGLE, CIRCLE, STAR, TRIANGLE, CROSS }

private data class DoodleElement(
    val type: DoodleType,
    val x: Float, // 0..1 relative to screen width
    val y: Float, // 0..1 relative to screen height
    val scale: Float,
    val rotation: Float,
    val phase: Float
)

private fun generateRandomDoodle(index: Int, seed: Long): DoodleElement {
    val random = Random(seed + index)
    return DoodleElement(
        type = DoodleType.values().random(random),
        x = random.nextFloat(),
        y = random.nextFloat(),
        scale = 0.8f + random.nextFloat() * 1.2f, // 0.8 to 2.0
        rotation = random.nextFloat() * 360f,
        phase = random.nextFloat() * 2 * PI.toFloat()
    )
}

// --- Drawing Helpers ---

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSquiggle(color: Color, strokeWidth: Float) {
    val path = Path()
    path.moveTo(-20f, 0f)
    path.cubicTo(-10f, -20f, 10f, 20f, 20f, 0f)
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCross(color: Color, strokeWidth: Float) {
    drawLine(color, start = Offset(-15f, -15f), end = Offset(15f, 15f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color, start = Offset(15f, -15f), end = Offset(-15f, 15f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(color: Color, strokeWidth: Float) {
    val path = Path()
    // Simple 4-point star
    path.moveTo(0f, -20f)
    path.quadraticBezierTo(5f, -5f, 20f, 0f)
    path.quadraticBezierTo(5f, 5f, 0f, 20f)
    path.quadraticBezierTo(-5f, 5f, -20f, 0f)
    path.quadraticBezierTo(-5f, -5f, 0f, -20f)
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(color: Color, strokeWidth: Float) {
    val path = Path()
    val size = 20f
    path.moveTo(0f, -size)
    path.lineTo(size, size)
    path.lineTo(-size, size)
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
}
