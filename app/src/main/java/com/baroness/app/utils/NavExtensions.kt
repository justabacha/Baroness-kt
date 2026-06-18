package com.baroness.app.utils

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder


fun NavController.navigateClean(
    route: String,
    clearBackStack: Boolean = false,
    popUpToRoute: String? = null,
    inclusive: Boolean = false,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true

        if (clearBackStack) {
            val target = popUpToRoute ?: graph.startDestinationRoute
            target?.let {
                popUpTo(it) {
                    this.inclusive = inclusive
                }
            }
        }
        builder()
    }
}