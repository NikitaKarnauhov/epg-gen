import org.jdom2.DocType
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.output.XMLOutputter
import java.io.File
import java.io.Writer
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

fun save(xmltv: Writer, playlist: Playlist, programmes: List<Programme>) {
    val doc = Document()
    doc.docType = DocType("tv", "xmltv.dtd")
    val root = Element("tv")
    root.setAttribute("generator-info-name", projectName)
    root.setAttribute("generator-info-url",
            "https://github.com/NikitaKarnauhov/${projectName}")
    doc.rootElement = root
    val nonEmpty = programmes.map { it.channel.id }.toHashSet()
    playlist.channels.toList()
            .filter { nonEmpty.contains(it.second.id) }
            .sortedBy { it.second.id }
            .forEach { (_, playlistChannel) ->
        val channel = Element("channel")
        channel.setAttribute("id", playlistChannel.id.toString())
        val displayName = Element("display-name")
        if (playlistChannel.scraper?.lang?.isNotEmpty() ?: false)
            displayName.setAttribute("lang", playlistChannel.scraper!!.lang)
        displayName.addContent(playlistChannel.name)
        channel.addContent(displayName)
        root.addContent(channel)
    }
    programmes.forEach {
        root.addContent(it.toXml())
    }
    val xml = XMLOutputter()
    xml.format = org.jdom2.output.Format.getPrettyFormat()
    xml.output(doc, xmltv)
}

fun executeBuild(cache: Cache, xmltv: File, offline: Boolean, date: LocalDate): Unit {
    if ((xmltv.exists() && (!xmltv.isFile || !xmltv.canWrite())) ||
            (!xmltv.exists() && (!xmltv.absoluteFile.parentFile.exists() ||
                    !xmltv.absoluteFile.parentFile.canWrite())))
        throw Exception("Cannot write XMLTV file: ${xmltv.absolutePath}")

    val status = Status.parse(cache)
    val programmes = mutableListOf<Programme>()

    if (!offline)
        executeCleanUp(cache, Cache.defaultMaxAgeDays)

    status.playlist.channels.forEach { _, playlistChannel ->
        playlistChannel.scraper?.let {
            programmes.addAll(
                if (offline)
                    it.restoreChannel(playlistChannel, cache, date)
                else
                    it.fetchChannel(playlistChannel, cache, date))
        }
    }

    if (xmltv.extension == "gz")
        xmltv.outputStream().use {
            val gz = GZIPOutputStream(it)
            gz.bufferedWriter().use { save(it, status.playlist, programmes) }
        }
    else
        xmltv.bufferedWriter().use { save(it, status.playlist, programmes) }
}
