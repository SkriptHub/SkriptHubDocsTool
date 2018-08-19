package net.skripthub.docstool.modals

import java.util.*


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
                      var cancellable: Boolean? = null
                      ) {
    fun toMap(): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        addProperty(map, "ID", id!!)
        addProperty(map, "Name", name!!)
        if( description != null){
            addArray(map, "Description", description!!)
        }
        if (examples != null){
            addArray(map, "Examples", examples!!)
        }
        if(usage != null){
            addProperty(map, "Usage", *usage!!)
        }
        if(since != null){
            addArray(map, "Since", since!!)
        }
        if(returnType != null){
            addProperty(map, "Return type", returnType!!)
        }
        if(changers != null){
            addArray(map, "Changers", changers!!)
        }
        addArray(map, "Patterns", patterns!!)
        if(eventValues != null){
            addArray(map, "Event values", eventValues!!)
        }
        if (cancellable != null) {
            map["Cancellable"] = cancellable!!
        }
        if (requiredPlugins != null){
            addArray(map, "Required Plugins", requiredPlugins!!)
        }
        return map
    }

    private fun addProperty(map: MutableMap<String, Any>, property: String, vararg value: String) {
        if (value.isEmpty())
            return
        val sb = StringBuilder()
        for (str in value) {
            if (str != null && !str.isEmpty()) {
                if (sb.isNotEmpty())
                    sb.append("\n")
                sb.append(str)
            }
        }
        if (sb.isNotEmpty())
            map[property] = sb.toString()
    }

    private fun addArray(map: MutableMap<String, Any>, property: String, array: Array<String>) {
        if (array.isEmpty())
            return
        map[property] = array
    }
}