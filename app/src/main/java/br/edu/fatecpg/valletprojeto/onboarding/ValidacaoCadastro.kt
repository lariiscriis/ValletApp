package br.edu.fatecpg.valletprojeto.onboarding

object ValidacaoCadastro {
    /**
     * Confere o número de registro (11 dígitos), sem consultar bases oficiais.
     * Referência: https://github.com/klawdyo/validation-br/blob/master/src/cnh.ts
     */
    fun cnhValida(valor: String): Boolean {
        val numero = valor.trim()
        if (!numero.matches(Regex("[0-9]{11}")) || numero.all { it == numero[0] }) return false
        fun verificador(soma: Int): Int {
            val digito = 11 - soma % 11
            return if (digito >= 10) 0 else digito
        }
        val primeiro = verificador((0 until 9).sumOf { (numero[it] - '0') * (it + 2) })
        val segundo = verificador((0 until 9).sumOf { (numero[it] - '0') * (it + 3) } + primeiro * 2)
        return numero[9] - '0' == primeiro && numero[10] - '0' == segundo
    }

    fun telefoneNormalizado(valor: String): String {
        val numeros = valor.filter { it in '0'..'9' }
        return if (numeros.length in 12..13 && numeros.startsWith("55")) numeros.drop(2) else numeros
    }
    fun telefoneValido(valor: String): Boolean {
        if (!valor.matches(Regex("[+0-9() .-]+"))) return false
        val n = telefoneNormalizado(valor)
        val ddds = setOf(11,12,13,14,15,16,17,18,19,21,22,24,27,28,31,32,33,34,35,37,38,41,42,43,44,45,46,47,48,49,51,53,54,55,61,62,63,64,65,66,67,68,69,71,73,74,75,77,79,81,82,83,84,85,86,87,88,89,91,92,93,94,95,96,97,98,99)
        if (n.length !in 10..11 || n.take(2).toIntOrNull() !in ddds) return false
        return if (n.length == 11) n[2] == '9' else n[2] in '2'..'5'
    }
    fun placaNormalizada(valor: String) = valor.trim().uppercase(java.util.Locale.ROOT).replace("-", "")
    fun placaValida(valor: String, tipo: String): Boolean {
        val padrao = when (tipo.trim().lowercase(java.util.Locale.ROOT)) {
            "carro" -> "[A-Z]{3}[0-9][A-Z][0-9]{2}"
            "moto" -> "[A-Z]{3}[0-9]{4}"
            else -> return false
        }
        return placaNormalizada(valor).matches(Regex(padrao))
    }
}
