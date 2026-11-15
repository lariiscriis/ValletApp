package br.edu.fatecpg.valletprojeto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Estacionamento visto de cima, girado na diagonal (estilo ilustração flat).
 * Tons de cinza + verde claro. A cena é desenhada UMA vez num bitmap (onSizeChanged),
 * então o onDraw é só um drawBitmap: barato e sem variar entre redraws.
 */
class ParkingMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------- Paleta ----------
    private val asphalt = Color.parseColor("#ECEFF1")
    private val curb = Color.parseColor("#F4F6F7")
    private val curbLine = Color.parseColor("#DDE2E6")
    private val white = Color.parseColor("#FFFFFF")
    private val windowColor = Color.parseColor("#B7C1C9")
    private val carColors = intArrayOf(
        Color.parseColor("#D0D6DB"),
        Color.parseColor("#C3CBD1"),
        Color.parseColor("#DDE1E5"),
        Color.parseColor("#B9C3CB"),
        Color.parseColor("#CDE8D7"), // verde bem claro
        Color.parseColor("#BBDDC9")  // verde bem claro
    )

    // Véu branco por cima da cena: 0 = cena forte, 255 = some. Aumente se o texto ainda brigar com o fundo.
    private val veilAlpha = 110

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()
    private var scene: Bitmap? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ---------- Medidas (em dp) ----------
    private val slotW get() = dp(38f)     // largura de cada vaga
    private val slotD get() = dp(76f)     // profundidade da vaga
    private val laneW get() = dp(60f)     // corredor de circulação
    private val divH get() = dp(24f)      // faixa/canteiro entre blocos
    private val angle = -26f              // inclinação da cena

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            drawScene(canvas, w.toFloat(), h.toFloat())
            canvas.drawColor(Color.argb(veilAlpha, 255, 255, 255))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scene?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun drawScene(c: Canvas, w: Float, h: Float) {
        c.drawColor(asphalt)
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(angle)

        // Cobre a tela inteira mesmo depois de girar
        val ext = hypot(w, h) / 2f + dp(60f)
        val period = slotD * 2 + laneW + divH
        val bStart = floor(-ext / period).toInt() - 1
        val bEnd = ceil(ext / period).toInt() + 1
        for (b in bStart..bEnd) drawBand(c, b, b * period, ext)

        c.restore()
    }

    // Uma "faixa": vagas em cima, corredor no meio, vagas embaixo e um divisor com canteiros
    private fun drawBand(c: Canvas, b: Int, y0: Float, ext: Float) {
        val yLaneTop = y0 + slotD
        val yLaneBot = yLaneTop + laneW
        val yBotEnd = yLaneBot + slotD
        val yDivEnd = yBotEnd + divH
        val kStart = (-ext / slotW).toInt() - 1
        val kEnd = (ext / slotW).toInt() + 1

        // 1. Divisor (meio-fio) liso entre os blocos
        paint.style = Paint.Style.FILL
        paint.color = curb
        c.drawRect(-ext, yBotEnd, ext, yDivEnd, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = curbLine
        c.drawLine(-ext, yBotEnd, ext, yBotEnd, paint)
        c.drawLine(-ext, yDivEnd, ext, yDivEnd, paint)

        // 2. Linhas das vagas (brancas)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = white
        for (k in kStart..kEnd) {
            val x = k * slotW
            c.drawLine(x, y0, x, yLaneTop, paint)
            c.drawLine(x, yLaneBot, x, yBotEnd, paint)
        }

        // 3. Faixa central tracejada do corredor
        paint.strokeWidth = dp(1.6f)
        paint.pathEffect = DashPathEffect(floatArrayOf(dp(12f), dp(10f)), 0f)
        val yc = (yLaneTop + yLaneBot) / 2f
        c.drawLine(-ext, yc, ext, yc, paint)
        paint.pathEffect = null

        // 4. Setas de sentido (uma mão em cada metade do corredor)
        val step = slotW * 6
        val first = floor(-ext / step).toInt() - 1
        val last = ceil(ext / step).toInt() + 1
        for (i in first..last) {
            val x = i * step + (b.mod(3)) * slotW * 2f
            drawArrow(c, x, yLaneTop + laneW * 0.26f, -1f)
            drawArrow(c, x + slotW * 3, yLaneTop + laneW * 0.74f, 1f)
        }

        // 5. Carros (alguns, de forma determinística e levemente tortos)
        for (k in kStart..kEnd) {
            val cx = (k + 0.5f) * slotW
            if (hash(b, k) % 100 < 56) {
                drawCar(c, cx, y0 + slotD / 2f, noseDown = false, seed = hash(b, k + 1000))
            }
            if (hash(b + 77, k) % 100 < 56) {
                drawCar(c, cx, yLaneBot + slotD / 2f, noseDown = true, seed = hash(b + 77, k + 1000))
            }
        }
    }

    private fun drawCar(c: Canvas, cx: Float, cy: Float, noseDown: Boolean, seed: Int) {
        val cw = slotW * 0.70f
        val cl = slotD * 0.74f
        val wobble = ((seed % 7) - 3) * 0.9f                      // -2.7° .. +2.7°
        val shift = ((seed / 7 % 5) - 2) * dp(1.2f)               // deslocamento pequeno dentro da vaga

        c.save()
        c.translate(cx + shift, cy)
        c.rotate(wobble + if (noseDown) 180f else 0f)

        val body = carColors[seed % carColors.size]

        // sombra
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(16, 0, 0, 0)
        rect.set(-cw / 2 + dp(1.5f), -cl / 2 + dp(2f), cw / 2 + dp(1.5f), cl / 2 + dp(2f))
        c.drawRoundRect(rect, dp(8f), dp(8f), paint)

        // carroceria
        paint.color = body
        rect.set(-cw / 2, -cl / 2, cw / 2, cl / 2)
        c.drawRoundRect(rect, dp(8f), dp(8f), paint)

        // para-brisa (frente = topo), teto e vidro traseiro
        paint.color = windowColor
        rect.set(-cw * 0.37f, -cl * 0.30f, cw * 0.37f, -cl * 0.11f)
        c.drawRoundRect(rect, dp(3f), dp(3f), paint)
        rect.set(-cw * 0.33f, cl * 0.17f, cw * 0.33f, cl * 0.29f)
        c.drawRoundRect(rect, dp(3f), dp(3f), paint)

        paint.color = lighten(body)
        rect.set(-cw * 0.37f, -cl * 0.10f, cw * 0.37f, cl * 0.16f)
        c.drawRoundRect(rect, dp(3f), dp(3f), paint)

        c.restore()
    }

    private fun drawArrow(c: Canvas, cx: Float, cy: Float, dir: Float) {
        val half = dp(20f)
        val head = dp(9f)
        val tipX = cx + dir * half

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.color = white
        c.drawLine(cx - dir * half, cy, tipX - dir * head * 0.5f, cy, paint)

        paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(tipX, cy)
        path.lineTo(tipX - dir * head, cy - head * 0.8f)
        path.lineTo(tipX - dir * head, cy + head * 0.8f)
        path.close()
        c.drawPath(path, paint)
    }

    // ---------- helpers ----------
    private fun hash(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

    private fun lighten(color: Int): Int {
        val r = Color.red(color) + (255 - Color.red(color)) / 5
        val g = Color.green(color) + (255 - Color.green(color)) / 5
        val b = Color.blue(color) + (255 - Color.blue(color)) / 5
        return Color.rgb(r, g, b)
    }
}