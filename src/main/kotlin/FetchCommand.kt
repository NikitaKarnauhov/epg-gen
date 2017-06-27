import java.time.LocalDate

fun executeFetch(cache: Cache, date: LocalDate): Unit {
    executeCleanUp(cache, Cache.defaultMaxAgeDays)
    val status = Status.parse(cache)
    status.playlist.channels.forEach { _, playlistChannel ->
        playlistChannel.scraper?.fetchChannel(playlistChannel, cache, date)
    }
}
