package com.shiv.assistant.data

import java.util.Locale
import kotlin.math.max

/** Ranks current encrypted facts without letting a weak inference displace an owner correction. */
object MemoryRetrieval {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf("the", "and", "for", "with", "this", "that", "from", "have", "was")

    private fun tokens(value:String):Set<String> = tokenPattern.findAll(value.lowercase(Locale.ROOT))
        .map { it.value }.filter { it.length >= 2 && it !in stopWords }.take(32).toSet()

    fun rank(records:List<MemoryRecord>,request:String,now:Long):List<MemoryRecord> {
        val live=records.filter { it.expiresAt==null || it.expiresAt>now }
        val query=tokens(request)
        val explicit=live.filter { it.explicit }
        return live.filter { candidate ->
            if(candidate.explicit) true else explicit.none { owner ->
                if(owner.category!=candidate.category) false else {
                    val a=tokens(owner.text)
                    val b=tokens(candidate.text)
                    val common=a.intersect(b).size
                    common>=2 && common.toDouble()/max(1,minOf(a.size,b.size))>=0.6
                }
            }
        }.map { record ->
            val words=tokens(record.text)
            val matches=query.intersect(words).size
            val relevance=if(query.isEmpty()) 0.0 else matches.toDouble()/query.size
            val ageDays=((now-record.updatedAt).coerceAtLeast(0L)/86_400_000.0)
            val freshness=1.0/(1.0+ageDays/30.0)
            record to (relevance*12.0+matches*2.0+freshness*2.0+
                record.confidence.coerceIn(0.0,1.0)+(if(record.explicit) 4.0 else 0.0))
        }.filter { query.isEmpty() || query.intersect(tokens(it.first.text)).isNotEmpty() }
            .sortedWith(compareByDescending<Pair<MemoryRecord,Double>> { it.second }
                .thenByDescending { it.first.updatedAt }.thenBy { it.first.id })
            .map { it.first }
    }
}
