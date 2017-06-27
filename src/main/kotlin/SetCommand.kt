fun executeSet(cache: Cache, playlistId: Int, scraperName: String,
               scraperId: Int): Unit
{
    val status = Status.parse(cache)
    val playlistChannel = status.playlist.channels.values.find {
        it.id == playlistId
    } ?: throw Exception("Channel ${playlistId} not found in playlist")
    val scraper = status.entries[scraperName] ?: throw Exception(
            "Scraper \"${scraperName}\" not found")
    val channel = scraper.channelsById[scraperId] ?: throw Exception(
            "Channel ${scraperId} not found for scraper \"${scraperName}\"")
    status.playlist.set(scraper, playlistChannel.name, channel, playlistId)
    cache.getStatus().bufferedWriter().use {
        it.write(status.toJson().toJsonString(true))
    }
}

fun executeUnset(cache: Cache, playlistId: Int): Unit {
    val status = Status.parse(cache)
    val playlistChannel = status.playlist.channels.values.find {
        it.id == playlistId
    } ?: throw Exception("Channel ${playlistId} not found in playlist")
    status.playlist.set(null, playlistChannel.name, null, playlistId)
    cache.getStatus().bufferedWriter().use {
        it.write(status.toJson().toJsonString(true))
    }
}
