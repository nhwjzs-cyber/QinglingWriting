package com.qingling.writing.core.model

data class InputMetrics(val baseSeq: Long,val inserted: Long,val activeMillis: Long)

/** Observed committed input, not hardware keystrokes. IME composition and program edits are excluded. */
class InputMeter(private val baseSeq: Long,initial: String,private val clock:()->Long={System.nanoTime()/1_000_000}) {
    private var stable=initial
    private var lastEvent: Long?=null
    private var active=true
    private var inserted=0L
    private var millis=0L
    fun setActive(value: Boolean) { if(value!=active) lastEvent=null;active=value }
    fun ignore(text: String) {stable=text;lastEvent=null}
    fun observe(text: String,composing: Boolean): InputMetrics {
        if(!composing && text!=stable) {
            if(active) {
                inserted+=insertedCodePoints(stable,text)
                val now=clock();val gap=lastEvent?.let {now-it}
                if(gap!=null && gap in 0..30_000) millis+=gap
                lastEvent=now
            }
            stable=text
        }
        return InputMetrics(baseSeq,inserted,millis)
    }
    fun snapshot()=InputMetrics(baseSeq,inserted,millis)
}

fun insertedCodePoints(before: String,after: String): Long {
    var prefix=0
    while(prefix<before.length && prefix<after.length && before[prefix]==after[prefix]) prefix++
    // Do not split surrogate pairs when edits replace one emoji with another.
    if(prefix>0 && prefix<after.length && Character.isLowSurrogate(after[prefix])) prefix--
    var oldEnd=before.length;var newEnd=after.length
    while(oldEnd>prefix && newEnd>prefix && before[oldEnd-1]==after[newEnd-1]) {oldEnd--;newEnd--}
    if(newEnd<after.length && newEnd>prefix && Character.isLowSurrogate(after[newEnd])) newEnd++
    return com.qingling.writing.domain.validation.WordCounter.count(after.substring(prefix,newEnd))
}
