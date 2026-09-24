package moe.rukamori.archivetune.morideobfuscator.youtubei

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.Executors

class YoutubeiRuntimeStackDeviceTest {
    @Test
    fun recursiveArrayCoercionDoesNotKillProcess() = runBlocking {
        Executors.newSingleThreadExecutor { runnable ->
            YoutubeiRuntimeStack.newThread(runnable, "Youtubei-stack-regression")
        }.asCoroutineDispatcher().use { dispatcher ->
            withContext(dispatcher) {
                val runtime = QuickJs.create(dispatcher)
                try {
                    runtime.maxStackSize = YoutubeiRuntimeStack.JAVASCRIPT_LIMIT_BYTES
                    runtime.evaluationTimeoutMillis = 5_000L
                    try {
                        // Exercise the array join/toString native recursion seen in the tombstone.
                        runtime.evaluate<String>(
                            "const a = []; a.toString = function() { return [a].join(); }; String(a);",
                        )
                        fail("Expected a controlled JavaScript stack overflow")
                    } catch (expected: QuickJsException) {
                        assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("stack", ignoreCase = true))
                    }
                    assertEquals(42, runtime.evaluate<Int>("21 * 2"))
                } finally {
                    runtime.close()
                }
            }
        }
    }
}
