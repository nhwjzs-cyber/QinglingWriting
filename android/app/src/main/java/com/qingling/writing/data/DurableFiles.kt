package com.qingling.writing.data

import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.IOException

internal object DurableFiles {
    fun syncDirectory(directory:File) {
        check(directory.isDirectory)
        val fd=Os.open(directory.absolutePath,OsConstants.O_RDONLY,0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }
    fun ensureDirectory(directory:File) {
        if(directory.isDirectory)return
        directory.parentFile?.let { ensureDirectory(it) }
        if(!directory.mkdir() && !directory.isDirectory)throw IOException("Cannot create private protection directory")
        directory.parentFile?.let { syncDirectory(it) }
    }
    fun write(file:File,bytes:ByteArray) {
        ensureDirectory(file.parentFile!!)
        val atomic=AtomicFile(file)
        var output:java.io.FileOutputStream?=null
        var published=false
        try {
            output=atomic.startWrite();output.write(bytes);output.fd.sync()
            atomic.finishWrite(output);published=true
            syncDirectory(file.parentFile!!)
            check(atomic.readFully().contentEquals(bytes)){"Protection write verification failed"}
        }catch(e:Exception){if(!published && output!=null)atomic.failWrite(output);throw e}
    }
}
