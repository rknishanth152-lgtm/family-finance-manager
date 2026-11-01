package com.family.finance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.family.finance.ui.navigation.AppNavHost
import com.family.finance.ui.theme.FamilyFinanceTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DefaultLanguage.applyIfUnset()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyFinanceTheme {
                AppNavHost()
            }
        }
    }
}
