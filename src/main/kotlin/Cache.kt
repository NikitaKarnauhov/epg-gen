import java.io.File
import java.time.LocalDate

class Cache(root: String) {
    val dir: File

    init {
        val env = System.getenv()
        var path: String? = if (root.isNotEmpty()) root else env["XDG_CACHE_HOME"]
        if (path == null)
            path = env["USERPROFILE"]?.let {
                it + File.separator + projectName
            }
        if (path == null)
            path = env["HOME"]?.let {
                it + File.separator + ".cache" + File.separator + projectName
            }
        dir = getDir(File(path ?: ".").canonicalFile)
    }

    fun getStatus(): File {
        return dir.resolve("status.json")
    }

    fun getScraperDir(scraper: String): File {
        return getDir(dir.resolve(scraper))
    }

    fun getIndexDir(scraper: String): File {
        return getDir(getScraperDir(scraper).resolve("index"))
    }

    fun getChannelDir(scraper: String, id: Int): File {
        return getDir(getScraperDir(scraper).resolve("channels").resolve("${id}"))
    }

    fun getEventDir(scraper: String, channelId: Int, date: LocalDate): File {
        return getDir(getChannelDir(scraper, channelId).resolve("events").resolve(
                "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)))
    }

    fun getIndex(scraper: String): List<File> {
        return getIndexDir(scraper).listFiles { f -> isJson(f) }.toList()
    }

    fun getIndex(scraper: String, id: Int): File {
        return getIndexDir(scraper).resolve("${id}.json")
    }

    fun clearIndex(): Unit {
        dir.listFiles { f -> f.isDirectory }.forEach {
            val indexDir = it.resolve("index")
            if (indexDir.exists() && indexDir.isDirectory)
                indexDir.listFiles { f -> isJson(f) }.forEach {
                    log.info("Removing file: ${it.absolutePath}")
                    it.delete()
                }
        }
    }

    fun getChannel(scraper: String, id: Int, date: LocalDate): File {
        return getChannelDir(scraper, id).resolve("%04d%02d%02d.json".format(
                date.year, date.monthValue, date.dayOfMonth))
    }

    fun getChannel(scraper: String, id: Int): List<Pair<LocalDate, File>> {
        return getChannelDir(scraper, id).listFiles { f -> isJson(f) }.mapNotNull {
            val file = it
            nameToDate(file)?.let { Pair(it, file) }
        }
    }

    fun getEvent(scraper: String, channelId: Int, date: LocalDate, id: Int): File {
        return getEventDir(scraper, channelId, date).resolve("${id}.json")
    }

    fun clear(before: LocalDate): Unit {
        dir.listFiles { f -> f.isDirectory }.forEach {
            val channelsDir = it.resolve("channels")
            if (channelsDir.exists() && channelsDir.isDirectory)
                channelsDir.listFiles { f -> f.isDirectory }.forEach { channelDir ->
                    channelDir.listFiles().forEach {
                        if (it.isFile) {
                            val file = it
                            nameToDate(file)?.let {
                                if (it.isBefore(before)) {
                                    log.info("Removing file: ${file.absolutePath}")
                                    file.delete()
                                }
                            }
                        } else if (it.isDirectory && it.name == "events")
                            it.listFiles { f -> f.isDirectory }.forEach {
                                val dir = it
                                nameToDate(dir)?.let {
                                    if (it.isBefore(before)) {
                                        log.info("Removing directory: ${dir.absolutePath}")
                                        dir.deleteRecursively()
                                    }
                                }
                            }
                    }
                }
        }
    }

    companion object {
        val defaultMaxAgeDays = 7

        val log = getLogger()

        fun getDir(dir: File): File {
            if (!dir.exists())
                dir.mkdirs()
            else if (!dir.isDirectory)
                throw Exception("Path '${dir.absolutePath}' is not a directory")
            else if (!dir.canWrite())
                throw Exception("Cannot write to directory '${dir.absolutePath}'")
            return dir
        }

        fun isJson(file: File): Boolean {
            return file.isFile && file.extension == "json"
        }

        val dateJsonRegex = Regex("""(\d{4})(\d{2})(\d{2})\.json""")
        val dateDirRegex = Regex("""(\d{4})(\d{2})(\d{2})""")

        fun nameToDate(file: File): LocalDate? {
            val re = if (file.isDirectory) dateDirRegex else dateJsonRegex
            return re.matchEntire(file.name)?.let {
                LocalDate.of(it.groupValues[1].toInt(),
                        it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
        }
    }
}
