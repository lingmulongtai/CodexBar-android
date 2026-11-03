package com.codexbar.android.core.workmanager

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseInputMergerKeepRulesTest {
    @Test fun `release retains the constructor WorkManager invokes reflectively`() {
        val appDir = listOf(File("."), File("app")).first { File(it, "proguard-rules.pro").isFile }
        val rules = File(appDir, "proguard-rules.pro").readText().replace("\r\n", "\n")
        val rule = Regex("""-keep class \* extends androidx\.work\.InputMerger\s*\{([^}]*)}""").find(rules)
        assertTrue("InputMerger subclasses must retain their names and reflective constructor", rule != null)
        assertTrue("Class-only keep rules do not preserve the default constructor in R8 full mode",
            rule?.groupValues?.get(1)?.contains("public <init>();") == true)
    }
}
