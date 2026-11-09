package br.edu.fatecpg.valletprojeto

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.edu.fatecpg.valletprojeto.onboarding.*
import br.edu.fatecpg.valletprojeto.viewmodel.FotoUsuarioViewModel
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

/** A foto é parte da etapa Conta; pode ser adicionada depois pelo perfil. */
class CadastroFotoUsuarioActivity : AppCompatActivity() {
    private val vm: FotoUsuarioViewModel by viewModels()
    private val roteador by lazy { RoteadorCadastro(this) }
    private lateinit var root: LinearLayout
    private lateinit var imagem: ImageView
    private lateinit var escolher: MaterialButton
    private lateinit var continuar: MaterialButton
    private lateinit var depois: MaterialButton
    private var pronto = false
    private val selecionar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
            vm.foto = uri
            mostrarFoto()
        }
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (vm.foto == null) vm.foto = savedInstanceState?.getString("foto")?.let(android.net.Uri::parse)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(ContextCompat.getColor(this@CadastroFotoUsuarioActivity, R.color.vallet_background))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        layoutInflater.inflate(R.layout.view_progresso_cadastro, root, true)
        val scroll = ScrollView(this)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val conteudo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        scroll.addView(conteudo)
        fun texto(valor: String, tamanho: Float) {
            conteudo.addView(TextView(this).apply {
                text = valor; textSize = tamanho; gravity = Gravity.CENTER
                typeface = ResourcesCompat.getFont(this@CadastroFotoUsuarioActivity, R.font.poppins_medium)
                setTextColor(ContextCompat.getColor(this@CadastroFotoUsuarioActivity, R.color.vallet_green_dark))
                setPadding(0, dp(12), 0, dp(12))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        conteudo.addView(ImageView(this).apply {
            setImageResource(R.drawable.allet)
            contentDescription = "Vallet"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(160), dp(70)))
        texto("Cadastre sua foto", 26f)
        texto("Deixe seu perfil com a sua cara.\nEscolha uma foto para continuar.", 14f)
        imagem = ImageView(this).apply {
            contentDescription = "Foto do perfil"
            setImageResource(R.drawable.ic_default_profile)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        conteudo.addView(imagem, LinearLayout.LayoutParams(dp(160), dp(160)).apply {
            topMargin = dp(20); bottomMargin = dp(20)
        })
        fun botao(titulo: String): MaterialButton = MaterialButton(this).apply {
            text = titulo; isAllCaps = false
            cornerRadius = dp(24)
            typeface = ResourcesCompat.getFont(this@CadastroFotoUsuarioActivity, R.font.poppins_medium)
            backgroundTintList = ContextCompat.getColorStateList(this@CadastroFotoUsuarioActivity, R.color.vallet_green_dark)
            setTextColor(ContextCompat.getColor(this@CadastroFotoUsuarioActivity, R.color.white))
            conteudo.addView(this, LinearLayout.LayoutParams(-1, dp(58)))
        }
        escolher = botao("Escolher foto")
        continuar = botao("Salvar foto e continuar")
        depois = botao("Adicionar depois")
        texto("Você pode alterar sua foto no perfil.", 12f)
        layoutInflater.inflate(R.layout.view_bolinhas_cadastro, root, true)
        IndicadoresCadastro.mostrar(root, 2)
        escolher.setOnClickListener { selecionar.launch(arrayOf("image/*")) }
        continuar.setOnClickListener {
            if (vm.foto == null) Toast.makeText(this, "Escolha uma foto ou toque em Adicionar depois", Toast.LENGTH_SHORT).show()
            else vm.concluir(this, false)
        }
        depois.setOnClickListener { vm.concluir(this, true) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (vm.estado.value?.salvando != true) moveTaskToBack(true) }
        })
        vm.estado.observe(this) { estado ->
            atualizar()
            estado.erro?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            if (estado.sucesso) roteador.verificar()
        }
        mostrarFoto()
        if (vm.estado.value?.salvando == true) pronto = true
        else if (vm.estado.value?.sucesso != true) {
            roteador.verificar(destinoAtual = {
                if (it == DestinoCadastro.FOTO) { pronto = true; atualizar() } else roteador.abrir(it)
            })
        }
        atualizar()
    }
    private fun mostrarFoto() {
        vm.foto?.let { Glide.with(this).load(it).circleCrop().into(imagem) }
    }
    private fun atualizar() {
        val ativo = pronto && vm.estado.value?.salvando != true && vm.estado.value?.sucesso != true
        escolher.isEnabled = ativo; continuar.isEnabled = ativo; depois.isEnabled = ativo
        continuar.text = if (vm.estado.value?.salvando == true) "Salvando…" else "Salvar foto e continuar"
    }
    override fun onSaveInstanceState(outState: Bundle) {
        vm.foto?.let { outState.putString("foto", it.toString()) }
        super.onSaveInstanceState(outState)
    }
}
