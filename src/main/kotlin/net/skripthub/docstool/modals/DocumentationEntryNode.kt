package net.skripthub.docstool.modals

import com.google.gson.JsonObject
import org.skriptlang.skript.lang.entry.EntryData
import org.skriptlang.skript.lang.entry.SectionEntryData
import org.skriptlang.skript.lang.entry.util.TriggerEntryData

data class DocumentationEntryNode(val name: String, val isRequired: Boolean, val isSection: Boolean, val defaultValue: String?) {

    companion object {

        fun from(entry: EntryData<*>) : DocumentationEntryNode {
            return DocumentationEntryNode(
                entry.key,
                !entry.isOptional,
                entry is SectionEntryData || entry is TriggerEntryData,
                entry.defaultValue?.toString()
            )
        }
    }

    fun asJsonElement(): JsonObject {
        val jsonNode = JsonObject()
        jsonNode.addProperty("name", name)
        jsonNode.addProperty("isRequired", isRequired)
        jsonNode.addProperty("isSection", isSection)
        jsonNode.addProperty("defaultValue", defaultValue)
        return jsonNode
    }
}
