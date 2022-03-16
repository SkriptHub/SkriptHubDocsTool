package net.skripthub.docstool.documentation

import ch.njol.skript.Skript
import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.lang.SkriptEventInfo
import ch.njol.skript.lang.SyntaxElementInfo
import ch.njol.skript.lang.function.Functions
import net.skripthub.docstool.modals.AddonData
import net.skripthub.docstool.modals.SyntaxData
import net.skripthub.docstool.utils.EventValuesGetter
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import ch.njol.skript.log.SkriptLogger
import ch.njol.skript.registrations.Classes
import ch.njol.skript.lang.function.JavaFunction
import net.skripthub.docstool.utils.ReflectionUtils
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import java.io.IOException
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.io.File
import kotlin.collections.ArrayList


class BuildDocs(private val instance: JavaPlugin, private val sender: CommandSender?) : Runnable{

    var addonMap: HashMap<String, AddonData> = hashMapOf()

    private val fileType: FileType = JsonFile(false)

    fun load() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(instance, this, 10L)
    }


    override fun run() {
        if (Skript.isAcceptRegistrations())
            return
        addonMap[Skript::class.java.`package`.name] = AddonData("Skript")
        for (addon in Skript.getAddons())
            addonMap[addon.plugin.javaClass.`package`.name] = AddonData(addon.name)
        // Events
        val getter = EventValuesGetter()
        for (eventInfoClassUnsafe in Skript.getEvents()){
            val eventInfoClass = eventInfoClassUnsafe as SkriptEventInfo<*>
            val addonEvents = getAddon(eventInfoClass)?.events
            // TODO Throw error when null
            if (addonEvents != null) {
                addSyntax(addonEvents,
                        GenerateSyntax.generateSyntaxFromEvent(eventInfoClass, getter))
            }
        }
        // Conditions
        for (syntaxElementInfo in Skript.getConditions()) {
//            if (EffectSection::class.java!!.isAssignableFrom(info.c))
//            //Separate effect sections to effects instead of conditions
//                addSyntax(getAddon(info.c).getEffects(), SyntaxInfo(info))
//            else
            val addonConditions = getAddon(syntaxElementInfo)?.conditions
            if (addonConditions != null) {
                addSyntax(addonConditions,
                        GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo))
            }
        }
        // Effects
        for (syntaxElementInfo in Skript.getEffects()) {
            val addonEffects = getAddon(syntaxElementInfo)?.effects
            if (addonEffects != null) {
                addSyntax(addonEffects,
                        GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo))
            }
        }
        // Expressions
        val types = arrayOfNulls<Class<*>>(Classes.getClassInfos().size)
        var x = 0
        for (info in Classes.getClassInfos())
            types[x++] = info.c
        // A LogHandler for expressions since it catch the changers, which can throw errors in console
        // such as "Expression X can only be used in event Y"
        val log = SkriptLogger.startParseLogHandler()
        Skript.getExpressions().forEachRemaining { info ->
            val addonExpressions = getAddon(info)?.expressions
            if(addonExpressions != null){
                addSyntax(addonExpressions,
                        GenerateSyntax.generateSyntaxFromExpression(info, types))
            }
        }
        log.clear()
        log.stop()

        // Types
        for (syntaxElementInfo in Classes.getClassInfos()) {
            val addonTypes = getAddon(syntaxElementInfo)?.types
            if(addonTypes != null){
                addSyntax(addonTypes,
                        GenerateSyntax.generateSyntaxFromClassInfo(syntaxElementInfo))
            }
        }

        // Functions
        val functions = ReflectionUtils.invokeMethod<Collection<JavaFunction<*>>>(Functions::class.java, "getJavaFunctions", null)
        if (functions != null) {
            for (info in functions) {
                // For now only Skript uses this
                val addonFunctions = getAddon(info.javaClass)?.functions
                if (addonFunctions != null) {
                    addSyntax(addonFunctions, GenerateSyntax.generateSyntaxFromFunctionInfo(info))
                }
            }
        }

        // Sections
        for (syntaxElementInfo in Skript.getSections()) {
            val addonSections = getAddon(syntaxElementInfo)?.sections
            if (addonSections != null) {
                addSyntax(addonSections,
                        GenerateSyntax.generateSyntaxFromSyntaxElementInfo(syntaxElementInfo))
            }
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
            val file = File(docsDir, addon.name + "." + fileType.extension)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                try {
                    file.createNewFile()
                } catch (io: IOException) {

                }

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

    private fun attemptIDMerge(listOfSyntaxData: MutableList<SyntaxData>, ids: MutableList<String>){
        // Only merge from like Syntax Types
        for(id in ids){
            attemptTypeMerge(id, listOfSyntaxData)
        }
    }

    private fun attemptTypeMerge(id: String, listOfSyntaxData: MutableList<SyntaxData>){
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
        if(syntax == null){
            return
        }
        if (syntax.name == null || syntax.name!!.isEmpty()) {
            return
        }
        if (syntax.patterns == null || syntax.patterns!!.isEmpty()) {
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

        var name = skriptEventInfo.c.`package`.name

        if (name == "ch.njol.skript.lang.util"){
            // Used Simple event or expression registration
            name = skriptEventInfo.originClassPath
        }

        // If null, bail and throw error
        return addonMap.entries
                .firstOrNull {
                    var key = it.key
                    while (key.contains('.')) {
                        if (name.startsWith(key))
                            return@firstOrNull true
                        key = key.substring(0, key.lastIndexOf('.'))
                    }
                    return@firstOrNull false
                }
            ?.value
    }

    private fun getAddon(c: Class<*>): AddonData? {
        val name = c.`package`.name
        // If null, bail and throw error
        return addonMap.entries
                .firstOrNull {
                    var key = it.key
                    while (key.contains('.')) {
                        if (name.startsWith(key))
                            return@firstOrNull true
                        key = key.substring(0, key.lastIndexOf('.'))
                    }
                    return@firstOrNull false
                }
                ?.value
    }
}