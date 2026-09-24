package com.wisso.wizefiles.vault

import android.os.Bundle
import android.view.View
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.storage.Storages

class AddVaultDialogActivity : BaseThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Storages.canAddVault()) {
            ensureProAccess(ProFeature.MULTIPLE_VAULTS)
            finish()
            return
        }
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<AddVaultDialogFragment>(AddVaultDialogFragment::class.java.name)
            }
        }
    }
}
