package com.qingling.writing.core.model

/** Refuse ambiguous or changed quotes rather than silently pointing to a wrong paragraph. */
object QuoteLocator {
    fun find(body:String,note:QuickNote):Int? {
        if(note.sourceQuote.isEmpty())return null
        val positions=mutableListOf<Int>();var at=body.indexOf(note.sourceQuote)
        while(at>=0 && positions.size<1000){positions.add(at);at=body.indexOf(note.sourceQuote,at+1)}
        if(positions.size==1)return positions[0]
        val contextual=positions.filter { p->
            (note.sourceBefore.isEmpty() || body.substring(0,p).endsWith(note.sourceBefore)) &&
            (note.sourceAfter.isEmpty() || body.substring(p+note.sourceQuote.length).startsWith(note.sourceAfter))
        }
        return contextual.singleOrNull()
    }
}
