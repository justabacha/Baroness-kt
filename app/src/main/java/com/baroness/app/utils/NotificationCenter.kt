package com.baroness.app.utils

import com.baroness.app.data.models.NotificationData
import com.baroness.app.viewmodels.NotificationViewModel

object NotificationCenter {
    private var viewModelRef: NotificationViewModel? = null

    fun setViewModel(viewModel: NotificationViewModel) {
        viewModelRef = viewModel
    }

    fun show(data: NotificationData) {
        viewModelRef?.showInAppNotification(data)
    }
}
