import com.beust.klaxon.*
import java.io.Reader
import java.time.*
import java.time.format.DateTimeFormatter

class YandexScraper(name: String = "yandex"): Scraper(name) {
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
        val channelsJson = index.array<JsonObject>("channels")
        channelsJson?.mapNotNull {
            val id = it.int("id")
            val n = it.string("title")
            val synonyms = it.array<String>("synonyms")?.toList()
            id?.let { n?.let { Channel(id, n, synonyms) } }
        }?.let {
            channels += it
            return it.isNotEmpty()
        }
        return false
    }

    fun parseEvent(channel: PlaylistChannel, event: JsonObject): Programme {
        val start = event.string("start")?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime()
        } ?: throw Exception("No start time")
        val result = Programme(channel, start)
        result.stop = event.string("finish")?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime()
        } ?: throw Exception("No stop time")
        event.obj("program")?.let { programJson ->
            programJson.string("title")?.let { result.title = it }
            programJson.obj("episode")?.let { episodeJson ->
                episodeJson.string("title")?.let { result.secondaryTitle = it }
                episodeJson.string("description")?.let { result.description = it }
            }
            programJson.string("description")?.let {
                if (result.description.isEmpty())
                    result.description = it
            }
            programJson.array<String>("countries")?.let { result.countries = it }
            programJson.obj("type")?.string("name")?.let {
                result.categories = listOf(it)
            }
            programJson.boolean("premiere")?.let {
                result.premiere = if (it) "" else null
            }
            programJson.array<JsonObject>("images")?.forEach { imageJson ->
                fun parseSize(sizeJson: JsonObject): Unit {
                    sizeJson.string("src")?.let {
                        result.icons += Programme.Icon(
                                if (Regex("""^\w+://""").matches(it))
                                    it
                                else
                                    "http://" + Regex("""^/+""").replace(it, ""),
                                sizeJson.int("width") ?: 0,
                                sizeJson.int("height") ?: 0)
                    }
                }
                val originalSize = imageJson.obj("originalSize")
                if (originalSize != null)
                    parseSize(originalSize)
                else
                    imageJson.obj("sizes")?.let { sizesJson ->
                        sizesJson.keys.sortedBy { it.toIntOrNull() ?: -1 }
                                .lastOrNull()?.let { largest ->
                            sizesJson.obj(largest)?.let { parseSize(it) }
                        }
                    }
            }
            programJson.array<JsonObject>("persons")?.forEach { personJson ->
                personJson.string("name")?.let { personName ->
                    when (personJson.string("role")) {
                        "presenter" -> result.credits.presenters += personName
                        "producer" -> result.credits.producers += personName
                        "director" -> result.credits.directors += personName
                        "writer" -> result.credits.writers += personName
                        "actor" -> result.credits.actors += Pair(personName, "")
                        "composer" -> result.credits.composers += personName
                        else -> null
                    }
                }
            }
            programJson.string("year")?.toIntOrNull()?.let {
                result.date = ProgrammeDate.Year(Year.of(it))
            }
            programJson.int("ageRestrinction")?.let {
                result.rating = Programme.Rating("${it}+")
            }
        }
        return result
    }

    fun parseChannel(channel: PlaylistChannel, reader: Reader): MutableList<Programme> {
        val result = mutableListOf<Programme>()
        val parser = Parser()
        val channelJson = parser.parse(reader) as JsonObject
        channelJson.array<JsonObject>("schedules")?.forEach { scheduleJson ->
            scheduleJson.array<JsonObject>("events")?.forEach { eventJson ->
                try {
                    result += parseEvent(channel, eventJson)
                } catch (e: Exception) {
                    log.warning(e.toString())
                }
            }
        }
        return result
    }

    override fun restoreIndex(regionId: Int, timeZone: Int, cache: Cache) {
        init(regionId, timeZone)
        cache.getIndex(name).forEach {
            log.info("Restore index: ${it.absolutePath}")
            it.bufferedReader().use { parseIndex(it) }
        }
        channelsById = channels.associateBy { it.id }
    }

    override fun fetchIndex(regionId: Int, timeZone: Int, cache: Cache) {
        init(regionId, timeZone)
        fetch(root +
                "?params={\"limit\":1000,\"fields\":\"id,title,synonyms\"}" +
                "&resource=channels&userRegion=${region}",
                cache.getIndex(name, 0)) {
            parseIndex(it)
        }
        channelsById = channels.associateBy { it.id }
    }

    override fun restoreChannel(
            channel: PlaylistChannel, cache: Cache, date: LocalDate): List<Programme>
    {
        val channelId = channel.channel?.id ?: -1
        if (channelId < 0)
            return emptyList()
        val to = getLastDate(date)
        val seconds = getDuration(to)
        if (seconds <= 0)
            return emptyList()
        val result = mutableListOf<Programme>()
        val file = cache.getChannel(name, channelId, to)
        if (file.exists() && file.isFile && file.canRead())
            file.bufferedReader().use {
                result += parseChannel(channel, it)
            }
        return result
    }

    override fun fetchChannel(
            channel: PlaylistChannel, cache: Cache, today: LocalDate): List<Programme>
    {
        val channelId = channel.channel?.id ?: -1
        if (channelId < 0)
            return emptyList()
        val to = getLastDate(today)
        val seconds = getDuration(to)
        if (seconds <= 0)
            return emptyList()
        val result = mutableListOf<Programme>()
        fetch(root +
                "?params={\"channelIds\":\"${channelId}\",\"duration\":${seconds}," +
                "\"channelProgramsLimit\":1000}" +
                "&resource=schedule&userRegion=${region}",
                cache.getChannel(name, channelId, to)) {
            result += parseChannel(channel, it)
            true
        }
        return result
    }

    fun getDuration(lastDate: LocalDate): Long {
        val now = OffsetDateTime.now()
        val toTime = OffsetDateTime.of(lastDate.plusDays(1).atStartOfDay(),
                ZoneOffset.ofTotalSeconds(tz*60)).plusHours(4)
        return toTime.toEpochSecond() - now.toEpochSecond()
    }

    companion object {
        val root: String = "https://tv.yandex.ru/ajax/i-tv-region/get"

        fun fromJson(obj: JsonObject): YandexScraper {
            return fromJson("yandex", obj)
        }

        fun getLastDate(today: LocalDate): LocalDate {
            val days = (6 - today.dayOfWeek.ordinal.toLong()).let { if (it > 0) it else 7 }
            return today.plusDays(days)
        }
    }
}
