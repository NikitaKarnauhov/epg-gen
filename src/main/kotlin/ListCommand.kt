import java.io.File
import java.nio.file.FileSystems

enum class ChannelStatus { All, Matched, Unmatched }

fun executeList(cache: Cache, pattern: String, channelStatus: ChannelStatus): Unit {
    data class Row(
            val playlistId: String, val playlistName: String,
            val scraper: String, val scraperId: String, val scraperName: String)
    val rows = mutableListOf<Row>()
    val status = Status.parse(cache)
    val matched = mutableSetOf<Pair<String, Int>>()
    val matcher = if (pattern.isNotEmpty())
        FileSystems.getDefault().getPathMatcher("glob:**${pattern.toLowerCase()}**")
    else
        null

    fun match(str: String) = matcher == null ||
            matcher.matches(File(str.toLowerCase()).toPath())

    status.playlist.channels
            .filterValues {
                channelStatus == ChannelStatus.All ||
                (channelStatus == ChannelStatus.Unmatched && it.channel == null) ||
                (channelStatus == ChannelStatus.Matched && it.channel != null)
            }.forEach { _, pc ->
        if (match(pc.name) || pc.channel?.let { match(it.name) } ?: false) {
            rows += Row(pc.id.toString(), pc.name, pc.scraper?.name ?: "",
                    pc.channel?.id?.toString() ?: "", pc.channel?.name ?: "")
            if (pc.scraper != null)
                pc.channel?.let { matched += Pair(pc.scraper.name, it.id) }
        }
    }

    if (channelStatus == ChannelStatus.All)
        status.entries.forEach { (name, scraper) ->
            scraper.channels.forEach { channel ->
                val names = mutableListOf<String>(channel.name)
                channel.aliases?.let { names.addAll(it) }
                names.forEach {
                    if (!matched.contains(Pair(name, channel.id)) && match(it))
                        rows += Row("", "", name, channel.id.toString(), it)
                }
            }
        }

    rows.sortWith(compareBy({ it.scraper }, { it.scraperName }, { it.playlistName }))
    rows.add(0, Row("ID", "name", "scraper", "scraper ID", "scraper name"))
    var playlistIdWidth = 0
    var playlistNameWidth = 0
    var scraperWidth = 0
    var scraperIdWidth = 0
    var scraperNameWidth = 0
    rows.forEach {
        playlistIdWidth = maxOf(playlistIdWidth, it.playlistId.length)
        playlistNameWidth = maxOf(playlistNameWidth, it.playlistName.length)
        scraperWidth = maxOf(scraperWidth, it.scraper.length)
        scraperIdWidth = maxOf(scraperIdWidth, it.scraperId.length)
        scraperNameWidth = maxOf(scraperNameWidth, it.scraperName.length)
    }
    rows.forEach {
        println("%s | %s | %s | %s | %s".format(
                it.playlistId.padStart(playlistIdWidth),
                it.playlistName.padEnd(playlistNameWidth),
                it.scraper.padStart(scraperWidth),
                it.scraperId.padStart(scraperIdWidth),
                it.scraperName.padEnd(scraperNameWidth)
        ))
    }
}
