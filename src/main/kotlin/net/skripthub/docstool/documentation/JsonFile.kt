package net.skripthub.docstool.documentation

import com.google.gson.*
import net.skripthub.docstool.modals.AddonData
import net.skripthub.docstool.modals.SyntaxData
import java.io.BufferedWriter
import java.io.IOException


/**
 * @author Tuke_Nuke on 30/07/2017
 */
class JsonFile(raw: Boolean) : FileType("json") {

    private val gson: Gson

    init {
        val gson = GsonBuilder().disableHtmlEscaping()
        if (!raw)
            gson.enableComplexMapKeySerialization().setPrettyPrinting()
        this.gson = gson.create()
    }

    @Throws(IOException::class)
    override fun write(writer: BufferedWriter, addon: AddonData) {
        val json = JsonObject()
        addSection(json, "events", addon.events)
        addSection(json, "conditions", addon.conditions)
        addSection(json, "effects", addon.effects)
        addSection(json, "expressions", addon.expressions)
        addSection(json, "types", addon.types)
        addSection(json, "functions", addon.functions)
        gson.toJson(json, writer)
    }

    fun addSection(json: JsonObject, property: String, list: MutableList<SyntaxData>) {
        val array = JsonArray()
        for (syntax in list) {
            val jsonSyntax = getJsonSyntax(syntax)
            if (jsonSyntax.has("patterns"))
                array.add(getJsonSyntax(syntax))
        }
        if (array.size() > 0)
            json.add(property, array)
    }

    fun getJsonSyntax(info: SyntaxData): JsonObject {
        val syntax = JsonObject()
        for (entry in info.toMap().entries) {
            val property = entry.key.toLowerCase().replace('_', ' ')
            if (entry.value is String)
                syntax.addProperty(property, entry.value as String)
            else if (entry.value is Boolean)
                syntax.addProperty(property, entry.value as Boolean)
            else {
                val json = JsonArray()
                for (str in entry.value as Array<String>) {
                    json.add(JsonPrimitive(str))
                }
                syntax.add(property, json)
            }
        }
        return syntax
    }
}

