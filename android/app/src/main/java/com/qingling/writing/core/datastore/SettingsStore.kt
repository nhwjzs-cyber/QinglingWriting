package com.qingling.writing.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.qingling.writing.core.model.EditorAppearance
import com.qingling.writing.core.model.EditorCanvas
import com.qingling.writing.core.model.EditorFont
import com.qingling.writing.core.model.FormatOptions
import com.qingling.writing.core.model.ParagraphSpacing
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/**
 * 外观主题三态(G04):首次默认**深色**(docs/01 P09 基线要求);
 * FOLLOW_SYSTEM 才响应系统明暗。选择立即持久化到 DataStore,重启保持。
 * DataStore 只放偏好(docs/03 §6 约束),不放任何正文/结构数据。
 */
enum class ThemeMode { DARK, LIGHT, FOLLOW_SYSTEM }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.settingsDataStore,
) {

    /** 缺省与非法值都回落到 DARK(首次启动默认深色,G04)。 */
    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { prefs ->
            val stored = prefs[KEY_THEME_MODE]
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.DARK
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    val reduceMotion: Flow<Boolean> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { it[KEY_REDUCE_MOTION] ?: false }

    suspend fun setReduceMotion(value: Boolean) { dataStore.edit { it[KEY_REDUCE_MOTION] = value } }

    // A read failure must never silently turn off an enabled privacy gate.
    val appLockEnabled: Flow<Boolean> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences().toMutablePreferences().apply { this[APP_LOCK] = true }) else throw e
    }.map { it[APP_LOCK] ?: false }
    suspend fun setAppLockEnabled(value: Boolean) { dataStore.edit { it[APP_LOCK] = value } }

    val recentSearch: Flow<String> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { it[RECENT_SEARCH].orEmpty() }
    suspend fun setRecentSearch(value: String) { dataStore.edit { it[RECENT_SEARCH] = value.take(200) } }
    val proofWords = dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.map { it[PROOF_WORDS].orEmpty() }
    suspend fun setProofWords(value: String) { require(value.length <= 5000); dataStore.edit { it[PROOF_WORDS] = value } }

    private val safePreferences = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }
    // Missing consent, a changed policy, or a read failure always keeps the entry gate closed.
    val privacyAccepted: Flow<Boolean> = dataStore.data.map {
        it[PRIVACY_CONSENT_VERSION] == com.qingling.writing.core.privacy.PrivacyPolicy.VERSION
    }.retryWhen { failure, _ ->
        if (failure is IOException) {
            emit(false)
            kotlinx.coroutines.delay(1_000)
            true
        } else false
    }
    suspend fun acceptPrivacyPolicy() {
        dataStore.edit { it[PRIVACY_CONSENT_VERSION] = com.qingling.writing.core.privacy.PrivacyPolicy.VERSION }
    }

    val appearance: Flow<EditorAppearance> = safePreferences.map { p ->
        EditorAppearance(
            fontSize = p[FONT_SIZE] ?: 17,
            lineHeightPercent = p[LINE_HEIGHT] ?: 190,
            gutter = p[GUTTER] ?: 34,
            font = EditorFont.entries.firstOrNull { it.name == p[FONT] } ?: EditorFont.SYSTEM,
            canvas = EditorCanvas.entries.firstOrNull { it.name == p[CANVAS] } ?: EditorCanvas.THEME,
            backgroundHex = p[BG] ?: "121212", textHex = p[TEXT] ?: "E6E6E6",
            imagePath = p[IMAGE] ?: "", imageDimPercent = p[DIM] ?: 75,
        ).normalized()
    }
    val editorGestureGuideSeen = safePreferences.map { it[booleanPreferencesKey("editor_gesture_guide_seen_v1")] ?: false }
    suspend fun dismissEditorGestureGuide() { dataStore.edit { it[booleanPreferencesKey("editor_gesture_guide_seen_v1")] = true } }
    val punctuationBar = safePreferences.map { it[PUNCTUATION] ?: true }
    val customFontId=safePreferences.map {it[CUSTOM_FONT].orEmpty()}
    suspend fun clearFontIfSelected(id: String) {dataStore.edit {if(it[CUSTOM_FONT]==id) it[CUSTOM_FONT]=""}}
    val dailyGoal = safePreferences.map { (it[DAILY_GOAL] ?: 2000).coerceIn(0,1_000_000) }
    val shelfSort=safePreferences.map {p->com.qingling.writing.core.model.ShelfSort.entries.firstOrNull {it.name==p[SHELF_SORT]} ?: com.qingling.writing.core.model.ShelfSort.UPDATED}
    suspend fun setShelfSort(value: com.qingling.writing.core.model.ShelfSort) {dataStore.edit {it[SHELF_SORT]=value.name}}
    suspend fun setDailyGoal(value: Int) { require(value in 0..1_000_000); dataStore.edit { it[DAILY_GOAL] = value } }
    val currentLine = safePreferences.map { it[CURRENT_LINE] ?: false }
    val autoHistoryLimit = safePreferences.map { (it[AUTO_HISTORY_LIMIT] ?: 20).takeIf { n -> n in listOf(20, 50, 100) } ?: 20 }
    suspend fun setAutoHistoryLimit(value: Int) { require(value in listOf(20, 50, 100)); dataStore.edit { it[AUTO_HISTORY_LIMIT] = value } }
    val exportPreferences=safePreferences.map{com.qingling.writing.core.model.ExportPreferences(
        com.qingling.writing.core.model.ExportFormat.entries.firstOrNull{v->v.name==it[EXPORT_FORMAT]}?:com.qingling.writing.core.model.ExportFormat.TXT,
        it[EXPORT_BOOK]?:true,it[EXPORT_VOLUMES]?:true,it[EXPORT_CHAPTERS]?:true)}
    suspend fun setExportPreferences(value:com.qingling.writing.core.model.ExportPreferences){dataStore.edit{it[EXPORT_FORMAT]=value.format.name;it[EXPORT_BOOK]=value.book;it[EXPORT_VOLUMES]=value.volumes;it[EXPORT_CHAPTERS]=value.chapters}}
    val lastCoverStyle=safePreferences.map { prefs->val p=prefs[LAST_COVER].orEmpty().split(',');com.qingling.writing.core.model.CoverStyle((p.getOrNull(0)?.toIntOrNull()?:0).coerceIn(0,4),(p.getOrNull(1)?.toIntOrNull()?:0).coerceIn(0,2)) }
    suspend fun setLastBookDefaults(author:String,style:com.qingling.writing.core.model.CoverStyle){dataStore.edit{it[LAST_AUTHOR]=author.trim();it[LAST_COVER]="${style.color},${style.motif}"}}
    val lastAuthor=safePreferences.map{it[LAST_AUTHOR].orEmpty()}
    suspend fun setLastAuthor(value:String){dataStore.edit{it[LAST_AUTHOR]=value.trim()}}
    val themeInitialized = safePreferences.map {it[KEY_THEME_MODE]!=null || it[THEME_INTRO]==true}
    suspend fun finishThemeIntro(){dataStore.edit{it[THEME_INTRO]=true}}
    val coverStyles = safePreferences.map {prefs -> prefs.asMap().mapNotNull { (key,value) ->
        if(!key.name.startsWith("cover_style_")) null else {val parts=value.toString().split(',');val color=parts.getOrNull(0)?.toIntOrNull();val motif=parts.getOrNull(1)?.toIntOrNull();
            if(color in 0..4 && motif in 0..2) key.name.removePrefix("cover_style_") to com.qingling.writing.core.model.CoverStyle(color!!,motif!!) else null}
    }.toMap() }
    suspend fun setCoverStyle(bookId:String,style:com.qingling.writing.core.model.CoverStyle) {dataStore.edit {it[stringPreferencesKey("cover_style_"+bookId)]=style.color.toString()+","+style.motif}}
    val chapterSummary = safePreferences.map { it[CHAPTER_SUMMARY] ?: false }
    suspend fun setChapterSummary(value: Boolean) { dataStore.edit { it[CHAPTER_SUMMARY] = value } }
    val swipePanels = safePreferences.map { it[SWIPE_PANELS] ?: true }
    suspend fun setSwipePanels(value: Boolean) { dataStore.edit { it[SWIPE_PANELS] = value } }
    val referenceDrawerEnabled = safePreferences.map { it[REFERENCE_ENABLED] ?: true }
    suspend fun setReferenceDrawerEnabled(value: Boolean) { dataStore.edit { it[REFERENCE_ENABLED] = value } }
    val referenceDrawerLevel = safePreferences.map { (it[REFERENCE_LEVEL] ?: 0).coerceIn(0, 2) }
    suspend fun setReferenceDrawerLevel(value: Int) { require(value in 0..2); dataStore.edit { it[REFERENCE_LEVEL] = value } }
    val referenceTab = safePreferences.map { (it[REFERENCE_TAB] ?: 0).coerceIn(0, 3) }
    suspend fun setReferenceTab(value: Int) { require(value in 0..3); dataStore.edit { it[REFERENCE_TAB] = value } }
    val accessoryPage = safePreferences.map { (it[ACCESSORY_PAGE] ?: 0).coerceIn(0, 3) }
    suspend fun setAccessoryPage(value: Int) { require(value in 0..3); dataStore.edit { it[ACCESSORY_PAGE] = value } }
    val coordinateMode = safePreferences.map { (it[COORDINATE_MODE] ?: 0).coerceIn(0, 2) }
    suspend fun setCoordinateMode(value: Int) { require(value in 0..2); dataStore.edit { it[COORDINATE_MODE] = value } }
    fun referenceHintSeen(bookId: String) = safePreferences.map { it[booleanPreferencesKey("reference_hint_" + bookId)] ?: false }
    suspend fun markReferenceHintSeen(bookId: String) { dataStore.edit { it[booleanPreferencesKey("reference_hint_" + bookId)] = true } }

    val keepAwake = safePreferences.map { it[KEEP_AWAKE] ?: false }
    val typewriter = safePreferences.map { it[TYPEWRITER] ?: false }
    suspend fun setTypewriter(value: Boolean) { dataStore.edit { it[TYPEWRITER] = value } }
    suspend fun setKeepAwake(value: Boolean) { dataStore.edit { it[KEEP_AWAKE] = value } }
    suspend fun setPunctuationBar(value: Boolean) { dataStore.edit { it[PUNCTUATION] = value } }
    suspend fun setCurrentLine(value: Boolean) { dataStore.edit { it[CURRENT_LINE] = value } }
    suspend fun setAppearance(value: EditorAppearance,customFontId: String?=null) {
        val a = value.normalized()
        dataStore.edit { p ->
            if(customFontId!=null) p[CUSTOM_FONT]=customFontId
            p[FONT_SIZE] = a.fontSize; p[LINE_HEIGHT] = a.lineHeightPercent; p[GUTTER] = a.gutter
            p[FONT] = a.font.name; p[CANVAS] = a.canvas.name
            p[BG] = a.backgroundHex; p[TEXT] = a.textHex; p[IMAGE] = a.imagePath; p[DIM] = a.imageDimPercent
        }
    }
    val formatOptions: Flow<FormatOptions> = safePreferences.map { p ->
        FormatOptions(p[INDENT] ?: true, p[TRIM] ?: true,
            ParagraphSpacing.entries.firstOrNull { it.name == p[PARAGRAPHS] } ?: ParagraphSpacing.KEEP)
    }
    suspend fun setFormatOptions(o: FormatOptions) {
        dataStore.edit { p -> p[INDENT] = o.indentTwoCharacters; p[TRIM] = o.trimLineEnds; p[PARAGRAPHS] = o.spacing.name }
    }

    companion object {
        private val REFERENCE_ENABLED = booleanPreferencesKey("reference_drawer_enabled")
        private val REFERENCE_LEVEL = intPreferencesKey("reference_drawer_level")
        private val REFERENCE_TAB = intPreferencesKey("reference_tab")
        private val ACCESSORY_PAGE = intPreferencesKey("accessory_page")
        private val COORDINATE_MODE = intPreferencesKey("coordinate_mode")
        private val PRIVACY_CONSENT_VERSION = stringPreferencesKey("privacy_consent_version")
        private val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        private val PUNCTUATION = booleanPreferencesKey("punctuation_bar")
        private val CUSTOM_FONT = stringPreferencesKey("imported_font_id")
        private val DAILY_GOAL = intPreferencesKey("daily_net_goal")
        private val SHELF_SORT = stringPreferencesKey("shelf_sort")
        private val CURRENT_LINE = booleanPreferencesKey("current_line")
        private val AUTO_HISTORY_LIMIT = intPreferencesKey("auto_history_limit")
        private val EXPORT_FORMAT=stringPreferencesKey("export_default_format")
        private val EXPORT_BOOK=booleanPreferencesKey("export_default_book")
        private val EXPORT_VOLUMES=booleanPreferencesKey("export_default_volumes")
        private val EXPORT_CHAPTERS=booleanPreferencesKey("export_default_chapters")
        private val LAST_COVER=stringPreferencesKey("last_cover_style")
        private val LAST_AUTHOR=stringPreferencesKey("last_book_author")
        private val THEME_INTRO=booleanPreferencesKey("theme_intro_done")
        private val CHAPTER_SUMMARY = booleanPreferencesKey("chapter_completion_summary")
        private val SWIPE_PANELS = booleanPreferencesKey("swipe_panels")
        private val KEEP_AWAKE = booleanPreferencesKey("editor_keep_awake")
        private val TYPEWRITER = booleanPreferencesKey("editor_typewriter")
        private val RECENT_SEARCH = stringPreferencesKey("recent_search")
        private val PROOF_WORDS = stringPreferencesKey("proof_words")
        private val FONT_SIZE = intPreferencesKey("editor_font_size")
        private val LINE_HEIGHT = intPreferencesKey("editor_line_height")
        private val GUTTER = intPreferencesKey("editor_gutter")
        private val FONT = stringPreferencesKey("editor_font")
        private val CANVAS = stringPreferencesKey("editor_canvas")
        private val BG = stringPreferencesKey("editor_background")
        private val TEXT = stringPreferencesKey("editor_text")
        private val IMAGE = stringPreferencesKey("editor_image")
        private val DIM = intPreferencesKey("editor_image_dim")
        private val INDENT = booleanPreferencesKey("format_indent")
        private val TRIM = booleanPreferencesKey("format_trim")
        private val PARAGRAPHS = stringPreferencesKey("format_paragraphs")
        private val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
