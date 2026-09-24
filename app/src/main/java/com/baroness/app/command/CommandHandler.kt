package com.baroness.app.command

interface CommandHandler {
    fun canHandle(intent: String): Boolean
    fun execute(intent: String, parameters: Map<String, Any?>?): Boolean
}
