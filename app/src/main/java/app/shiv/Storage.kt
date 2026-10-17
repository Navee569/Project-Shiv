package app.shiv

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(@PrimaryKey val id: String = UUID.randomUUID().toString(), val text: String, val fromUser: Boolean, val createdAt: Long = System.currentTimeMillis(), val eventLabel: String? = null)

@Entity(tableName = "memory_events")
data class MemoryEvent(@PrimaryKey val id: String = UUID.randomUUID().toString(), val kind: String, val title: String, val createdAt: Long = System.currentTimeMillis(), val reminderAt: Long? = null)

@Dao
interface ShivDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") fun messages(): Flow<List<ChatMessage>>
    @Insert suspend fun insertMessage(message: ChatMessage)
    @Insert suspend fun insertEvent(event: MemoryEvent)
}

@Database(entities = [ChatMessage::class, MemoryEvent::class], version = 1, exportSchema = true)
abstract class ShivDatabase : RoomDatabase() {
    abstract fun dao(): ShivDao
    companion object {
        @Volatile private var instance: ShivDatabase? = null
        fun get(context: Context): ShivDatabase = instance ?: synchronized(this) {
            instance ?: run {
                SQLiteDatabase.loadLibs(context)
                val factory = SupportOpenHelperFactory(LocalKeyStore(context).databaseKey())
                Room.databaseBuilder(context, ShivDatabase::class.java, "shiv-memory.db").openHelperFactory(factory).build().also { instance = it }
            }
        }
    }
}

class ShivRepository(context: Context) {
    private val dao = ShivDatabase.get(context).dao()
    val messages = dao.messages()
    suspend fun add(message: ChatMessage) = dao.insertMessage(message)
    suspend fun addEvent(event: MemoryEvent) = dao.insertEvent(event)
}
