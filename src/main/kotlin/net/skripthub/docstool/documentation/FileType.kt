package net.skripthub.docstool.documentation

import net.skripthub.docstool.modals.AddonData
import java.io.BufferedWriter
import java.io.IOException

/**
 * @author Tuke_Nuke on 21/07/2017
 */
abstract class FileType(val extension: String) {

    @Throws(IOException::class)
    abstract fun write(writer: BufferedWriter, addon: AddonData)

}