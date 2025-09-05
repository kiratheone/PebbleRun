package com.arikachmad.pebblerun

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform