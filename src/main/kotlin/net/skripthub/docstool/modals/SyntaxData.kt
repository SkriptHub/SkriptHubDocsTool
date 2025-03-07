package net.skripthub.docstool.modals

import ch.njol.util.StringUtils

@Suppress("ArrayInDataClass")
data class SyntaxData(var id: String? = null,
                      var name: String? = null,
                      var since: Array<String>? = null,
                      var returnType: String? = null,
                      var requiredPlugins: Array<String>? = null,
                      var description: Array<String>? = null,
                      var examples: Array<String>? = null,
                      var patterns: Array<String>? = null,
                      var usage: Array<String>? = null,
                      var changers: Array<String>? = null,
                      var eventValues: Array<String>? = null,
                      var cancellable: Boolean? = null,
                      var keywords: Array<String>? = null,
                      var entries: Array<DocumentationEntryNode>? = null,
    ) {

    fun toMap(): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()

        addProperty(map, "id", id!!)
        addProperty(map, "name", name!!)

        addProperty(map, "return_type", returnType)
        addArray(map, "description", description)
        addArray(map, "examples", examples)
        addArray(map, "since", since)
        addArray(map, "patterns", patterns)
        addArray(map, "Changers", changers)
        addArray(map, "event_values", eventValues)
        addArray(map, "required_plugins", requiredPlugins)
        addArray(map, "entries", entries)
        addArray(map, "keywords", keywords)
        usage?.let { addProperty(map, "usage", *it) }
        cancellable?.let { map["cancellable"] = it }
        return map
    }

    private fun addProperty(map: MutableMap<String, Any>, property: String, vararg values: String?) {
        map[property] = if (values.filterNotNull().isNotEmpty()) StringUtils.join(values.filterNotNull(), "\n") else return
    }

    private fun <T> addArray(map: MutableMap<String, Any>, property: String, array: Array<T>?) {
        map[property] = if (!array.isNullOrEmpty()) array else return
    }
}
