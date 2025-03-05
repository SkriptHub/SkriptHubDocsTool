package net.skripthub.docstool.documentation

import ch.njol.skript.classes.Changer
import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.doc.*
import ch.njol.skript.lang.ExpressionInfo
import ch.njol.skript.lang.SkriptEventInfo
import ch.njol.skript.lang.SyntaxElementInfo
import ch.njol.skript.lang.function.JavaFunction
import ch.njol.skript.registrations.Classes
import ch.njol.skript.util.Utils
import net.skripthub.docstool.modals.DocumentationEntryNode
import net.skripthub.docstool.modals.SyntaxData
import net.skripthub.docstool.utils.EventValuesGetter
import net.skripthub.docstool.utils.ReflectionUtils
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.event.Cancellable
import org.skriptlang.skript.lang.entry.EntryValidator
import org.skriptlang.skript.lang.entry.SectionEntryData
import org.skriptlang.skript.lang.entry.util.TriggerEntryData
import org.skriptlang.skript.lang.structure.StructureInfo
import java.lang.reflect.Field

class GenerateSyntax {
    companion object {

        data class CodeNameData(val isArray:Boolean, val codeName:String) {
            override fun toString(): String {
                return Utils.toEnglishPlural(codeName, isArray)
            }
        }

        fun generateSyntaxFromEvent(info: SkriptEventInfo<*>, getter: EventValuesGetter?) : SyntaxData? {
            if (info.description != null && info.description!!.contentEquals(SkriptEventInfo.NO_DOC)) {
                return null
            }
            val data = SyntaxData()
            data.name = info.getName()
            data.id = info.id
            if (info.documentationID != null) {
                data.id = info.documentationID
            }
            data.description = cleanDescription(info.description as? Array<String>)
            data.examples = cleanExamples(info.examples as Array<String>?)
            data.patterns = cleanupSyntaxPattern(info.patterns)
            if (data.name != null && data.name!!.startsWith("On ")) {
                for (x in 0 until data.patterns!!.size)
                    data.patterns!![x] = "[on] " + data.patterns!![x]
            }

            val sinceString = removeHTML(info.since)
            if (sinceString != null) {
                data.since = arrayOf(sinceString)
            }

            for (c in info.events)
                if (Cancellable::class.java.isAssignableFrom(c)) {
                    data.cancellable = java.lang.Boolean.TRUE
                } else {
                    data.cancellable = java.lang.Boolean.FALSE
                    break
                }

            data.requiredPlugins = info.requiredPlugins as Array<String>?

            if (getter != null) {
                val classes = getter.getEventValues(info.events)
                if (classes == null || classes.isEmpty())
                    return null
                val time = arrayOf("past event-", "event-", "future event-")
                val times = ArrayList<String>()
                for (x in classes.indices)
                    (0 until classes[x].size)
                            .mapNotNull { grabCodeName(classes[x][it]) }
                            .mapTo(times) { time[x] + it }
                // Sort the event values alphabetically to prevent update churn
                times.sortBy { it }
                data.eventValues = times.toTypedArray()
            }

            data.entries = getEntriesFromSkriptEventInfo(info)

            return data
        }

        private fun grabCodeName(classObj: Class<*>) : CodeNameData? {
            val expectedClass: Class<*> = if (classObj.isArray) classObj.componentType else classObj
            var classInfo = Classes.getExactClassInfo(expectedClass)
            if (classInfo == null) {
                classInfo = Classes.getSuperClassInfo(expectedClass)
            }
            if (classInfo == null)
                return null
            return CodeNameData(classObj.isArray, classInfo.codeName)
        }

        fun generateSyntaxFromSyntaxElementInfo(info: SyntaxElementInfo<*>, sender: CommandSender?): SyntaxData? {
            val data = SyntaxData()
            val syntaxInfoClass = info.getElementClass()
            if (syntaxInfoClass.isAnnotationPresent(NoDoc::class.java))
                return null
            if (syntaxInfoClass.isAnnotationPresent(Name::class.java))
                data.name = syntaxInfoClass.getAnnotation(Name::class.java).value
            if (data.name == null || data.name!!.isEmpty())
                data.name = syntaxInfoClass.simpleName
            data.id = syntaxInfoClass.simpleName
            if (syntaxInfoClass.isAnnotationPresent(DocumentationId::class.java)){
                data.id = syntaxInfoClass.getAnnotation(DocumentationId::class.java).value
            }
            if (syntaxInfoClass.isAnnotationPresent(Description::class.java))
                data.description = cleanDescription(syntaxInfoClass.getAnnotation(Description::class.java).value)
            data.examples = getCleanedExamplesFromClassInfo(syntaxInfoClass)
            data.patterns = cleanupSyntaxPattern(info.patterns)
            if (syntaxInfoClass.isAnnotationPresent(Since::class.java)) {
                data.since = removeHTML(syntaxInfoClass.getAnnotation(Since::class.java).value)
            }
            if (syntaxInfoClass.isAnnotationPresent(RequiredPlugins::class.java))
                data.requiredPlugins = syntaxInfoClass.getAnnotation(RequiredPlugins::class.java).value

            data.entries = getEntriesFromSkriptElementInfo(info, sender)

            return data
        }

        fun generateSyntaxFromStructureInfo(info: StructureInfo<*>): SyntaxData? {
            val data = SyntaxData()
            val syntaxInfoClass = info.getElementClass()
            if (syntaxInfoClass.isAnnotationPresent(NoDoc::class.java))
                return null
            if (syntaxInfoClass.isAnnotationPresent(Name::class.java))
                data.name = syntaxInfoClass.getAnnotation(Name::class.java).value
            if (data.name == null || data.name!!.isEmpty())
                data.name = syntaxInfoClass.simpleName
            data.id = syntaxInfoClass.simpleName
            if (syntaxInfoClass.isAnnotationPresent(DocumentationId::class.java)){
                data.id = syntaxInfoClass.getAnnotation(DocumentationId::class.java).value
            }
            if (syntaxInfoClass.isAnnotationPresent(Description::class.java))
                data.description = cleanDescription(syntaxInfoClass.getAnnotation(Description::class.java).value)
            data.examples = getCleanedExamplesFromClassInfo(syntaxInfoClass)
            data.patterns = cleanupSyntaxPattern(info.patterns)
            if (syntaxInfoClass.isAnnotationPresent(Since::class.java)) {
                data.since = removeHTML(syntaxInfoClass.getAnnotation(Since::class.java).value)
            }
            if (syntaxInfoClass.isAnnotationPresent(RequiredPlugins::class.java))
                data.requiredPlugins = syntaxInfoClass.getAnnotation(RequiredPlugins::class.java).value

            data.entries = getEntriesFromStructureInfo(info)

            return data
        }

        fun generateSyntaxFromExpression(info: ExpressionInfo<*, *>, classes: Array<Class<*>?>, sender: CommandSender?): SyntaxData? {
            val data = generateSyntaxFromSyntaxElementInfo(info, sender) ?: return null
            val ci = Classes.getSuperClassInfo(info.returnType)
            if (ci != null)
                data.returnType = if (ci.docName == null || ci.docName!!.isEmpty()) ci.codeName else ci.docName
            else
                data.returnType = "Object"
            val array = ArrayList<String>()
            val expr = ReflectionUtils.newInstance(info.getElementClass())
            try {
                for (mode in Changer.ChangeMode.values()) {
                    if (Changer.ChangerUtils.acceptsChange(expr, mode, *classes))
                        array.add(mode.name.lowercase().replace('_', ' '))
                }
            } catch (e: Throwable) {
                array.add("unknown")
            }

            data.changers = array.toTypedArray()
            return data
        }

        fun generateSyntaxFromClassInfo(info: ClassInfo<*>): SyntaxData? {
            if (info.docName != null && info.docName.equals(ClassInfo.NO_DOC))
                return null
            val data = SyntaxData()
            if (info.docName != null){
                data.name = info.docName
            } else {
                data.name = info.codeName
            }
            data.id = info.c.simpleName
            if (data.id.equals("Type")) {
                data.id += data.name?.replace(" ", "")
            }
            if (info.documentationID != null) {
                data.id = info.documentationID;
            }

            data.description = cleanDescription(info.description as? Array<String>)
            data.examples = cleanExamples(info.examples as? Array<String>)
            data.usage = cleanUsages(info.usage as? Array<String>)
            val sinceString = removeHTML(info.since)
            if (sinceString != null){
                data.since = arrayOf(sinceString)
            }

            if (info.userInputPatterns != null && info.userInputPatterns!!.isNotEmpty()) {
                val size = info.userInputPatterns!!.size
                data.patterns = Array (size) { _ -> "" }
                var x = 0
                for (p in info.userInputPatterns!!) {
                    data.patterns!![x++] = p!!.pattern()
                            .replace("\\((.+?)\\)\\?".toRegex(), "[$1]")
                            .replace("(.)\\?".toRegex(), "[$1]")
                }
            } else {
                data.patterns = Array (1) { _ -> info.codeName }
            }
            return data
        }

        fun generateSyntaxFromFunctionInfo(info: JavaFunction<*>): SyntaxData? {
            val data = SyntaxData()
            data.name = info.name
            data.id = "function_" + info.name
            data.description = cleanDescription(info.description as? Array<String>)
            data.examples = cleanExamples(info.examples as? Array<String>)
            val sb = StringBuilder()
            sb.append(info.name).append("(")
            if (info.parameters != null) {
                var index = 0
                for (p in info.parameters) {
                    if (index++ != 0)
                    //Skip the first parameter
                        sb.append(", ")
                    sb.append(p)
                }
            }
            sb.append(")")
            data.patterns = cleanupSyntaxPattern(arrayOf(sb.toString()), true)
            val sinceString = removeHTML(info.since)
            if (sinceString != null){
                data.since = arrayOf(sinceString)
            }
            val infoReturnType = info.returnType
            if (infoReturnType != null){
                data.returnType = if (infoReturnType.docName == null || infoReturnType.docName!!.isEmpty())
                    infoReturnType.codeName
                else
                    infoReturnType.docName
            }
            return data
        }

        private fun getEntriesFromSkriptElementInfo(info: SyntaxElementInfo<*>, sender: CommandSender?) : Array<DocumentationEntryNode>? {
            // See if the class has a EntryValidator and try to pull that out to use as the source of truth.
            val elementClass = info.getElementClass() ?: return null
            val fields: Array<Field>

            try {
                fields = elementClass.declaredFields;
            } catch (ex: Exception) {
                sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                        + ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to access declared fields " +
                        "for ${info.originClassPath} to find the SectionValidator.")

                // ex.printStackTrace();
                return null;
            }

            for (field in fields) {
                var entryValidator : EntryValidator? = null

                if (field.type.isAssignableFrom(EntryValidator::class.java)) {
                    try {
                        field.isAccessible = true
                        entryValidator = field.get(null) as? EntryValidator ?: break
                    } catch (ex: Exception) {
                        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                                + ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to find the " +
                                "EntryValidator for ${info.originClassPath}")

                        // ex.printStackTrace();
                        return null;
                    }
                } else if (field.type.isAssignableFrom(EntryValidator.EntryValidatorBuilder::class.java)) {
                    try {
                        field.isAccessible = true
                        val entryValidatorBuilder : EntryValidator.EntryValidatorBuilder = field.get(null) as? EntryValidator.EntryValidatorBuilder ?: break
                        entryValidator = entryValidatorBuilder.build()

                    } catch (ex: Exception) {
                        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                                + ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to find the " +
                                "EntryValidator.EntryValidatorBuilder for ${info.originClassPath}")

                        // ex.printStackTrace();
                        return null;
                    }
                }

                if (entryValidator != null) {
                    val entriesArray : MutableList<DocumentationEntryNode> = mutableListOf()
                    for (entry in entryValidator.entryData) {
                        entriesArray.add(DocumentationEntryNode(
                                entry.key,
                                !entry.isOptional,
                                entry is SectionEntryData || entry is TriggerEntryData,
                                entry.defaultValue.toString()))
                    }

                    return entriesArray.toTypedArray()
                }
            }

            return null;
        }

        private fun getEntriesFromStructureInfo(info: StructureInfo<*>) : Array<DocumentationEntryNode>? {
            val entryValidator = info.entryValidator
            val entriesArray : MutableList<DocumentationEntryNode> = mutableListOf()

            if (entryValidator != null) {
                for (entry in entryValidator.entryData) {
                    entriesArray.add(DocumentationEntryNode(
                            entry.key,
                            !entry.isOptional,
                            entry is SectionEntryData,
                            entry.defaultValue.toString()))
                }
            }

            return entriesArray.toTypedArray()
        }

        private fun getEntriesFromSkriptEventInfo(info: SkriptEventInfo<*>) : Array<DocumentationEntryNode>? {
            val entryValidator = info.entryValidator
            val entriesArray : MutableList<DocumentationEntryNode> = mutableListOf()

            if (entryValidator != null) {
                for (entry in entryValidator.entryData) {
                    entriesArray.add(DocumentationEntryNode(
                            entry.key,
                            !entry.isOptional,
                            entry is SectionEntryData,
                            entry.defaultValue.toString()))
                }
            }

            return entriesArray.toTypedArray()
        }

        private fun cleanupSyntaxPattern(patterns: Array<String>, isFunctionPattern: Boolean = false): Array<String>{
            if(patterns.isEmpty()){
                return patterns
            }
            for (i in patterns.indices){
                patterns[i] = patterns[i]
                        .replace("""\\([()])""".toRegex(), "$1")
                        .replace("""-?\d+¦""".toRegex(), "")
                        .replace("""-?\d+Â¦""".toRegex(), "")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("""%-(.+?)%""".toRegex()) {
                            match -> match.value.replace("-", "")
                        }
                        .replace("""%~(.+?)%""".toRegex()) {
                            match -> match.value.replace("~", "")
                        }
                        .replace("()", "")
                        .replace("""@-\d""".toRegex(), "")
                        .replace("""@\d""".toRegex(), "")
                        .replace("""\d¦""".toRegex(), "")

                if (!isFunctionPattern) {
                    patterns[i] = patterns[i]
                            .replace("""(\w+):""".toRegex(), "")
                            .replace("""\[:""".toRegex(), "[")
                            .replace("""\(:""".toRegex(), "(")
                            .replace("""\|:""".toRegex(), "|")
                }
            }
            return patterns
        }

        private fun removeHTML(description: Array<String>?): Array<String>{
            if(description.isNullOrEmpty()){
                return emptyArray()
            }
            for (i in description.indices){
                description[i] = this.removeHTML(description[i])!!
            }
            return description
        }

        private fun getCleanedExamplesFromClassInfo(syntaxInfoClass: Class<*>): Array<String>?{
            // Skript does an if/else tree here that has no docs, I suspect some addon devs are going to mix and match
            // these annotation by accident, lets make sure to capture everything.

            // Example and Example.Examples annotations were added in 2.10.2
            // Examples is the classic example annotation

            val combinedExamples = ArrayList<String>()
            if (syntaxInfoClass.isAnnotationPresent(Example::class.java)) {
                combinedExamples.add(syntaxInfoClass.getAnnotation(Example::class.java).value)
            }

            if (syntaxInfoClass.isAnnotationPresent(Example.Examples::class.java)) {
                val listOfExamples = syntaxInfoClass.getAnnotation(Example.Examples::class.java).value
                listOfExamples.map { example -> combinedExamples.add(example.value) }
            }

            if (syntaxInfoClass.isAnnotationPresent(Examples::class.java)) {
                combinedExamples.addAll(syntaxInfoClass.getAnnotation(Examples::class.java).value)
            }

            return if (combinedExamples.isEmpty()) null else cleanExamples(combinedExamples.toTypedArray())
        }

        private fun cleanExamples(examples: Array<String>?): Array<String>?{
            if (examples.isNullOrEmpty()){
                return examples
            }
            if (examples.size == 1 && examples[0].isEmpty()){
                return null
            }
            for (i in examples.indices){
                examples[i] = examples[i]
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
            }

            return examples
        }

        private fun cleanDescription(description: Array<String>?): Array<String>?{
            if (description.isNullOrEmpty()){
                return description
            }

            val cleanedDescription = removeHTML(description)
            for (i in cleanedDescription.indices){
                cleanedDescription[i] = cleanedDescription[i]
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
            }

            return description
        }

        private fun cleanUsages(usages: Array<String>?): Array<String>?{
            if (usages.isNullOrEmpty()){
                return usages
            }

            val cleanedUsages = removeHTML(usages)
            for (i in cleanedUsages.indices){
                cleanedUsages[i] = cleanedUsages[i]
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
            }

            return cleanedUsages
        }

        private fun removeHTML(string: String?): String?{
            if(string.isNullOrEmpty()){
                return string
            }
            return string.replace("""<.+?>(.+?)</.+?>""".toRegex(), "$1")
        }
    }
}