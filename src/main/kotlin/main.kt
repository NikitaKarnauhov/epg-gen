import java.io.File
import net.sourceforge.argparse4j.*
import net.sourceforge.argparse4j.impl.Arguments
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.internal.HelpScreenException
import java.time.LocalDate

val projectName = "epg-gen"
val projectVersion = "0.1"

fun dateFromString(time: String): LocalDate? {
    val ints = time.split('-').mapNotNull { it.toIntOrNull() }
    if (ints.size < 3)
        return null
    return LocalDate.of(ints[0], ints[1], ints[2]);
}

enum class Command {
    Match, Fetch, Build, List, Set, Unset, CleanUp
}

fun executeCleanUp(cache: Cache, days: Int) {
    cache.clear(LocalDate.now().minusDays(days.toLong()))
}

fun main(args: Array<String>) {
    try {
        val ap = ArgumentParsers.newArgumentParser(projectName)
        ap.version("${projectName} version ${projectVersion}")
        ap.addArgument("-c", "--cache")
                .help("cache directory")
                .metavar("DIR")
                .setDefault("")
        ap.addArgument("-l", "--log-file")
                .help("log filename")
                .metavar("FILE")
                .setDefault("")
        ap.addArgument("-v", "--verbose")
                .help("output more info")
                .action(Arguments.storeTrue())
                .setDefault(false)
        ap.addArgument("--version")
                .help("show program version and exit")
                .action(Arguments.version())

        val sps = ap.addSubparsers()
        val matchArgs = sps.addParser("match")
                .help("match playlist channels with the ones fetched from scrapers")
                .setDefault("cmd", Command.Match)
        matchArgs.addArgument("FILE")
                .help("M3U8 playlist")
                .setDefault("")
                .nargs("?")
        matchArgs.addArgument("-i", "--interactive")
                .help("request user input on approximate channel matches")
                .action(Arguments.storeTrue())
                .setDefault(false)
        matchArgs.addArgument("-c", "--clear")
                .help("clear current matches")
                .action(Arguments.storeTrue())
                .setDefault(false)
        matchArgs.addArgument("-p", "--prefer")
                .help("prefer specified scraper")
                .choices("yandex", "mailru")
                .setDefault("")
        matchArgs.addArgument("--mailru-region")
                .help("region to use with tv.mail.ru")
                .metavar("INT")
                .type(Int::class.java)
                .setDefault(-1)
        matchArgs.addArgument("--mailru-tz")
                .help("UTC timezone offset (in minutes) to use with tv.mail.ru")
                .metavar("INT")
                .type(Int::class.java)
                .setDefault(-1)
        matchArgs.addArgument("--yandex-region")
                .help("region to use with tv.yandex.ru")
                .metavar("INT")
                .type(Int::class.java)
                .setDefault(-1)
        matchArgs.addArgument("--yandex-tz")
                .help("UTC timezone offset (in minutes) to use with tv.yandex.ru")
                .metavar("INT")
                .type(Int::class.java)
                .setDefault(-1)
        matchArgs.addArgument("-n", "--offline")
                .help("don't download anything, use cached data only")
                .action(Arguments.storeTrue())
                .setDefault(false)

        val fetchArgs = sps.addParser("fetch")
                .help("fetch index and/or events for configured channels")
                .setDefault("cmd", Command.Fetch)
        fetchArgs.addArgument("-d", "--date")
                .help("use alternative date instead of today (in YYYY-MM-DD format)")

        val buildArgs = sps.addParser("build")
                .help("generate EPG in XMLTV format")
                .setDefault("cmd", Command.Build)
        buildArgs.addArgument("FILE")
                .help("XMLTV output file (compressed if has '.gz' suffix)")
        buildArgs.addArgument("-d", "--date")
                .help("use alternative date instead of today (in YYYY-MM-DD format)")
        buildArgs.addArgument("-n", "--offline")
                .help("don't download anything, use cached data only")
                .action(Arguments.storeTrue())
                .setDefault(false)

        val listArgs = sps.addParser("list")
                .help("display list of channels")
                .setDefault("cmd", Command.List)
        listArgs.addArgument("PATTERN")
                .help("list only channels that match given PATTERN")
                .nargs("?")
                .setDefault("")
        val listStatusGroup = listArgs.addMutuallyExclusiveGroup()
        listStatusGroup.addArgument("-m", "--matched")
                .help("list only those of playlist channels that HAVE matches")
                .dest("status")
                .action(Arguments.storeConst())
                .setConst(ChannelStatus.Matched)
                .setDefault(ChannelStatus.All)
        listStatusGroup.addArgument("-u", "--unmatched")
                .help("list only those playlist channels that DON'T HAVE matches")
                .dest("status")
                .action(Arguments.storeConst())
                .setConst(ChannelStatus.Unmatched)
                .setDefault(ChannelStatus.All)

        val setArgs = sps.addParser("set")
                .help("set channel mapping")
                .setDefault("cmd", Command.Set)
        setArgs.addArgument("ID")
                .help("playlist channel ID")
                .type(Int::class.java)
        setArgs.addArgument("PROVIDER")
                .help("scraper name")
        setArgs.addArgument("PROVIDER_ID")
                .help("scraper channel ID")
                .type(Int::class.java)

        val unsetArgs = sps.addParser("unset")
                .help("unset channel mapping")
                .setDefault("cmd", Command.Unset)
        unsetArgs.addArgument("ID")
                .help("playlist channel ID")
                .type(Int::class.java)

        val cleanUpArgs = sps.addParser("cleanup")
                .help("clean up cached channels")
                .setDefault("cmd", Command.CleanUp)
        cleanUpArgs.addArgument("DAYS")
                .help("preserve data at most DAYS old")
                .type(Int::class.java)

        try {
            val parsed = ap.parseArgs(args)
            val cache = Cache(parsed.getString("cache"))
            initLogger(parsed.getString("log_file"), parsed.getBoolean("verbose"))

            when (parsed.get<Command>("cmd")) {
                Command.Match -> executeMatch(
                        cache,
                        File(parsed.getString("FILE")),
                        parsed.getBoolean("interactive"),
                        parsed.getBoolean("clear"),
                        parsed.getString("prefer"),
                        parsed.getInt("mailru_region"),
                        parsed.getInt("mailru_tz"),
                        parsed.getInt("yandex_region"),
                        parsed.getInt("yandex_tz"),
                        parsed.getBoolean("offline"))
                Command.Fetch -> executeFetch(
                        cache,
                        parsed.getString("date")?.let { dateFromString(it) } ?: LocalDate.now())
                Command.Build -> executeBuild(
                        cache,
                        File(parsed.getString("FILE")),
                        parsed.getBoolean("offline"),
                        parsed.getString("date")?.let { dateFromString(it) } ?: LocalDate.now())
                Command.List -> executeList(
                        cache,
                        parsed.getString("PATTERN"),
                        parsed.get("status"))
                Command.Set -> executeSet(
                        cache,
                        parsed.getInt("ID"),
                        parsed.getString("PROVIDER"),
                        parsed.getInt("PROVIDER_ID"))
                Command.Unset -> executeUnset(cache, parsed.getInt("ID"))
                Command.CleanUp -> executeCleanUp(cache, parsed.getInt("DAYS"))
                null -> {}
            }
        } catch (_: HelpScreenException) {
        } catch (e: ArgumentParserException) {
            System.err.println("ERROR: ${e.localizedMessage}")
            System.err.println(e.parser.formatHelp())
        }
    } catch (e: Exception) {
        getLogger().severe(e.toString())
    }
}

