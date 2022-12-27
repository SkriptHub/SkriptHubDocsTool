package net.skripthub.docstool.modals

data class AddonData(var name: String,
                     var events: MutableList<SyntaxData> = mutableListOf(),
                     var conditions: MutableList<SyntaxData> = mutableListOf(),
                     var effects: MutableList<SyntaxData> = mutableListOf(),
                     var expressions: MutableList<SyntaxData> = mutableListOf(),
                     var types: MutableList<SyntaxData> = mutableListOf(),
                     var functions: MutableList<SyntaxData> = mutableListOf(),
                     var sections: MutableList<SyntaxData> = mutableListOf(),
                     var structures: MutableList<SyntaxData> = mutableListOf()) {
    fun sortLists() {
        events.sortBy { info -> info.name }
        conditions.sortBy { info -> info.name }
        effects.sortBy { info -> info.name }
        expressions.sortBy { info -> info.name }
        types.sortBy { info -> info.name }
        functions.sortBy { info -> info.name }
        sections.sortBy { info -> info.name }
        structures.sortBy { info -> info.name }
    }
}