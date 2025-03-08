package net.skripthub.docstool.modals

import ch.njol.util.StringUtils

@Suppress("ArrayInDataClass")
data class SyntaxData(var id: String? = null,
                      var name: String? = null,
                      var description: Array<String>? = null,
                      var examples: Array<String>? = null,
                      var usage: Array<String>? = null,
                      var since: Array<String>? = null,
                      var returnType: String? = null,
                      var changers: Array<String>? = null,
                      var patterns: Array<String>? = null,
                      var eventValues: Array<String>? = null,
                      var cancellable: Boolean? = null,
                      var requiredPlugins: Array<String>? = null,
                      var entries: Array<DocumentationEntryNode>? = null,
                      var keywords: Array<String>? = null,
    ) {

    fun toMap(): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()

        addProperty(map, "id", id!!)
        addProperty(map, "name", name!!)
        addArray(map, "description", description)
        addArray(map, "examples", examples)
        usage?.let { addProperty(map, "usage", *it) }
        addArray(map, "since", since)
        addProperty(map, "return type", returnType)
        addArray(map, "changers", changers)
        addArray(map, "patterns", patterns)
        addArray(map, "event values", eventValues)
        cancellable?.let { map["cancellable"] = it }
        addArray(map, "required plugins", requiredPlugins)
        addArray(map, "entries", entries)
        addArray(map, "keywords", keywords)
        return map
    }

    private fun addProperty(map: MutableMap<String, Any>, property: String, vararg values: String?) {
        map[property] = if (values.filterNotNull().isNotEmpty()) StringUtils.join(values.filterNotNull(), "\n") else return
    }

    private fun <T> addArray(map: MutableMap<String, Any>, property: String, array: Array<T>?) {
        map[property] = if (!array.isNullOrEmpty()) array else return
    }
}
