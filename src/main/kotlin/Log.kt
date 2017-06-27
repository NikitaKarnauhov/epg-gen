import java.util.logging.*

fun getLogger(): Logger {
    return Logger.getLogger(projectName)
}

fun initLogger(logFile: String, verbose: Boolean) {
    val log = getLogger()
    log.useParentHandlers = false

    if (logFile.isNotEmpty()) {
        val fh = FileHandler(logFile);
        fh.formatter = SimpleFormatter()
        fh.level = Level.FINE
        log.addHandler(fh)
    }

    class Formatter : java.util.logging.Formatter() {
        override fun format(rec: LogRecord?): String {
            if (rec != null)
                return rec.level.toString() + ": " + rec.message + "\n";
            return ""
        }
    }

    val ch = ConsoleHandler();
    ch.formatter = Formatter()
    ch.level = if (verbose) Level.FINE else Level.WARNING
    log.addHandler(ch)
}
