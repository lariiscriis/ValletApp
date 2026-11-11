package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apresentação local: a decisão de sessão/cadastro permanece exclusivamente na MainActivity. */
class AberturaActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {
    private lateinit var root: FrameLayout
    private lateinit var video: TextureView
    private lateinit var logo: ImageView
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var preparado = false
    private var retomadaPendente = false
    private var posicao = 0
    private var concluido = false
    private var navegou = false
    private var emPrimeiroPlano = false
    private var larguraVideo = 0
    private var alturaVideo = 0
    private var restante = 45_000L
    private var inicioEspera = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { concluir() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Um novo toque no launcher não deve cobrir a tarefa que já está aberta.
        if (!isTaskRoot && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        posicao = savedInstanceState?.getInt("posicao") ?: 0
        concluido = savedInstanceState?.getBoolean("concluido") ?: false
        restante = savedInstanceState?.getLong("restante") ?: 45_000L
        root = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.vallet_background))
        }
        video = TextureView(this).apply {
            isOpaque = false
            alpha = 0f
            surfaceTextureListener = this@AberturaActivity
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        logo = ImageView(this).apply {
            setImageResource(R.drawable.vallet_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val tamanho = (160 * resources.displayMetrics.density).toInt()
        root.addView(logo, FrameLayout.LayoutParams(tamanho, tamanho, android.view.Gravity.CENTER))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = concluir()
        })
        // Obtém o fundo real do vídeo fora da thread da UI; não atrasa sua reprodução.
        lifecycleScope.launch {
            val cor = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        resources.openRawResourceFd(R.raw.vallet_logo_reveal).use {
                            retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                        }
                        val frame = retriever.getFrameAtTime(0)
                        frame?.let {
                            val pixel = it.getPixel(0, 0)
                            it.recycle()
                            pixel
                        }
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
            if (cor != null && !isFinishing) {
                root.setBackgroundColor(cor)
                window.statusBarColor = cor
                window.navigationBarColor = cor
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::video.isInitialized) return
        emPrimeiroPlano = true
        if (concluido) { seguir(); return }
        inicioEspera = SystemClock.elapsedRealtime()
        handler.postDelayed(timeout, restante.coerceAtLeast(0))
        if (video.isAvailable) preparar()
    }

    private fun preparar() {
        if (!emPrimeiroPlano || concluido) return
        player?.let {
            if (preparado && !retomadaPendente) executar(it)
            return
        }
        val textura = video.surfaceTexture ?: return
        try {
            surface = Surface(textura)
            val novo = MediaPlayer()
            player = novo
            novo.setVolume(0f, 0f) // MediaPlayer não solicita foco; não usamos AudioManager.
            novo.isLooping = false
            novo.setSurface(surface)
            resources.openRawResourceFd(R.raw.vallet_logo_reveal).use {
                novo.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            novo.setOnVideoSizeChangedListener { _, w, h ->
                larguraVideo = w; alturaVideo = h; ajustarProporcao()
            }
            novo.setOnPreparedListener { mp ->
                if (player !== mp) return@setOnPreparedListener
                preparado = true
                if (posicao > 0) {
                    retomadaPendente = true
                    mp.setOnSeekCompleteListener {
                        retomadaPendente = false
                        if (emPrimeiroPlano && !concluido) executar(it)
                    }
                    mp.seekTo(posicao)
                } else if (emPrimeiroPlano) executar(mp)
            }
            novo.setOnInfoListener { mp, what, _ ->
                if (player === mp && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    video.alpha = 1f
                    logo.visibility = View.GONE
                }
                false
            }
            novo.setOnCompletionListener { if (player === it) concluir() }
            novo.setOnErrorListener { mp, _, _ ->
                if (player === mp) concluir()
                true
            }
            novo.prepareAsync()
        } catch (_: Exception) {
            concluir()
        }
    }

    private fun executar(mp: MediaPlayer) {
        runCatching { mp.start() }.onFailure { concluir() }
    }

    private fun ajustarProporcao() {
        if (larguraVideo <= 0 || alturaVideo <= 0 || video.width == 0 || video.height == 0) return
        val escala = minOf(video.width.toFloat() / larguraVideo, video.height.toFloat() / alturaVideo)
        video.setTransform(Matrix().apply {
            setScale(larguraVideo * escala / video.width, alturaVideo * escala / video.height,
                video.width / 2f, video.height / 2f)
        })
    }

    private fun concluir() {
        concluido = true
        handler.removeCallbacks(timeout)
        liberar()
        if (emPrimeiroPlano) seguir()
    }

    private fun seguir() {
        if (navegou || !emPrimeiroPlano || isFinishing) return
        navegou = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onPause() {
        emPrimeiroPlano = false
        handler.removeCallbacks(timeout)
        if (inicioEspera > 0) restante = (restante - (SystemClock.elapsedRealtime() - inicioEspera)).coerceAtLeast(0)
        inicioEspera = 0
        if (preparado) runCatching {
            posicao = player?.currentPosition ?: posicao
            player?.pause()
        }
        super.onPause()
    }

    override fun onStop() { liberar(); super.onStop() }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        liberar()
        super.onDestroy()
    }

    private fun liberar() {
        val atual = player
        player = null
        if (preparado) runCatching { posicao = atual?.currentPosition ?: posicao }
        preparado = false
        retomadaPendente = false
        atual?.release()
        surface?.release()
        surface = null
        if (::video.isInitialized) video.alpha = 0f
        if (::logo.isInitialized && !concluido) logo.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (preparado) runCatching { posicao = player?.currentPosition ?: posicao }
        outState.putInt("posicao", posicao)
        outState.putBoolean("concluido", concluido)
        val gasto = if (inicioEspera > 0) SystemClock.elapsedRealtime() - inicioEspera else 0L
        outState.putLong("restante", (restante - gasto).coerceAtLeast(0))
        super.onSaveInstanceState(outState)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = preparar()
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = ajustarProporcao()
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { liberar(); return true }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
