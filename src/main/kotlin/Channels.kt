data class Channel(val id: Int, val name: String, val aliases: List<String>? = null)

typealias Channels = List<Channel>

data class PlaylistChannel(val scraper: Scraper?,
                           val name: String,
                           var channel: Channel? = null,
                           var id: Int = 0)
