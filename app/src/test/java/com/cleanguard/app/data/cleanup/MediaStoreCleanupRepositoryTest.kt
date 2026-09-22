package com.cleanguard.app.data.cleanup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreCleanupRepositoryTest {
    @Test
    fun `parses supported MediaStore ids`() {
        assertEquals(MediaStoreItemReference("image", 42L), parseMediaStoreItemId("media:image:42"))
        assertEquals(MediaStoreItemReference("video", 7L), parseMediaStoreItemId("media:video:7"))
        assertEquals(MediaStoreItemReference("audio", 9L), parseMediaStoreItemId("media:audio:9"))
    }

    @Test
    fun `rejects malformed and non MediaStore ids`() {
        assertNull(parseMediaStoreItemId("saf:document:42"))
        assertNull(parseMediaStoreItemId("media:image:-1"))
        assertNull(parseMediaStoreItemId("media:file:1"))
        assertNull(parseMediaStoreItemId("media:image:1/../../2"))
    }
}
