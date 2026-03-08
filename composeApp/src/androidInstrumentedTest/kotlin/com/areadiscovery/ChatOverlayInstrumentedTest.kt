package com.areadiscovery

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the AI Chat overlay feature.
 * Chat is triggered by tapping the AISearchBar on the main map screen.
 */
@RunWith(AndroidJUnit4::class)
class ChatOverlayInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private fun waitForMapReady() {
        composeTestRule.waitUntil(timeoutMillis = 30_000) {
            composeTestRule.onAllNodes(hasText("Ask anything..."))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openChatOverlay() {
        // Tap the AISearchBar — opens ChatOverlay directly
        composeTestRule.onNodeWithText("Ask anything...").performClick()

        // Wait for ChatOverlay header
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(hasText("Ask about this area"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sendQuestion(question: String) {
        // Type in the ChatOverlay input field and tap Send
        composeTestRule.onNodeWithText("Ask a question...").performTextInput(question)
        composeTestRule.onNodeWithContentDescription("Send").performClick()
    }

    @Test
    fun searchBarIsVisibleOnReadyState() {
        waitForMapReady()
        composeTestRule.onNodeWithText("Ask anything...").assertIsDisplayed()
    }

    @Test
    fun tappingSearchBarOpensChatOverlay() {
        waitForMapReady()
        openChatOverlay()
        composeTestRule.onNodeWithText("Ask about this area").assertIsDisplayed()
    }

    @Test
    fun chatOverlayShowsUserBubbleAfterQuestion() {
        waitForMapReady()
        openChatOverlay()
        sendQuestion("Is it safe here?")

        // The user message should appear as a bubble
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("Is it safe here?"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Is it safe here?").assertIsDisplayed()
    }

    @Test
    fun chatOverlayStreamsAiResponse() {
        waitForMapReady()
        openChatOverlay()
        sendQuestion("What is nearby?")

        // Wait for AI response to complete (follow-up chips appear)
        composeTestRule.waitUntil(timeoutMillis = 30_000) {
            composeTestRule.onAllNodes(hasText("Tell me more"))
                .fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasText("What's nearby?"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun closeChatOverlayReturnsToMap() {
        waitForMapReady()
        openChatOverlay()

        // Close via X button
        composeTestRule.onNodeWithContentDescription("Close chat").performClick()

        // Verify we're back to the map — search bar visible again
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("Ask anything..."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Ask anything...").assertIsDisplayed()
    }

    @Test
    fun chatInputBarVisibleInOverlay() {
        waitForMapReady()
        openChatOverlay()

        // Verify input bar with placeholder and send button
        composeTestRule.onNodeWithText("Ask a question...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send").assertIsDisplayed()
    }
}
