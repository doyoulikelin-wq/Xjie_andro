package com.xjie.app.core.util

/** 对应 iOS [Constants.swift] APIConstants */
object ApiConstants {
    const val REQUEST_TIMEOUT_S = 15L
    const val UPLOAD_TIMEOUT_S = 60L
    const val LLM_TIMEOUT_S = 90L
    const val MAX_RETRIES = 2
    const val PAGE_SIZE = 20
}

/** 对应 iOS ChartConstants */
object ChartConstants {
    const val PAD_LEFT = 40f
    const val PAD_RIGHT = 8f
    const val PAD_TOP = 8f
    const val PAD_BOTTOM = 28f
    const val TARGET_LOW = 70.0
    const val TARGET_HIGH = 180.0
    val REF_LINES = doubleArrayOf(70.0, 140.0, 180.0)
    const val LABEL_FONT_SIZE = 9f
    const val LINE_WIDTH = 1.5f
}
