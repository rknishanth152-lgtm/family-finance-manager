package com.family.finance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.family.finance.ui.common.LocalAppContainer
import com.family.finance.ui.navigation.AppNavHost
import com.family.finance.ui.theme.FamilyFinanceTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run after super.onCreate: on API 33+ AppCompat ignores the call until the
        // activity's delegate exists. Applying it recreates the activity once, in Tamil.
        DefaultLanguage.applyIfUnset()
        enableEdgeToEdge()
        val container = (application as FamilyFinanceApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                FamilyFinanceTheme {
                    AppNavHost()
                }
            }
        }
    }
}
