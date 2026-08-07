package com.xjie.app.feature.xage

/** One page index is the source of truth for both capsule taps and horizontal swipes. */
internal enum class XAgeSection(val label: String) {
    Data("数据"),
    Chat("问答"),
    XAge("X年龄"),
}

internal data class XAgeShellState(val page: Int = 0) {
    val normalizedPage: Int
        get() = page.coerceIn(0, XAgeSection.entries.lastIndex)

    val selectedSection: XAgeSection
        get() = sectionForPage(normalizedPage)

    fun selecting(section: XAgeSection): XAgeShellState = copy(page = section.ordinal)

    /** Opening and returning from a child destination must not rewrite the shell page. */
    fun returningFromChild(): XAgeShellState = copy(page = normalizedPage)

    companion object {
        val pageCount: Int
            get() = XAgeSection.entries.size

        fun sectionForPage(page: Int): XAgeSection =
            XAgeSection.entries[page.coerceIn(0, XAgeSection.entries.lastIndex)]
    }
}
