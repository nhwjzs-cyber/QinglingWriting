package com.qingling.writing.core.model

/** Readable scene markers live in the existing chapter-outline body, so backups retain them. */
internal enum class SceneStatus(val marker: String, val label: String) { TODO(" ", "待写"), CURRENT(">", "正在写"), DONE("x", "已写") }
internal data class ReferenceScene(val line: Int, val text: String, val status: SceneStatus)
internal data class ChapterReference(val goal: String, val scenes: List<ReferenceScene>) {
    val nextScene: String get() = (scenes.firstOrNull { it.status == SceneStatus.CURRENT } ?: scenes.firstOrNull { it.status == SceneStatus.TODO })?.text ?: "暂无待写分场"
    companion object {
        private val marked = Regex("^\\s*(?:[-*]\\s*)?\\[([ xX>])\\]\\s*(.*)$")
        fun parse(body: String): ChapterReference {
            val goal = mutableListOf<String>(); val scenes = mutableListOf<ReferenceScene>(); var inScenes = false
            body.split('\n').forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.trimStart('#', ' ').trimEnd('：', ':') == "分场") { inScenes = true; return@forEachIndexed }
                val match = marked.matchEntire(line)
                if (match != null) {
                    val status = when (match.groupValues[1].lowercase()) { "x" -> SceneStatus.DONE; ">" -> SceneStatus.CURRENT; else -> SceneStatus.TODO }
                    if(match.groupValues[2].isNotBlank()) scenes += ReferenceScene(index, match.groupValues[2], status)
                } else if (inScenes && line.isNotEmpty()) scenes += ReferenceScene(index, line.removePrefix("- "), SceneStatus.TODO)
                else if (!inScenes && line.isNotEmpty()) goal += line.removePrefix("本章目标：").removePrefix("目标：").removePrefix("目标:")
            }
            return ChapterReference(goal.joinToString("\n"), scenes)
        }
        fun advance(body: String, scene: ReferenceScene): String {
            val lines = body.split('\n').toMutableList()
            val raw = lines.getOrNull(scene.line) ?: return body
            val next = when (scene.status) { SceneStatus.TODO -> SceneStatus.CURRENT; SceneStatus.CURRENT -> SceneStatus.DONE; SceneStatus.DONE -> SceneStatus.TODO }
            fun markedLine(raw:String,status:SceneStatus):String {
                if(marked.matches(raw.trim())){val at=raw.indexOf('[');return raw.take(at+1)+status.marker+raw.drop(at+2)}
                val indent=raw.takeWhile{it==' '||it=='\t'}
                return indent+"- ["+status.marker+"] "+raw.removePrefix(indent).removePrefix("- ")
            }
            val replacement = markedLine(raw,next)
            if (next == SceneStatus.CURRENT) parse(body).scenes.filter { it.line != scene.line && it.status == SceneStatus.CURRENT }.forEach { other ->
                val previous = lines[other.line]
                lines[other.line] = markedLine(previous,SceneStatus.TODO)
            }
            lines[scene.line] = replacement
            return lines.joinToString("\n")
        }
    }
}
