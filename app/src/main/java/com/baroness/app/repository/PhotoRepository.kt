package com.baroness.app.repository

import com.baroness.app.models.PhotoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PhotoRepository {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Helper to calculate date string relative to today
    private fun getDateStringOffset(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return sdf.format(cal.time)
    }

    private val basePhotos = listOf(
        // Today
        PhotoItem(
            id = "photo_1",
            url = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e",
            title = "Golden Sandy Beach Sunset",
            dateString = getDateStringOffset(0),
            location = "Maldives"
        ),
        PhotoItem(
            id = "photo_2",
            url = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05",
            title = "Misty Forest Peaks",
            dateString = getDateStringOffset(0),
            location = "Swiss Alps"
        ),
        PhotoItem(
            id = "photo_3",
            url = "https://images.unsplash.com/photo-1502082553048-f009c37129b9",
            title = "Deep Green Woodlands",
            dateString = getDateStringOffset(0),
            location = "Oregon, USA"
        ),
        PhotoItem(
            id = "photo_4",
            url = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d",
            title = "Forest Canopy Boardwalk",
            dateString = getDateStringOffset(0),
            location = "Vancouver, Canada"
        ),

        // Yesterday
        PhotoItem(
            id = "photo_5",
            url = "https://images.unsplash.com/photo-1469474968028-56623f02e42e",
            title = "Alpine Mountain Ridge",
            dateString = getDateStringOffset(1),
            location = "Dolomites, Italy"
        ),
        PhotoItem(
            id = "photo_6",
            url = "https://images.unsplash.com/photo-1472214222541-d510753a8707",
            title = "Serene Green Valley",
            dateString = getDateStringOffset(1),
            location = "New Zealand"
        ),
        PhotoItem(
            id = "photo_7",
            url = "https://images.unsplash.com/photo-1501785888041-af3ef285b470",
            title = "Lakeside Pier and Boats",
            dateString = getDateStringOffset(1),
            location = "Hallstatt, Austria"
        ),

        // This Week (3-6 days ago)
        PhotoItem(
            id = "photo_8",
            url = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e",
            title = "Sunlight filtering through Woods",
            dateString = getDateStringOffset(3),
            location = "Kyoto, Japan"
        ),
        PhotoItem(
            id = "photo_9",
            url = "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf",
            title = "Offroad Adventure Trail",
            dateString = getDateStringOffset(4),
            location = "Utah, USA"
        ),
        PhotoItem(
            id = "photo_10",
            url = "https://images.unsplash.com/photo-1433832597046-4f10e10ac764",
            title = "Hot Air Balloon Flight",
            dateString = getDateStringOffset(5),
            location = "Cappadocia, Turkey"
        ),
        PhotoItem(
            id = "photo_11",
            url = "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
            title = "Mirror Mountain Reflection",
            dateString = getDateStringOffset(6),
            location = "Yosemite, USA"
        ),

        // Earlier (7+ days ago)
        PhotoItem(
            id = "photo_12",
            url = "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86",
            title = "Snowy Winter Pine Trees",
            dateString = getDateStringOffset(8),
            location = "Lapland, Finland"
        ),
        PhotoItem(
            id = "photo_13",
            url = "https://images.unsplash.com/photo-1498050108023-c5249f4df085",
            title = "Minimal Workspace Laptop",
            dateString = getDateStringOffset(10),
            location = "Tokyo, Japan"
        ),
        PhotoItem(
            id = "photo_14",
            url = "https://images.unsplash.com/photo-1518770660439-4636190af475",
            title = "Abstract Circuit Motherboard",
            dateString = getDateStringOffset(12),
            location = "Seoul, South Korea"
        ),
        PhotoItem(
            id = "photo_15",
            url = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e",
            title = "Classic Studio Headphones",
            dateString = getDateStringOffset(14),
            location = "London, UK"
        ),
        PhotoItem(
            id = "photo_16",
            url = "https://images.unsplash.com/photo-1523275335684-37898b6baf30",
            title = "Elegant White Analog Watch",
            dateString = getDateStringOffset(18),
            location = "Geneva, Switzerland"
        ),
        PhotoItem(
            id = "photo_17",
            url = "https://images.unsplash.com/photo-1496181130204-755241524eab",
            title = "Cozy Morning Desk Brew",
            dateString = getDateStringOffset(21),
            location = "Paris, France"
        ),
        PhotoItem(
            id = "photo_18",
            url = "https://images.unsplash.com/photo-1542291026-7eec264c27ff",
            title = "Bright Red Running Sneaker",
            dateString = getDateStringOffset(25),
            location = "Portland, USA"
        ),
        PhotoItem(
            id = "photo_19",
            url = "https://images.unsplash.com/photo-1485827404703-89b55fcc595e",
            title = "Robotic Hand Cybernetics",
            dateString = getDateStringOffset(30),
            location = "Silicon Valley, USA"
        ),
        PhotoItem(
            id = "photo_20",
            url = "https://images.unsplash.com/photo-1504674900247-0877df9cc836",
            title = "Grilled Salmon Asparagus Plate",
            dateString = getDateStringOffset(35),
            location = "Barcelona, Spain"
        ),
        PhotoItem(
            id = "photo_21",
            url = "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38",
            title = "Wood Fired Pepperoni Pizza",
            dateString = getDateStringOffset(42),
            location = "Naples, Italy"
        ),
        PhotoItem(
            id = "photo_22",
            url = "https://images.unsplash.com/photo-1565958011703-44f9829ba187",
            title = "Gourmet Strawberry Cream Cake",
            dateString = getDateStringOffset(50),
            location = "Vienna, Austria"
        )
    )

    /**
     * Fetches all photos. Simulates a network call with a 1.2-second delay.
     */
    fun fetchPhotos(): Flow<List<PhotoItem>> = flow {
        // Simulate network loading
        delay(1200)
        emit(basePhotos)
    }
}
