package io.github.alihansarigit.snoop.feedback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class SessionStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sessionAndAttachmentsSurviveReload() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val session = store.start("Checkout")
        val screenshot = byteArrayOf(1, 2, 3, 4)

        val item = store.addFeedback(
            note = "Total overlaps the CTA",
            category = BuiltInCategories.Bugfix,
            screenshotPng = screenshot,
            semanticsSnapshot = "- Button id=checkout",
            networkTransactionIds = listOf(4L, 4L, 7L),
        )

        val restored = SessionStore(root)
        val restoredSession = restored.find(session.id)
        assertNotNull(restoredSession)
        assertEquals("Checkout", restoredSession?.name)
        assertEquals(listOf(4L, 7L), restoredSession?.items?.single()?.networkTransactionIds)
        val attachment = restoredSession?.items?.single()?.attachments?.single()
        assertArrayEquals(
            screenshot,
            store.sessionDirectory(session.id).resolve(attachment!!.relativePath).readBytes(),
        )
        assertEquals(item.id, restoredSession.items.single().id)
    }

    @Test
    fun closeClearsActiveSessionAndWritesStableManifest() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val session = store.start("Dark mode")

        val closed = store.closeActive()

        assertEquals(session.id, closed?.id)
        assertNotNull(closed?.closedAt)
        assertNull(store.active.value)
        assertNull(SessionStore(root).active.value)
    }

    @Test
    fun pendingCrashCanBeAttachedToAutomaticSession() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        store.writePendingCrash(
            PendingCrash(
                timestamp = 1L,
                threadName = "main",
                stackTrace = "IllegalStateException",
                deviceInfo = mapOf("model" to "test"),
                breadcrumbs = listOf(Breadcrumb(1L, "tap", "checkout")),
                logs = listOf("boom"),
            ),
            screenshotPng = byteArrayOf(9, 8, 7),
        )

        val item = store.attachPendingCrash()

        assertNotNull(item)
        assertEquals("bugfix", item?.category?.id)
        assertEquals("IllegalStateException", item?.stackTrace)
        assertNull(store.readPendingCrash())
        assertEquals(1, store.active.value?.items?.size)
    }

    @Test
    fun pinnedRegionsAndScreenContextSurviveReload() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val session = store.start("Pinned checkout")
        val region = byteArrayOf(7, 6, 5, 4)

        store.addFeedback(
            note = "Review pinned controls",
            category = BuiltInCategories.UiUx,
            screenshotPng = null,
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
            screenContext = FeedbackScreenContext(
                platformName = "Android 16",
                routeName = "CheckoutActivity",
                screenFileHint = "CheckoutScreen.kt",
                subScreen = "Payment",
            ),
            capturedPins = listOf(
                CapturedFeedbackPin(
                    id = 1,
                    xRatio = 0.25f,
                    yRatio = 0.75f,
                    componentName = "Button (\"Pay\")",
                    userNote = "The label is clipped",
                    timestamp = 3L,
                    subScreen = "Payment",
                    croppedRegionPng = listOf(region),
                ),
            ),
        )

        val restoredItem = SessionStore(root).find(session.id)?.items?.single()
        val restoredPin = restoredItem?.pins?.single()

        assertEquals("CheckoutActivity", restoredItem?.screenContext?.routeName)
        assertEquals("Button (\"Pay\")", restoredPin?.componentName)
        assertEquals(0.25f, restoredPin?.xRatio)
        assertEquals("Payment", restoredPin?.subScreen)
        val regionPath = restoredPin?.croppedAttachmentPaths?.single()
        assertArrayEquals(
            region,
            store.sessionDirectory(session.id).resolve(regionPath!!).readBytes(),
        )
    }

    @Test
    fun feedbackCanBeSavedToASelectedOpenSessionWithoutChangingActiveSession() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val selected = store.start("Selected")
        val active = store.start("Active")

        val saved = store.addFeedbackToSession(
            sessionId = selected.id,
            note = "Save this to the selected session",
            category = BuiltInCategories.Feature,
            screenshotPng = null,
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
        )

        assertNotNull(saved)
        assertEquals(1, store.find(selected.id)?.items?.size)
        assertEquals(0, store.find(active.id)?.items?.size)
        assertEquals(active.id, store.active.value?.id)
    }

    @Test
    fun closedSessionRejectsNewFeedback() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val closed = store.start("Closed")
        store.closeActive()

        val saved = store.addFeedbackToSession(
            sessionId = closed.id,
            note = "Must not be saved",
            category = BuiltInCategories.Bugfix,
            screenshotPng = null,
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
        )

        assertNull(saved)
        assertEquals(0, store.find(closed.id)?.items?.size)
        assertNull(store.active.value)
    }

    @Test
    fun deletingActiveSessionRemovesAttachmentsAndClearsActiveState() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val session = store.start("Delete me")
        store.addFeedback(
            note = "Has evidence",
            category = BuiltInCategories.UiUx,
            screenshotPng = byteArrayOf(1, 3, 5, 7),
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
        )
        val sessionDirectory = store.sessionDirectory(session.id)

        val deleted = store.delete(session.id)

        assertEquals(true, deleted)
        assertEquals(false, sessionDirectory.exists())
        assertNull(store.find(session.id))
        assertNull(store.active.value)
        assertNull(SessionStore(root).find(session.id))
    }

    @Test
    fun agentBundleContainsMarkdownManifestAndAttachments() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val session = store.start("Agent export")
        val screenshot = byteArrayOf(4, 3, 2, 1)
        val item = store.addFeedback(
            note = "Export this",
            category = BuiltInCategories.UiUx,
            screenshotPng = screenshot,
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
        )
        val attachmentPath = item.attachments.single().relativePath

        val bundle = store.writeAgentBundle(
            sessionId = session.id,
            markdown = "# Feedback",
            attachmentPaths = listOf(attachmentPath),
        )

        assertTrue(bundle.isFile)
        ZipFile(bundle).use { zip ->
            assertNotNull(zip.getEntry("feedback.md"))
            assertNotNull(zip.getEntry("manifest.json"))
            assertArrayEquals(
                screenshot,
                zip.getInputStream(zip.getEntry(attachmentPath)).readBytes(),
            )
        }
    }

    @Test
    fun partialBundleKeepsItsOwnFileAndCarriesTheFilteredManifest() {
        val root = temporaryFolder.newFolder("feedback")
        val store = SessionStore(root)
        val session = store.start("Selected pins")
        val item = store.addFeedback(
            note = "Only this pin",
            category = BuiltInCategories.Bugfix,
            screenshotPng = byteArrayOf(9, 9),
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
        )
        val attachmentPath = item.attachments.single().relativePath

        val fullBundle = store.writeAgentBundle(
            sessionId = session.id,
            markdown = "# Everything",
            attachmentPaths = listOf(attachmentPath),
        )
        val partialBundle = store.writeAgentBundle(
            sessionId = session.id,
            markdown = "# Only pin 2",
            attachmentPaths = listOf(attachmentPath),
            bundleName = "${session.id}-selection-agent-feedback.zip",
            manifestJson = """{"id":"${session.id}","partial":true}""",
        )

        // A partial export must not overwrite the full-session bundle.
        assertNotEquals(fullBundle.absolutePath, partialBundle.absolutePath)
        assertTrue(fullBundle.isFile)
        assertTrue(partialBundle.isFile)

        ZipFile(fullBundle).use { zip ->
            assertEquals(
                "# Everything",
                zip.getInputStream(zip.getEntry("feedback.md")).readBytes().decodeToString(),
            )
        }
        ZipFile(partialBundle).use { zip ->
            assertEquals(
                "# Only pin 2",
                zip.getInputStream(zip.getEntry("feedback.md")).readBytes().decodeToString(),
            )
            // The filtered manifest replaces the on-disk one, so unselected pins do not leak.
            assertEquals(
                """{"id":"${session.id}","partial":true}""",
                zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString(),
            )
        }
    }
}
