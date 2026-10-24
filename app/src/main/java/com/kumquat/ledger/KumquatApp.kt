package com.kumquat.ledger

import android.app.Application
import com.kumquat.ledger.data.AppDatabase
import com.kumquat.ledger.data.LedgerRepository
import com.kumquat.ledger.data.SessionStore

class KumquatApp : Application() {

    val repository: LedgerRepository by lazy { LedgerRepository(AppDatabase.get(this)) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SessionStore.init(this)
    }

    companion object {
        lateinit var instance: KumquatApp
            private set
    }
}
