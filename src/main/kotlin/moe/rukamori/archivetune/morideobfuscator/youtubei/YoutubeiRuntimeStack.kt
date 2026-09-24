package moe.rukamori.archivetune.morideobfuscator.youtubei

internal object YoutubeiRuntimeStack {
    const val JAVASCRIPT_LIMIT_BYTES = 2L * 1024L * 1024L

    // QuickJS limits native stack usage; it does not allocate a larger stack.
    // Leave room for ART/JNI frames and exception handling before the guard page.
    private const val THREAD_STACK_BYTES = 8L * 1024L * 1024L

    fun newThread(runnable: Runnable, name: String): Thread =
        Thread(null, runnable, name, THREAD_STACK_BYTES).apply { isDaemon = true }
}
