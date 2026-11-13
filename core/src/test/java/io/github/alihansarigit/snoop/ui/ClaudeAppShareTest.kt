package io.github.alihansarigit.snoop.ui

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClaudeAppShareTest {

    @Test
    fun multipleImagesKeepPromptOnFirstSharedItem() {
        val markdown = "Fix the pinned checkout button"
        val firstImage = Uri.parse("content://snoop/first.png")
        val secondImage = Uri.parse("content://snoop/second.png")

        val intent = ClaudeAppShare.buildIntent(
            sessionName = "Checkout",
            markdown = markdown,
            imageUris = listOf(firstImage, secondImage),
        )

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(markdown, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(
            listOf(firstImage, secondImage),
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM),
        )
        assertEquals(2, intent.clipData?.itemCount)
        assertEquals(markdown, intent.clipData?.getItemAt(0)?.text)
        assertEquals(firstImage, intent.clipData?.getItemAt(0)?.uri)
        assertNull(intent.clipData?.getItemAt(1)?.text)
        assertEquals(secondImage, intent.clipData?.getItemAt(1)?.uri)
    }

    @Test
    fun singleImageUsesSingleSendContractWithPrompt() {
        val markdown = "Fix the selected pin"
        val image = Uri.parse("content://snoop/pin.png")

        val intent = ClaudeAppShare.buildIntent(
            sessionName = "Checkout",
            markdown = markdown,
            imageUris = listOf(image),
        )

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(markdown, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(image, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(1, intent.clipData?.itemCount)
        assertEquals(markdown, intent.clipData?.getItemAt(0)?.text)
        assertEquals(image, intent.clipData?.getItemAt(0)?.uri)
    }
}
