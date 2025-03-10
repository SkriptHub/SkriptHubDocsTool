package net.skripthub.docstool.modals

import com.google.gson.JsonObject

data class AddonMetadata(var version: String) {

    fun getJsonElement(): JsonObject {
        val jsonNode = JsonObject()
        jsonNode.addProperty("version", version)
        return jsonNode
    }
}
