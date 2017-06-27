import java.io.File

fun editDistance(lhs: String, rhs: String): Int {
    val d = IntArray((lhs.length + 1)*(rhs.length + 1))
    fun get(l: Int, r: Int): Int = d[l*(rhs.length + 1) + r]
    fun set(l: Int, r: Int, w: Int) { d[l*(rhs.length + 1) + r] = w }
    fun eq(l: Int, r: Int) = lhs[l].equals(rhs[r], ignoreCase = true)

    (0..lhs.length).forEach { set(it, 0, it) }
    (0..rhs.length).forEach { set(0, it, it) }

    for (l in 1..lhs.length)
        for (r in 1..rhs.length) {
            var w = minOf(get(l - 1, r) + 1, get(l, r - 1) + 1,
                    get(l - 1, r - 1) + (if (eq(l - 1, r - 1)) 0 else 1))
            if (l > 1 && r > 1 && eq(l - 1, r - 2) && eq(l - 2, r - 1))
                w = minOf(w, get(l - 2, r - 2) + 1)
            set(l, r, w)
        }

    return get(lhs.length, rhs.length)
}

sealed class ChannelMatch {
    class None: ChannelMatch()
    class Exact(val channel: PlaylistChannel): ChannelMatch()
    class Approximate(val channels: List<Pair<PlaylistChannel, String>>): ChannelMatch()
}

fun matchChannel(channel: PlaylistChannel,
                 scrapers: MutableMap<String, Scraper>,
                 preferred: String): ChannelMatch
{
    var matches = emptyList<Triple<PlaylistChannel, Int, String>>()

    fun strip(name: String): String {
        var s = Regex("""(\p{IsAlphabetic})(\d)""").replace(name) {
            it.groups[1]!!.value + " " + it.groups[2]!!.value
        }
        s = Regex("""(\d)(\p{IsAlphabetic})""").replace(s) {
            it.groups[1]!!.value + " " + it.groups[2]!!.value
        }
        return Regex("""[\p{Punct}\p{Space}]|\b(tv|тв|channel|канал|телеканал|network|hd)\b""",
                RegexOption.IGNORE_CASE).replace(s, "")
    }

    fun containsWord(needle: String, haystack: String): Boolean {
        return Regex("\\b${needle}\\b", RegexOption.IGNORE_CASE).find(haystack) != null
    }

    fun processScraper(scraper: Scraper): ChannelMatch? {
        for (scraperChannel in scraper.channels) {
            val playlistChannel = PlaylistChannel(
                    scraper, scraperChannel.name, scraperChannel)
            val names = mutableListOf<String>(scraperChannel.name)
            scraperChannel.aliases?.let { names.addAll(it) }
            names.forEach {
                if (channel.name.compareTo(it, ignoreCase = true) == 0)
                    return ChannelMatch.Exact(playlistChannel)
                val scraperName = strip(it)
                val name = strip(channel.name)
                val w = editDistance(name, scraperName)
                if (w < minOf(3, name.length, scraperName.length))
                    matches += Triple(playlistChannel, w, it)
                else if (containsWord(name, it) ||
                        containsWord(scraperName, channel.name))
                    matches += Triple(playlistChannel, 3, it)
                else if (name.length >= 3 && scraperName.length >= 3 &&
                        (scraperName.contains(name, ignoreCase = true) ||
                                name.contains(scraperName, ignoreCase = true)))
                    matches += Triple(playlistChannel, 4, it)
            }
        }
        return null
    }

    if (preferred.isNotEmpty())
        scrapers[preferred]?.let { processScraper(it) }?.let { return it }

    for ((name, scraper) in scrapers)
        if (name != preferred)
            processScraper(scraper)?.let { return it }

    if (matches.isEmpty())
        return ChannelMatch.None()

    val exact = matches.filter { it.second == 0 }
            .distinctBy { it.first.channel?.name?.toLowerCase() }
    if (exact.size == 1) {
        matches.find {
            it.first.channel?.id == exact.first().first.channel?.id &&
            it.first.scraper?.name == preferred
        }?.let {
            return ChannelMatch.Exact(it.first)
        }
        return ChannelMatch.Exact(exact.first().first)
    }

    return ChannelMatch.Approximate(matches.sortedBy { it.second }.map {
        Pair(it.first, it.third)
    })
}

sealed class ChosenChannel {
    class Skip: ChosenChannel()
    class SkipAll: ChosenChannel()
    class Item(val channel: PlaylistChannel): ChosenChannel()
}

fun chooseChannel(target: PlaylistChannel, matches: List<Pair<PlaylistChannel, String>>): ChosenChannel {
    println("\nChoose channel mapping for \"${target.name}\":")
    println("A. (skip all)")
    println("0. (skip)")

    matches.forEachIndexed { index, (match, name) ->
        println("${index + 1}. \"${name}\" (${match.scraper?.name})")
    }

    while (true) {
        print("Enter (0 .. ${matches.size} or \"A\", default: 0): ")
        val answer = readLine()
        if (answer != null && !answer.isEmpty()) {
            if (answer.equals("a", ignoreCase = true))
                return ChosenChannel.SkipAll()
            val chosen = answer.toIntOrNull()
            if (chosen != null && chosen <= matches.size) {
                if (chosen > 0)
                    return ChosenChannel.Item(matches[chosen - 1].first)
                return ChosenChannel.Skip()
            }
        } else
            return ChosenChannel.Skip()
    }
}

fun executeMatch(cache: Cache, m3u8: File, interactive: Boolean,
                 clear: Boolean, preferred: String,
                 mailruRegion: Int, mailruTZ: Int,
                 yandexRegion: Int, yandexTZ: Int,
                 offline: Boolean): Unit
{
    val status = Status.parse(cache, mailruRegion, mailruTZ, yandexRegion, yandexTZ)
    val log = getLogger()

    if (clear)
        status.playlist.channels.forEach { _, playlistChannel ->
            playlistChannel.channel = null
        }

    if (!offline) {
        cache.clearIndex()
        status.entries["mailru"]?.fetchIndex(status.mailruRegion,
                status.mailruTimeZone, cache)
        status.entries["yandex"]?.fetchIndex(status.yandexRegion,
                status.yandexTimeZone, cache)
        status.playlist.channels.forEach { _, playlistChannel ->
            playlistChannel.scraper?.let { scraper -> playlistChannel.channel?.let {
                val updatedChannel = scraper.channelsById[it.id]
                if (updatedChannel?.name != it.name) {
                    if (updatedChannel != null)
                        log.info("Channel title changed: '${it.name}' -> '${updatedChannel.name}'")
                    else
                        log.info("Channel removed: '${it.name}'")
                    playlistChannel.channel = null
                    status.needUpdate = true
                } else
                    playlistChannel.channel = updatedChannel
            } }
        }
    } else if (status.playlist.channels.isEmpty()) {
        status.entries["mailru"]?.restoreIndex(status.mailruRegion,
                status.mailruTimeZone, cache)
        status.entries["yandex"]?.restoreIndex(status.yandexRegion,
                status.yandexTimeZone, cache)
        status.needUpdate = status.playlist.channels.isNotEmpty()
    }

    var skipAll = !interactive
    var matchedCount: Int = 0
    val skipped: MutableList<PlaylistChannel> = mutableListOf()

    fun processChannel(pc: PlaylistChannel) {
        var channel: PlaylistChannel? = status.playlist.channels[pc.name]

        if (channel != null) {
            if (channel.channel != null) {
                log.info("Channel \"${pc.name}\" matched to " +
                        "\"${channel.channel!!.name}\"")
                ++matchedCount
                return
            }
            channel = null
        }

        val match = matchChannel(pc, status.entries, preferred)
        when (match) {
            is ChannelMatch.Exact -> channel = match.channel
            is ChannelMatch.Approximate -> {
                if (!skipAll) {
                    val chosen = chooseChannel(pc, match.channels)
                    when (chosen) {
                        is ChosenChannel.SkipAll -> skipAll = true
                        is ChosenChannel.Item -> channel = chosen.channel
                    }
                    println()
                }
            }
        }

        if (channel != null) {
            log.info("Channel \"${pc.name}\" matched to \"${channel.name}\"")
            status.needUpdate = true
            ++matchedCount
        } else {
            log.info("Channel \"${pc.name}\" skipped")
            skipped.add(pc)
        }

        status.needUpdate = status.playlist.set(channel?.scraper,
                pc.name, channel?.channel, pc.id) || status.needUpdate
    }

    if (m3u8.name.isNotEmpty()) {
        if (!m3u8.exists() || !m3u8.isFile || !m3u8.canRead())
            throw Exception("Cannot read playlist file: ${m3u8.absolutePath}")
        val m3u = m3u8.inputStream().use { Playlist.fromM3U(it) }
        m3u.channels.forEach { processChannel(it.value) }
    } else
        status.playlist.channels.forEach { processChannel(it.value) }

    println("${matchedCount} channels matched")
    println("${skipped.size} channels not matched:")
    skipped.sortedBy { it.name }.forEach { println("    ${it.name}") }

    if (status.needUpdate)
        cache.getStatus().bufferedWriter().use {
            it.write(status.toJson().toJsonString(true))
        }
}
