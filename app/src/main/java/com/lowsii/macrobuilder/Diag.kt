package com.smac.macrobuilder

object Diag {
    @Volatile var connected = false
    @Volatile var lastKey = "-"
    @Volatile var result = ""
    @Volatile var arg = ""
    @Volatile var lastInject = ""
}
