package com.family.finance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.family.finance.ui.session.SessionGate
import com.family.finance.ui.theme.FamilyFinanceTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run after super.onCreate: on API 33+ AppCompat ignores the call until the
        // activity's delegate exists. Applying it recreates the activity once, in Tamil.
        DefaultLanguage.applyIfUnset()
        enableEdgeToEdge()
        val session = (application as FamilyFinanceApplication).session
        setContent {
            FamilyFinanceTheme {
                SessionGate(session)
            }
        }
    }
}
