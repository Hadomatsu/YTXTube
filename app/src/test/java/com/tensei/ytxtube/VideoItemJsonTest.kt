package com.tensei.ytxtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoItemJsonTest {
    @Test
    fun roundTripKeepsEverySavedField() {
        val v = VideoItem(
            id = "dQw4w9WgXcQ", title = "Title", channel = "Channel", channelUrl = "https://youtube.com/channel/UC1",
            thumbnail = "https://i.ytimg.com/vi/x/hq.jpg", avatar = null, durationSec = 213, views = 42, uploaded = "1 day ago",
        )
        assertEquals(v, videoItemFromJson(JSONObject(v.toJson().toString())))
    }

    @Test
    fun missingIdIsSkipped() {
        assertNull(videoItemFromJson(JSONObject().put("title", "no id")))
    }
}
