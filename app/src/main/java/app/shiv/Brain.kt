package app.shiv

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import org.json.JSONObject
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ActionTier { FREE, ACT_THEN_TELL, ASK_FIRST }

data class BrainReply(
    val text: String,
    val event: EventRecord? = null,
    val memory: MemoryFact? = null,
    val reminderLeadMinutes: Int? = null,
    val reschedule: RescheduleRequest? = null,
    val draft: ReplyDraft? = null,
    val sms: SmsDraft? = null,
    val deleteEverything: Boolean = false,
    val forgetPhrase: String? = null
)

data class RescheduleRequest(val search: String, val newStart: Long)
data class SmsDraft(val person: String, val body: String)
data class AgentDecision(
    val tool: String?, val say: String, val text: String? = null, val key: String? = null,
    val minutes: Int? = null, val title: String? = null, val startsAt: Long? = null,
    val place: String? = null, val person: String? = null, val eventId: String? = null,
    val notificationTitle: String? = null, val notificationBody: String? = null
)
data class BrainRuntimeStatus(val queuedRequests: Int = 0, val inferenceRunning: Boolean = false, val lastRoutingMillis: Long? = null, val lastReplyMillis: Long? = null, val status: String = "No local chat model installed")

/**
 * The single boundary for all assistant intelligence. Beta reasoning and event/photo understanding
 * are local, deterministic functions; no network request or raw audio/photo persistence occurs here.
 */
interface BrainPort {
    suspend fun reply(input: String, context: String): String
}

/** The only class allowed to call a generative model. All inference runs locally. */
class Brain(private val appContext: Context? = null) : BrainPort {
    companion object {
        const val REMINDER_LEAD_KEY = "default_lead_minutes"
        const val DEFAULT_LEAD_MINUTES = 10
    }

    private val modelLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var loadedEngine: Engine? = null
    @Volatile private var loadedPath: String? = null
    @Volatile var modelStatus: String = "No local chat model installed"
        private set
    @Volatile var lastInferenceMillis: Long? = null
        private set
    @Volatile var lastRoutingMillis: Long? = null
        private set
    val runtimeStatus = kotlinx.coroutines.flow.MutableStateFlow(BrainRuntimeStatus())
    private val inferenceQueue = PrioritizedInferenceQueue { publishRuntimeStatus() }

    /** Close only non-process-wide instances; BrainProvider keeps the shared instance alive. */
    suspend fun close() {
        inferenceQueue.close()
        modelLock.lock()
        try { closeEngine() } finally { modelLock.unlock() }
    }

    private fun publishRuntimeStatus() {
        runtimeStatus.value = BrainRuntimeStatus(
            queuedRequests = inferenceQueue.queuedCount,
            inferenceRunning = inferenceQueue.isRunning,
            lastRoutingMillis = lastRoutingMillis,
            lastReplyMillis = lastInferenceMillis,
            status = modelStatus
        )
    }

    /** Copy the selected .litertlm file into private app storage; no model download is performed. */
    suspend fun importModel(uri: android.net.Uri, role: String): String = withContext(Dispatchers.IO) {
        modelLock.lock()
        try {
            val context = requireNotNull(appContext) { "Brain requires an application context" }
            require(role == "fast" || role == "chat")
            val models = File(context.filesDir, "models").apply { mkdirs() }
            val target = File(models, "$role.litertlm")
            val temporary = File(models, "$role.litertlm.importing")
            val previous = File(models, "$role.litertlm.previous")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open the selected model" }.use { source -> temporary.outputStream().use { source.copyTo(it) } }
            }
            require(temporary.length() > 1_000_000L) { temporary.delete(); "The selected file is too small to be a model" }
            closeEngine()
            previous.delete()
            if (target.exists()) check(target.renameTo(previous)) { temporary.delete(); "Could not safely replace the existing model" }
            if (!temporary.renameTo(target)) {
                previous.renameTo(target)
                temporary.delete()
                error("Could not safely install the selected model")
            }
            previous.delete()
            modelStatus = "${role.replaceFirstChar(Char::uppercase)} model imported (${target.length() / 1_000_000} MB)"
            publishRuntimeStatus()
            target.absolutePath
        } finally { modelLock.unlock(); publishRuntimeStatus() }
    }

    fun installedModels(): Pair<Boolean, Boolean> {
        val root = appContext?.let { File(it.filesDir, "models") } ?: return false to false
        return File(root, "fast.litertlm").isFile to File(root, "chat.litertlm").isFile
    }

    override suspend fun reply(input: String, context: String): String =
        generateDecision(input, context, allowNotifications = false)?.say.orEmpty()

    suspend fun decideProactive(context: String): AgentDecision? =
        generateDecision(context.take(800), context, allowNotifications = true)

    private suspend fun generateDecision(input: String, context: String, allowNotifications: Boolean): AgentDecision? =
        inferenceQueue.submit(
            priority = when {
                allowNotifications -> 10
                Regex("\\b(urgent|emergency|right now|immediately)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input) -> 100
                else -> 50
            },
            staleAfterMillis = if (allowNotifications) 45_000L else 120_000L,
            staleValue = { if (allowNotifications) AgentDecision("stay_quiet", "") else AgentDecision("ask_user", "That request waited too long in the local AI queue. Please send it again.") }
        ) {
        withContext(Dispatchers.IO) {
        try {
            modelLock.lock()
            try {
            val root = appContext?.let { File(it.filesDir, "models") }
            val started = System.currentTimeMillis()
            val fast = root?.let { File(it, "fast.litertlm") }?.takeIf { it.isFile }?.absolutePath
            val chat = root?.let { File(it, "chat.litertlm") }?.takeIf { it.isFile }?.absolutePath
            if (fast == null && chat == null) return@withContext if (allowNotifications) AgentDecision("stay_quiet", "") else AgentDecision(
                "ask_user", "I’m here, but my on-device language model hasn’t been installed yet. Event and reminder tools still work; add a local model in Settings when you’re ready."
            )
            // One mutex and a single cached engine ensure inference never overlaps. The small model
            // first routes urgency/quiet decisions; the larger model is loaded only for conversation.
            val routeStarted = System.currentTimeMillis()
            var route = fast?.let { runModel(it, "Classify this user message. Return exactly one word: QUIET if no reply is appropriate, URGENT if immediate safety/time-critical, COMMITMENT if a promise/event, MEMORY if a personal fact, otherwise CHAT. Message: ${input.take(500)}", 12) }
            val validRoutes = setOf("QUIET", "URGENT", "COMMITMENT", "MEMORY", "CHAT")
            if (fast != null && (route?.trim()?.uppercase(Locale.ROOT) ?: "") !in validRoutes) {
                route = runModel(fast, "Return only one of these exact words: QUIET, URGENT, COMMITMENT, MEMORY, CHAT. Message: ${input.take(500)}", 12)
                if ((route?.trim()?.uppercase(Locale.ROOT) ?: "") !in validRoutes) {
                    modelStatus = "Fast router returned an invalid result; safely stayed quiet"
                    return@withContext AgentDecision("stay_quiet", "")
                }
            }
            if (fast != null) lastRoutingMillis = System.currentTimeMillis() - routeStarted
            if (route?.trim()?.equals("QUIET", ignoreCase = true) == true) {
                modelStatus = "Fast router chose silence"
                return@withContext AgentDecision("stay_quiet", "")
            }
            val path = chat ?: fast!!
            val proactiveTool = if (allowNotifications) ", send_notification" else ""
            val proactiveRule = if (allowNotifications) " In this proactive check, choose send_notification only if it is useful, timely, non-repetitive, and worth interrupting; otherwise choose stay_quiet. send_notification arguments are {title,body}." else " Do not use send_notification in a chat response."
            val prompt = """You are Shiv, a loyal, warm, concise personal manager. Use the private context. Do not invent facts or claim an action happened unless the app did it. Return exactly one JSON object and no markdown, with keys "tool", "arguments", "say". "tool" must be null or one of: create_event, update_event, set_reminder, save_memory, save_rule, ask_user, stay_quiet$proactiveTool. Use only a tool when its arguments are fully grounded in the user's words and context; if time or meaning is unclear, choose ask_user. Otherwise use null.$proactiveRule Allowed arguments: create_event={title,startsAt,person?,place?}, update_event={eventId,newStart}, set_reminder={minutes}, save_memory={text}, save_rule={key,value}, ask_user={}, stay_quiet={}, null={}. startsAt/newStart are Unix milliseconds. Only use future dates. set_reminder minutes must be 0..1440; save_rule key must be "$REMINDER_LEAD_KEY". Keep "say" under 90 words and never claim an unexecuted tool succeeded.

PRIVATE CONTEXT (on this phone only):
$context

User: $input"""
            var decision: AgentDecision? = null
            for (attempt in 0..1) {
                val request = if (attempt == 0) prompt else "$prompt\n\nYour previous output was invalid. Return only the required valid JSON object this time."
                decision = parseDecision(runModel(path, request, if (allowNotifications) 100 else 180), allowNotifications)
                if (decision != null) break
            }
            lastInferenceMillis = System.currentTimeMillis() - started
            modelStatus = if (decision == null) "Model returned invalid tool JSON twice; safely stayed quiet" else "${File(path).name} · ready · ${lastInferenceMillis} ms"
            decision ?: AgentDecision("stay_quiet", "")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            modelStatus = "Local model unavailable: ${failure.javaClass.simpleName}"
            AgentDecision("ask_user", "My on-device model couldn’t answer just now. Your saved data is safe; check model setup and try again.")
            } finally { modelLock.unlock() }
        } finally { publishRuntimeStatus() }
        }
    }

    private fun parseDecision(raw: String, allowNotifications: Boolean): AgentDecision? = runCatching {
        val root = JSONObject(raw.trim())
        require(root.keys().asSequence().toSet() == setOf("tool", "arguments", "say"))
        val rawTool = root.get("tool")
        val tool = if (rawTool == JSONObject.NULL) null else rawTool as? String ?: error("tool must be a string or null")
        val args = root.getJSONObject("arguments")
        val say = root.getString("say").trim().takeIf { it.length <= 480 } ?: error("invalid say")
        fun keys(vararg expected: String) = require(args.keys().asSequence().toSet() == expected.toSet())
        fun string(key: String, limit: Int): String = args.getString(key).trim().also { require(it.isNotEmpty() && it.length <= limit) }
        fun integer(key: String, range: LongRange): Long {
            val value = args.get(key) as? Number ?: error("$key must be a number")
            val long = value.toLong()
            require(value.toDouble() == long.toDouble() && long in range)
            return long
        }
        val now = System.currentTimeMillis()
        val future = (now + 30_000L)..(now + 365L * 24 * 60 * 60 * 1000L)
        when (tool) {
            null, "stay_quiet", "ask_user" -> { keys(); if (tool == "stay_quiet") require(say.isEmpty()) else require(say.isNotEmpty()); AgentDecision(tool, say) }
            "save_memory" -> { keys("text"); require(say.isNotEmpty()); AgentDecision(tool, say, text = string("text", 240).also { require(it.length >= 3) }) }
            "save_rule" -> {
                keys("key", "value")
                val key = string("key", 60); val value = string("value", 5)
                val numeric = value.toIntOrNull() ?: error("value must be a whole number")
                require(key == REMINDER_LEAD_KEY && numeric in 0..1440 && say.isNotEmpty())
                AgentDecision(tool, say, key = key, minutes = numeric)
            }
            "set_reminder" -> { keys("minutes"); val minutes = integer("minutes", 0L..1440L).toInt(); require(say.isNotEmpty()); AgentDecision(tool, say, minutes = minutes) }
            "create_event" -> {
                val allowed = setOf("title", "startsAt", "person", "place")
                require(args.keys().asSequence().toSet().containsAll(setOf("title", "startsAt")))
                require(args.keys().asSequence().toSet().all { it in allowed })
                val title = string("title", 120); require(title.length >= 2)
                val startsAt = integer("startsAt", future)
                val person = if (args.has("person")) string("person", 80) else null
                val place = if (args.has("place")) string("place", 120) else null
                require(say.isNotEmpty()); AgentDecision(tool, say, title = title, startsAt = startsAt, person = person, place = place)
            }
            "update_event" -> {
                keys("eventId", "newStart")
                val eventId = string("eventId", 80); val newStart = integer("newStart", future)
                require(say.isNotEmpty()); AgentDecision(tool, say, eventId = eventId, startsAt = newStart)
            }
            "send_notification" -> {
                require(allowNotifications); keys("title", "body")
                val title = string("title", 80); val body = string("body", 220)
                AgentDecision(tool, "", notificationTitle = title, notificationBody = body)
            }
            else -> error("Unsupported or unsafe tool")
        }
    }.getOrNull()

    private suspend fun runModel(path: String, prompt: String, maxTokens: Int): String {
        val engine = getEngine(path)
        val output = StringBuilder()
        engine.createConversation(ConversationConfig(
            systemInstruction = Contents.of("You are Shiv, a private on-device personal manager. Be honest and do not invent or claim actions."),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.65),
            maxOutputToken = maxTokens
        )).use { conversation -> conversation.sendMessageAsync(prompt).collect { output.append(it.toString()) } }
        return output.toString()
    }

    private suspend fun getEngine(path: String): Engine {
        loadedEngine?.takeIf { loadedPath == path && it.isInitialized() }?.let { return it }
        closeEngine()
        val engine = try {
            Engine(EngineConfig(modelPath = path, backend = Backend.GPU(), cacheDir = appContext?.cacheDir?.path)).also { it.initialize() }
        } catch (_: Exception) {
            Engine(EngineConfig(modelPath = path, backend = Backend.CPU(), cacheDir = appContext?.cacheDir?.path)).also { it.initialize() }
        }
        loadedEngine = engine
        loadedPath = path
        return engine
    }

    private fun closeEngine() {
        runCatching { loadedEngine?.close() }
        loadedEngine = null
        loadedPath = null
    }

    fun answer(
        input: String,
        profile: UserProfile?,
        memories: List<MemoryFact>,
        reminderLeadMinutes: Int
    ): BrainReply {
        val message = input.trim()
        if (message.isBlank()) return BrainReply("I’m listening. No rush—though my calendar would like a word.")

        parseReminderCorrection(message)?.let {
            return BrainReply(withRecall("Done. I’ll remind you $it minutes early from now on. See? I can learn.", message, profile, memories), reminderLeadMinutes = it)
        }
        if (isDeleteAll(message)) return BrainReply(withRecall("I can erase all your local Shiv data. Please confirm below; that cannot be undone.", message, profile, memories), deleteEverything = true)
        parseForgetRequest(message)?.let {
            return BrainReply(withRecall("I can forget that saved fact. Confirm below and I’ll remove it.", message, profile, memories), forgetPhrase = it)
        }
        parseSms(message)?.let {
            return BrainReply(withRecall("I’ve prepared the message to ${it.person}. Confirm to open your messaging app; it will not send until you tap Send there.", message, profile, memories), sms = it)
        }
        parseDraft(message)?.let {
            return BrainReply(withRecall("Draft saved for ${it.person}. I left it in your drafts; you’re still in charge of sending it.", message, profile, memories), draft = it)
        }
        parseReschedule(message)?.let {
            return BrainReply(withRecall("I’ll move the matching event and update its reminder. I’ll tell you when it’s done.", message, profile, memories), reschedule = it)
        }

        val lead = parseEvent(message)
        if (lead != null) {
            val memory = extractMemory(message)
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(lead.startsAt)
            val placeText = lead.place?.let { " at $it" }.orEmpty()
            val personText = lead.person?.let { " with $it" }.orEmpty()
            val response = "On it: ${lead.title}$personText$placeText, $whenText. I’ve saved it and set your $reminderLeadMinutes-minute reminder."
            return BrainReply(withRecall(response, message, profile, memories), event = lead, memory = memory)
        }

        val fact = extractMemory(message)
        val name = profile?.name?.trim()?.takeIf { it.isNotEmpty() }
        val salutation = name?.let { "$it, " }.orEmpty()
        val conversational = when {
            message.contains("thank", true) -> "You’re welcome, ${salutation}I’ve got you. (Try not to make a habit of being right all the time.)"
            message.contains("hello", true) || message.equals("hi", true) || message.contains("hey", true) -> "Hey${salutation}what are we getting done today? I brought the imaginary clipboard."
            fact != null -> "Got it. I’ll remember: ${fact.text}. Your personal manager is taking notes—metaphorically, and encrypted."
            else -> "${salutation}Got it. I’m on your side. Want me to turn that into a reminder or a note?"
        }
        return BrainReply(withRecall(conversational, message, profile, memories), memory = fact)
    }

    suspend fun respond(
        input: String,
        profile: UserProfile?,
        memories: List<MemoryFact>,
        reminderLeadMinutes: Int,
        contextSnapshot: String
    ): BrainReply {
        val parsed = answer(input, profile, memories, reminderLeadMinutes)
        // Deterministic actions are parsed without a model call. Remaining decisions go through
        // strict local JSON validation; malformed output is retried once and then stays quiet.
        if (parsed.event != null || parsed.reminderLeadMinutes != null || parsed.reschedule != null ||
            parsed.draft != null || parsed.sms != null || parsed.deleteEverything || parsed.forgetPhrase != null) return parsed
        val decision = generateDecision(input, contextSnapshot, allowNotifications = false) ?: return parsed.copy(text = "")
        return when (decision.tool) {
            "stay_quiet" -> parsed.copy(text = "")
            null, "ask_user" -> parsed.copy(text = decision.say)
            "save_memory" -> parsed.copy(text = decision.say, memory = MemoryFact(text = requireNotNull(decision.text)))
            "save_rule", "set_reminder" -> parsed.copy(text = decision.say, reminderLeadMinutes = requireNotNull(decision.minutes))
            "create_event" -> parsed.copy(text = decision.say, event = EventRecord(
                title = requireNotNull(decision.title), startsAt = requireNotNull(decision.startsAt),
                place = decision.place, person = decision.person, source = "CHAT"
            ))
            "update_event" -> parsed.copy(text = decision.say, reschedule = RescheduleRequest(requireNotNull(decision.eventId), requireNotNull(decision.startsAt)))
            else -> parsed.copy(text = "")
        }
    }

    private fun withRecall(reply: String, input: String, profile: UserProfile?, memories: List<MemoryFact>): String {
        val relevant = memories.firstOrNull { isRelevant(input, it.text) }?.text
            ?: memories.firstOrNull()?.text
            ?: profile?.notes?.takeIf { it.isNotBlank() }
        return if (relevant.isNullOrBlank()) reply else "$reply\n\nKeeping in mind: $relevant."
    }

    fun extractEvent(text: String, source: String = "CHAT"): EventRecord? = parseEvent(text, source)

    fun describePhoto(labels: List<String>): String = when {
        labels.isEmpty() -> "I couldn’t confidently identify anything in that photo. The picture was only analyzed on this phone and wasn’t saved by Shiv."
        else -> "This looks like ${labels.take(3).joinToString(", ")}. That’s my on-device best guess; the photo wasn’t saved by Shiv."
    }

    /** On-device speech only. If the phone has no downloaded local speech model, returns null. */
    fun createOnDeviceTranscriber(
        context: Context,
        onText: (String) -> Unit,
        onRecognitionError: (Int) -> Unit
    ): OnDeviceTranscriber? {
        if (android.os.Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return null
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            override fun onError(error: Int) = onRecognitionError(error)
            override fun onResults(results: android.os.Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
            }
        })
        return OnDeviceTranscriber(recognizer)
    }

    class OnDeviceTranscriber internal constructor(private val recognizer: SpeechRecognizer) {
        fun start() {
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            recognizer.startListening(request)
        }
        fun close() = recognizer.destroy()
    }

    /** The full image-analysis call stays behind Brain and uses ML Kit's bundled local model. */
    fun describePhoto(
        image: InputImage,
        onImageConsumed: () -> Unit = {},
        onAnalysisUnresolved: (Throwable) -> Unit = {},
        onDescription: (String) -> Unit
    ) {
        val labeler = MlKitPhotoLabeler(
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS),
            onTaskUnresolved = onAnalysisUnresolved
        )
        PhotoLabelingPipeline.run(
            image = image,
            labeler = labeler,
            callbackExecutor = PhotoLabelingExecutors.mainCallback,
            onImageConsumed = onImageConsumed,
            onDeliveryFailure = {
                android.util.Log.e("ShivPhotoLabeler", "Image result could not be delivered to the main thread", it)
            }
        ) { result ->
            val description = result.fold(
                onSuccess = { labels -> describePhoto(labels.sortedByDescending { it.confidence }.map { it.text }) },
                onFailure = { "I couldn’t confidently identify the photo. It was analyzed on this phone and Shiv didn’t save it." }
            )
            onDescription(description)
        }
    }

    fun extractMemory(text: String): MemoryFact? {
        val value = text.trim().trimEnd('.', '!', '?')
        val explicit = Regex("^(?:please\\s+)?(?:remember that|remember|keep in mind|for your memory)\\s+(.+)$", RegexOption.IGNORE_CASE).find(value)
        val personal = Regex("^(?:i am|i'm|i work|i live|i prefer|i like|i dislike|my (?:name|birthday|job|role|routine|preference|goal|partner|family|doctor|address)\\b)\\s+(.+)$", RegexOption.IGNORE_CASE).find(value)
        val candidate = explicit?.groupValues?.get(1)?.trim() ?: personal?.let { value } ?: return null
        if (candidate.length < 3 || candidate.length > 240) return null
        return MemoryFact(text = candidate)
    }

    fun parseEvent(text: String, source: String = "CHAT"): EventRecord? {
        if (!Regex("\\b(meeting|meet|appointment|call|lunch|dinner|interview|class|flight|remind me|remember to|doctor|pickup|pick up|deadline|commitment|visit)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        val date = parseDateTime(text, Calendar.getInstance()) ?: return null
        val person = Regex("\\bwith\\s+([\\p{L}][\\p{L}'-]*(?:\\s+[\\p{L}][\\p{L}'-]*)?)(?=\\s+(?:on|today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|at)\\b|[,.;]|$)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', '!')?.takeIf { it.isNotBlank() }
        val placeMatch = Regex("\\b(?:at|in)\\s+(?!\\d)([\\p{L}][\\p{L}0-9 '&.-]{1,40}?)(?=\\s+with\\b|\\s+(?:on|today|tomorrow|at)\\b|[,.;]|$)", RegexOption.IGNORE_CASE).find(text)
        val place = placeMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val title = text.replace(Regex("^(?:please\\s+)?(?:remind me to|remind me|remember to)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:on\\s+)?(?:today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bin\\s+\\d+\\s+days?\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bat\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bwith\\s+[\\p{L}][\\p{L}'-]*(?:\\s+[\\p{L}][\\p{L}'-]*)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:at|in)\\s+(?!\\d)[\\p{L}][\\p{L}0-9 '&.-]{1,40}$", RegexOption.IGNORE_CASE), "")
            .trim().trim(',', '.', '!', ':')
        if (title.isBlank()) return null
        return EventRecord(id = UUID.randomUUID().toString(), title = title, startsAt = date.timeInMillis, place = place, person = person, source = source)
    }

    fun parseDateTime(text: String, base: Calendar = Calendar.getInstance()): Calendar? {
        val day = (base.clone() as Calendar).apply {
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val lower = text.lowercase(Locale.getDefault())
        when {
            Regex("\\btomorrow\\b").containsMatchIn(lower) -> day.add(Calendar.DAY_OF_YEAR, 1)
            Regex("\\btoday\\b").containsMatchIn(lower) -> Unit
            else -> {
                val weekdays = mapOf("monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY, "sunday" to Calendar.SUNDAY)
                val match = weekdays.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(lower) }
                val relative = Regex("\\bin\\s+(\\d+)\\s+days?\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                if (match != null) {
                    var delta = (match.value - day.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (delta == 0) delta = 7
                    day.add(Calendar.DAY_OF_YEAR, delta)
                } else if (relative != null) day.add(Calendar.DAY_OF_YEAR, relative)
                else return null
            }
        }
        val clock = Regex("\\bat\\s+(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)?\\b|\\b(\\d{1,2})(?::([0-5]\\d))\\s*(am|pm)\\b", RegexOption.IGNORE_CASE).find(text) ?: return null
        val hourRaw = (clock.groupValues[1].ifBlank { clock.groupValues[4] }).toIntOrNull() ?: return null
        val minute = (clock.groupValues[2].ifBlank { clock.groupValues[5] }).toIntOrNull() ?: 0
        val meridiem = clock.groupValues[3].ifBlank { clock.groupValues[6] }.lowercase()
        if (hourRaw !in 0..23 || (meridiem.isNotBlank() && hourRaw !in 1..12)) return null
        val hour = when (meridiem) { "pm" -> if (hourRaw == 12) 12 else hourRaw + 12; "am" -> if (hourRaw == 12) 0 else hourRaw; else -> if (hourRaw in 1..7) hourRaw + 12 else hourRaw }
        day.set(Calendar.HOUR_OF_DAY, hour); day.set(Calendar.MINUTE, minute)
        return day
    }

    fun parseReminderCorrection(text: String): Int? {
        val match = Regex("(?:remind me|reminder).{0,35}?(\\d{1,3})\\s+minutes?\\s+early", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(\\d{1,3})\\s+minutes?\\s+early", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..1440 }
    }

    fun parseReschedule(text: String): RescheduleRequest? {
        val match = Regex("^(?:please\\s+)?(?:reschedule|move|change)\\s+(?:my\\s+)?(.+?)\\s+to\\s+(.+)$", RegexOption.IGNORE_CASE).find(text.trim()) ?: return null
        val date = parseDateTime(match.groupValues[2]) ?: return null
        return RescheduleRequest(match.groupValues[1].trim(), date.timeInMillis)
    }

    fun parseDraft(text: String): ReplyDraft? {
        val match = Regex("^draft\\s+(?:a\\s+)?(?:reply\\s+)?to\\s+([\\p{L}][\\p{L} .'-]{0,35}):\\s*(.+)$", RegexOption.IGNORE_CASE).find(text.trim()) ?: return null
        return ReplyDraft(person = match.groupValues[1].trim(), body = match.groupValues[2].trim())
    }

    fun parseSms(text: String): SmsDraft? {
        val match = Regex("^(?:please\\s+)?send\\s+(?:a\\s+)?(?:text|message)\\s+to\\s+([\\p{L}][\\p{L} .'-]{0,35}):\\s*(.+)$", RegexOption.IGNORE_CASE).find(text.trim())
            ?: Regex("^(?:please\\s+)?text\\s+([\\p{L}][\\p{L} .'-]{0,35}):\\s*(.+)$", RegexOption.IGNORE_CASE).find(text.trim())
            ?: return null
        return SmsDraft(match.groupValues[1].trim(), match.groupValues[2].trim())
    }

    fun isDeleteAll(text: String): Boolean = Regex("\\b(delete|erase|wipe)\\s+(?:all\\s+)?(?:my\\s+)?(?:shiv\\s+)?data\\b|\\bforget everything\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

    fun parseForgetRequest(text: String): String? = Regex("^(?:please\\s+)?forget\\s+(?:that\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        .find(text.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 3 }

    fun isRelevant(input: String, fact: String): Boolean {
        val stop = setOf("that", "this", "with", "from", "when", "what", "have", "your", "about", "would", "could", "please", "remind", "meeting", "today", "tomorrow")
        val words = input.lowercase().split(Regex("[^\\p{L}0-9]+" )).filter { it.length > 3 && it !in stop }
        return words.any { it in fact.lowercase() }
    }
}

/** Shared process-wide instance ensures the view model and background worker share one inference lock. */
object BrainProvider {
    @Volatile private var instance: Brain? = null
    fun get(context: Context): Brain = instance ?: synchronized(this) {
        instance ?: Brain(context.applicationContext).also { instance = it }
    }
}

/** Single-consumer inference queue: urgent user requests outrank ordinary chat, which outranks
 * background proactive checks. Expired work is answered safely instead of blocking indefinitely. */
internal class PrioritizedInferenceQueue(private val onChanged: () -> Unit = {}) : AutoCloseable {
    private data class Task(
        val priority: Int, val sequence: Long, val createdAt: Long, val staleAfterMillis: Long,
        val isCancelled: () -> Boolean, val cancelResult: (CancellationException) -> Unit,
        val execute: suspend () -> Unit
    )
    private val sequence = AtomicLong()
    private val tasks = PriorityBlockingQueue<Task>(11, compareByDescending<Task> { it.priority }.thenBy { it.sequence })
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var closed = false
    @Volatile var isRunning: Boolean = false
        private set
    val queuedCount: Int get() = tasks.size
    private val worker = workerScope.launch {
        for (ignored in wake) {
            while (true) {
                val task = tasks.poll() ?: break
                if (task.isCancelled()) continue
                isRunning = true
                onChanged()
                try { task.execute() }
                catch (_: CancellationException) { /* Per-request cancellation must not kill the consumer. */ }
                catch (_: Throwable) { /* Isolate task failures. */ }
                finally { isRunning = false; onChanged() }
            }
        }
    }
    suspend fun <T> submit(priority: Int, staleAfterMillis: Long, staleValue: () -> T, block: suspend () -> T): T {
        check(!closed) { "Inference queue is closed" }
        if (tasks.size >= MAX_QUEUED) throw RejectedExecutionException("Local inference queue is full")
        val result = CompletableDeferred<T>()
        val createdAt = System.currentTimeMillis()
        val task = Task(priority, sequence.getAndIncrement(), createdAt, staleAfterMillis,
            isCancelled = { result.isCancelled }, cancelResult = { result.cancel(it) },
            execute = {
                if (result.isCancelled) return@Task
                if (System.currentTimeMillis() - createdAt > staleAfterMillis) result.complete(staleValue())
                else try { result.complete(block()) }
                catch (failure: CancellationException) { result.cancel(failure) }
                catch (failure: Throwable) { result.completeExceptionally(failure) }
            })
        tasks.add(task); onChanged(); wake.trySend(Unit)
        try { return result.await() }
        finally {
            if (!currentCoroutineContext().isActive) {
                val cancelled = CancellationException("Inference caller cancelled")
                result.cancel(cancelled); tasks.remove(task); wake.trySend(Unit); onChanged()
            }
        }
    }
    override fun close() {
        if (closed) return
        closed = true; wake.close()
        val cancelled = CancellationException("Inference queue closed")
        tasks.toList().forEach { if (tasks.remove(it)) it.cancelResult(cancelled) }
        worker.cancel(cancelled); workerScope.cancel(cancelled); onChanged()
    }
    private companion object { const val MAX_QUEUED = 32 }
}