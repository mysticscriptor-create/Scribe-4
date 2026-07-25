package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.ShortcutAction

object DefaultShortcuts {

    val all: List<ShortcutAction> = listOf(
        ShortcutAction(id = "tab",        label = "Tab",     kind = "insert", payload = "    "),
        ShortcutAction(id = "h1",         label = "H1",      kind = "insert", payload = "# "),
        ShortcutAction(id = "h2",         label = "H2",      kind = "insert", payload = "## "),
        ShortcutAction(id = "bold",       label = "B",       kind = "wrap",   payload = "**",  closing = "**"),
        ShortcutAction(id = "italic",     label = "I",       kind = "wrap",   payload = "*",   closing = "*"),
        ShortcutAction(id = "code",       label = "‹›",      kind = "wrap",   payload = "`",   closing = "`"),
        ShortcutAction(id = "quote",      label = "\u201C\u201D",  kind = "pair", payload = "\u201C", closing = "\u201D"),
        ShortcutAction(id = "smartquote", label = "\u2018\u2019",  kind = "pair", payload = "\u2018", closing = "\u2019"),
        ShortcutAction(id = "paren",      label = "( )",     kind = "pair",   payload = "(",   closing = ")"),
        ShortcutAction(id = "bracket",    label = "[ ]",     kind = "pair",   payload = "[",   closing = "]"),
        ShortcutAction(id = "brace",      label = "{ }",     kind = "pair",   payload = "{",   closing = "}"),
        ShortcutAction(id = "blockquote", label = "Quote",   kind = "insert", payload = "\n> "),
        ShortcutAction(id = "list",       label = "•",       kind = "insert", payload = "\n- "),
        ShortcutAction(id = "hr",         label = "—",       kind = "insert", payload = "\n\n---\n\n"),
        ShortcutAction(id = "emdash",     label = "—",       kind = "insert", payload = " \u2014 "),
        ShortcutAction(id = "ellipsis",   label = "…",       kind = "insert", payload = "\u2026")
    )
}
