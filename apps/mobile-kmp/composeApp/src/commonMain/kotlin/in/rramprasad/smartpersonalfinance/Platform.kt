package `in`.rramprasad.smartpersonalfinance

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform