package com.team.cardoctor.ui

import com.team.cardoctor.data.Part
import com.team.cardoctor.data.RepairShop
import com.team.cardoctor.data.Weather

/** Everything the screens can ask for. MainViewModel implements it; render tests pass a no-op. */
interface UiActions {
    fun select(part: Part)
    fun routeTo(shop: RepairShop)
    fun runSelfDiagnosis()
    fun openReport()
    fun closeReport()
    fun callRoadside()
    /** Start listening for one spoken question (SpeechToText). */
    fun startVoice()
    /** Treat [text] as if the driver had said it (demo panel on an emulator without a mic). */
    fun voiceText(text: String)
    fun scenario(id: String)
    fun toggleFault(part: Part)
    fun toggleSlowLeak()
    fun setNight(v: Boolean?)
    fun setWeather(v: Weather?)
    fun setSpeed(v: Int?)
    fun setDriving(v: Boolean?)
    fun setRemainingKm(v: Double?)
    fun park()

    object None : UiActions {
        override fun select(part: Part) = Unit
        override fun routeTo(shop: RepairShop) = Unit
        override fun runSelfDiagnosis() = Unit
        override fun openReport() = Unit
        override fun closeReport() = Unit
        override fun callRoadside() = Unit
        override fun startVoice() = Unit
        override fun voiceText(text: String) = Unit
        override fun scenario(id: String) = Unit
        override fun toggleFault(part: Part) = Unit
        override fun toggleSlowLeak() = Unit
        override fun setNight(v: Boolean?) = Unit
        override fun setWeather(v: Weather?) = Unit
        override fun setSpeed(v: Int?) = Unit
        override fun setDriving(v: Boolean?) = Unit
        override fun setRemainingKm(v: Double?) = Unit
        override fun park() = Unit
    }
}
