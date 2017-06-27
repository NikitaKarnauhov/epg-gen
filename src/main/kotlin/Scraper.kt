import com.beust.klaxon.*
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import java.io.File
import java.io.Reader
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

abstract class Scraper(val name: String) {
    var region: Int = 0
    var tz: Int = 0
    var timestamp: OffsetDateTime = OffsetDateTime.MIN
    var channels: Channels = emptyList<Channel>()

    abstract val log: Logger
    open var channelsById = mapOf<Int, Channel>()
    open val lang: String = ""

    fun toJson(): JsonObject {
        return json {
            obj(
                    "region" to region,
                    "tz" to tz,
                    "timestamp" to timestamp.format(DateTimeFormatter.ISO_DATE_TIME),
                    "channels" to array(channels.map {
                        obj(
                                "id" to it.id,
                                "name" to it.name,
                                "aliases" to array(it.aliases ?: emptyList())
                        )
                    })
            )
        }
    }

    open fun restoreIndex(regionId: Int, timeZone: Int, cache: Cache) {}

    open fun restoreChannel(
            channel: PlaylistChannel, cache: Cache, date: LocalDate): List<Programme>
    {
        return emptyList<Programme>()
    }

    open fun fetchIndex(regionId: Int, timeZone: Int, cache: Cache) {}

    open fun fetchChannel(
            channel: PlaylistChannel, cache: Cache, today: LocalDate): List<Programme>
    {
        return emptyList<Programme>()
    }

    fun fetch(url: String, file: File,
              callback: (Reader) -> Boolean): Boolean
    {
        try {
            if (file.exists() && file.isFile && file.canRead()) {
                log.info("Using cached data: ${url} -> ${file.absolutePath}")
                return file.bufferedReader().use(callback)
            }
            var retry: Int = 0
            retry@ while (retry < 5) {
                val (_, response, result) = url.httpGet().responseString()
                ++retry
                when (result) {
                    is Result.Failure -> {
                        val timeout = if (response.httpStatusCode == 429) 5 * 60 * 1000 else 5000
                        log.warning(result.error.toString() + "; retry after ${timeout} ms")
                        Thread.sleep(timeout.toLong())
                        continue@retry
                    }
                    is Result.Success -> {
                        log.info("Fetched URL: ${url} -> ${file.absolutePath}")
                        file.bufferedWriter().use { it.write(result.component1()) }
                        return callback(result.value.reader())
                    }
                }
            }
        } catch (error: Exception) {
            log.warning(error.toString())
        }
        return false
    }

    companion object {
        inline fun <reified T: Scraper> fromJson(name: String, obj: JsonObject): T {
            val constructor = T::class.java.getConstructor(String::class.java)
            val result: T = constructor.newInstance(name)
            obj.int("region")?.let { result.region = it }
            obj.int("tz")?.let { result.tz = it }
            obj.string("timestamp")?.let {
                result.timestamp = OffsetDateTime.parse(
                        it, DateTimeFormatter.ISO_DATE_TIME)
            }
            val channels = obj.array<JsonObject>("channels")
            channels?.mapNotNull {
                val id = it.int("id")
                val n = it.string("name")
                val aliases = it.array<String>("aliases")?.toList()
                id?.let { n?.let { Channel(id, n, aliases) } }
            }?.let {
                result.channels = it
            }
            return result
        }
    }
}

