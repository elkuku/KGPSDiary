package org.elkuku.kgpsdiary.auth

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The signed-in account, kept on the phone between starts; never a token. */
interface AccountStore {
    fun load(): Account?

    fun save(account: Account)

    fun clear()
}

class SharedPreferencesAccountStore @Inject constructor(@ApplicationContext context: Context) : AccountStore {
    private val preferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    override fun load(): Account? = preferences.getString(EMAIL, null)?.let { Account(it, preferences.getString(NAME, null)) }

    override fun save(account: Account) = preferences.edit {
        putString(EMAIL, account.email)
        putString(NAME, account.displayName)
    }

    override fun clear() = preferences.edit { clear() }

    private companion object {
        const val EMAIL = "email"
        const val NAME = "displayName"
    }
}
