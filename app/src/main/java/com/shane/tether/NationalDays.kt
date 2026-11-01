package com.shane.tether

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

data class NationalDay(
    val name: String,
    val hashtag: String,
    val url: String,
    val categories: List<String>,
    val fixed: IntArray?,      // [month, day]
    val floating: IntArray?,   // [month, isoWeekday 1-7, weekOfMonth 1-4 or -1 = last]
    val dates: Set<String>,    // listed ISO dates
    val registered: Boolean,
)

/**
 * National Day Calendar list (1,931 days), bundled in assets/national_days.json so it works offline.
 * Uses the site's listed dates for 2026–2027 and the recurrence rules for any other year.
 */
object NationalDays {
    private const val BASE = "https://nationaldaycalendar.com/celebrations/"

    @Volatile private var cache: List<NationalDay>? = null
    @Volatile private var listedYears: Set<Int> = emptySet()
    @Volatile var categoryLabels: Map<String, String> = emptyMap()
        private set

    suspend fun load(ctx: Context): List<NationalDay> = cache ?: withContext(Dispatchers.IO) {
        cache ?: parse(ctx.assets.open("national_days.json").bufferedReader().use { it.readText() })
            .also { cache = it }
    }

    suspend fun on(ctx: Context, date: LocalDate): List<NationalDay> =
        load(ctx).filter { matches(it, date) }.sortedWith(compareBy({ !it.registered }, { it.name }))

    private fun parse(json: String): List<NationalDay> {
        val root = JSONObject(json)
        val years = root.optJSONArray("years")
        listedYears = (0 until (years?.length() ?: 0)).map { years!!.getInt(it) }.toSet()
        val cats = root.optJSONObject("cats")
        categoryLabels = cats?.keys()?.asSequence()?.associateWith { cats.getString(it) } ?: emptyMap()
        val arr = root.getJSONArray("days")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val c = o.optJSONArray("c")
            val d = o.optJSONArray("d")
            val f = o.optJSONArray("f")
            val w = o.optJSONArray("w")
            NationalDay(
                name = o.getString("n"),
                hashtag = o.optString("h"),
                url = if (o.has("u")) BASE + o.getString("u") else o.optString("url"),
                categories = (0 until (c?.length() ?: 0)).map { c!!.getString(it) },
                fixed = f?.let { intArrayOf(it.getInt(0), it.getInt(1)) },
                floating = w?.let { intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2)) },
                dates = (0 until (d?.length() ?: 0)).map { d!!.getString(it) }.toSet(),
                registered = o.optInt("reg", 0) == 1,
            )
        }
    }

    fun matches(d: NationalDay, date: LocalDate): Boolean {
        if (date.year in listedYears) return date.toString() in d.dates
        d.fixed?.let { return date.monthValue == it[0] && date.dayOfMonth == it[1] }
        d.floating?.let { (m, wd, wom) ->
            if (date.monthValue != m || date.dayOfWeek.value != wd) return false
            return if (wom == -1) date.plusWeeks(1).monthValue != m else (date.dayOfMonth - 1) / 7 + 1 == wom
        }
        return false
    }

    // ------------------------------------------------ presentation

    fun toItem(d: NationalDay): DayItem {
        val labels = d.categories.mapNotNull { categoryLabels[it] }.take(3).joinToString(" · ")
        val extract = listOf(labels, d.hashtag).filter { it.isNotBlank() }.joinToString("\n")
        val (emoji, idea) = ideaFor(d)
        return DayItem(d.name, extract.ifBlank { null }, d.url, emoji, idea)
    }

    // Word-start regexes so "Vacation" doesn't match "cat", etc.
    private val emojiWords: List<Pair<Regex, String>> = listOf(
        "pizza" to "🍕", "cupcake" to "🧁", "cake" to "🎂", "cookie" to "🍪", "chocolate" to "🍫",
        "coffee" to "☕", "tea\\b" to "🍵", "ice cream" to "🍦", "donut" to "🍩", "doughnut" to "🍩",
        "taco" to "🌮", "burrito" to "🌯", "wine" to "🍷", "beer" to "🍺", "cocktail" to "🍸",
        "hamburger" to "🍔", "burger" to "🍔", "pies?\\b" to "🥧", "cheese" to "🧀", "pancake" to "🥞",
        "waffle" to "🧇", "candy" to "🍬", "apple" to "🍎", "banana" to "🍌", "strawberr" to "🍓",
        "popcorn" to "🍿", "sandwich" to "🥪", "bacon" to "🥓", "eggs?\\b" to "🥚", "fries" to "🍟",
        "pasta" to "🍝", "spaghetti" to "🍝", "soup" to "🍲", "sushi" to "🍣", "bread" to "🍞",
        "dogs?\\b" to "🐶", "pupp" to "🐶", "cats?\\b" to "🐱", "kitten" to "🐱", "birds?\\b" to "🐦",
        "horses?\\b" to "🐴", "bears?\\b" to "🐻", "fish" to "🐟", "turtle" to "🐢", "bees?\\b" to "🐝",
        "butterfl" to "🦋", "magic" to "🪄", "joke" to "😂", "trick or treat" to "🎃", "halloween" to "🎃",
        "kiss" to "💋", "hug" to "🤗", "love" to "💞", "friend" to "🫶", "sibling" to "👫",
        "mother" to "💐", "father" to "👔", "books?\\b" to "📚", "reading" to "📖", "music" to "🎶",
        "danc" to "💃", "movie" to "🎬", "games?\\b" to "🎮", "beach" to "🏖️", "sun\\b" to "☀️",
        "stars?\\b" to "⭐", "moon" to "🌙", "flower" to "🌸", "trees?\\b" to "🌳", "photo" to "📸",
        "doorbell" to "🔔", "nap\\b" to "😴", "sleep" to "😴", "selfie" to "🤳",
    ).map { (pattern, emoji) -> Regex("\\b$pattern") to emoji }

    private val categoryIdeas = listOf(
        "food-and-beverages" to ("🍰" to "Make or order it together tonight and rate it out of 10 ⭐"),
        "animal" to ("🐾" to "Watch cute animal videos together or visit a furry friend 🐶"),
        "music" to ("🎶" to "Make a shared playlist and slow dance in the kitchen 💃"),
        "arts-entertainment" to ("🎬" to "Movie or show night, snacks and cuddles included 🍿"),
        "nature" to ("🌿" to "Take a walk outside together — seawall sunset, anyone? 🌅"),
        "health-awareness" to ("💗" to "Go for a walk together and check in on each other's wellbeing"),
        "medical" to ("🩺" to "Remind each other to book that check-up you've been putting off"),
        "states-destinations" to ("🗺️" to "Plan a dream trip together, even if it's just for fun ✈️"),
        "education-history" to ("📜" to "Learn one fun fact about it and quiz each other"),
        "science" to ("🔬" to "Watch a short science video together and share the coolest fact"),
        "invention" to ("💡" to "Invent something silly together — a new couple's tradition counts"),
        "technology-communication" to ("📱" to "Send each other a cute voice note today"),
        "social-awareness" to ("🤝" to "Do one kind thing for someone together"),
        "military" to ("🎖️" to "Take a moment to appreciate the people who serve"),
        "a-look-back" to ("📼" to "Scroll back through your oldest photos together"),
        "pop-culture-lifestyle" to ("✨" to "Do it together and snap a selfie to remember it 📸"),
    )

    private fun ideaFor(d: NationalDay): Pair<String, String> {
        val lower = d.name.lowercase()
        val wordEmoji = emojiWords.firstOrNull { it.first.containsMatchIn(lower) }?.second
        val cat = categoryIdeas.firstOrNull { (slug, _) -> slug in d.categories }?.second
            ?: ("🎈" to "Celebrate it your own way and tell each other how it went 💫")
        return (wordEmoji ?: cat.first) to cat.second
    }
}
