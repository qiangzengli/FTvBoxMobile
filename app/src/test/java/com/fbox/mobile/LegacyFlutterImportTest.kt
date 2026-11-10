package com.fbox.mobile

import org.junit.Assert.*
import org.junit.Test
import java.util.Arrays
import java.util.Collections

class LegacyFlutterImportTest {
    @Test
    fun mapsSubscriptionScopedSourcesWithoutChangingSourceKeys() {
        assertEquals("电影天堂", LegacyFlutterImport.sourceKey("1794279852721851::电影天堂"))
        assertEquals("plugin::site", LegacyFlutterImport.sourceKey("subscription::plugin::site"))
        assertEquals("legacy-site", LegacyFlutterImport.sourceKey("legacy-site"))
    }

    @Test
    @Throws(Exception::class)
    fun readsFlutterJsonListsInTheirSavedOrder() {
        val stored = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![\"newest\",\"older\"]"
        assertEquals(listOf("newest", "older"), LegacyFlutterImport.stringList(stored))
        assertEquals(emptyList<String>(), LegacyFlutterImport.stringList(""))
    }
}
