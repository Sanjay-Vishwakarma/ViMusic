package it.vfsfitvnm.innertube.catalog

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from

/**
 * Matches curated catalog entries to real YouTube Music items.
 *
 * Results are scored rather than taking the first hit: title similarity,
 * artist agreement, release year, and a penalty for remixes / covers /
 * karaoke / lo-fi edits the curated entry didn't ask for. Below the
 * threshold an entry resolves to null ("not found"), never to a guess.
 *
 * Each function returns `null` on a network failure (so the caller can keep
 * its cache and retry later) and `Result.success(null)` when YouTube answered
 * but nothing matched well enough.
 */
object CatalogResolver {
    private const val ALBUM_THRESHOLD = 70
    private const val ARTIST_THRESHOLD = 80

    private val unwantedWords = listOf(
        "remix", "cover", "karaoke", "instrumental", "lofi", "lo fi", "slowed", "reverb",
        "mashup", "8d", "tribute", "sped up", "nightcore", "jukebox", "non stop", "nonstop",
    )

    // ---- public API -------------------------------------------------------

    suspend fun resolveAlbum(entry: CatalogAlbum): Result<Innertube.AlbumItem?>? {
        val queries = buildList {
            add(listOfNotNull(entry.title, entry.artistHint).joinToString(" "))
            if (entry.artistHint != null) add(entry.title)
            entry.aliases.forEach { add(listOfNotNull(it, entry.artistHint).joinToString(" ")) }
        }.distinct()

        var answered = false
        for (query in queries) {
            val page = Innertube.searchPage(
                body = SearchBody(query = query, params = Innertube.SearchFilter.Album.value),
                fromMusicShelfRendererContent = Innertube.AlbumItem.Companion::from
            ) ?: return null // cancelled
            val items = page.getOrNull()?.items ?: if (page.isFailure) continue else emptyList()
            answered = true

            items
                .map { it to scoreAlbum(entry, it) }
                .filter { (_, score) -> score >= ALBUM_THRESHOLD }
                .maxByOrNull { (_, score) -> score }
                ?.let { (album, _) -> return Result.success(album) }
        }
        return if (answered) Result.success(null) else null
    }

    suspend fun resolveArtist(entry: CatalogArtist): Result<Innertube.ArtistItem?>? {
        var answered = false
        for (query in (listOf(entry.name) + entry.aliases).distinct()) {
            val page = Innertube.searchPage(
                body = SearchBody(query = query, params = Innertube.SearchFilter.Artist.value),
                fromMusicShelfRendererContent = Innertube.ArtistItem.Companion::from
            ) ?: return null
            val items = page.getOrNull()?.items ?: if (page.isFailure) continue else emptyList()
            answered = true

            items
                .map { it to scoreArtist(entry, it) }
                .filter { (_, score) -> score >= ARTIST_THRESHOLD }
                .maxByOrNull { (_, score) -> score }
                ?.let { (artist, _) -> return Result.success(artist) }
        }
        return if (answered) Result.success(null) else null
    }

    /** Popular songs for a category: de-duplicated, no remixes/covers. */
    suspend fun popularSongs(category: CatalogCategory, limit: Int = 20): Result<List<Innertube.SongItem>>? {
        val page = Innertube.searchPage(
            body = SearchBody(query = category.songsQuery, params = Innertube.SearchFilter.Song.value),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        ) ?: return null

        return page.map { itemsPage ->
            itemsPage?.items.orEmpty()
                .filterNot { song -> hasUnwantedWord(song.info?.name.orEmpty(), wanted = "") }
                .filterNot { song -> looksLikeCompilation(song.info?.name.orEmpty()) }
                .distinctBy { song ->
                    normalize(song.info?.name.orEmpty().substringBefore(" (").substringBefore(" [")) +
                        "|" + normalize(song.authors?.firstOrNull()?.name.orEmpty())
                }
                .distinctBy { it.key }
                .take(limit)
        }
    }

    // ---- scoring ----------------------------------------------------------

    internal fun scoreAlbum(entry: CatalogAlbum, album: Innertube.AlbumItem): Int {
        val candidate = album.info?.name.orEmpty()
        val titleScore = (listOf(entry.title) + entry.aliases)
            .maxOf { titleSimilarity(it, candidate) }

        var score = titleScore

        entry.artistHint?.let { hint ->
            // All of the hint's words must appear: "Pawan Singh" must not
            // match "Pawan Raja" on the shared first name.
            val authors = tokens(album.authors?.mapNotNull { it.name }?.joinToString(" ").orEmpty())
            val hintTokens = tokens(hint)
            val shared = authors.intersect(hintTokens).size
            // No artist agreement at all: only an exact title may pass
            // ("Border" must not match "Border Girl / Cocoa Sugar").
            if (shared == 0 && titleScore < 100) return 0
            score += when {
                hintTokens.isNotEmpty() && shared == hintTokens.size -> 25
                shared > 0 -> 5
                else -> -15
            }
        }

        val candidateYear = album.year?.trim()?.toIntOrNull()
        val wantedYear = entry.year?.toIntOrNull()
        if (candidateYear != null && wantedYear != null) {
            score += when (kotlin.math.abs(candidateYear - wantedYear)) {
                0 -> 10
                1 -> 0 // soundtracks often release the year before the film
                else -> -20
            }
        }

        // "Lai Bhaari (From "Kaam Chalu Hai")" is a single from another film.
        if (Regex("""\(\s*from\b""", RegexOption.IGNORE_CASE).containsMatchIn(candidate) &&
            !entry.title.contains("from", ignoreCase = true)
        ) score -= 40

        if (hasUnwantedWord(candidate, wanted = entry.title)) score -= 60

        return score
    }

    internal fun scoreArtist(entry: CatalogArtist, artist: Innertube.ArtistItem): Int {
        val got = normalize(artist.info?.name.orEmpty())
        if (got.isEmpty()) return 0

        val nameScore = (listOf(entry.name) + entry.aliases).maxOf { name ->
            val wanted = normalize(name)
            when {
                got == wanted -> 100
                got.replace(" ", "") == wanted.replace(" ", "") -> 95 // "Ajay Atul" vs "Ajay-Atul"
                got.startsWith("$wanted ") -> 70 // "Arvind Akela Kallu Ji"
                else -> (jaccard(tokens(wanted), tokens(got)) * 60).toInt()
            }
        }

        // Same-name fan/re-upload channels are tiny; the real artist has an
        // audience. "Arvind Akela Kallu Ji" (71M monthly) must beat an exact
        // "Arvind Akela Kallu" channel with 125 subscribers.
        val audience = audienceOf(artist.subscribersCountText)
        val audienceBonus = when {
            audience == null -> 0
            audience >= 1_000_000 -> 20
            audience >= 100_000 -> 15
            audience >= 10_000 -> 10
            audience >= 1_000 -> 5
            else -> -25
        }
        return nameScore + audienceBonus
    }

    /** "655M monthly audience" / "1.26M subscribers" / "98 subscribers" -> count. */
    internal fun audienceOf(text: String?): Long? {
        val m = Regex("""([\d.,]+)\s*([KMB])?\s+(monthly audience|subscribers?)""", RegexOption.IGNORE_CASE)
            .find(text ?: return null) ?: return null
        val number = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (m.groupValues[2].uppercase()) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
        return (number * multiplier).toLong()
    }

    private fun titleSimilarity(wantedRaw: String, candidateRaw: String): Int {
        val wanted = normalize(wantedRaw)
        // Soundtrack albums are listed as "Aashiqui 2 (Original Motion Picture Soundtrack)".
        val candidateFull = normalize(candidateRaw)
        val candidate = normalize(candidateRaw.replace(Regex("""\(.*?\)|\[.*?]"""), " "))

        if (wanted.isEmpty()) {
            // Titles that are only symbols ("÷"): compare literally.
            return if (candidateRaw.trim().startsWith(wantedRaw.trim())) 100 else 0
        }
        return when {
            candidate == wanted || candidateFull == wanted -> 100
            candidate.startsWith("$wanted ") || candidateFull.startsWith("$wanted ") -> 75
            else -> (jaccard(tokens(wanted), tokens(candidate)) * 70).toInt()
        }
    }

    /** "Top Hits 2026 Best English Songs 2026 - Trending Music ..." uploads. */
    private fun looksLikeCompilation(title: String): Boolean {
        val t = " ${normalize(title)} "
        val markers = listOf(" top hits ", " best songs ", " hit songs ", " trending ", " playlist ", " all songs ", " best of ")
        return title.length > 80 || markers.any { t.contains(it) }
    }

    private fun hasUnwantedWord(candidate: String, wanted: String): Boolean {
        val c = " ${normalize(candidate)} "
        val w = " ${normalize(wanted)} "
        return unwantedWords.any { word -> c.contains(" $word ") && !w.contains(" $word ") }
    }

    internal fun normalize(s: String): String =
        s.lowercase()
            .replace('&', ' ')
            .replace(Regex("""[^\p{L}\p{M}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    private fun tokens(s: String): Set<String> =
        normalize(s).split(' ').filter { it.isNotBlank() }.toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
}
