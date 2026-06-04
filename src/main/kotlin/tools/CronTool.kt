package tools

import utils.CronScheduler

class CronTool(private val scheduler: CronScheduler) : Tool {
    override val name = "cron"
    override val description = "Schedule, list, or remove recurring tasks (cron jobs). The prompt will be sent to you as a message when the job fires."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("add", "remove", "list"),
                "description" to "add = schedule a new job, remove = delete a job, list = show all jobs"
            ),
            "prompt" to mapOf(
                "type" to "string",
                "description" to "The task prompt to run on schedule (required for 'add')"
            ),
            "interval" to mapOf(
                "type" to "number",
                "description" to "Interval in minutes between runs (required for 'add', min 5)"
            ),
            "jobId" to mapOf(
                "type" to "number",
                "description" to "Job ID to remove (required for 'remove')"
            )
        ),
        "required" to listOf("action")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val action = args["action"] as? String ?: return "Error: missing 'action' argument"

        return when (action) {
            "add" -> {
                val prompt = args["prompt"] as? String ?: return "Error: missing 'prompt' argument"
                val interval = ((args["interval"] as? Double)?.toInt() ?: return "Error: missing 'interval' argument")
                    .coerceAtLeast(5)
                scheduler.addJob(prompt, interval)
            }
            "remove" -> {
                val jobId = ((args["jobId"] as? Double)?.toInt() ?: return "Error: missing 'jobId' argument")
                scheduler.removeJob(jobId)
            }
            "list" -> scheduler.listJobs()
            else -> "Error: unknown action '$action'. Use add, remove, or list."
        }
    }
}
