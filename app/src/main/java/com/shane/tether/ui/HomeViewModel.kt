package com.shane.tether.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shane.tether.Cloud
import com.shane.tether.Couple
import com.shane.tether.LivePoint
import com.shane.tether.Prefs
import com.shane.tether.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private fun <T> Flow<T>.state(init: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), init)

    /** True when browsing without a partner ("Skip for now"): shows a demo partner so the UI can be explored. */
    val preview: Boolean = !Prefs.isPaired

    /** Ticks over at midnight so the question of the day (and its answers) refresh daily. */
    val today: StateFlow<String> = flow {
        while (true) {
            emit(Cloud.dayKey(System.currentTimeMillis()))
            delay(30_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, Cloud.dayKey(System.currentTimeMillis()))

    private val demoMe = MutableStateFlow<LivePoint?>(null)
    private val demoPartner = MutableStateFlow<LivePoint?>(null)

    val partner: StateFlow<LivePoint?> = if (preview) demoPartner else Cloud.liveFlow(Cloud.partnerSlot).state(null)
    val me: StateFlow<LivePoint?> = if (preview) demoMe else Cloud.liveFlow(Cloud.mySlot).state(null)
    val partnerProfile: StateFlow<Profile?> =
        if (preview) MutableStateFlow(Profile("Your love", "", "🥰", System.currentTimeMillis() - 40 * 60_000L))
        else Cloud.profileFlow(Cloud.partnerSlot).state(null)
    val places = Cloud.placesFlow().state(emptyList())
    val messages = Cloud.messagesFlow().state(emptyList())
    val events = Cloud.eventsFlow().state(emptyList())
    val couple = Cloud.coupleFlow().state(Couple())
    @OptIn(ExperimentalCoroutinesApi::class)
    val daily: StateFlow<Map<String, String>> = today.flatMapLatest { Cloud.dailyFlow(it) }.state(emptyMap())

    // ---- weekly recap (cached for the session) ----
    val recap = MutableStateFlow<Recap?>(null)
    val recapLoading = MutableStateFlow(false)

    fun loadRecap(force: Boolean = false) {
        if (preview || recapLoading.value || (recap.value != null && !force)) return
        recapLoading.value = true
        viewModelScope.launch {
            recap.value = runCatching { Recaps.compute(places.value, events.value) }.getOrNull()
            recapLoading.value = false
        }
    }

    // ---- partner's avatar photo ----
    val partnerAvatar: StateFlow<android.graphics.Bitmap?> =
        Cloud.avatarFlow(Cloud.partnerSlot)
            .map { b64 -> b64?.let { com.shane.tether.Avatars.decode(it) } }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .state(null)

    init {
        if (preview) setPreviewLocation(6.8013, -58.1551, 25f) // Georgetown until we know where you are
    }

    /** Places you at [lat],[lng] and a pretend partner ~1.2 km away. */
    fun setPreviewLocation(lat: Double, lng: Double, acc: Float) {
        if (!preview) return
        val now = System.currentTimeMillis()
        demoMe.value = LivePoint(lat, lng, acc, 0f, 82, false, now, false, false, null)
        demoPartner.value = LivePoint(lat + 0.0085, lng + 0.0065, 12f, 0f, 64, true, now - 3 * 60_000L, false, false, null)
    }
}
