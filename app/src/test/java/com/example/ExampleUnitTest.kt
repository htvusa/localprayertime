package com.example

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    try {
      val azanText = URL("https://raw.githubusercontent.com/htvusa/pa/master/azan/manifest.json").readText()
      println("AZAN_MANIFEST_START")
      println(azanText)
      println("AZAN_MANIFEST_END")
    } catch (e: Exception) {
      println("AZAN_ERROR: " + e.message)
    }

    try {
      val quranText = URL("https://raw.githubusercontent.com/htvusa/pa/master/quran/manifest.json").readText()
      println("QURAN_MANIFEST_START")
      println(quranText)
      println("QURAN_MANIFEST_END")
    } catch (e: Exception) {
      println("QURAN_ERROR: " + e.message)
    }
    
    assertEquals(4, 2 + 2)
  }
}
