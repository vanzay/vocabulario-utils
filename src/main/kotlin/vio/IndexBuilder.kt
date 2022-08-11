package vio

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.io.FileReader
import java.nio.file.Paths
import java.util.*

private fun initDir(dir: File) {
    if (dir.exists()) {
        dir.deleteRecursively()
    }
    dir.mkdirs()
}

private fun build(indexDir: File) {
    val index = FSDirectory.open(Paths.get(indexDir.absolutePath))
    val writer = IndexWriter(index, IndexWriterConfig())

    phraseDAO.forEach { phraseId, term, _, basePhraseTerm ->
        addPhrase(phraseId, term, basePhraseTerm, writer)
    }

    writer.close()
    index.close()
}

private fun addPhrase(id: Long, phrase: String, basePhraseTerm: String?, writer: IndexWriter) {
    val doc = Document()
    doc.add(StringField("term", phrase, Field.Store.YES))
    // TODO add basePhraseTerm to use it in text analyzer
    // doc.add(StringField("basePhraseTerm", basePhraseTerm ?: "", Field.Store.YES))
    doc.add(StoredField("id", id))
    writer.addDocument(doc)
}

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

    val indexDir = File(props.getProperty("indices.path"), props.getProperty("language.iso3"))
    initDir(indexDir)
    build(indexDir)
}