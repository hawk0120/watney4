package utils

interface AgentHook {
    suspend fun onNotification(msg: IncomingMessage): IncomingMessage? = msg
}

class HookRegistry(private val hooks: List<AgentHook> = emptyList()) {
    suspend fun intercept(msg: IncomingMessage): IncomingMessage? {
        var current = msg
        for (hook in hooks) {
            val result = hook.onNotification(current) ?: return null
            current = result
        }
        return current
    }

    val isEmpty: Boolean get() = hooks.isEmpty()
}
