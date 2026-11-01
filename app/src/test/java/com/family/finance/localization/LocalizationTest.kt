package com.family.finance.localization

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the English/Tamil resources on every build (PRD 7.2, 7.7): no missing or extra
 * Tamil entries, identical placeholders, real Tamil text, and no user-visible strings
 * hard-coded in Kotlin (CLAUDE.md).
 */
class LocalizationTest {

    // Unit tests run with the module directory (app/) as the working directory.
    private val res = File("src/main/res")
    private val english = load(File(res, "values/strings.xml"))
    private val tamil = load(File(res, "values-ta/strings.xml"))

    private data class Entry(val text: String, val translatable: Boolean)

    /** Strings and each plural quantity, keyed "name" or "name#quantity". */
    private fun load(file: File): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = linkedMapOf<String, Entry>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            out[e.getAttribute("name")] = Entry(e.textContent, e.getAttribute("translatable") != "false")
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val p = plurals.item(i) as Element
            val items = p.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                out["${p.getAttribute("name")}#${item.getAttribute("quantity")}"] = Entry(item.textContent, true)
            }
        }
        return out
    }

    private val placeholder = Regex("""%(\d+\$)?[sdf]""")

    @Test fun `every translatable English string has a Tamil version and vice versa`() {
        val needed = english.filterValues { it.translatable }.keys
        assertWithMessage("Missing in values-ta").that(needed - tamil.keys).isEmpty()
        assertWithMessage("In Tamil but not English").that(tamil.keys - english.keys).isEmpty()
        assertWithMessage("Non-translatable strings must not be duplicated in Tamil")
            .that(english.filterValues { !it.translatable }.keys.intersect(tamil.keys)).isEmpty()
    }

    @Test fun `placeholders match between languages`() {
        tamil.forEach { (key, ta) ->
            val en = english.getValue(key)
            assertWithMessage("Placeholders differ for $key")
                .that(placeholder.findAll(ta.text).map { it.value }.sorted().toList())
                .isEqualTo(placeholder.findAll(en.text).map { it.value }.sorted().toList())
        }
    }

    @Test fun `no empty strings`() {
        (english + tamil.mapKeys { "ta:${it.key}" }).forEach { (key, e) ->
            assertWithMessage("Empty string $key").that(e.text.isNotBlank()).isTrue()
        }
    }

    @Test fun `Tamil strings contain Tamil text, not leftover English`() {
        // Pure-format strings legitimately have no words to translate.
        val formatOnly = setOf("transfer_from_to", "period_range", "conflict_version_line")
        val tamilScript = Regex("[஀-௿]")
        val untranslated = tamil.filter { (key, e) -> key !in formatOnly && !tamilScript.containsMatchIn(e.text) }.keys
        assertWithMessage("Tamil file has strings with no Tamil characters").that(untranslated).isEmpty()
    }

    @Test fun `no user-visible text is hard-coded in Kotlin UI code`() {
        // A literal passed as visible text or a content description...
        val literal = Regex("""(?:\bText\(|contentDescription\s*=)\s*"((?:[^"\\]|\\.)*)"""")
        // ...is fine if, once ${...} / $name code is removed, no words remain (e.g. "${count}", "₹").
        val code = Regex("""\$\{[^}]*\}|\$[A-Za-z_]\w*""")
        val words = Regex("[A-Za-z஀-௿]")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val hardCoded = literal.findAll(line).any { words.containsMatchIn(it.groupValues[1].replace(code, "")) }
                    if (hardCoded) "${file.name}:${i + 1}: ${line.trim()}" else null
                }
            }.toList()
        assertWithMessage("Use string resources instead").that(offenders).isEmpty()
    }

    @Test fun `resource files are found`() {
        assertThat(english).isNotEmpty()
        assertThat(tamil).isNotEmpty()
    }
}
