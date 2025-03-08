package net.skripthub.docstool.documentation

import ch.njol.skript.classes.Changer.ChangeMode
import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.doc.*
import ch.njol.skript.lang.ExpressionInfo
import ch.njol.skript.lang.SkriptEventInfo
import ch.njol.skript.lang.SyntaxElementInfo
import ch.njol.skript.lang.function.JavaFunction
import ch.njol.skript.registrations.Classes
import ch.njol.util.StringUtils
import net.skripthub.docstool.modals.DocumentationEntryNode
import net.skripthub.docstool.modals.SyntaxData
import net.skripthub.docstool.utils.EventValuesGetter
import net.skripthub.docstool.utils.ReflectionUtils
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.event.Cancellable
import org.skriptlang.skript.lang.entry.EntryValidator
import org.skriptlang.skript.lang.entry.EntryValidator.EntryValidatorBuilder
import org.skriptlang.skript.lang.structure.StructureInfo
import java.lang.reflect.Field
import java.util.*
import java.util.function.Function

class GenerateSyntax {

    companion object {

        fun generateSyntaxFromSyntaxElementInfo(info: SyntaxElementInfo<*>, sender: CommandSender?): SyntaxData? {
            val syntaxInfoClass = info.getElementClass()
            if (syntaxInfoClass.isAnnotationPresent(NoDoc::class.java))
                return null

            val data = SyntaxData()
            data.name = grabAnnotation(syntaxInfoClass, Name::class.java, { it.value.ifBlank { null } }, syntaxInfoClass.simpleName)
            data.id = grabAnnotation(syntaxInfoClass, DocumentationId::class.java, { it.value.ifBlank { null } }, syntaxInfoClass.simpleName)
            data.description = cleanHTML(grabAnnotation(syntaxInfoClass, Description::class.java, { it.value }))
            data.patterns = cleanSyntaxInfoPatterns(info.patterns)
            data.entries = generateEntriesFromSyntaxElementInfo(info, sender)
            data.examples = cleanSyntaxInfoExamples(syntaxInfoClass)
            data.since = cleanHTML(grabAnnotation(syntaxInfoClass, Since::class.java, { it.value }))
            data.requiredPlugins = cleanHTML(grabAnnotation(syntaxInfoClass, RequiredPlugins::class.java, { it.value }))
            data.keywords = grabAnnotation(syntaxInfoClass, Keywords::class.java, { it.value })

            return data
        }

        fun generateSyntaxFromExpression(info: ExpressionInfo<*, *>, classes: Array<Class<*>?>, sender: CommandSender?): SyntaxData? {
            val data = generateSyntaxFromSyntaxElementInfo(info, sender) ?: return null

            // Return Type
            val classInfo = Classes.getExactClassInfo(info.returnType) ?: Classes.getSuperClassInfo(info.returnType)
            if (classInfo != null)
                data.returnType = if (classInfo.docName.isNullOrBlank()) classInfo.codeName else classInfo.docName
            else
                // TODO: Throw an error when compiling the json letting the developer know
                data.returnType = "Object"

            // Changers
            val expr = ReflectionUtils.newInstance(info.getElementClass())
            // TODO: test for changes in previous supported changers. Loss of classInfo changers are expected
            try {
                data.changers = expr.acceptedChangeModes.entries.filter { it.value is Array<Class<*>> }
                    .map { it.key.name.lowercase(Locale.getDefault()).replace('_', ' ') }
                    .toTypedArray()
            } catch (exception: Exception) {
                data.changers = arrayOf("unknown")
            }

            return data
        }

        fun generateSyntaxFromEvent(info: SkriptEventInfo<*>, getter: EventValuesGetter?, sender: CommandSender?): SyntaxData? {
            if (info.description != null && info.description.contentEquals(SkriptEventInfo.NO_DOC)) {
                return null
            }
            val data = SyntaxData()
            data.name = info.getName()
            data.id = when {
                info.documentationID != null -> info.documentationID
                else -> info.id
            }
            data.description = cleanHTML(info.description)
            data.examples = cleanHTML(info.examples)
            data.since = if (!info.since.isNullOrBlank()) arrayOf(cleanHTML(info.since)!!) else null
            data.cancellable = info.events.all { Cancellable::class.java.isAssignableFrom(it.javaClass) }
            data.patterns = cleanSyntaxInfoPatterns(info.patterns).map { "[on] $it" }.toTypedArray()
            data.requiredPlugins = info.requiredPlugins
            data.keywords = info.keywords

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
                data.eventValues = times.sortedBy { it }.toTypedArray()
            }

            data.entries = generateEntriesFromSyntaxElementInfo(info, sender)

            return data
        }

        @Suppress("UNCHECKED_CAST")
        fun generateSyntaxFromClassInfo(info: ClassInfo<*>, sender: CommandSender?) : SyntaxData? {
            if (info.docName != null && info.docName.equals(ClassInfo.NO_DOC))
                return null
            val data = SyntaxData()

            data.name = when {
                info.docName != null -> info.docName
                else -> info.codeName
            }
            data.id = when {
                info.documentationID != null -> info.documentationID
                info.c.simpleName.equals("Type") -> "${info.c.simpleName}${data.name?.replace(" ", "")}"
                else -> info.c.simpleName
            }
            data.description = cleanHTML(info.description as? Array<String>)
            data.examples = cleanHTML(info.examples as? Array<String>)
            data.usage = cleanHTML(info.usage as? Array<String>)
            data.since = if (!info.since.isNullOrBlank()) arrayOf(cleanHTML(info.since)!!) else null
            // TODO: implement keywords when skript does
            val changer = info.changer
            if (changer != null)
                data.changers = ChangeMode.values()
                    .filter { changer.acceptChange(it) != null }
                    .map { it.name.lowercase(Locale.getDefault()).replace('_', ' ') }
                    .toTypedArray()

            if (!info.userInputPatterns.isNullOrEmpty()) {
                val size = info.userInputPatterns!!.size
                data.patterns = Array(size) { _ -> "" }

                for (test in info.userInputPatterns!!.indices) {
                    data.patterns!![test] = info.userInputPatterns!![test].pattern()
                        .replace("\\((.+?)\\)\\?".toRegex(), "[$1]")
                        .replace("(.)\\?".toRegex(), "[$1]")
                }
            } else {
                data.patterns = Array(1) { _ -> info.codeName }
            }
            return data
        }

        fun generateSyntaxFromFunctionInfo(info: JavaFunction<*>, sender: CommandSender?) : SyntaxData {
            val data = SyntaxData()
            data.name = info.name
            data.id = "function_" + info.name
            data.description = cleanHTML(info.description)
            data.examples = cleanHTML(info.examples)
            data.keywords = info.keywords

            val parametersString = StringBuilder("${info.name}(")
            if (!info.parameters.isNullOrEmpty()) {
                parametersString.append(StringUtils.join(info.parameters.map { it.toString() }.toTypedArray(), ","))
            }
            parametersString.append(")")

            data.patterns = cleanSyntaxInfoPatterns(arrayOf(parametersString.toString()), true)
            val sinceString = cleanHTML(info.since)
            if (sinceString != null) {
                data.since = arrayOf(sinceString)
            }
            val infoReturnType = info.returnType
            if (infoReturnType != null) {
                data.returnType =
                    if (infoReturnType.docName.isNullOrBlank()) infoReturnType.codeName else infoReturnType.docName
            }
            return data
        }

        private fun generateEntriesFromSyntaxElementInfo(info: SyntaxElementInfo<*>, sender: CommandSender?) : Array<DocumentationEntryNode>? {
            if (info is StructureInfo) {
                val entryValidator = info.entryValidator ?: return null
                return entryValidator.entryData.map(DocumentationEntryNode::from).toTypedArray()
            }
            // See if the class has a EntryValidator and try to pull that out to use as the source of truth.
            val elementClass = info.getElementClass() ?: return null
            val fields: Array<Field>

            try {
                fields = elementClass.declaredFields
            } catch (ex: Exception) {
                sender?.sendMessage(
                    "[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                            + ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to access declared fields " +
                            "for ${info.originClassPath} to find the SectionValidator."
                )

                // ex.printStackTrace();
                return null
            }

            for (field in fields) {
                var entryValidator: EntryValidator? = null

                if (field.type.isAssignableFrom(EntryValidator::class.java)) {
                    try {
                        field.isAccessible = true
                        entryValidator = field.get(null) as? EntryValidator ?: break
                    } catch (ex: Exception) {
                        sender?.sendMessage(
                            "[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                                    + ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to find the " +
                                    "EntryValidator for ${info.originClassPath}"
                        )

                        // ex.printStackTrace();
                        return null
                    }
                } else if (field.type.isAssignableFrom(EntryValidatorBuilder::class.java)) {
                    try {
                        field.isAccessible = true
                        val entryValidatorBuilder: EntryValidatorBuilder =
                            field.get(null) as? EntryValidatorBuilder ?: break
                        entryValidator = entryValidatorBuilder.build()

                    } catch (ex: Exception) {
                        sender?.sendMessage(
                            "[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                                    + ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to find the " +
                                    "EntryValidator.EntryValidatorBuilder for ${info.originClassPath}"
                        )

                        // ex.printStackTrace();
                        return null
                    }
                }

                if (entryValidator?.entryData == null) return null
                return entryValidator.entryData.map(DocumentationEntryNode::from).toTypedArray()
            }

            return null
        }

        private fun cleanSyntaxInfoExamples(syntaxInfoClass: Class<*>): Array<String>? {
            // Skript does an if/else tree here that has no docs, I suspect some addon devs are going to mix and match
            // these annotation by accident, lets make sure to capture everything.

            // Example annotation was added in 2.10.2
            // Examples is the classic example annotation

            val combinedExamples = ArrayList<String?>()
            combinedExamples.addAll(grabRepeatableAnnotation(syntaxInfoClass, Example::class.java, { it.value }))
            grabAnnotation(syntaxInfoClass, Examples::class.java, { it.value })?.toCollection(combinedExamples)

            return if (combinedExamples.filterNotNull().isEmpty()) null else cleanHTML(combinedExamples.filterNotNull().toTypedArray())
        }

        private fun cleanSyntaxInfoPatterns(patterns: Array<String>, isFunctionPattern: Boolean = false): Array<String> {
            if (patterns.isEmpty()) {
                return patterns
            }
            for (i in patterns.indices) {
                patterns[i] = patterns[i]
                    .replace("""\\([()])""".toRegex(), "$1")
                    .replace("""-?\d+¦""".toRegex(), "")
                    .replace("""-?\d+Â¦""".toRegex(), "")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("""%-(.+?)%""".toRegex()) { match ->
                        match.value.replace("-", "")
                    }
                    .replace("""%~(.+?)%""".toRegex()) { match ->
                        match.value.replace("~", "")
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

        private fun cleanHTML(string: String?): String? {
            if (string.isNullOrBlank()) return string
            return string
                .replace("""<.+?>(.+?)</.+?>""".toRegex(), "$1")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

        private fun cleanHTML(strings: Array<String>?): Array<String>? {
            if (strings.isNullOrEmpty()) return null
            return strings.mapNotNull(::cleanHTML).toTypedArray()
        }

        private fun grabCodeName(classObj: Class<*>): String? {
            val expectedClass: Class<*> = if (classObj.isArray) classObj.componentType else classObj
            val classInfo = Classes.getExactClassInfo(expectedClass)
                ?: Classes.getSuperClassInfo(expectedClass)
                ?: return null // If neither of the two methods managed to find a classinfo, return null
            val name = classInfo.name
            return if (classObj.isArray) name.plural else name.singular
        }

        private fun <A : Annotation, R> grabAnnotation(source: Class<*>, annotation: Class<A>, supplier: Function<A, R?>, default: R? = null): R? {
            if (!source.isAnnotationPresent(annotation))
                return default
            return supplier.apply(source.getAnnotation(annotation)) ?: default
        }

        private inline fun <A : Annotation, reified R> grabRepeatableAnnotation(source: Class<*>, annotation: Class<A>, supplier: Function<A, R?>, default: R? = null): Array<R?> {
            if (!source.isAnnotationPresent(annotation)) return arrayOf(default)
            return source.getAnnotationsByType(annotation)
                .map { supplier.apply(it) }
                .toTypedArray()
        }

    }
}
