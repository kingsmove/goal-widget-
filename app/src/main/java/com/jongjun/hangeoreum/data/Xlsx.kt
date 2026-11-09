package com.jongjun.hangeoreum.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** One cell: its text and, if Excel stored it as a number (dates, times), the number. */
data class XCell(val text: String, val num: Double?)

/** Minimal .xlsx reader: sheet name -> rows -> cells (column A = index 0). */
object Xlsx {
    fun read(bytes: ByteArray): Map<String, List<List<XCell?>>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && e.name.endsWith(".xml") || e.name.endsWith(".rels")) files[e.name] = zip.readBytes()
            }
        }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        fun doc(name: String) = files[name]?.let { factory.newDocumentBuilder().parse(ByteArrayInputStream(it)).documentElement }

        val shared = mutableListOf<String>()
        doc("xl/sharedStrings.xml")?.let { root ->
            val sis = root.getElementsByTagName("si")
            for (i in 0 until sis.length) shared += allText(sis.item(i) as Element)
        }

        val rels = HashMap<String, String>()
        doc("xl/_rels/workbook.xml.rels")?.let { root ->
            val list = root.getElementsByTagName("Relationship")
            for (i in 0 until list.length) {
                val r = list.item(i) as Element
                val target = r.getAttribute("Target").removePrefix("/").let { if (it.startsWith("xl/")) it else "xl/$it" }
                rels[r.getAttribute("Id")] = target
            }
        }

        val out = LinkedHashMap<String, List<List<XCell?>>>()
        val wb = doc("xl/workbook.xml") ?: error("엑셀 파일이 아니에요")
        val sheets = wb.getElementsByTagName("sheet")
        for (i in 0 until sheets.length) {
            val s = sheets.item(i) as Element
            val name = s.getAttribute("name")
            val rid = s.getAttribute("r:id")
            val path = rels[rid] ?: "xl/worksheets/sheet${i + 1}.xml"
            val root = doc(path) ?: continue
            out[name] = readSheet(root, shared)
        }
        return out
    }

    private fun allText(e: Element): String {
        val ts = e.getElementsByTagName("t")
        val sb = StringBuilder()
        for (i in 0 until ts.length) sb.append(ts.item(i).textContent)
        return sb.toString()
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    private fun readSheet(root: Element, shared: List<String>): List<List<XCell?>> {
        val rows = sortedMapOf<Int, MutableList<XCell?>>()
        val rowEls = root.getElementsByTagName("row")
        for (i in 0 until rowEls.length) {
            val row = rowEls.item(i) as Element
            val rIdx = row.getAttribute("r").toIntOrNull()?.minus(1) ?: i
            val cells = mutableListOf<XCell?>()
            val cs = row.getElementsByTagName("c")
            for (j in 0 until cs.length) {
                val c = cs.item(j) as Element
                val col = c.getAttribute("r").let { if (it.isEmpty()) cells.size else colIndex(it) }
                val type = c.getAttribute("t")
                val v = c.getElementsByTagName("v").let { if (it.length > 0) it.item(0).textContent else null }
                val cell = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }?.let { XCell(it, null) }
                    "inlineStr" -> XCell(allText(c), null)
                    "str" -> v?.let { XCell(it, null) }
                    "b" -> v?.let { XCell(if (it == "1") "TRUE" else "FALSE", null) }
                    "e" -> null
                    else -> v?.let { XCell(it, it.toDoubleOrNull()) }
                }
                while (cells.size <= col) cells.add(null)
                cells[col] = cell
            }
            rows[rIdx] = cells
        }
        val maxRow = rows.keys.maxOrNull() ?: -1
        return (0..maxRow).map { rows[it] ?: mutableListOf() }
    }
}
