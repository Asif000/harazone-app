package com.harazone.util

fun poiTypeEmoji(type: String): String = when {
    type.contains("food") || type.contains("restaurant") || type.contains("cafe") || type.contains("bakery") -> "\uD83C\uDF5C" // 🍜
    type.contains("bar") || type.contains("pub") || type.contains("nightlife") || type.contains("entertainment") -> "\uD83C\uDFAD" // 🎭
    type.contains("park") || type.contains("garden") || type.contains("nature") -> "\uD83C\uDF33" // 🌳
    type.contains("historic") || type.contains("heritage") || type.contains("monument") || type.contains("memorial") -> "\uD83C\uDFDB" // 🏛
    type.contains("shop") || type.contains("market") || type.contains("mall") || type.contains("store") -> "\uD83D\uDED2" // 🛒
    type.contains("art") || type.contains("gallery") || type.contains("museum") -> "\uD83C\uDFA8" // 🎨
    type.contains("transit") || type.contains("station") || type.contains("transport") -> "\uD83D\uDE87" // 🚇
    type.contains("beach") || type.contains("coast") || type.contains("waterfront") -> "\uD83C\uDF0A" // 🌊
    type.contains("temple") || type.contains("church") || type.contains("mosque") || type.contains("religious") -> "\uD83D\uDD4C" // 🕌
    type.contains("hotel") || type.contains("hostel") || type.contains("accommodation") -> "\uD83C\uDFE8" // 🏨
    type.contains("safety") || type.contains("police") || type.contains("security") -> "\uD83D\uDEE1" // 🛡
    type.contains("landmark") || type.contains("attraction") || type.contains("viewpoint") -> "\uD83D\uDDFC" // 🗼
    type.contains("district") || type.contains("neighborhood") || type.contains("area") -> "\uD83C\uDFD8" // 🏘
    type.contains("sport") || type.contains("stadium") || type.contains("gym") -> "\u26BD" // ⚽
    type.contains("library") || type.contains("education") || type.contains("university") -> "\uD83D\uDCDA" // 📚
    type.contains("hospital") || type.contains("clinic") || type.contains("health") -> "\uD83C\uDFE5" // 🏥
    else -> "\uD83D\uDCCD" // 📍
}
