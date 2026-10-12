package com.satnamapps.shapecenterfinder.model

sealed class Screen (val route: String) {

    object Dashboard : Screen("dashboard")
    object CenterFinder : Screen("centerFinder")
    object ObjectCount : Screen("objectCount")

    object Profile : Screen("profile")
}