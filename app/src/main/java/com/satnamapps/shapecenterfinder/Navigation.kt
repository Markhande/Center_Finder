package com.satnamapps.shapecenterfinder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.satnamapps.shapecenterfinder.cemera_model.CameraFinderScreen
import com.satnamapps.shapecenterfinder.model.Screen

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ){
        composable(Screen.CenterFinder.route) {
            CameraFinderScreen(
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.ObjectCount.route) {
            ObjectCounterScreen()
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onCenterFinderClick = {
                    navController.navigate(Screen.CenterFinder.route)
                },
                onObjectCounterClick = {
                    navController.navigate(Screen.ObjectCount.route)
                }
            )
        }
    }
}