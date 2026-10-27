package com.shiv.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/** The Activity owns presentation state; data and Brain remain in their own layers. */
class ShivViewModel(application:Application):AndroidViewModel(application) {
    val controller=ShivController(application)
}
