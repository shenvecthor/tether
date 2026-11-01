package com.shane.tether

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** One holiday / observance for a given day. */
data class DayItem(
    val title: String,
    val extract: String?,
    val url: String?,
    val emoji: String,
    val idea: String,
)

data class GyHoliday(val date: LocalDate, val name: String)

/**
 * Free, key-less sources:
 *  - Wikipedia "On this day" holidays & observances feed (every day of the year, worldwide)
 *  - Nager.Date public holidays for Guyana (GY)
 * Results are cached on the phone so each day is only downloaded once.
 */
object Holidays {
    private const val UA = "Tether/1.2 (private couples app for Android)"

    suspend fun forDay(ctx: Context, date: LocalDate): List<DayItem> = withContext(Dispatchers.IO) {
        val sp = ctx.getSharedPreferences("holidays", Context.MODE_PRIVATE)
        val key = "wiki_${date.monthValue}_${date.dayOfMonth}" // same observances every year
        val json = sp.getString(key, null) ?: run {
            val url = "https://en.wikipedia.org/api/rest_v1/feed/onthisday/holidays/%02d/%02d"
                .format(date.monthValue, date.dayOfMonth)
            val body = get(url)
            if (sp.all.size > 60) sp.edit().clear().apply()
            sp.edit().putString(key, body).apply()
            body
        }
        parseWiki(json)
    }

    suspend fun guyana(ctx: Context, year: Int): List<GyHoliday> = withContext(Dispatchers.IO) {
        val sp = ctx.getSharedPreferences("holidays_gy", Context.MODE_PRIVATE)
        val key = "gy_$year"
        val json = sp.getString(key, null) ?: run {
            val body = get("https://date.nager.at/api/v3/PublicHolidays/$year/GY")
            sp.edit().putString(key, body).apply()
            body
        }
        runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    GyHoliday(LocalDate.parse(o.getString("date")), o.optString("name").ifBlank { o.optString("localName") })
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Api-User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parseWiki(json: String): List<DayItem> {
        val arr = JSONObject(json).optJSONArray("holidays") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<DayItem>()
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            val text = h.optString("text").trim().trimEnd(':')
            if (text.isEmpty() || !seen.add(text)) continue
            val page = h.optJSONArray("pages")?.optJSONObject(0)
            val extract = page?.optString("extract")?.takeIf { it.isNotBlank() }
            val url = page?.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")
                ?.takeIf { it.isNotBlank() }
            val (emoji, idea) = ideaFor(text + " " + (extract ?: ""), i)
            out += DayItem(text, extract, url, emoji, idea)
        }
        return out
    }

    // ------------------------------------------------ "Celebrate together" ideas

    private val rules: List<Triple<List<String>, String, String>> = listOf(
        Triple(listOf("halloween"), "🎃", "Carve a mini pumpkin or watch a spooky movie cuddled up 👻"),
        Triple(listOf("christmas"), "🎄", "Exchange a tiny handmade gift and play carols together 🎁"),
        Triple(listOf("new year"), "🎆", "Write three wishes for each other for the year ahead ✨"),
        Triple(listOf("valentine"), "💘", "Swap love letters, then read them out loud 💌"),
        Triple(listOf("mother"), "💐", "Call your moms together and tell them something sweet"),
        Triple(listOf("father"), "👔", "Share your favourite memory of your dads"),
        Triple(listOf("diwali", "deepavali"), "🪔", "Light diyas together and share sweets 🍬"),
        Triple(listOf("holi", "phagwah"), "🌈", "Wear bright colours and take a colourful selfie together"),
        Triple(listOf("eid"), "🌙", "Share a special meal and send warm wishes to family"),
        Triple(listOf("chocolate", "candy", "cake", "cookie", "dessert", "sweet", "sugar", "ice cream", "pie", "pancake", "donut"),
            "🍫", "Share a sweet treat and feed each other the first bite 🍰"),
        Triple(listOf("coffee", " tea"), "☕", "Have a coffee date — a video-call one counts too"),
        Triple(listOf("food", "pizza", "bread", "rice", "cheese", "taco", "pasta", "sandwich", "soup", "curry", "fish", "cook", "vegan"),
            "🍽️", "Cook it together tonight and rate it out of 10 ⭐"),
        Triple(listOf("wine", "beer", "rum", "cocktail", "drink"), "🥂", "Toast to each other (mocktails count!)"),
        Triple(listOf("music", "song", "jazz", "dance", "radio", "guitar", "opera"), "🎶", "Make a shared playlist and slow dance in the kitchen 💃"),
        Triple(listOf("book", "literacy", "poetry", "reading", "library", "writer", "author"), "📚", "Read a page aloud to each other or swap favourite quotes"),
        Triple(listOf("earth", "environment", "ocean", "forest", "tree", "water", "nature", "wildlife", "animal", "bird", "dog", "cat", "planet"),
            "🌿", "Go for a walk outside together — seawall sunset, anyone? 🌅"),
        Triple(listOf("love", "kiss", "hug", "friend", "romance", "kindness", "smile", "happiness", "compliment"),
            "💞", "Send a love tap and plan a cozy night in 🥰"),
        Triple(listOf("independence", "republic", "national day", "liberation", "revolution", "constitution", "unity", "flag", "arrival"),
            "🎉", "Learn three fun facts about it and quiz each other 🌍"),
        Triple(listOf("teacher", "education", "science", "student", "children", "youth", "child", "school"),
            "🎓", "Tell each other about your favourite teacher or class"),
        Triple(listOf("health", "heart", "cancer", "disease", "aids", "diabetes", "mental", "nurse", "doctor"),
            "💗", "Take a walk together and check in on each other's wellbeing"),
        Triple(listOf("remembrance", "memorial", "victims", "martyr", "dead", "souls"),
            "🕯️", "Light a candle together and share a fond memory"),
        Triple(listOf("saint", "feast", "christian", "church", "orthodox", "catholic", "religious", "prayer", "anglican", "lutheran"),
            "🕊️", "Take a quiet moment to share what you're grateful for 🙏"),
        Triple(listOf("women", "men's", "human rights", "peace", "refugee", "freedom", "equality"),
            "🤝", "Talk about someone who inspires you both"),
        Triple(listOf("sport", "football", "cricket", "olympic", "running", "bicycle", "fitness"),
            "🏏", "Play a quick game together — loser makes dinner 😄"),
        Triple(listOf("art", "film", "cinema", "photo", "design", "animation", "comic"),
            "🎨", "Recreate an old photo of you two 📸"),
    )

    private val fallbackEmoji = listOf("✨", "🎈", "🌟", "🎊", "💫")
    private val fallbackIdeas = listOf(
        "Do one small surprise for each other today 🎁",
        "Take a cute selfie together to remember the day 📸",
        "Plan a mini date around it this week 🗓️",
        "Text each other one thing you love about today ✨",
        "Look it up together and share the funniest fact 😂",
    )

    private fun ideaFor(text: String, index: Int): Pair<String, String> {
        val t = text.lowercase()
        for ((keys, emoji, idea) in rules) if (keys.any { t.contains(it) }) return emoji to idea
        return fallbackEmoji[index % fallbackEmoji.size] to fallbackIdeas[index % fallbackIdeas.size]
    }
}
