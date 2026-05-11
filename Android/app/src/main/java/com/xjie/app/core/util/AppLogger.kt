package com.xjie.app.core.util

import timber.log.Timber

/** 对应 iOS [AppLogger.swift] 的子系统分类。 */
object AppLogger {
    val ui: Timber.Tree get() = Timber.tag("UI")
    val auth: Timber.Tree get() = Timber.tag("Auth")
    val network: Timber.Tree get() = Timber.tag("Net")
    val data: Timber.Tree get() = Timber.tag("Data")
}
