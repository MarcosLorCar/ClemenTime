package com.example.clementime

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Test
import java.io.File

class LayoutPreservingTextStripper : PDFTextStripper() {
    init {
        sortByPosition = true
    }

    val linesMap = mutableMapOf<Int, MutableList<TextPosition>>()

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        for (tp in textPositions) {
            val yKey = (tp.yDirAdj / 5.0).toInt()
            linesMap.getOrPut(yKey) { mutableListOf() }.add(tp)
        }
    }

    fun getLayoutText(): String {
        val sb = StringBuilder()
        val sortedYKeys = linesMap.keys.sorted()
        val allPositions = linesMap.values.flatten()
        val maxX = allPositions.map { it.xDirAdj }.maxOrNull() ?: 800f
        
        val scale = maxOf(1.0f, maxX / 220f)

        for (yKey in sortedYKeys) {
            val charPositions = linesMap[yKey] ?: continue
            val sortedChars = charPositions.sortedBy { it.xDirAdj }
            val lineSb = StringBuilder()
            
            for (tp in sortedChars) {
                val x = tp.xDirAdj
                val col = (x / scale).toInt()
                
                while (lineSb.length < col) {
                    lineSb.append(' ')
                }
                
                val unicode = tp.unicode
                if (lineSb.length == col) {
                    lineSb.append(unicode)
                } else {
                    lineSb.append(unicode)
                }
            }
            sb.append(lineSb.toString()).append("\n")
        }
        return sb.toString()
    }
}

class ExampleUnitTest {
    @Test
    fun testLayoutPreservingStripper() {
        val pdfFile = File("/home/orange/Downloads/ESI2026-27-GRADO_1C_Grupos.pdf")
        if (!pdfFile.exists()) {
            println("PDF file does not exist!")
            return
        }

        val doc = PDDocument.load(pdfFile)
        val stripper = LayoutPreservingTextStripper()
        stripper.startPage = 1
        stripper.endPage = 1
        
        // This parses the page and populates linesMap
        stripper.getText(doc)
        doc.close()

        val layoutText = stripper.getLayoutText()
        println("=== RECONSTRUCTED LAYOUT TEXT ===")
        println(layoutText)
        println("=================================")
    }
}