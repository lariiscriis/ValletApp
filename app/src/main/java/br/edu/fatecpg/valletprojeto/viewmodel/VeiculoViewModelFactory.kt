package br.edu.fatecpg.valletprojeto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class VeiculoViewModelFactory : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VeiculoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VeiculoViewModel() as T
        }

        throw IllegalArgumentException("ViewModel desconhecido")
    }
}