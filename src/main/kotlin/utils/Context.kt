package utils

class Context {
    private var messages: MutableList<ChatMessage>? = null

    fun bind(messages: MutableList<ChatMessage>) {
        this.messages = messages
    }

    fun isBound(): Boolean = messages != null

    fun toList(): List<ChatMessage> = checkNotNull(messages) { "Context not bound" }.toList()

    val size: Int get() = checkNotNull(messages) { "Context not bound" }.size

    fun add(msg: ChatMessage) {
        checkNotNull(messages) { "Context not bound" }.add(msg)
    }

    fun truncate(keepLast: Int): String {
        val msgs = checkNotNull(messages) { "Context not bound" }
        val systemMessages = msgs.filter { it.role == "system" }
        val nonSystem = msgs.filter { it.role != "system" }
        val toRemove = (nonSystem.size - keepLast).coerceAtLeast(0)
        if (toRemove == 0) return "Nothing to truncate — ${nonSystem.size} non-system messages (≤ $keepLast)"
        msgs.clear()
        msgs.addAll(systemMessages)
        msgs.addAll(nonSystem.takeLast(keepLast.coerceAtLeast(1)))
        return "Truncated $toRemove message(s). Keeping last $keepLast of ${nonSystem.size} non-system messages. New total: ${msgs.size}"
    }

    fun inject(role: String, content: String, index: Int? = null): String {
        val msgs = checkNotNull(messages) { "Context not bound" }
        val chatMsg = ChatMessage(role, content)
        val systemCount = msgs.count { it.role == "system" }
        val minIndex = systemCount
        val clampedIndex = if (index != null) index.coerceIn(minIndex, msgs.size) else msgs.size
        msgs.add(clampedIndex, chatMsg)
        val clamped = index != null && clampedIndex != index
        return buildString {
            append("Injected $role message (${content.length} chars)")
            if (clamped) append(" at clamped position $clampedIndex (requested $index, min=$minIndex)")
            else append(" at position $clampedIndex")
            append(". Total: ${msgs.size}")
        }
    }

    fun clearNonSystem(): Int {
        val msgs = checkNotNull(messages) { "Context not bound" }
        val count = msgs.count { it.role != "system" }
        msgs.removeAll { it.role != "system" }
        return count
    }
}
