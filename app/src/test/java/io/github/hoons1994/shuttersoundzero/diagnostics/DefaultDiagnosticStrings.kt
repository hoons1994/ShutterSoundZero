package io.github.hoons1994.shuttersoundzero.diagnostics

import io.github.hoons1994.shuttersoundzero.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// JVM formatter tests use the same default text as the Android app.
private val defaultStrings by lazy {
    val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    buildMap {
        for (file in listOf("diagnostics.xml", "camera_routine.xml")) {
            val nodes = parser.parse(File("src/main/res/values", file)).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val id = R.string::class.java.getField(element.getAttribute("name")).getInt(null)
                put(id, element.textContent)
            }
        }
    }
}

internal fun defaultDiagnosticText(id: Int): String = defaultStrings.getValue(id)
