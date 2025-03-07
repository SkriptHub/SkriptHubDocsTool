package net.skripthub.docstool.documentation

import com.google.gson.*
import net.skripthub.docstool.modals.AddonData
import net.skripthub.docstool.modals.AddonMetadata
import net.skripthub.docstool.modals.DocumentationEntryNode
import net.skripthub.docstool.modals.SyntaxData
import java.io.BufferedWriter
import java.io.IOException
import java.util.*


/**
 * @author Tuke_Nuke on 30/07/2017
 */
class JsonFile(raw: Boolean) {

    private val gson: Gson

    init {
        val gson = GsonBuilder().disableHtmlEscaping()
        if (!raw)
            gson.enableComplexMapKeySerialization().setPrettyPrinting()
        this.gson = gson.create()
    }

    @Throws(IOException::class)
    fun write(writer: BufferedWriter, addon: AddonData) {
        val json = JsonObject()
        addMetadata(json, addon.metadata)
        addSection(json, "events", addon.events)
        addSection(json, "conditions", addon.conditions)
        addSection(json, "effects", addon.effects)
        addSection(json, "expressions", addon.expressions)
        addSection(json, "types", addon.types)
        addSection(json, "functions", addon.functions)
        addSection(json, "sections", addon.sections)
        addSection(json, "structures", addon.structures)
        gson.toJson(json, writer)
    }

    private fun addSection(json: JsonObject, property: String, list: MutableList<SyntaxData>) {
        val array = JsonArray()
        list.map(::getJsonSyntax).filter { it.has("id") }.forEach { array.add(it) }
        if (array.size() > 0)
            json.add(property, array)
    }

    private fun getJsonSyntax(info: SyntaxData): JsonObject {
        val syntax = JsonObject()
        for (entry in info.toMap().entries) {
            val property = entry.key
            when (entry.value) {
                is String -> syntax.addProperty(property, entry.value as String)
                is Boolean -> syntax.addProperty(property, entry.value as Boolean)
                is Array<*> -> {
                    val json = JsonArray()
                    for (arrayValue in entry.value as Array<*>) {
                        if (arrayValue is String) {
                            json.add(JsonPrimitive(arrayValue))
                        } else if (arrayValue is DocumentationEntryNode) {
                            json.add(arrayValue.asJsonElement())
                        } else {
                            throw IllegalArgumentException("Unsupported Entry Type: found ${entry.value.javaClass} with key ${entry.key}")
                        }
                    }
                    syntax.add(property, json)
                }
                else -> throw IllegalArgumentException("Unsupported Entry Type: found ${entry.value.javaClass} with key ${entry.key}")
            }
        }
        return syntax
    }

    private fun addMetadata(json: JsonObject, metadata: AddonMetadata) {
        json.add("metadata", metadata.getJsonElement())
    }

}
