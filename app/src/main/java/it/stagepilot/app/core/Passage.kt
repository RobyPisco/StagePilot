package it.stagepilot.app.core

fun songSections(body: String): List<String> = body.lines().filter { it.startsWith('#') }.map { it.removePrefix("#").trim() }.filter { it.isNotBlank() }.distinct()

/** A passage view is derived; the full original chart is never replaced. */
fun passageBody(body: String, section: String): String {
    if (section == "Brano intero") return body
    val lines = body.lines()
    val start = lines.indexOfFirst { it.startsWith('#') && it.removePrefix("#").trim() == section }
    if (start < 0) return body
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith('#') } ?: lines.size
    return lines.subList(start, end).joinToString("\n").trim()
}
