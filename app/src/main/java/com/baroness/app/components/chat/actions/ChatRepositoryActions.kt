package com.baroness.app.components.chat.actions

import android.content.Context
import android.widget.Toast
import com.baroness.app.models.Message

object ChatRepositoryActions {
    
    fun star(context: Context, message: Message) {
        // Placeholder feedback
        Toast.makeText(context, "Added to Starred", Toast.LENGTH_SHORT).show()
    }

    fun pin(message: Message) {
        // Implementation later
    }

    fun remindMe(message: Message) {
        // Implementation later
    }

    fun messageInfo(message: Message) {
        // Implementation later
    }

    fun createWish(context: Context, message: Message) {
        // Placeholder feedback
        Toast.makeText(context, "Added to Wishlist", Toast.LENGTH_SHORT).show()
    }
}
