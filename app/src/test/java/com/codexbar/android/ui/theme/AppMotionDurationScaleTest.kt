package com.codexbar.android.ui.theme

import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.*
import org.junit.Test

class AppMotionDurationScaleTest {
    @Test fun `app scale replaces system context values including off and ten times slower`() {
        listOf(0f, .5f, 1f, 10f).forEach { systemScale ->
            val inherited = object : MotionDurationScale { override val scaleFactor = systemScale }
            val app = AppMotionDurationScale()
            val context = EmptyCoroutineContext + inherited + app
            assertSame(app, context[MotionDurationScale])
            assertEquals(1f, context[MotionDurationScale]!!.scaleFactor, 0f)
            app.speed = 2f
            assertEquals(.5f, context[MotionDurationScale]!!.scaleFactor, 0f)
        }
    }
}
