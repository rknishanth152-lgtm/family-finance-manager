package com.family.finance

import android.app.Application
import com.family.finance.data.AppContainer

class FamilyFinanceApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
