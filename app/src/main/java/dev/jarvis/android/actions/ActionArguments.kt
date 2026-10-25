package dev.jarvis.android.actions

import org.json.JSONObject
import java.net.URI
import java.text.Normalizer
import java.util.Locale

class ActionArguments(private val json: JSONObject, allowed: Set<String>) {
    init { require(json.keys().asSequence().all { it in allowed }) { "La acción contiene parámetros no admitidos." } }
    fun text(name: String, max: Int, optional: Boolean = false): String {
        if(optional && !json.has(name)) return ""
        val value=json.opt(name)
        require(value is String && value.isNotBlank() && value.length<=max && value.none { it=='\u0000' || (it.code<32 && it!='\n' && it!='\t') }) { "El parámetro $name no es válido." }
        return value.trim()
    }
    fun integer(name: String, range: IntRange): Int {
        val value=json.opt(name)
        require((value is Int || value is Long) && (value as Number).toLong() in range.first.toLong()..range.last.toLong()) { "El parámetro $name está fuera de rango." }
        return (value as Number).toInt()
    }
    fun choice(name: String, values: Set<String>) = text(name,80).also { require(it in values) { "Opción no admitida." } }
    companion object {
        fun phone(value: String): String {
            require(Regex("\\+?[0-9 ()-]{3,30}").matches(value)) { "Usa un número explícito sin códigos ni extensiones." }
            val number=value.filter { it.isDigit() || it=='+' }
            require(number.count { it.isDigit() } in 3..18)
            return number
        }
        fun https(value: String): String {
            val uri=URI(value)
            require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawUserInfo==null && uri.port in setOf(-1,443)) { "Solo se admiten direcciones HTTPS públicas sin credenciales." }
            require(uri.host.lowercase(Locale.ROOT) !in setOf("localhost","127.0.0.1","0.0.0.0","::1"))
            return uri.toASCIIString()
        }
        fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9]"),"")
    }
}
