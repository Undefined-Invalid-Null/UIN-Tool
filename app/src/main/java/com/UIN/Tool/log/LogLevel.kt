package com.UIN.Tool.log

enum class LogLevel(val priority: Int, val tag: String) {
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E"),
    SUCCESS(4, "S"),
    ACTION(4, "A"),
    PARAM(4, "P"),
    ENTER(4, "→"),
    EXIT(4, "←")
}