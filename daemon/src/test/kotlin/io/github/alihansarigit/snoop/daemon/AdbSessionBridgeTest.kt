package io.github.alihansarigit.snoop.daemon

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdbSessionBridgeTest {

    /**
     * `adb` exits 0 even when the on-device `tar` fails, so the diagnostic would
     * otherwise be stored as an archive the agent cannot open.
     */
    @Test
    fun refusesOutputThatIsNotATarArchive() {
        withFakeAdb("printf 'tar: SESSION_ID: No such file or directory'") { adb, output ->
            val failure = assertFailsWith<IllegalArgumentException> {
                AdbSessionBridge(adb).pullSessionArchive("com.example.debug", "session-1", output)
            }
            assertTrue(failure.message!!.contains("did not return a tar archive"))
            assertTrue(File(output, "session-1.tar").let { !it.exists() })
        }
    }

    @Test
    fun storesOutputThatCarriesTheTarMagic() {
        // A 512-byte header with `ustar` at offset 257 is the minimum a reader accepts.
        withFakeAdb(
            "printf '%0.s\\0' {1..257}; printf 'ustar'; printf '%0.s\\0' {1..250}",
        ) { adb, output ->
            val archive = AdbSessionBridge(adb)
                .pullSessionArchive("com.example.debug", "session-1", output)
            assertTrue(archive.isFile)
            assertTrue(archive.length() >= 512)
        }
    }

    private fun withFakeAdb(script: String, block: (String, File) -> Unit) {
        val directory = Files.createTempDirectory("snoop-fake-adb").toFile()
        try {
            val adb = File(directory, "adb").apply {
                writeText("#!/bin/bash\n$script\n")
                setExecutable(true)
            }
            block(adb.absolutePath, File(directory, "out"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
