package vio

import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

const val LIMIT = 1000

object PhraseTable : LongIdTable(columnName = "phrase_id") {
    val term: Column<String> = varchar("term", length = 128)
    val basePhraseId = long("base_phrase_id").references(PhraseTable.id).nullable()
    val languageId: Column<Int> = integer("language_id")
}

class PhraseDAO(private val languageId: Int) {

    fun forEach(rowHandler: (phraseId: Long, term: String, basePhraseId: Long?, basePhraseTerm: String?) -> Unit) {
        val (firstRowNum, lastRowNum) = getIdInterval()

        var leftBound = firstRowNum
        var rightBound = leftBound + LIMIT

        while (leftBound <= lastRowNum) {
            transaction {
                addLogger(StdOutSqlLogger)
                val basePhrase = PhraseTable.alias("bp")
                PhraseTable
                    .join(basePhrase, JoinType.LEFT, PhraseTable.basePhraseId, basePhrase[PhraseTable.id])
                    .slice(PhraseTable.id, PhraseTable.term, PhraseTable.basePhraseId, basePhrase[PhraseTable.term])
                    .select { (PhraseTable.id greaterEq leftBound) and (PhraseTable.id less rightBound) and (PhraseTable.languageId eq languageId) }
                    .forEach { row ->
                        val phraseId = row[PhraseTable.id]
                        val term = row[PhraseTable.term]
                        val basePhraseId = row[PhraseTable.basePhraseId]
                        val basePhraseTerm = row[basePhrase[PhraseTable.term]]
                        rowHandler(phraseId.value, term, basePhraseId, basePhraseTerm)
                    }
                leftBound += LIMIT
                rightBound += LIMIT
            }
        }
    }

    private fun getIdInterval(): Pair<Long, Long> {
        var interval = Pair<Long, Long>(0, 0)
        transaction {
            addLogger(StdOutSqlLogger)
            val row = PhraseTable
                .slice(PhraseTable.id.min(), PhraseTable.id.max())
                .select { PhraseTable.languageId eq languageId }
                .single()
            interval = Pair(row[PhraseTable.id.min()]?.value ?: 0, row[PhraseTable.id.max()]?.value ?: 0)
        }
        return interval
    }

    fun insert(phrases: List<Pair<String, Long?>>): Map<String, Long> {
        val idMap = mutableMapOf<String, Long>()
        transaction {
//            addLogger(StdOutSqlLogger)
            val phrasesIds = PhraseTable.batchInsert(phrases) { phrase ->
                val (term, basePhraseId) = phrase
                this[PhraseTable.term] = term
                this[PhraseTable.basePhraseId] = basePhraseId
                this[PhraseTable.languageId] = languageId
            }
            phrasesIds.forEach { row ->
                idMap[row[PhraseTable.term]] = row[PhraseTable.id].value
            }
            commit()
        }
        return idMap
    }
}