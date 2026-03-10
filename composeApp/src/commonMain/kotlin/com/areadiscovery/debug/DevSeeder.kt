package com.areadiscovery.debug

import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.domain.model.SavedPoi
import com.areadiscovery.domain.repository.SavedPoiRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Debug-only test data seeder. Pre-populates saved_pois with realistic
 * world-wide data to test engagement levels, AI context injection, and
 * the Saved vibe chip without manually saving 30+ POIs.
 *
 * Personas:
 *  - FRESH: 0 saves (no-op)
 *  - LIGHT: 3 saves in one city
 *  - REGULAR: 12 saves across 3 cities
 *  - POWER: 50 saves across 8 cities (date-night bias)
 *
 * Call [seedIfEmpty] on app launch in debug builds only.
 * Call [seedPersona] to force a specific persona (clears existing saves first).
 */
object DevSeeder {

    enum class Persona { FRESH, LIGHT, REGULAR, POWER }

    /**
     * Seeds directly into the SQLDelight database, bypassing Koin entirely.
     * Call from MainActivity.onCreate BEFORE setContent so data is present
     * when ViewModels start collecting.
     */
    fun seedDirect(database: AreaDiscoveryDatabase, persona: Persona, force: Boolean = false) {
        val count = database.saved_poisQueries.observeSavedIds().executeAsList().size
        println("DevSeeder: seedDirect persona=$persona force=$force existing=$count")
        if (!force && count > 0) return

        // Clear if forcing
        if (force) {
            database.saved_poisQueries.observeSavedIds().executeAsList().forEach {
                database.saved_poisQueries.deleteById(it)
            }
        }

        val pois = when (persona) {
            Persona.FRESH -> emptyList()
            Persona.LIGHT -> lightPersona()
            Persona.REGULAR -> regularPersona()
            Persona.POWER -> powerPersona()
        }
        pois.forEach { poi ->
            database.saved_poisQueries.insertOrReplace(
                poi_id = poi.id,
                name = poi.name,
                type = poi.type,
                area_name = poi.areaName,
                lat = poi.lat,
                lng = poi.lng,
                why_special = poi.whySpecial,
                saved_at = poi.savedAt,
            )
        }
        val verified = database.saved_poisQueries.observeSavedIds().executeAsList().size
        println("DevSeeder: seeded ${pois.size} POIs directly, verified count=$verified")
    }

    suspend fun seedIfEmpty(repo: SavedPoiRepository, persona: Persona = Persona.POWER) {
        println("DevSeeder: seedIfEmpty called with persona=$persona")
        val existing = repo.observeSavedIds().first()
        println("DevSeeder: existing saves count=${existing.size}")
        if (existing.isNotEmpty()) return
        seedPersona(repo, persona)
    }

    suspend fun seedPersona(repo: SavedPoiRepository, persona: Persona) {
        println("DevSeeder: seeding persona=$persona")
        // Clear existing
        val existingIds = repo.observeSavedIds().first()
        existingIds.forEach { repo.unsave(it) }

        val pois = when (persona) {
            Persona.FRESH -> emptyList()
            Persona.LIGHT -> lightPersona()
            Persona.REGULAR -> regularPersona()
            Persona.POWER -> powerPersona()
        }
        pois.forEach { repo.save(it) }
        println("DevSeeder: seeded ${pois.size} POIs")
    }

    // ── LIGHT: 3 saves, one city (Lisbon) ──────────────────────────

    private fun lightPersona(): List<SavedPoi> = listOf(
        poi("Pastéis de Belém", "bakery", "Belém, Lisbon", 38.6976, -9.2030,
            "The original pastel de nata since 1837 — recipe is a closely guarded secret known by only three people", 3),
        poi("LX Factory", "cultural_space", "Alcântara, Lisbon", 38.7033, -9.1783,
            "Converted industrial complex under the 25 de Abril Bridge — independent bookshops, studios, weekend market", 2),
        poi("Miradouro da Graça", "viewpoint", "Graça, Lisbon", 38.7181, -9.1314,
            "Locals' favourite viewpoint — less crowded than Santa Luzia, panoramic over Alfama rooftops to the Tagus", 1),
    )

    // ── REGULAR: 12 saves, 3 cities ────────────────────────────────

    private fun regularPersona(): List<SavedPoi> = lightPersona() + listOf(
        // Tokyo (4)
        poi("Omoide Yokocho", "food_alley", "Shinjuku, Tokyo", 35.6938, 139.6989,
            "Tiny post-war yakitori alleys — squeeze onto a stool, point at the grill, eat the best skewers of your life", 12),
        poi("TeamLab Borderless", "museum", "Odaiba, Tokyo", 35.6264, 139.7837,
            "Walk into a living painting — digital art that flows around you and reacts to your movement", 11),
        poi("Meiji Jingu", "shrine", "Shibuya, Tokyo", 35.6764, 139.6993,
            "Forest shrine in the heart of the city — 100-year-old trees filter all noise, feels like time travel", 10),
        poi("Golden Gai", "nightlife", "Shinjuku, Tokyo", 35.6942, 139.7032,
            "Six narrow alleys, 200+ tiny bars seating 5-8 each — every door is a different world", 9),

        // Doha (5)
        poi("Souq Waqif", "market", "Al Souq, Doha", 25.2867, 51.5333,
            "Restored 19th-century trading market — falcon sellers, spice stalls, perfume makers, all still operating daily", 8),
        poi("Museum of Islamic Art", "museum", "Corniche, Doha", 25.2959, 51.5393,
            "I.M. Pei's last major work — geometric perfection floating on a man-made island, houses 1400 years of art", 7),
        poi("The Pearl-Qatar", "neighborhood", "The Pearl, Doha", 25.3684, 51.5510,
            "Artificial island with Mediterranean-style marina — walk the boardwalk at sunset for views of the city skyline", 6),
        poi("Katara Cultural Village", "cultural_space", "Katara, Doha", 25.3590, 51.5268,
            "Amphitheatre, galleries, and beach in one complex — where traditional Qatari culture meets contemporary art", 5),
        poi("Al Zubarah Fort", "landmark", "Al Zubarah, Qatar", 25.9781, 51.0328,
            "UNESCO World Heritage desert fort — 18th-century pearl trading outpost, hauntingly empty and photogenic", 4),
    )

    // ── POWER: 50 saves, 8 cities (date-night bias) ───────────────

    private fun powerPersona(): List<SavedPoi> = regularPersona() + listOf(
        // New York (6) — date night focus
        poi("Employees Only", "cocktail_bar", "West Village, NYC", 40.7335, -74.0020,
            "Speakeasy behind a psychic's neon sign — award-winning cocktails, late-night burger at 1am is legendary", 50),
        poi("Westlight", "rooftop_bar", "Williamsburg, NYC", 40.7128, -73.9660,
            "28th floor rooftop — floor-to-ceiling windows, Manhattan skyline, craft cocktails. THE date-night view", 49),
        poi("Dante", "restaurant", "Greenwich Village, NYC", 40.7326, -73.9993,
            "World's best bar (2019) — Negroni variations, Italian small plates, century-old Greenwich Village corner", 48),
        poi("The Cloisters", "museum", "Fort Tryon Park, NYC", 40.8649, -73.9318,
            "Medieval European monastery rebuilt stone by stone in upper Manhattan — unicorn tapestries, herb garden, river views", 47),
        poi("Brooklyn Bridge Park Pier 1", "park", "DUMBO, NYC", 40.7004, -73.9971,
            "Sunset here is unbeatable — Manhattan skyline turns gold, Brooklyn Bridge overhead, picnic on the lawn", 46),
        poi("Casa Enrique", "restaurant", "Long Island City, NYC", 40.7462, -73.9232,
            "Michelin-starred Mexican — mole negro takes 3 days to make, mezcal list rivals anything in Oaxaca", 45),

        // Isfahan (5) — heritage
        poi("Naqsh-e Jahan Square", "landmark", "Isfahan, Iran", 32.6575, 51.6777,
            "One of the largest squares in the world — 400 years old, surrounded by mosques, palaces, and bazaar arcades", 44),
        poi("Si-o-se-pol Bridge", "landmark", "Isfahan, Iran", 32.6424, 51.6684,
            "33-arch bridge lit up at night — locals picnic underneath, tea houses in the lower arcades", 43),
        poi("Sheikh Lotfollah Mosque", "mosque", "Isfahan, Iran", 32.6571, 51.6782,
            "No minarets, no courtyard — private royal mosque with tilework that shifts from cream to pink as the sun moves", 42),
        poi("Vank Cathedral", "cathedral", "Julfa, Isfahan", 32.6341, 51.6622,
            "Armenian cathedral with Islamic-influenced frescoes — stunning collision of Christian and Persian art traditions", 41),
        poi("Bazaar of Isfahan", "market", "Isfahan, Iran", 32.6610, 51.6750,
            "Two kilometers of vaulted brick corridors — coppersmith hammering echoes, saffron scent, carpet merchants serving tea", 40),

        // Dubai (5)
        poi("Alserkal Avenue", "cultural_space", "Al Quoz, Dubai", 25.1427, 55.2260,
            "Industrial warehouses turned contemporary art district — galleries, indie cinema, specialty coffee. Dubai's creative soul", 39),
        poi("XVA Gallery", "gallery", "Al Fahidi, Dubai", 25.2636, 55.2987,
            "Courtyard gallery in a restored wind-tower house — contemporary Middle Eastern art in the oldest neighborhood", 38),
        poi("Sikka Art & Design Festival Area", "neighborhood", "Al Fahidi, Dubai", 25.2632, 55.2972,
            "Narrow lanes between coral-stone houses — art installations pop up in doorways, coffee served on rooftops", 37),
        poi("At.mosphere", "restaurant", "Downtown Dubai", 25.1972, 55.2744,
            "Dining at 442m in the Burj Khalifa — sunset set-menu as the city lights switch on below your feet", 36),
        poi("La Petite Maison", "restaurant", "DIFC, Dubai", 25.2148, 55.2804,
            "Riviera-style French in the financial district — truffle burrata, lemon tart, and a terrace that feels like Nice", 35),

        // São Paulo (5)
        poi("Beco do Batman", "street_art", "Vila Madalena, São Paulo", -23.5564, -46.6869,
            "Narrow alley covered floor-to-ceiling in murals — changes every few months, always worth a revisit", 34),
        poi("Pinacoteca", "museum", "Luz, São Paulo", -23.5342, -46.6336,
            "Oldest art museum in the city — Brazilian modernists in a beautiful 19th-century building with light-filled galleries", 33),
        poi("Bar do Arnesto", "bar", "Pinheiros, São Paulo", -23.5665, -46.6926,
            "Sidewalk bar that spills into the street — cold chopps, coxinha, and conversations that last until 3am", 32),
        poi("Ibirapuera Park", "park", "Ibirapuera, São Paulo", -23.5874, -46.6576,
            "São Paulo's Central Park — Niemeyer-designed museum, jogging trails, Sunday drummers, and the best people-watching", 31),
        poi("A Casa do Porco", "restaurant", "Centro, São Paulo", -23.5474, -46.6468,
            "World's #8 restaurant — pork in every form imaginable. The porco san zé tasting is transcendent", 30),

        // Karachi (4)
        poi("Burns Garden", "park", "Saddar, Karachi", 24.8579, 67.0187,
            "Colonial-era garden with massive banyan trees — locals come to escape the heat and read newspapers", 29),
        poi("Empress Market", "market", "Saddar, Karachi", 24.8597, 67.0204,
            "Victorian Gothic market building — spices piled in pyramids, bird sellers, fabric merchants, total sensory overload", 28),
        poi("Kolachi Restaurant", "restaurant", "DHA, Karachi", 24.7870, 67.0300,
            "Seafood on the Arabian Sea — boats bring the catch to the kitchen, biryani is legendary, sunset over the water", 27),
        poi("Mohatta Palace Museum", "museum", "Clifton, Karachi", 24.8172, 67.0320,
            "Pink sandstone palace from 1927 — Rajasthani architecture, contemporary art exhibits, garden courtyard", 26),

        // Zurich (5) — cost-conscious picks
        poi("Frau Gerolds Garten", "beer_garden", "Zurich West", 47.3880, 8.5175,
            "Container garden bar with rooftop allotments — surprisingly affordable beers, DJs on weekends, fairy lights everywhere", 25),
        poi("Polyterrasse ETH", "viewpoint", "Hochschulviertel, Zurich", 47.3764, 8.5475,
            "Free panoramic view from the university terrace — the whole city, lake, and Alps in one sweep. Students study here", 24),
        poi("Hiltl", "restaurant", "Bahnhofstrasse, Zurich", 47.3737, 8.5364,
            "World's oldest vegetarian restaurant (since 1898) — buffet by weight, global flavours, surprisingly filling and affordable", 23),
        poi("Lindenhof", "park", "Altstadt, Zurich", 47.3733, 8.5405,
            "Roman hilltop with lime trees — free chess tables, view over the Limmat to the university. Locals' secret lunch spot", 22),
        poi("Uetliberg", "hiking", "Uetliberg, Zurich", 47.3497, 8.4919,
            "City mountain — 30-min train ride, panoramic trail along the ridge, fog sea in autumn. Free except the train ticket", 21),

        // Shanghai (5)
        poi("Tianzifang", "neighborhood", "French Concession, Shanghai", 31.2095, 121.4737,
            "Shikumen laneway maze — art studios, tea houses, tiny bars hidden behind laundry lines", 20),
        poi("Yu Garden", "garden", "Old City, Shanghai", 31.2274, 121.4922,
            "400-year-old classical garden — rockeries, koi ponds, zigzag bridge. Go early morning before the crowds", 19),
        poi("The Bund", "waterfront", "Huangpu, Shanghai", 31.2400, 121.4900,
            "Art Deco banking buildings facing the Pudong skyline — walk it at night when both sides light up", 18),
        poi("Jing'an Temple", "temple", "Jing'an, Shanghai", 31.2236, 121.4486,
            "Golden Buddhist temple surrounded by skyscrapers — incense smoke drifting past glass towers. Peak Shanghai contrast", 17),
        poi("Lost Heaven", "restaurant", "The Bund, Shanghai", 31.2389, 121.4870,
            "Yunnan cuisine in a colonial mansion — mushroom hotpot, Dai-style grilled fish, cocktails on the terrace overlooking the river", 16),

        // Baghdad (3)
        poi("Al-Mutanabbi Street", "cultural_space", "Baghdad, Iraq", 33.3370, 44.3870,
            "Historic booksellers' street — rebuilt after 2007 bombing, Friday book market is intellectual heartbeat of the city", 15),
        poi("Iraqi Museum", "museum", "Baghdad, Iraq", 33.3322, 44.3827,
            "Mesopotamian treasures spanning 7000 years — Sumerian gold, Assyrian reliefs, some pieces returned after 2003 looting", 14),
        poi("Abu Nuwas Street", "waterfront", "Baghdad, Iraq", 33.3200, 44.3900,
            "Tigris riverfront promenade — masgouf (open-fire grilled carp) restaurants, tea gardens, sunset strolls along the river", 13),
    )

    // ── Helpers ─────────────────────────────────────────────────────

    @OptIn(ExperimentalTime::class)
    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun poi(
        name: String,
        type: String,
        areaName: String,
        lat: Double,
        lng: Double,
        whySpecial: String,
        daysAgo: Int,
    ) = SavedPoi(
        id = "$name|$lat|$lng",
        name = name,
        type = type,
        areaName = areaName,
        lat = lat,
        lng = lng,
        whySpecial = whySpecial,
        savedAt = nowMs() - (daysAgo.toLong() * 24 * 60 * 60 * 1000),
    )
}
