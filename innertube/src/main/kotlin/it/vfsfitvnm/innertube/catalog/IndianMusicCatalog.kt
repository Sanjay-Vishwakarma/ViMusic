package it.vfsfitvnm.innertube.catalog

/**
 * Curated collection shown on the Explore tab.
 *
 * Only NAMES live here. Every album/artist is resolved at runtime against
 * YouTube Music (see [CatalogResolver]); artwork, track lists, artist info
 * and IDs all come from there and are cached by the app. Nothing here is a
 * song, a URL or an image.
 */
data class CatalogAlbum(
    /** Title as people know it, plus optional alternative spellings. */
    val title: String,
    /** A main artist/composer, used only to rank search results. */
    val artistHint: String? = null,
    val aliases: List<String> = emptyList(),
    val year: String? = null,
)

data class CatalogArtist(
    val name: String,
    /** Other spellings YouTube Music may use ("Avdhoot Gupte"). */
    val aliases: List<String> = emptyList(),
)

data class CatalogCategory(
    val id: String,
    val title: String,
    /** YouTube Music song-search query for the "Popular songs" row. */
    val songsQuery: String,
    val albums: List<CatalogAlbum>,
    val artists: List<CatalogArtist>,
)

object IndianMusicCatalog {
    private fun artists(vararg names: String) = names.map(::CatalogArtist)

    val hindi = CatalogCategory(
        id = "hindi",
        title = "Hindi",
        songsQuery = "latest Bollywood songs",
        albums = listOf(
            CatalogAlbum("Aashiqui 2", "Mithoon", year = "2013"),
            CatalogAlbum("Kabir Singh", "Mithoon", year = "2019"),
            CatalogAlbum("Rockstar", "A.R. Rahman", year = "2011"),
            CatalogAlbum("Yeh Jawaani Hai Deewani", "Pritam", year = "2013"),
            CatalogAlbum("Ae Dil Hai Mushkil", "Pritam", year = "2016"),
            CatalogAlbum("Jab We Met", "Pritam", year = "2007"),
            CatalogAlbum("Kal Ho Naa Ho", "Shankar-Ehsaan-Loy", year = "2003"),
            CatalogAlbum("Rang De Basanti", "A.R. Rahman", year = "2006"),
            CatalogAlbum("Brahmastra", "Pritam", year = "2022"),
            CatalogAlbum("Animal", year = "2023"), // multi-composer, listed as Various Artists
        ),
        artists = artists(
            "Arijit Singh", "Shreya Ghoshal", "Atif Aslam", "Jubin Nautiyal", "Sonu Nigam",
            "Neha Kakkar", "A.R. Rahman", "Pritam", "Badshah", "Vishal Mishra",
        ),
    )

    val bhojpuri = CatalogCategory(
        id = "bhojpuri",
        title = "Bhojpuri",
        songsQuery = "Bhojpuri hit songs",
        albums = listOf(
            CatalogAlbum("Chhath Geet", "Sharda Sinha"),
            CatalogAlbum("Nirahua Hindustani", year = "2014"),
            CatalogAlbum("Raja Babu", "Dinesh Lal Yadav", year = "2015"),
        ),
        artists = artists(
            "Pawan Singh", "Khesari Lal Yadav", "Dinesh Lal Yadav", "Ritesh Pandey", "Shilpi Raj",
            "Neelkamal Singh", "Arvind Akela Kallu", "Manoj Tiwari", "Sharda Sinha", "Pramod Premi Yadav",
        ),
    )

    val marathi = CatalogCategory(
        id = "marathi",
        title = "Marathi",
        songsQuery = "Marathi hit songs",
        albums = listOf(
            CatalogAlbum("Sairat", "Ajay-Atul", year = "2016"),
            CatalogAlbum("Natarang", "Ajay-Atul", year = "2010"),
            CatalogAlbum("Ved", "Ajay-Atul", year = "2022"),
            CatalogAlbum("Timepass", "Chinar-Mahesh", year = "2014"),
            CatalogAlbum("Duniyadari", year = "2013"),
        ),
        // "Ajay-Atul" has no artist page on YouTube Music (only the two
        // individual singers), so the duo appears through their albums above.
        artists = listOf(
            CatalogArtist("Avadhoot Gupte", aliases = listOf("Avdhoot Gupte")),
        ) + artists(
            "Bela Shende", "Adarsh Shinde", "Anand Shinde",
            "Swapnil Bandodkar", "Vaishali Samant", "Kartiki Gaikwad",
        ),
    )

    val haryanvi = CatalogCategory(
        id = "haryanvi",
        title = "Haryanvi",
        songsQuery = "Haryanvi hit songs",
        albums = emptyList(),
        artists = artists(
            "Masoom Sharma", "Sapna Choudhary", "Raju Punjabi", "Khasa Aala Chahar", "Diler Kharkiya",
            "Amanraj Gill", "Ajay Hooda", "Renuka Panwar", "Gulzaar Chhaniwala", "Ruchika Jangid",
        ),
    )

    val english = CatalogCategory(
        id = "english",
        title = "English",
        songsQuery = "English pop hits",
        albums = listOf(
            CatalogAlbum("÷", "Ed Sheeran", aliases = listOf("Divide", "÷ (Deluxe)"), year = "2017"),
            CatalogAlbum("1989", "Taylor Swift", year = "2014"),
            CatalogAlbum("After Hours", "The Weeknd", year = "2020"),
            CatalogAlbum("Thriller", "Michael Jackson", year = "1982"),
            CatalogAlbum("21", "Adele", year = "2011"),
            CatalogAlbum("Future Nostalgia", "Dua Lipa", year = "2020"),
            CatalogAlbum("Abbey Road", "The Beatles", year = "1969"),
            CatalogAlbum("Hybrid Theory", "Linkin Park", year = "2000"),
            CatalogAlbum("A Head Full of Dreams", "Coldplay", year = "2015"),
            CatalogAlbum("Evolve", "Imagine Dragons", year = "2017"),
        ),
        artists = artists(
            "Ed Sheeran", "Taylor Swift", "The Weeknd", "Justin Bieber", "Dua Lipa",
            "Coldplay", "Imagine Dragons", "Billie Eilish", "Adele", "Eminem",
        ),
    )

    val oldHindi = CatalogCategory(
        id = "old_hindi",
        title = "Old Hindi",
        songsQuery = "old Hindi evergreen songs",
        albums = listOf(
            CatalogAlbum("Mughal-E-Azam", "Naushad", year = "1960"),
            CatalogAlbum("Sholay", "R.D. Burman", year = "1975"),
            CatalogAlbum("Guide", "S.D. Burman", year = "1965"),
            CatalogAlbum("Pakeezah", "Ghulam Mohammed", year = "1972"),
            CatalogAlbum("Aradhana", "S.D. Burman", year = "1969"),
            CatalogAlbum("Amar Prem", "R.D. Burman", year = "1972"),
            CatalogAlbum("Bobby", "Laxmikant-Pyarelal", year = "1973"),
            CatalogAlbum("Kati Patang", "R.D. Burman", year = "1970"),
            CatalogAlbum("Anand", "Salil Chowdhury", year = "1971"),
            CatalogAlbum("Abhimaan", "S.D. Burman", year = "1973"),
        ),
        artists = artists(
            "Lata Mangeshkar", "Mohammed Rafi", "Kishore Kumar", "Mukesh", "Asha Bhosle",
        ) + listOf(
            // YouTube Music's real page is "Rahul Dev Burman"; "R.D Burman" channels are empty.
            CatalogArtist("R.D. Burman", aliases = listOf("Rahul Dev Burman")),
        ) + artists(
            "S.D. Burman", "Manna Dey", "Talat Mahmood", "Geeta Dutt",
        ),
    )

    val categories: List<CatalogCategory> = listOf(hindi, bhojpuri, marathi, haryanvi, english, oldHindi)

    fun category(id: String?): CatalogCategory = categories.firstOrNull { it.id == id } ?: hindi
}
