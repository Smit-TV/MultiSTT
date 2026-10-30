package com.github.smittv.multistt

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.IOException

object ZipUtils {

    private fun createZip(
        destinationZip: File,
        overwrite: Boolean
    ): ZipFile {
        destinationZip.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) {
                throw IOException("Failed to create destination directory.")
            }
        }

        if (destinationZip.exists()) {
            if (!overwrite) {
                throw IOException("Destination ZIP already exists.")
            }

            if (!destinationZip.delete()) {
                throw IOException("Failed to overwrite existing ZIP.")
            }
        }

        return ZipFile(destinationZip)
    }

    private fun defaultParameters() = ZipParameters().apply {
        compressionMethod = CompressionMethod.DEFLATE
        compressionLevel = CompressionLevel.NORMAL
    }

    private fun addRecursively(
        zip: ZipFile,
        root: File,
        current: File
    ) {
        val relativePath = current.relativeTo(root).invariantSeparatorsPath

        if (current.isDirectory) {
            val children = current.listFiles().orEmpty()

            if (relativePath.isNotEmpty()) {
                val params = ZipParameters().apply {
                    fileNameInZip = "$relativePath/"
                }
                zip.addFolder(current, params)
            }

            for (child in children) {
                addRecursively(zip, root, child)
            }
        } else {
            val params = defaultParameters().apply {
                fileNameInZip = relativePath
            }

            zip.addFile(current, params)
        }
    }

    fun zipDirectory(
        sourceDir: File,
        destinationZip: File,
        overwrite: Boolean = true
    ): Result<File> = runCatching {

        require(sourceDir.exists()) {
            "Source directory does not exist."
        }

        require(sourceDir.isDirectory) {
            "Source is not a directory."
        }

        require(
            !destinationZip.canonicalFile.toPath()
                .startsWith(sourceDir.canonicalFile.toPath())
        ) {
            "Destination ZIP cannot be inside the source directory."
        }

        val zip = createZip(destinationZip, overwrite)

        val children = sourceDir.listFiles().orEmpty()

        if (children.isEmpty()) {
            zip.addFolder(sourceDir)
        } else {
            for (child in children) {
                addRecursively(zip, sourceDir, child)
            }
        }

        destinationZip
    }.recoverCatching {
        when (it) {
            is ZipException ->
                throw IOException("ZIP error: ${it.message}", it)
            is SecurityException ->
                throw IOException("Permission denied.", it)
            else -> throw it
        }
    }

    fun zipFiles(
        files: Array<File>,
        destinationZip: File,
        overwrite: Boolean = true
    ): Result<File> = runCatching {

        require(files.isNotEmpty()) {
            "Nothing to archive."
        }

        val zip = createZip(destinationZip, overwrite)

        for (file in files) {
            require(file.exists()) {
                "File does not exist: ${file.absolutePath}"
            }

            if (file.isDirectory) {
                addRecursively(zip, file.parentFile ?: file, file)
            } else {
                zip.addFile(file, defaultParameters())
            }
        }

        destinationZip
    }.recoverCatching {
        when (it) {
            is ZipException ->
                throw IOException("ZIP error: ${it.message}", it)
            is SecurityException ->
                throw IOException("Permission denied.", it)
            else -> throw it
        }
    }
}