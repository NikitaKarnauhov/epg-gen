import com.beust.klaxon.*

class Status {
    val entries = mutableMapOf<String, Scraper>(
            "mailru" to MailruScraper(), "yandex" to YandexScraper())
    var playlist = Playlist()
    var mailruRegion: Int = -1
    var mailruTimeZone: Int = -1
    var yandexRegion: Int = -1
    var yandexTimeZone: Int = -1
    var needUpdate: Boolean = false

    fun toJson(): JsonObject {
        val doc = json { obj("playlist" to playlist.toJson()) }
        entries.forEach { name, scraper -> doc[name] = scraper.toJson() }
        return doc
    }

    companion object {
        fun parse(cache: Cache,
                  mailruRegion: Int = -1, mailruTimeZone: Int = -1,
                  yandexRegion: Int = -1, yandexTimeZone: Int = -1): Status
        {
            val status = Status()
            status.mailruRegion = mailruRegion
            status.mailruTimeZone = mailruTimeZone
            status.yandexRegion = yandexRegion
            status.yandexTimeZone = yandexTimeZone

            if (cache.getStatus().exists())
                try {
                    val channelsJson = cache.getStatus().bufferedReader().use {
                        Parser().parse(it) as JsonObject
                    }
                    channelsJson.obj("mailru")?.let {
                        val scraper = MailruScraper.fromJson(it)
                        if ((mailruRegion < 0 || scraper.region == mailruRegion) &&
                                (mailruTimeZone < 0 || scraper.tz == mailruTimeZone))
                        {
                            status.entries["mailru"] = scraper
                            status.mailruRegion = scraper.region
                            status.mailruTimeZone = scraper.tz
                        } else {
                            status.needUpdate = true
                        }
                    }
                    channelsJson.obj("yandex")?.let {
                        val scraper = YandexScraper.fromJson(it)
                        if ((yandexRegion < 0 || scraper.region == yandexRegion) &&
                                (yandexTimeZone < 0 || scraper.tz == yandexTimeZone))
                        {
                            status.entries["yandex"] = scraper
                            status.yandexRegion = scraper.region
                            status.yandexTimeZone = scraper.tz
                        } else {
                            status.needUpdate = true
                        }
                    }
                    channelsJson.array<JsonObject>("playlist")?.let {
                        status.playlist = Playlist.fromJson(it, status.entries)
                    }
                } catch (e: Exception) {
                    val msg = "failed parsing '${cache.getStatus().absolutePath}':\n${e}"
                    getLogger().warning(msg)
                    status.needUpdate = true
                }

            status.entries.values.forEach {
                it.channelsById = it.channels.associateBy { it.id }
            }

            if (status.mailruRegion < 0)
                status.mailruRegion = 70

            if (status.mailruTimeZone < 0)
                status.mailruTimeZone = 180

            if (status.yandexRegion < 0)
                status.yandexRegion = 213

            if (status.yandexTimeZone < 0)
                status.yandexTimeZone = 180

            return status
        }
    }
}
