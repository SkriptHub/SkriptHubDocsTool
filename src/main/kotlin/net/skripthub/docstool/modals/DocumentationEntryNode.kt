package net.skripthub.docstool.modals

import com.google.gson.JsonObject

data class DocumentationEntryNode (
        val name: String,
        val isRequired: Boolean,
        val isSection: Boolean,
        val defaultValue: String?
) {
    constructor(name: String,
                isRequired: Boolean,
                isSection: Boolean) : this(name, isRequired, isSection, null) {}

    constructor(name: String,
                isRequired: Boolean,
                defaultValue: String) : this(name, isRequired, false, defaultValue) {}
    fun getJsonElement(): JsonObject {
        val jsonNode = JsonObject()
        jsonNode.addProperty("name", name)
        jsonNode.addProperty("isRequired", isRequired)
        jsonNode.addProperty("isSection", isSection)
        jsonNode.addProperty("defaultValue", defaultValue)
        return jsonNode
    }
}
