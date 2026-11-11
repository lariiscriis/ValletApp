import java.awt.Color as IconColor
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "br.edu.fatecpg.valletprojeto"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.edu.fatecpg.valletprojeto"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

// Recursos derivados da arte oficial. Não modifica a imagem usada nas telas.
val recursosMarca = layout.buildDirectory.dir("generated/valletBrandRes")
val gerarIconesVallet by tasks.registering {
    val arte = layout.projectDirectory.file("src/main/res/drawable/vallet_icon.png")
    inputs.file(arte)
    outputs.dir(recursosMarca)
    doLast {
        val original = javax.imageio.ImageIO.read(arte.asFile)
            ?: error("Não foi possível ler a arte oficial do ícone Vallet")
        val destino = recursosMarca.get().asFile
        fun renderizar(tamanho: Int, ocupacao: Double, fundo: Boolean, circular: Boolean): BufferedImage {
            val imagem = BufferedImage(tamanho, tamanho, BufferedImage.TYPE_INT_ARGB)
            val g = imagem.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                if (circular) g.clip = Ellipse2D.Double(0.0, 0.0, tamanho.toDouble(), tamanho.toDouble())
                if (fundo) {
                    g.color = IconColor(0xF0, 0xF2, 0xEB)
                    g.fillRect(0, 0, tamanho, tamanho)
                }
                val escala = tamanho * ocupacao / maxOf(original.width, original.height)
                val w = (original.width * escala).toInt()
                val h = (original.height * escala).toInt()
                g.drawImage(original, (tamanho - w) / 2, (tamanho - h) / 2, w, h, null)
            } finally {
                g.dispose()
            }
            return imagem
        }
        fun gravar(pasta: String, nome: String, imagem: BufferedImage) {
            val arquivo = destino.resolve("$pasta/$nome.png")
            arquivo.parentFile.mkdirs()
            check(javax.imageio.ImageIO.write(imagem, "png", arquivo))
        }
        for ((densidade, tamanho) in mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)) {
            gravar("mipmap-$densidade", "vallet_launcher", renderizar(tamanho, 0.80, true, false))
            gravar("mipmap-$densidade", "vallet_launcher_round", renderizar(tamanho, 0.80, true, true))
        }
        // Tela de 108dp: a arte ocupa 54dp, dentro da zona segura das máscaras.
        gravar("drawable-nodpi", "vallet_launcher_foreground", renderizar(432, 0.50, false, false))
    }
}
android.sourceSets.getByName("main").res.srcDir(recursosMarca)
tasks.named("preBuild").configure { dependsOn(gerarIconesVallet) }

dependencies {

    //qr code
    implementation("com.google.zxing:core:3.5.3")


    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation(libs.firebase.storage.ktx)

    implementation("com.google.firebase:firebase-messaging-ktx")

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation ("com.firebase:geofire-android-common:3.2.0")

    // Media3
    implementation(libs.androidx.media3.common.ktx)

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // componentes UI
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("com.google.android.material:material:1.14.0")


    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}


