package com.baroness.app.components.chat.actions

import android.content.Context
import android.widget.Toast
import com.baroness.app.models.Message

object ChatCommunicationActions {
    
    fun reply(message: Message) {
        // Implementation later
    }

    fun edit(message: Message) {
        // Implementation later
    }

    fun unsend(context: Context, message: Message) {
        // Placeholder feedback
        Toast.makeText(context, "Message unsent", Toast.LENGTH_SHORT).show()
    }

    fun deleteForMe(context: Context, message: Message) {
        // Placeholder feedback
        Toast.makeText(context, "Deleted for me", Toast.LENGTH_SHORT).show()
    }

    fun deleteForEveryone(context: Context, message: Message) {
        // Placeholder feedback
        Toast.makeText(context, "Deleted for everyone", Toast.LENGTH_SHORT).show()
    }

    fun delete(context: Context, message: Message) {
        // Legacy bridge if needed, but we should use the specific ones
        Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
    }
}
