package net.skripthub.docstool.modals

import com.google.gson.JsonObject

data class DocumentationEntryNode (
        val name: String,
        val isRequired: Boolean,
        val isSection: Boolean
) {
    fun getJsonElement(): JsonObject {
        val jsonNode = JsonObject()
        jsonNode.addProperty("name", name)
        jsonNode.addProperty("isRequired", isRequired)
        jsonNode.addProperty("isSection", isSection)
        return jsonNode
    }
}
