import com.beust.klaxon.*
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import java.io.InputStream

class Playlist {
    var channels = mutableMapOf<String, PlaylistChannel>()

    fun set(scraper: Scraper?, name: String, channel: Channel?, id: Int): Boolean {
        val updated = !channels.contains(name)
        channels[name] = PlaylistChannel(scraper, name, channel, id)
        return updated
    }

    fun toJson(): JsonArray<Any?> {
        return json {
            array(channels.values.sortedBy { it.id }.map {
                val playlistId = it.id
                val name = it.name
                if (it.scraper != null) {
                    val id = it.channel?.id ?: 0
                    obj("scraper" to it.scraper.name, "id" to id,
                            "playlistId" to playlistId, "name" to name)
                } else
                    obj("playlistId" to playlistId, "name" to name)
            })
        }
    }

    companion object {
        fun fromJson(arr: JsonArray<JsonObject>, scrapers: Map<String, Scraper>): Playlist {
            val result = Playlist()
            result.channels = arr.mapNotNull {
                val scraper = it.string("scraper")?.let { scrapers[it] }
                val id = it.int("id") ?: 0
                val playlistId = it.int("playlistId") ?: 0
                val name = it.string("name")
                name?.let {
                    val channel = scraper?.channels?.find { it.id == id }
                    PlaylistChannel(scraper, name, channel, playlistId)
                }
            }.associateBy {
                it.name
            }.toMutableMap()
            return result
        }

        fun fromM3U(stream: InputStream): Playlist {
            val result = Playlist()
            val parser = PlaylistParser(stream, Format.EXT_M3U, Encoding.UTF_8,
                    ParsingMode.LENIENT)
            val playlist = parser.parse()
            var id = 0
            result.channels = playlist.mediaPlaylist.tracks.associateBy(
                    { it.trackInfo.title },
                    { PlaylistChannel(null, it.trackInfo.title, id = ++id) }).toMutableMap()
            return result
        }
    }
}
