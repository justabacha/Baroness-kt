package com.baroness.app.components.chat.actions

import android.content.Context
import android.widget.Toast
import com.baroness.app.models.Message

object ChatUtilityActions {
    
    fun copy(context: Context, message: Message) {
        // Placeholder feedback
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun translate(message: Message) {
        // Implementation later
    }

    fun share(message: Message) {
        // Implementation later
    }

    fun readAloud(message: Message) {
        // Implementation later
    }

    fun searchWithinChat(message: Message) {
        // Implementation later
    }
}
