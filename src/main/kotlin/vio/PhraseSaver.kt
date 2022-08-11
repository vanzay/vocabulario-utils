package vio

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.io.FileReader
import java.util.*

private fun loadJson(pathname: String): MutableMap<String, String> {
    val mapper = jacksonObjectMapper()
    mapper.registerKotlinModule()
    val typeRef = object : TypeReference<MutableMap<String, String>>() {}
    return mapper.readValue(File(pathname), typeRef)
}

private fun clearDuplicates() {
    val duplicates =
        termsMap.keys.filter { it[0].isUpperCase() && termsMap[it.lowercase(Locale.getDefault())] != null }.toSet()

    val iter = termsMap.keys.iterator()
    while (iter.hasNext()) {
        val key = iter.next()
        if (duplicates.contains(key)) {
            println("Duplicate $key (remove)")
            iter.remove()
        } else {
            val basePhrase = termsMap[key]!!
            if (duplicates.contains(basePhrase)) {
                println("Duplicate $basePhrase (change link)")
                termsMap[key] = basePhrase.lowercase(Locale.getDefault())
            }
        }
    }
}

private fun removeDeepLinks(baseTerms: Set<String>, level: Int) {
    println("Remove deep links, Level $level")
    val termsForms = termsMap.filter { (k, v) -> baseTerms.contains(v) }.map { (k, v) -> k }.toSet()
    if (level > 2 && level and 1 == 1) {
        termsForms.forEach { k ->
            println("Unlink $k -> ${termsMap[k]} -> ${termsMap[termsMap[k]]}")
            termsMap[k] = "-"
        }
    }
    if (termsForms.isNotEmpty()) {
        removeDeepLinks(termsForms, level + 1)
    }
}

private fun savePhrases() {
    println("Save base phrases")
    insertPhrasesIntoDB { baseTerm -> baseTerm == "-" }
    updateTermsMaps()

    println("Save rest phrases")
    while (termsMap.isNotEmpty()) {
        println("-- ${termsMap.size}")
        insertPhrasesIntoDB { baseTerm -> termIdMap.contains(baseTerm) }
        updateTermsMaps()
    }
}

private fun insertPhrasesIntoDB(predicate: (String) -> Boolean) {
    val phrases = mutableListOf<Pair<String, Long?>>()

    for ((term, baseTerm) in termsMap) {
        if (predicate(baseTerm)) {
            phrases.add(Pair(term, termIdMap[baseTerm]))
            if (phrases.size >= BATCH_SIZE) {
                phraseDAO.insert(phrases)
                phrases.clear()
            }
        }
    }

    if (phrases.isNotEmpty()) {
        phraseDAO.insert(phrases)
        phrases.clear()
    }
}

private fun updateTermsMaps() {
    phraseDAO.forEach { phraseId, term, _, _ ->
        if (!termIdMap.containsKey(term)) {
            termIdMap[term] = phraseId
            termsMap.remove(term)
        }
    }
}

const val BATCH_SIZE = 100
private val termIdMap = mutableMapOf<String, Long>()
private lateinit var termsMap: MutableMap<String, String>
private lateinit var phraseDAO: PhraseDAO

fun main() {
    val props = Properties()
    props.load(FileReader("application.properties"))

    Database.connect(
        props.getProperty("db.url"),
        props.getProperty("db.driver"),
        props.getProperty("db.user"),
        props.getProperty("db.password")
    )

    phraseDAO = PhraseDAO(props.getProperty("language.id").toInt())

    termsMap = loadJson(props.getProperty("dict.filename"))

    // TODO condition depends on language
    termsMap.entries.removeIf { (k, v) -> k.length > 64 || v.length > 64 || k[0].isUpperCase() || v[0].isUpperCase() }
    clearDuplicates()
    removeDeepLinks(setOf("-"), 1)
    savePhrases()
}
