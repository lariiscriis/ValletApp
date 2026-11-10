package br.edu.fatecpg.valletprojeto.service

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
        val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl("https://viacep.com.br/ws/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val viaCepService: ViaCepService = retrofit.create(ViaCepService::class.java)

        // Nova conexão com a API do Vallet (FastAPI)
        val valletRetrofit: Retrofit = Retrofit.Builder()
                .baseUrl("http://SEU-ENDERECO-IP:8000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val valletApiService: ValletApiService = valletRetrofit.create(ValletApiService::class.java)
}
