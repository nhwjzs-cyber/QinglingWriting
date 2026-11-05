package com.qingling.writing.core.files
import java.nio.ByteBuffer
import java.nio.ByteOrder
object FontFileValidator {
    const val MAX_BYTES=20*1024*1024
    fun validate(bytes: ByteArray) {
        require(bytes.size in 28..MAX_BYTES) {"字体大小须为 28 字节至 20 MB"}
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val signature=b.int
        require(signature==0x00010000 || signature==0x4F54544F) {"仅支持标准 TTF / OTF 字体，不支持 TTC、WOFF 或压缩包"}
        val count=b.short.toInt() and 0xffff
        require(count in 1..512 && 12L+count*16L<=bytes.size) {"字体目录损坏"}
        val tags=mutableSetOf<Int>()
        for(i in 0 until count) {
            b.position(12+i*16);val tag=b.int;b.int
            val offset=b.int.toLong() and 0xffffffffL;val length=b.int.toLong() and 0xffffffffL
            require(tags.add(tag) && offset>=12L+count*16L && offset+length<=bytes.size) {"字体数据不完整"}
        }
        require(0x636D6170 in tags && 0x68656164 in tags && 0x6E616D65 in tags) {"字体缺少必要的字符或名称表"}
    }
}
