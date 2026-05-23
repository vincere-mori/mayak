package app.beacon.core.geo

/**
 * Best-effort guess of a server's country from its display name. Subscription
 * panels rarely give structured geo data, so the name is all there is — it may
 * carry a flag emoji, an ISO code, a country word or a known city.
 *
 * Returns an upper-case ISO-3166 alpha-2 code, or null when nothing matches.
 */
object CountryDetector {

    private const val FLAG_BASE = 0x1F1E6 // regional indicator 'A'

    private val NAME_TO_CODE: Map<String, String> = buildMap {
        fun add(code: String, vararg names: String) = names.forEach { this[it] = code }
        add("NL", "netherlands", "holland", "нидерланды", "голландия", "amsterdam", "амстердам")
        add("DE", "germany", "германия", "deutschland", "frankfurt", "франкфурт")
        add("US", "usa", "united states", "america", "сша", "америка", "штаты")
        add("GB", "uk", "united kingdom", "britain", "england", "британия", "англия", "london", "лондон")
        add("FR", "france", "франция", "paris", "париж")
        add("FI", "finland", "финляндия", "helsinki", "хельсинки")
        add("SE", "sweden", "швеция", "stockholm")
        add("RU", "russia", "россия", "moscow", "москва")
        add("JP", "japan", "япония", "tokyo", "токио")
        add("SG", "singapore", "сингапур")
        add("HK", "hong kong", "hongkong", "гонконг")
        add("TR", "turkey", "turkiye", "турция", "istanbul", "стамбул")
        add("PL", "poland", "польша", "warsaw", "варшава")
        add("CA", "canada", "канада")
        add("CH", "switzerland", "швейцария", "zurich")
        add("AT", "austria", "австрия", "vienna")
        add("IT", "italy", "италия", "milan")
        add("ES", "spain", "испания", "madrid")
        add("NO", "norway", "норвегия")
        add("AE", "uae", "emirates", "оаэ", "эмираты", "dubai", "дубай")
        add("IN", "india", "индия", "mumbai")
        add("AU", "australia", "австралия", "sydney")
        add("KR", "korea", "корея", "seoul")
        add("CN", "china", "китай")
        add("KZ", "kazakhstan", "казахстан", "almaty")
        add("LV", "latvia", "латвия", "riga")
        add("LT", "lithuania", "литва", "vilnius")
        add("EE", "estonia", "эстония", "tallinn")
        add("CZ", "czech", "czechia", "чехия", "prague")
        add("RO", "romania", "румыния", "bucharest")
        add("UA", "ukraine", "украина", "kyiv", "kiev")
        add("IE", "ireland", "ирландия", "dublin")
        add("BG", "bulgaria", "болгария")
        add("AM", "armenia", "армения", "yerevan")
        add("BR", "brazil", "бразилия")
        add("HU", "hungary", "венгрия", "budapest")
    }

    private val KNOWN_CODES: Set<String> = NAME_TO_CODE.values.toSet()

    fun detect(name: String): String? {
        flagToCode(name)?.let { return it }

        val lower = name.lowercase()
        NAME_TO_CODE.entries
            .firstOrNull { (word, _) -> lower.contains(word) }
            ?.let { return it.value }

        Regex("""\b([A-Za-z]{2})\b""").findAll(name)
            .map { it.groupValues[1].uppercase() }
            .firstOrNull { it in KNOWN_CODES }
            ?.let { return it }

        return null
    }

    /** Extracts an ISO code from a regional-indicator flag emoji, if present. */
    private fun flagToCode(name: String): String? {
        val codePoints = name.codePoints().toArray()
        for (i in 0 until codePoints.size - 1) {
            val a = codePoints[i]
            val b = codePoints[i + 1]
            if (a in FLAG_BASE..(FLAG_BASE + 25) && b in FLAG_BASE..(FLAG_BASE + 25)) {
                val c1 = 'A' + (a - FLAG_BASE)
                val c2 = 'A' + (b - FLAG_BASE)
                return "$c1$c2"
            }
        }
        return null
    }
}
