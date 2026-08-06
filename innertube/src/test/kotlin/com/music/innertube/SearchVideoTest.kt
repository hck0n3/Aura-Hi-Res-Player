package com.music.innertube

import com.music.innertube.YouTube.SearchFilter
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SearchVideoTest {
    // The explicit `: Unit` is load-bearing. The body's last expression is
    // `items?.forEach { … }`, which is `Unit?` — not `Unit` — so an expression body made the compiled
    // method return Object, and JUnit rejects the whole CLASS with "Method testVideoSearch() should be
    // void". That took every other test in this module down with it: `:innertube:test` has been failing
    // to initialise since ec290c85, so the module has effectively had no test coverage at all.
    @Test
    fun testVideoSearch(): Unit = runBlocking {
        val result = YouTube.search("fakira", SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"))
        println("Result: $result")
        if (result.isSuccess) {
            println("Items size: ${result.getOrNull()?.items?.size}")
            result.getOrNull()?.items?.forEach {
                println("Item: $it")
            }
        } else {
            println("Error: ${result.exceptionOrNull()}")
        }
    }
}
