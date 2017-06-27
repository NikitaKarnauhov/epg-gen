import com.beust.klaxon.*
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import org.jsoup.Jsoup
import java.io.File
import java.io.Reader
import java.time.*

class MailruScraper(name: String = "mailru"): Scraper(name) {
    var currentDate: LocalDate = LocalDate.MIN
    var currentMinutes: Int = -1

    override val log = getLogger()
    override var channelsById = mapOf<Int, Channel>()
    override val lang: String = "ru"

    fun init(regionId: Int, timeZone: Int) {
        region = regionId
        tz = timeZone
        timestamp = OffsetDateTime.now()
        channels = emptyList<Channel>()
        channelsById = mapOf<Int, Channel>()
    }

    fun parseIndex(reader: Reader): Boolean {
        val parser = Parser()
        val index = parser.parse(reader) as JsonObject
        val data = index.array<JsonObject>("data")
        data?.mapNotNull {
            it.array<JsonObject>("channel")?.mapNotNull {
                val id = it.string("id")?.toInt()
                val n = it.string("name")
                id?.let { n?.let { Channel(id, n) } }
            }
        }?.flatten()?.let {
            channels += it
            return it.isNotEmpty()
        }
        return false
    }

    fun parseChannel(channel: PlaylistChannel, reader: Reader,
                     date: LocalDate): MutableList<Pair<String, Programme>>
    {
        val result = mutableListOf<Pair<String, Programme>>()
        val parser = Parser()
        val channelJson = parser.parse(reader) as JsonObject
        currentDate = date
        currentMinutes = -1
        channelJson.array<JsonObject>("schedule")?.forEach {
            val event = it.obj("event")
            event?.array<JsonObject>("past")?.mapNotNullTo(result) {
                programmeFromJson(channel, it)
            }
            event?.array<JsonObject>("current")?.mapNotNullTo(result) {
                programmeFromJson(channel, it)
            }
        }
        return result
    }

    fun parseEvent(programme: Programme, reader: Reader) {
        val parser = Parser()
        val obj = parser.parse(reader) as JsonObject
        val eventJson = obj.obj("tv_event") ?: return
        eventJson.string("episode_num")?.let { programme.episodeNum = it }
        eventJson.string("episode_title")?.let { programme.secondaryTitle = it }
        eventJson.string("descr")?.let {
            programme.description = Jsoup.parse(it).text()
        }
        eventJson.array<JsonObject>("genre")?.forEach {
            it.string("title")?.let { programme.categories += it }
        }
        eventJson.array<JsonObject>("country")?.forEach {
            it.string("title")?.let { programme.countries += it }
        }
        eventJson.array<JsonObject>("participants")?.forEach {
            val participants: JsonObject = it
            it.string("title")?.let {
                when (it) {
                    "Режиссеры" -> participants.array<JsonObject>("persons")?.forEach {
                        it.string("name")?.let {
                            programme.credits.directors += it
                        }
                    }
                    "В ролях" -> participants.array<JsonObject>("persons")?.forEach {
                        it.string("name")?.let {
                            programme.credits.actors += Pair(it, "")
                        }
                    }
                    "Участники" -> participants.array<JsonObject>("persons")?.forEach {
                        it.string("name")?.let {
                            programme.credits.presenters += it
                        }
                    }
                    else -> null
                }
            }
        }
        eventJson.string("age_restrict")?.let {
            if (it.isNotEmpty())
                programme.rating = Programme.Rating(it)
        }
        eventJson.obj("year")?.let {
            it["title"]?.let {
                when (it) {
                    is Int -> programme.date = ProgrammeDate.Year(java.time.Year.of(it))
                    is String -> it.toIntOrNull()?.let {
                        programme.date = ProgrammeDate.Year(java.time.Year.of(it))
                    }
                    else -> null
                }
            }
        }
        eventJson.obj("tv_gallery")?.array<JsonObject>("items")?.forEach {
            it.obj("preview")?.string("src")?.let {
                programme.icons += Programme.Icon(it)
            } ?: it.obj("original")?.string("src")?.let {
                programme.icons += Programme.Icon(it)
            }
        }
    }

    fun programmeFromJson(channel: PlaylistChannel, obj: JsonObject): Pair<String, Programme>? {
        val id = obj.string("id") ?: return null
        val start = obj.string("start") ?: return null
        val minutes = minutesFromJson(start) ?: return null
        if (minutes < currentMinutes)
            currentDate = currentDate.plusDays(1)
        currentMinutes = minutes
        val startTime = LocalDateTime.of(currentDate,
                LocalTime.MIDNIGHT.plusMinutes(currentMinutes.toLong()))
        val result = Programme(channel, startTime)
        result.title = obj.string("name") ?: ""
        return Pair(id, result)
    }

    override fun restoreIndex(regionId: Int, timeZone: Int, cache: Cache) {
        init(regionId, timeZone)
        cache.getIndex(name).forEach {
            log.info("Restore index: ${it.absolutePath}")
            it.bufferedReader().use { parseIndex(it) }
        }
        channelsById = channels.associateBy { it.id }
    }

    override fun restoreChannel(
            channel: PlaylistChannel, cache: Cache, date: LocalDate): List<Programme>
    {
        val channelId = channel.channel?.id ?: -1
        if (channelId < 0)
            return emptyList()
        val result = mutableListOf<Triple<String, Programme, LocalDate>>()
        val (from, to) = getDateRange(date)
        cache.getChannel(name, channelId)
                .filter { (date, _) -> !date.isBefore(from) && !date.isAfter(to) }
                .forEach { (date, file) ->
            log.info("Restore channel: ${file.absolutePath}")
            file.bufferedReader().use {
                result += parseChannel(channel, it, date).map {
                    Triple(it.first, it.second, date)
                }
            }
        }
        result.forEach { (id, programme, date) ->
            val file = cache.getEvent(name, channelId, date, id.toInt())
            if (file.exists() && file.isFile && file.canRead())
                file.bufferedReader().use { parseEvent(programme, it) }
        }
        return setStopTimes(result.map { it.second }.sortedBy { it.start })
    }

    override fun fetchIndex(regionId: Int, timeZone: Int, cache: Cache) {
        init(regionId, timeZone)
        var page: Int = 1
        while (fetch(
                root + "channel/index/?region_id=${regionId}&page=${page}",
                cache.getIndex(name, page)) {
                    parseIndex(it)
                })
            ++page;
        channelsById = channels.associateBy { it.id }
    }

    override fun fetchChannel(
            channel: PlaylistChannel, cache: Cache, today: LocalDate): List<Programme>
    {
        val channelId = channel.channel?.id ?: -1
        if (channelId < 0)
            return emptyList()
        val (from, to) = getDateRange(today)
        var day = from
        val result = mutableListOf<Triple<String, Programme, LocalDate>>()
        do {
            val date = java.lang.String.format("%04d-%02d-%02d",
                    day.year, day.monthValue, day.dayOfMonth)
            val url = root + "channel/?region_id=${region}&" +
                    "channel_id=${channelId}&date=${date}"
            fetch(url, cache.getChannel(name, channelId, day)) {
                result += parseChannel(channel, it, day).map {
                    Triple(it.first, it.second, day)
                }
                true
            }
            day = day.plusDays(1)
        } while (day <= to)
        result.forEach { (id, programme, date) ->
            val url = root + "event/?id=${id}&region_id=${region}"
            fetch(url, cache.getEvent(name, channelId, date, id.toInt())) {
                parseEvent(programme, it)
                true
            }
        }
        return setStopTimes(result.map { it.second }.sortedBy { it.start })
    }

    companion object {
        val root: String = "https://tv.mail.ru/ajax/"

        fun fromJson(obj: JsonObject): MailruScraper {
            return fromJson("mailru", obj)
        }

        fun minutesFromJson(time: String): Int? {
            val ints = time.split(':').mapNotNull { it.toIntOrNull() }
            if (ints.size < 2)
                return null
            return ints[0]*60 + ints[1]
        }

        fun setStopTimes(programmes: List<Programme>): List<Programme> {
            var prevStart = LocalDateTime.MIN
            for (programme in programmes.reversed()) {
                programme.stop = prevStart
                prevStart = programme.start
            }
            return programmes
        }

        fun getDateRange(today: LocalDate): Pair<LocalDate, LocalDate> {
            val days = (6 - today.dayOfWeek.ordinal.toLong()).let { if (it > 0) it else 7 }
            return Pair(today.minusDays(1), today.plusDays(days))
        }
    }
}
