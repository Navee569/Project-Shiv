package app.shiv.data.repository

import android.content.Context
import app.shiv.ShivDao
import app.shiv.ShivDatabase
import app.shiv.domain.repository.ShivViewRepository

/** Room-backed app repository. Room remains an implementation detail of the consumer. */
class RoomShivViewRepository internal constructor(dao: ShivDao) : ShivViewRepository by dao {
    constructor(context: Context) : this(ShivDatabase.get(context.applicationContext).dao())
}
