package bzh.edgar.bixbrother

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

private const val RAY_COUNT = 12
private const val RAY_WIDTH = 16F
private val RAY_COLOR = Color(0xe2, 0x1b, 0x22, 0xff).toArgb()

class SunraysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var currentRotation = 0f
    private var rotationSpeed = 45000L
    private val path = Path()

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = rotationSpeed
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            currentRotation = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setBackgroundColor(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = measuredWidth.coerceAtMost(measuredHeight)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val centerX = w / 2f
        val centerY = h / 2f
        val radius = w / 2f

        paint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(RAY_COLOR, RAY_COLOR, 0),
            floatArrayOf(0F, 0.3F, 1F),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2f

        for (i in 0 until RAY_COUNT) {
            path.reset()

            val angle = (i * 360f / RAY_COUNT) + currentRotation

            val startAngle = angle - RAY_WIDTH / 2
            val endAngle = angle + RAY_WIDTH / 2

            val startX = centerX + radius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
            val startY = centerY + radius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
            val endX = centerX + radius * cos(Math.toRadians(endAngle.toDouble())).toFloat()
            val endY = centerY + radius * sin(Math.toRadians(endAngle.toDouble())).toFloat()

            path.moveTo(centerX, centerY)
            path.lineTo(startX, startY)
            path.lineTo(endX, endY)
            path.close()

            canvas.drawPath(path, paint)
        }
    }
}
