package app.mayak

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun addsAccessAndFindsItOnHomeAndServers() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Серверы").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Ссылка доступа").performTextInput(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=reality&sni=example.org&pbk=sample&type=tcp#Family%20VPN"
        )
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hasText("Добавить доступ") and hasClickAction()).performClick()
        compose.waitUntil(5000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Family VPN").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasText("Главная") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Главная").assertIsDisplayed()
        compose.onNodeWithText("Family VPN").assertIsDisplayed()
        compose.onNodeWithText("Подключить").assertIsDisplayed()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "mayak-review-home.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mayak")
        }
        val resolver = compose.activity.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Серверы").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Family VPN").assertIsDisplayed()
        compose.onNodeWithText("Выбран для подключения").assertIsDisplayed()
    }
}
