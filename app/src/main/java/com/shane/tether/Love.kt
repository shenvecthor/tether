package com.shane.tether

import kotlin.math.abs

data class LoveTap(val id: String, val emoji: String, val label: String, val verb: String)

/** Couple features: inspired by Between, Paired, Bond Touch, Jagat and Couple Widget. */
object Love {
    val taps = listOf(
        LoveTap("heart", "❤️", "Heart", "sent you a heart"),
        LoveTap("kiss", "😘", "Kiss", "blew you a kiss"),
        LoveTap("hug", "🤗", "Hug", "sent you a big hug"),
        LoveTap("miss", "🥺", "Miss you", "misses you"),
        LoveTap("think", "💭", "Thinking", "is thinking of you"),
        LoveTap("rose", "🌹", "Rose", "sent you a rose"),
        LoveTap("love", "🥰", "Love you", "loves you so much"),
        LoveTap("wink", "😉", "Wink", "winked at you"),
    )

    fun byId(id: String): LoveTap? = taps.firstOrNull { it.id == id }

    /** First 8 are shown up front; the rest sit behind "More moods". */
    val moods = listOf(
        "😊" to "Happy", "😍" to "In love", "🥺" to "Missing you", "😴" to "Sleepy",
        "😌" to "Chilling", "🥳" to "Excited", "😤" to "Stressed", "🍕" to "Hungry",
        // more
        "🤗" to "Cuddly", "😇" to "Grateful", "🎧" to "Vibing", "🎬" to "Watching",
        "💼" to "Working", "📚" to "Studying", "🚗" to "On the road", "🍳" to "Cooking",
        "🏋️" to "Working out", "🎮" to "Gaming", "🛍️" to "Shopping", "😢" to "Sad",
        "😡" to "Annoyed", "🤒" to "Unwell", "🥶" to "Cold", "🌧️" to "Gloomy",
    )
    const val MAIN_MOODS = 8

    fun moodLabel(emoji: String): String = moods.firstOrNull { it.first == emoji }?.second ?: ""

    /** One shared question a day, same on both phones (Paired-style). */
    private val questions = listOf(
        "What's your favourite memory of us so far?",
        "What's one small thing I do that makes you smile?",
        "Where would you love us to travel together?",
        "What song reminds you of us?",
        "What did you think the first time you saw me?",
        "What's your perfect lazy Sunday together?",
        "What's something new you'd like us to try this month?",
        "Which of my habits secretly makes you laugh?",
        "What's the best date we've ever been on?",
        "What's a dream you've never told anyone?",
        "If we had a day with no responsibilities, what would we do?",
        "What food would you want me to cook for you?",
        "What's one thing you'd like us to get better at as a couple?",
        "When did you first know you liked me?",
        "What's your favourite photo of us and why?",
        "What makes you feel most loved?",
        "What would our dream home look like?",
        "What movie should we watch together next?",
        "What's a tiny gift that would make your whole day?",
        "Describe us in three words.",
        "What's a goal you want us to reach together this year?",
        "What's the funniest moment we've shared?",
        "Which fictional couple are we most like?",
        "What do you look forward to most when we see each other?",
        "What's something you're proud of me for?",
        "If we could relive one day together, which would it be?",
        "What nickname should I call you that I don't already?",
        "What's your favourite way to spend time with me?",
        "What tradition should we start together?",
        "What's one thing on your bucket list we should do together?",
        "What was your first impression of my family or friends?",
        "What's a compliment you'd love to hear more often?",
        "Where should our next date be?",
        "What do you love most about our relationship right now?",
        "What's something you want to learn together?",
        "Which song would be our first dance?",
        "What's your favourite thing about my personality?",
        "What makes you feel safe with me?",
        "If we wrote a book about us, what would the title be?",
        "What's the sweetest thing I've ever done for you?",
        "What would you plan for our perfect anniversary?",
        "What small moment today made you think of me?",
        "What's one way I can support you better this week?",
        "What's a place in Guyana you want us to visit together?",
        "What's your love language, and has it changed?",
        "What are you most grateful for about us today?",
        "What adventure should we have before the year ends?",
        "What do you imagine us doing ten years from now?",
        "What's something silly you'd like us to do together?",
        "What's your favourite thing to do when we're apart?",
    )

    fun questionFor(epochDay: Long): String = questions[abs(epochDay % questions.size).toInt()]
}
