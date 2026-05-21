package app.beacon.desktop

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertFalse

class SingBoxProcessTest {
    @Test
    fun stopDeletesGeneratedConfig() {
        val config = Files.createTempFile("beacon-sing-box", ".json")
        Files.writeString(config, "{}")

        SingBoxProcess(configFile = config).stop()

        assertFalse(config.exists())
    }
}
