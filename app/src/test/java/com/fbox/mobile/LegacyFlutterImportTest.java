package com.fbox.mobile;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class LegacyFlutterImportTest {
    @Test public void mapsSubscriptionScopedSourcesWithoutChangingSourceKeys() {
        assertEquals("电影天堂", LegacyFlutterImport.sourceKey("1794279852721851::电影天堂"));
        assertEquals("plugin::site", LegacyFlutterImport.sourceKey("subscription::plugin::site"));
        assertEquals("legacy-site", LegacyFlutterImport.sourceKey("legacy-site"));
    }

    @Test public void readsFlutterJsonListsInTheirSavedOrder() throws Exception {
        String stored = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![\"newest\",\"older\"]";
        assertEquals(Arrays.asList("newest", "older"), LegacyFlutterImport.stringList(stored));
        assertEquals(Collections.emptyList(), LegacyFlutterImport.stringList(""));
    }
}
