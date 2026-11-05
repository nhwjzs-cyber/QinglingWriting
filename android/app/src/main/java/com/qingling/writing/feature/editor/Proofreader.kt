package com.qingling.writing.feature.editor

internal data class ProofIssue(val start:Int,val end:Int,val message:String,val excerpt:String,val stableKey:String="",val matched:String="")
internal data class ProofResult(val issues:List<ProofIssue>,val truncated:Boolean)
internal object Proofreader {
    fun removeRepeated(source:String,issue:ProofIssue):String? {
        if(!issue.message.startsWith("相邻重复") || issue.start<0 || issue.end>source.length || issue.end<=issue.start)return null
        val found=source.substring(issue.start,issue.end)
        val n=found.length/2
        if(found!=issue.matched || found.length%2!=0 || n==0 || found.take(n)!=found.drop(n))return null
        return source.removeRange(issue.start+n,issue.end)
    }
    private const val LIMIT=1000
    fun scan(text:String,words:List<String>,longParagraph:Int=300):ProofResult {
        require(text.length<=2_000_000) {"本章超过校对上限，请分章后检查"}
        require(longParagraph in 50..5000)
        val result=ArrayList<ProofIssue>();var truncated=false
        fun add(start:Int,end:Int,message:String) {
            if(result.size>=LIMIT){truncated=true;return}
            val from=(start-18).coerceAtLeast(0);val to=(start+80).coerceAtMost(text.length)
            result.add(ProofIssue(start,end,message,text.substring(from,to).replace('\n',' ')))
        }
        val pairs=mapOf('（' to '）','(' to ')','【' to '】','[' to ']','「' to '」','『' to '』','《' to '》','〈' to '〉','“' to '”','‘' to '’')
        val closers=pairs.values.toSet();val stack=ArrayDeque<Pair<Char,Int>>()
        for(i in text.indices) {
            val c=text[i]
            if(c in pairs) { if(stack.size>=LIMIT) {truncated=true;break};stack.addLast(c to i) }
            else if(c in closers) {
                if(stack.lastOrNull()?.first?.let {pairs[it]}==c) stack.removeLast()
                else add(i,i+1,"右标点 $c 未匹配，请检查嵌套")
            }
            if(i>0 && c==text[i-1] && Character.UnicodeScript.of(c.code)==Character.UnicodeScript.HAN && (i<2 || text[i-2]!=c)) add(i-1,i+1,"相邻重复字：$c$c")
        }
        stack.forEach {(c,i)->add(i,i+1,"左标点 $c 未闭合")}
        var i=0
        while(i<text.length-3) {
            val length=(4 downTo 2).firstOrNull { n -> i+2*n<=text.length &&
                (i until i+n).all { Character.UnicodeScript.of(text[it].code)==Character.UnicodeScript.HAN } &&
                text.regionMatches(i,text,i+n,n) && (i+1 until i+n).any {text[it]!=text[i]} }
            if(length!=null) {add(i,i+2*length,"相邻重复词：${text.substring(i,i+length)}");i+=2*length} else i++
        }
        var offset=0
        while(offset<text.length) {
            val next=text.indexOf('\n',offset).let {if(it<0)text.length else it}
            val count=text.codePointCount(offset,next)
            if(count>longParagraph) add(offset,next,"段落较长：$count 字符")
            offset=next+1
        }
        words.asSequence().map {it.trim()}.filter {it.isNotEmpty()}.distinct().take(100).forEach {word->
            var at=0
            while(at<=text.length-word.length) {val pos=text.indexOf(word,at,ignoreCase=true);if(pos<0)break;add(pos,pos+word.length,"词表命中：$word");at=pos+word.length;if(truncated)break}
        }
        val counts=mutableMapOf<String,Int>()
        val issues=result.sortedWith(compareBy({it.start},{it.end})).map{issue->
            val signature=issue.message.substringBefore('：')+"|"+issue.excerpt.replace(Regex("\\s+")," ").trim()
            val occurrence=counts.getOrDefault(signature,0);counts[signature]=occurrence+1
            val key=java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}+":"+occurrence
            issue.copy(stableKey=key,matched=text.substring(issue.start,issue.end))
        }
        return ProofResult(issues,truncated)
    }
}
