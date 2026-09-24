package com.baroness.app.command

import android.content.Context
import android.util.Log
import com.baroness.app.command.handlers.ClockCommandHandler
import com.baroness.app.command.handlers.MediaCommandHandler
import com.baroness.app.command.handlers.NavigationCommandHandler

class LocalCommandExecutor(context: Context) {

    private val handlers: List<CommandHandler> = listOf(
        MediaCommandHandler(context),
        NavigationCommandHandler(context),
        ClockCommandHandler(context)
    )

    fun execute(intentName: String?, parameters: Map<String, Any?>?) {
        if (intentName.isNullOrBlank()) return
        Log.d("LocalCommandExecutor", "Executing intent: $intentName with params: $parameters")

        val handler = handlers.find { it.canHandle(intentName) }
        if (handler != null) {
            val success = handler.execute(intentName, parameters)
            Log.d("LocalCommandExecutor", "Handler executed $intentName with success=$success")
        } else {
            Log.w("LocalCommandExecutor", "No registered handler found for intent: $intentName")
        }
    }
}
