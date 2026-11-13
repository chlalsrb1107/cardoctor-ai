package com.team.cardoctor

import com.team.cardoctor.data.Category
import com.team.cardoctor.data.ShopSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parses a recorded-shape Overpass response (no network in tests). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopSearchTest {

    private val sample = """
        {"elements":[
          {"type":"node","id":1,"lat":37.570,"lon":126.980,"tags":{"shop":"car_repair","name":"가나 자동차공업사","addr:street":"세종대로","addr:housenumber":"10"}},
          {"type":"way","id":2,"center":{"lat":37.5666,"lon":126.9781},"tags":{"shop":"tyres","name":"다라 타이어"}},
          {"type":"node","id":3,"lat":37.568,"lon":126.979,"tags":{"shop":"car_repair"}},
          {"type":"node","id":4,"lat":37.600,"lon":127.000,"tags":{"shop":"car_repair","name":"마바 카센터"}}
        ]}
    """.trimIndent()

    @Test fun parses_named_shops_sorted_by_distance() {
        val shops = ShopSearch.parse(sample, 37.5665, 126.9780)
        assertEquals(listOf("다라 타이어", "가나 자동차공업사", "마바 카센터"), shops.map { it.name })
        assertEquals("세종대로 10", shops[1].address)
        assertEquals(setOf(Category.TIRE), shops[0].handles)
        assertTrue(Category.BRAKE in shops[1].handles)
    }

    @Test fun empty_or_broken_response_gives_no_shops() {
        assertEquals(0, ShopSearch.parse("""{"elements":[]}""", 0.0, 0.0).size)
        assertEquals(0, ShopSearch.parse("""{}""", 0.0, 0.0).size)
    }
}
