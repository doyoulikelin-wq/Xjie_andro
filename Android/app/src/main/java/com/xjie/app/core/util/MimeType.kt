package com.xjie.app.core.util

/** 对应 iOS [MIMETypeHelper.swift] */
object MimeType {
    fun forExtension(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "csv" -> "text/csv"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    fun forFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return forExtension(ext)
    }
}
