package net.skripthub.docstool.documentation

import ch.njol.skript.Skript
import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.lang.SkriptEventInfo
import ch.njol.skript.lang.SyntaxElementInfo
import ch.njol.skript.lang.function.Functions
import ch.njol.skript.log.SkriptLogger
import ch.njol.skript.registrations.Classes
import net.skripthub.docstool.modals.AddonData
import net.skripthub.docstool.modals.AddonMetadata
import net.skripthub.docstool.modals.SyntaxData
import net.skripthub.docstool.utils.EventValuesGetter
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.*


class BuildDocs(private val instance: JavaPlugin, private val sender: CommandSender?) : Runnable{

    private var addonMap: HashMap<String, AddonData> = hashMapOf()

    // Skript has multiple internal class packages such as ch.njol.skript and org.skriptlang.skript.
    // Since we base the package mapping based off of the entry class, which for Skript lives in the
    // ch.njol.skript package, we don't and can't know about org.skriptlang.skript.
    // addonPackageMap is a hard coded map of known internal packages so we are always mapping back
    // to the right addon.
    private var addonPackageMap: HashMap<String, String> = hashMapOf(
        "org.skriptlang.skript" to "ch.njol.skript",
        "Skript" to "ch.njol.skript"
    )

    private val fileType: FileType = JsonFile(false)

    fun load() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(instance, this, 10L)
    }

    override fun run() {
        if (Skript.isAcceptRegistrations())
            return
        addonMap[Skript::class.java.`package`.name] = AddonData(
                "Skript", AddonMetadata(Skript.getVersion().toString()))
        for (addon in Skript.getAddons())
            addonMap[addon.plugin.javaClass.`package`.name] = AddonData(
                    addon.name, AddonMetadata(addon.version.toString()))

        // Events
        val getter = EventValuesGetter()
        for (eventInfoClassUnsafe in Skript.getEvents()){
            val eventInfoClass = eventInfoClassUnsafe as SkriptEventInfo<*>
            val addonEvents = getAddon(eventInfoClass)?.events ?: continue
            // TODO Throw error when null
            addSyntax(addonEvents, GenerateSyntax.generateSyntaxFromEvent(eventInfoClass, getter, sender))
        }

        // Conditions
        for (syntaxElementInfo in Skript.getConditions()) {
            val addonConditions = getAddon(syntaxElementInfo)?.conditions ?: continue
            addSyntax(addonConditions, GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo, sender))
        }

        // Effects
        for (syntaxElementInfo in Skript.getEffects()) {
            val addonEffects = getAddon(syntaxElementInfo)?.effects ?: continue
            addSyntax(addonEffects, GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo, sender))
        }

        // Expressions
        // A LogHandler for expressions since it catch the changers, which can throw errors in console
        // such as "Expression X can only be used in event Y"
        val log = SkriptLogger.startParseLogHandler()
        for (info in Skript.getExpressions()) {
            val addonExpressions = getAddon(info)?.expressions ?: continue
            addSyntax(addonExpressions, GenerateSyntax.generateSyntaxFromExpression(info, sender))
        }
        log.clear()
        log.stop()

        // Types
        for (syntaxElementInfo in Classes.getClassInfos()) {
            val addonTypes = getAddon(syntaxElementInfo)?.types ?: continue
            addSyntax(addonTypes, GenerateSyntax.generateSyntaxFromClassInfo(syntaxElementInfo))
        }

        // Functions
        for (info in Functions.getJavaFunctions()) {
            val addonFunctions = getAddon(info.javaClass)?.functions ?: continue
            addSyntax(addonFunctions, GenerateSyntax.generateSyntaxFromFunctionInfo(info))
        }

        // Sections
        for (syntaxElementInfo in Skript.getSections()) {
            val addonSections = getAddon(syntaxElementInfo)?.sections ?: continue
            addSyntax(addonSections, GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo, sender))
        }

        // Structures
        for (syntaxElementInfo in Skript.getStructures()) {
            val addonStructures = getAddon(syntaxElementInfo)?.structures ?: continue
            addSyntax(addonStructures, GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo, sender))
        }

        // Error Check Each Addon! (No id collisions)
        for (addon in addonMap.keys){
            val addonInfo = addonMap[addon] ?: continue
            val idSet : MutableSet<String> = mutableSetOf()
            // Get results from test and attempt merge
            attemptIDMerge(addonInfo.events, idCollisionTest(idSet, addonInfo.events, addon))
            attemptIDMerge(addonInfo.conditions, idCollisionTest(idSet, addonInfo.conditions, addon))
            attemptIDMerge(addonInfo.effects, idCollisionTest(idSet, addonInfo.effects, addon))
            attemptIDMerge(addonInfo.expressions, idCollisionTest(idSet, addonInfo.expressions, addon))
            attemptIDMerge(addonInfo.types, idCollisionTest(idSet, addonInfo.types, addon))
            attemptIDMerge(addonInfo.functions, idCollisionTest(idSet, addonInfo.functions, addon))
            attemptIDMerge(addonInfo.sections, idCollisionTest(idSet, addonInfo.sections, addon))
            attemptIDMerge(addonInfo.structures, idCollisionTest(idSet, addonInfo.structures, addon))
        }

        // Write to JSON
        // Before, lets delete old files...
        val docsDir = File(instance.dataFolder, "documentation/")
        if (docsDir.exists()) {
            val files = docsDir.listFiles()
            if (files != null)
                for (f in files)
                    f.delete()
        } else
            docsDir.mkdirs()
        // Done, now let's write them all into files
        for (addon in addonMap.values) {
            addon.sortLists()
            val file = File(docsDir, "${addon.name}.${fileType.extension}")
            if (!file.exists()) {
                file.parentFile.mkdirs()
                try {
                    file.createNewFile()
                } catch (ignored: IOException) {}
            }
            try {
                BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8")).use { writer -> fileType.write(writer, addon) }
            } catch (io: IOException) {
                io.printStackTrace()
            }

        }

        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                + ChatColor.RESET + "] " + ChatColor.GREEN + "Docs have been generated!")

    }

    private fun idCollisionTest(idSet: MutableSet<String>, listOfSyntaxData: MutableList<SyntaxData>, addon: String): MutableList<String>{
        val idCollisions = mutableListOf<String>()
        for (syntax in listOfSyntaxData){
            val id = syntax.id ?: continue
            val result = idSet.add(id)
            if (!result){
                // PANIC!!! ID COLLISION!!!!
                idCollisionErrorMessage(addon, id)
                idCollisions.add(id)
            }
        }
        return idCollisions
    }

    private fun attemptIDMerge(listOfSyntaxData: MutableList<SyntaxData>, ids: MutableList<String>) {
        // Only merge from like Syntax Types
        for(id in ids){
            attemptTypeMerge(listOfSyntaxData, id)
        }
    }

    private fun attemptTypeMerge(listOfSyntaxData: MutableList<SyntaxData>, id: String) {
        // Message attempting merge
        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                + ChatColor.RESET + "] " + ChatColor.GREEN + "Attempting merge of $id")
        val idCollisionList: ArrayList<SyntaxData> = ArrayList()
        val iterator = listOfSyntaxData.listIterator()
        while (iterator.hasNext()) {
            val syntax = iterator.next()
            val syntaxId = syntax.id ?: continue
            if(id == syntaxId){
                idCollisionList.add(syntax)
                iterator.remove()
            }
        }
        // No collision, might be a different syntax type
        if(idCollisionList.size < 2){
            listOfSyntaxData.addAll(idCollisionList)
            sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                    + ChatColor.RESET + "] " + ChatColor.GOLD + "Merge of $id unsuccessful, conflict might be "
                    + "between two different syntax types or it might have already been resolved")
            return
        }
        // Use first instance as a template and try to merge into first instance
        val firstInstance = idCollisionList[0]
        var repairedCount = 0
        for (i in 1 until idCollisionList.size){
            val syntaxToMerge = idCollisionList[i]
            val syntaxToMergeDesc = firstInstance.description
            if (syntaxToMergeDesc != null && syntaxToMerge.description != null && syntaxToMerge.patterns != null) {
                if(syntaxToMerge.name == firstInstance.name
                        && syntaxToMerge.description!!.contentEquals(syntaxToMergeDesc.clone())){
                    // Match found, add to firstInstance
                    firstInstance.patterns = firstInstance.patterns?.plus(syntaxToMerge.patterns!!)
                    repairedCount += 1
                    continue
                }
            }
            // Failed add back to master list
            listOfSyntaxData.add(syntaxToMerge)
        }
        // Add first instance back to list
        listOfSyntaxData.add(firstInstance)

        if(repairedCount > 0){
            sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                    + ChatColor.RESET + "] " + ChatColor.GREEN + "Merged ${repairedCount + 1} out of ${idCollisionList.size} "
                    + "instances of $id")
            return
        }
        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                + ChatColor.RESET + "] " + ChatColor.RED + "Unable to merge ${idCollisionList.size} "
                + "instances of $id")
    }

    private fun idCollisionErrorMessage(addon: String, id: String){
        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                + ChatColor.RESET + "] " + ChatColor.RED + "ID COLLISION DETECTED!\n" +
                "Plugin: $addon\nMultiple syntax elements with the same id: $id")
    }

    private fun addSyntax(list: MutableList<SyntaxData>, syntax: SyntaxData?) {
        if (syntax == null) {
            return
        }
        if (syntax.name.isNullOrEmpty()) {
            return
        }
        if (syntax.patterns.isNullOrEmpty()) {
            return
        }
        list.add(syntax)
    }

    private fun getAddon(info: ClassInfo<*>): AddonData? {
        return when {
            info.parser != null -> getAddon(info.parser!!::class.java)
            info.serializer != null -> getAddon(info.serializer!!::class.java)
            info.changer != null -> getAddon(info.changer!!::class.java)
            else -> getAddon(info.javaClass)
        }
    }

    private fun getAddon(skriptEventInfo: SyntaxElementInfo<*>): AddonData? {

        var name = skriptEventInfo.getElementClass().`package`.name
        // var testName = skriptEventInfo.getElementClass().packageName

        if (name == "ch.njol.skript.lang.util") {
            // Used Simple event or expression registration
            name = skriptEventInfo.originClassPath
        }

        // Check to see if we need to remap the package to the addon root package.
        val mappedPackageNode = addonPackageMap.entries.firstOrNull { name.startsWith(it.key) }
        if (mappedPackageNode != null) {
            name = mappedPackageNode.value
        }

        // DEBUG
//        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
//                + ChatColor.RESET + "] " + ChatColor.YELLOW + "Calced Package Name: " + ChatColor.BLUE + "$name")
//        val originClassPathName = skriptEventInfo.originClassPath
//        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
//                + ChatColor.RESET + "] " + ChatColor.YELLOW + "originClassPath: " + ChatColor.GREEN + "$originClassPathName")
//        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
//                + ChatColor.RESET + "] " + ChatColor.YELLOW + "Test Package Name: " + ChatColor.GREEN + "$testName")
//        val patterntest = skriptEventInfo.patterns[0]
//        sender?.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
//                + ChatColor.RESET + "] " + ChatColor.YELLOW + "First Pattern: " + ChatColor.LIGHT_PURPLE + "$patterntest")

        // If null, bail and throw error
        return addonMap.entries
                .firstOrNull { name.startsWith(it.key) }
                ?.value
    }

    private fun getAddon(classObj: Class<*>): AddonData? {
        var name = classObj.`package`.name
        // If null, bail and throw error

        // Check to see if we need to remap the package to the addon root package.
        val mappedPackageNode = addonPackageMap.entries.firstOrNull { name.startsWith(it.key) }
        if (mappedPackageNode != null) {
            name = mappedPackageNode.value
        }

        return addonMap.entries
                .firstOrNull { name.startsWith(it.key) }
                ?.value
    }
}
