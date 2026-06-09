package tools

import utils.ReminderScheduler

class RemindTool(private val scheduler: ReminderScheduler) : Tool {
    override val name = "remind"
    override val description = "Schedule a one-time reminder. The prompt will be sent back to you as a [reminder] notification after the delay."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "prompt" to mapOf(
                "type" to "string",
                "description" to "The reminder message to send to yourself"
            ),
            "delayMinutes" to mapOf(
                "type" to "number",
                "description" to "Minutes from now to fire the reminder (min 1)"
            )
        ),
        "required" to listOf("prompt", "delayMinutes")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val prompt = args["prompt"] as? String ?: return "Error: missing 'prompt' argument"
        val delay = ((args["delayMinutes"] as? Double)?.toInt() ?: return "Error: missing 'delayMinutes' argument")
            .coerceAtLeast(1)
        return scheduler.schedule(prompt, delay)
    }
}
