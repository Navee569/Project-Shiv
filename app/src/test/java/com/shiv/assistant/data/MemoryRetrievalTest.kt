package com.shiv.assistant.data

import org.junit.Assert.*
import org.junit.Test

class MemoryRetrievalTest {
    private val now=1_800_000_000_000L
    @Test fun relevantOwnerCorrectionOutranksAndSuppressesConflictingInference() {
        val inferred=MemoryRecord("old","I travel to Mysuru by bus","TRAVEL","pattern",
            0.6,false,updatedAt=now-1000)
        val corrected=MemoryRecord("new","I travel to Mysuru by train","TRAVEL",
            "owner-correction",1.0,true,"old",updatedAt=now-2000)
        val unrelated=MemoryRecord("other","My favourite food is dosa","FOOD","owner",
            updatedAt=now)
        val result=MemoryRetrieval.rank(listOf(inferred,corrected,unrelated),
            "How do I travel to Mysuru?",now)
        assertEquals("new",result.first().id)
        assertFalse(result.any { it.id=="old" })
        assertFalse(result.any { it.id=="other" })
    }
    @Test fun expiryAndRecencyAreApplied() {
        val expired=MemoryRecord("expired","My dentist is nearby","FACT","owner",
            expiresAt=now-1)
        val recent=MemoryRecord("recent","My dentist is downtown","FACT","owner",
            updatedAt=now)
        assertEquals(listOf("recent"),MemoryRetrieval.rank(listOf(expired,recent),
            "dentist",now).map { it.id })
    }
}
