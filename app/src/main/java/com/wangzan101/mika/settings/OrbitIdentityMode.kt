package com.wangzan101.mika.settings

enum class OrbitIdentityMode {
    Original, Monochrome;

    companion object {
        fun parse(value: String?): OrbitIdentityMode = entries.firstOrNull { it.name == value } ?: Original
    }
}
