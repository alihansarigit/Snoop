package io.github.alihansarigit.snoop.feedback

import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMarkdownExporterTest {
    @Test
    fun includesEvidenceLinkedToSession() {
        val item = FeedbackItem(
            id = "item",
            createdAt = 2L,
            category = BuiltInCategories.Bugfix,
            note = "Checkout fails",
            breadcrumbs = listOf(Breadcrumb(1L, "touch", "checkout_button")),
            networkTransactionIds = listOf(5L),
            stackTrace = "Boom",
        )
        val session = FeedbackSession("session", "Checkout", 1L, 2L, items = listOf(item))
        val linked = NetworkTransaction(
            id = 5L,
            method = "POST",
            url = "https://example.test/checkout",
            responseCode = 500,
            responseBody = """{"error":"failed"}""",
            status = TransactionStatus.COMPLETE,
        )
        val unrelated = linked.copy(id = 9L, url = "https://example.test/unrelated")

        val markdown = AgentMarkdownExporter.export(session, listOf(linked, unrelated))

        assertTrue(markdown.contains("Checkout fails"))
        assertTrue(markdown.contains("checkout_button"))
        assertTrue(markdown.contains("https://example.test/checkout"))
        assertFalse(markdown.contains("https://example.test/unrelated"))
    }

    @Test
    fun formatsPinnedAgentTasksWithSelectedRegions() {
        val item = FeedbackItem(
            id = "item",
            createdAt = 2L,
            category = BuiltInCategories.UiUx,
            note = "Checkout polish",
            screenContext = FeedbackScreenContext(
                platformName = "Android 16",
                routeName = "CheckoutActivity",
                subScreen = "Payment",
            ),
            pins = listOf(
                FeedbackPin(
                    id = 1,
                    xRatio = 0.1234f,
                    yRatio = 0.8766f,
                    componentName = "Button (\"Pay\")",
                    userNote = "The label is clipped",
                    timestamp = 3L,
                    subScreen = "Payment",
                    croppedAttachmentPaths = listOf("attachments/item-pin1-1.png"),
                ),
            ),
        )
        val session = FeedbackSession("session", "Checkout", 1L, 2L, items = listOf(item))

        val markdown = AgentMarkdownExporter.export(session, emptyList())

        assertTrue(markdown.contains("CheckoutActivity"))
        assertTrue(markdown.contains("Pin #1 · Button (\"Pay\")"))
        assertTrue(markdown.contains("X:0.123, Y:0.877"))
        assertTrue(markdown.contains("The label is clipped"))
        assertTrue(
            markdown.contains(
                "![Feedback 1 · Pin 1 · Photo 1](attachments/item-pin1-1.png)",
            ),
        )
    }

    @Test
    fun writesFullScreenshotBeforeOrderedPinPhotos() {
        val screenshot = FeedbackAttachment(
            type = "screenshot",
            relativePath = "attachments/item-screenshot.png",
            mimeType = "image/png",
            createdAt = 2L,
        )
        val pin = FeedbackPin(
            id = 2,
            xRatio = 0.5f,
            yRatio = 0.5f,
            componentName = "Checkout",
            userNote = "Align it",
            timestamp = 3L,
            croppedAttachmentPaths = listOf(
                "attachments/item-pin2-1.png",
                "attachments/item-pin2-2.png",
            ),
        )
        val item = FeedbackItem(
            id = "item",
            createdAt = 2L,
            category = BuiltInCategories.UiUx,
            note = "Polish",
            attachments = listOf(screenshot),
            pins = listOf(pin),
        )

        val markdown = AgentMarkdownExporter.export(
            FeedbackSession("session", "Checkout", 1L, 2L, items = listOf(item)),
            emptyList(),
        )

        val screenshotIndex = markdown.indexOf(
            "![Feedback 1 · Full screenshot](attachments/item-screenshot.png)",
        )
        val firstPhotoIndex = markdown.indexOf(
            "![Feedback 1 · Pin 2 · Photo 1](attachments/item-pin2-1.png)",
        )
        val secondPhotoIndex = markdown.indexOf(
            "![Feedback 1 · Pin 2 · Photo 2](attachments/item-pin2-2.png)",
        )
        assertTrue(screenshotIndex >= 0)
        assertTrue(firstPhotoIndex > screenshotIndex)
        assertTrue(secondPhotoIndex > firstPhotoIndex)
    }
}
