import org.jdom2.Element
import java.lang.Math.abs
import java.time.LocalDate
import java.time.LocalDateTime

sealed class ProgrammeDate {
    open fun toXml(): String {return ""}

    class Year(val value: java.time.Year): ProgrammeDate() {
        override fun toXml(): String {
            return value.toString()
        }
    }

    class Month(val value: java.time.YearMonth): ProgrammeDate() {
        override fun toXml(): String {
            return "%04d%02d".format(value.year, value.monthValue)
        }
    }

    class Day(val value: java.time.LocalDate): ProgrammeDate() {
        override fun toXml(): String {
            return "%04d%02d%02d".format(value.year, value.monthValue, value.dayOfMonth)
        }
    }
}

fun LocalDateTime.toXml(time: Boolean = true, tz: Int = 0): String {
    var s = "%04d%02d%02d".format(this.year, this.monthValue, this.dayOfMonth)
    if (time) {
        s += "%02d%02d%02d".format(this.hour, this.minute, this.second)
        if (tz != 0)
            s += " %c%02d%02d".format(if (tz > 0) '+' else '-', abs(tz)/60, abs(tz)%60)
    }
    return s
}

fun Boolean.toXml() = if (this) "yes" else "no"

fun addTextElement(xml: Element, name: String, data: String,
                   checkEmpty: Boolean = true, lang: String = ""): Element?
{
    if (!checkEmpty || !data.isEmpty()) {
        val element = Element(name)
        if (lang.isNotEmpty())
            element.setAttribute("lang", lang)
        element.addContent(data)
        xml.addContent(element)
        return element
    }
    return null
}

data class Programme(val channel: PlaylistChannel, val start: LocalDateTime) {
    var stop: LocalDateTime = LocalDateTime.MIN
    var title: String = ""
    var secondaryTitle: String = ""
    var description: String = ""

    class Credits {
        var directors: List<String> = emptyList()
        var actors: List<Pair<String, String>> = emptyList()
        var writers: List<String> = emptyList()
        var adapters: List<String> = emptyList()
        var producers: List<String> = emptyList()
        var composers: List<String> = emptyList()
        var editors: List<String> = emptyList()
        var presenters: List<String> = emptyList()
        var commentators: List<String> = emptyList()
        var guests: List<String> = emptyList()

        fun isEmpty() = directors.isEmpty() && actors.isEmpty() &&
                writers.isEmpty() && adapters.isEmpty() && producers.isEmpty() &&
                composers.isEmpty() && editors.isEmpty() && presenters.isEmpty() &&
                commentators.isEmpty() && guests.isEmpty()

        fun toXml(): Element {
            val xml = Element("credits")
            directors.forEach { addTextElement(xml, "director", it) }
            actors.forEach { (actor, role) ->
                addTextElement(xml, "actor", actor)?.let {
                    if (!role.isEmpty())
                        it.setAttribute("role", role)
                }
            }
            writers.forEach { addTextElement(xml, "writer", it) }
            adapters.forEach { addTextElement(xml, "adapter", it) }
            producers.forEach { addTextElement(xml, "producer", it) }
            composers.forEach { addTextElement(xml, "composer", it) }
            editors.forEach { addTextElement(xml, "editor", it) }
            presenters.forEach { addTextElement(xml, "presenter", it) }
            commentators.forEach { addTextElement(xml, "commentator", it) }
            guests.forEach { addTextElement(xml, "guest", it) }
            return xml
        }
    }

    val credits = Credits()

    var date: ProgrammeDate? = null
    var categories: List<String> = emptyList()
    var language: String = ""
    var origLanguage: String = ""

    enum class LengthUnits { Seconds, Minutes, Hours }
    var length: Pair<Int, LengthUnits>? = null

    data class Icon (val src: String, var width: Int = 0, var height: Int = 0) {
        fun toXml(): Element {
            val xml = Element("icon")
            xml.setAttribute("src", src)
            if (width > 0)
                xml.setAttribute("width", width.toString())
            if (height > 0)
                xml.setAttribute("height", height.toString())
            return xml
        }
    }

    var icons: List<Icon> = emptyList()

    var countries: List<String> = emptyList()
    var episodeNum: String = ""

    data class Video(val present: Boolean, val colour: Boolean?,
                     val aspect: String, val quality: String)
    {
        fun toXml(): Element {
            val xml = Element("video")
            addTextElement(xml, "present", present.toXml())
            if (!present)
                return xml
            colour?.let { addTextElement(xml, "colour", it.toXml()) }
            if (!aspect.isEmpty())
                addTextElement(xml, "aspect", aspect)
            if (!quality.isEmpty())
                addTextElement(xml, "quality", quality)
            return xml
        }
    }

    var video : Video? = null

    enum class Stereo { Mono, Stereo, Dolby, DolbyDigital, Bilingual, Surround }
    data class Audio(val present: Boolean, val stereo: Stereo?) {
        fun toXml(): Element {
            val xml = Element("video")
            addTextElement(xml, "present", present.toXml())
            if (!present)
                return xml
            stereo?.let {
                addTextElement(xml, "stereo",
                    when (it) {
                        Stereo.Mono -> "mono"
                        Stereo.Stereo -> "stereo"
                        Stereo.Dolby -> "dolby"
                        Stereo.DolbyDigital -> "dolby digital"
                        Stereo.Bilingual -> "bilingual"
                        Stereo.Surround -> "surround"
                        else -> ""
                    })
            }
            return xml
        }
    }

    var audio: Audio? = null

    var previouslyShown: Pair<LocalDateTime?, Channel?>? = null
    var premiere: String? = null
    var lastChance: String? = null
    var new: Boolean = false

    enum class SubtitlesType { Teletext, Onscreen, DeafSigned }
    data class Subtitles(val language: String = "", val type: SubtitlesType? = null) {
        fun toXml(): Element {
            val xml = Element("subtitles")
            if (!language.isEmpty())
                xml.setAttribute("language", language)
            type?.let {
                addTextElement(xml, "type",
                        when (it) {
                            SubtitlesType.Teletext -> "teletext"
                            SubtitlesType.Onscreen -> "onscreen"
                            SubtitlesType.DeafSigned -> "deaf-signed"
                            else -> ""
                        })
            }
            return xml
        }
    }

    var subtitles: Subtitles? = null

    data class Rating(val value: String, val system: String = "",
                      val icon: Icon? = null)
    {
        fun toXml(name: String): Element {
            val xml = Element(name)
            if (!system.isEmpty())
                xml.setAttribute("system", system)
            addTextElement(xml, "value", value, false)
            icon?.let { xml.addContent(it.toXml()) }
            return xml
        }
    }

    var rating: Rating? = null
    var starRating: Rating? = null

    enum class ReviewType {Text, Url}
    data class Review(val value: String, val type: ReviewType,
                      val source: String = "", val reviewer: String = "")
    {
        fun toXml(): Element {
            val xml = Element("review")
            xml.setAttribute("type", if (type == ReviewType.Text) "text" else "url")
            xml.addContent(value)
            addTextElement(xml, "source", source)
            addTextElement(xml, "reviewer", reviewer)
            return xml
        }
    }

    var review: Review? = null

    fun toXml(): Element {
        val xml = Element("programme")
        val tz = channel.scraper?.tz ?: 0
        xml.setAttribute("start", start.toXml(tz = tz))
        if (stop != LocalDateTime.MIN)
            xml.setAttribute("stop", stop.toXml(tz = tz))
        xml.setAttribute("channel", channel.id.toString())

        val lang = channel.scraper?.lang ?: ""
        addTextElement(xml, "title", title, lang = lang)
        addTextElement(xml, "sub-title", secondaryTitle, lang = lang)
        addTextElement(xml, "desc", description, lang = lang)

        if (!credits.isEmpty())
            xml.addContent(credits.toXml())

        date?.let { addTextElement(xml, "date", it.toXml()) }

        categories.forEach { addTextElement(xml, "category", it, lang = lang) }
        addTextElement(xml, "language", language)
        addTextElement(xml, "orig-language", origLanguage)

        length?.let { (value, units) ->
            addTextElement(xml, "length", value.toString())?.setAttribute("units",
                    when (units) {
                        LengthUnits.Seconds -> "seconds"
                        LengthUnits.Minutes -> "minutes"
                        else -> "hours"
                    })
        }

        icons.forEach { xml.addContent(it.toXml()) }
        countries.forEach { addTextElement(xml, "country", it, lang = lang) }
        addTextElement(xml, "episode-num", episodeNum)
        video?.let { xml.addContent(it.toXml()) }
        audio?.let { xml.addContent(it.toXml()) }

        previouslyShown?.let { (start, channel) ->
            val element = Element("previously-shown")
            start?.let { element.setAttribute("start", it.toXml()) }
            channel?.let { element.setAttribute("channel", it.id.toString()) }
            xml.addContent(element)
        }

        premiere?.let { addTextElement(xml, "premiere", it, false) }
        lastChance?.let { addTextElement(xml, "last-chance", it, false) }

        if (new)
            xml.addContent(Element("new"))

        subtitles?.let { xml.addContent(it.toXml()) }
        rating?.let { xml.addContent(it.toXml("rating")) }
        starRating?.let { xml.addContent(it.toXml("star-rating")) }

        return xml
    }
}
