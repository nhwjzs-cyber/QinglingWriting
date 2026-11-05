package com.qingling.writing

import android.app.Application
import com.qingling.writing.app.AppContainer

/**
 * 应用入口(docs/03 §2)。
 * 持有手工依赖容器 [AppContainer];M1 起在此接入 Room / DataStore 等,
 * 不引入 Hilt(工具链矩阵 M0 未包含,依赖注入保持手工容器)。
 */
class WritingApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
