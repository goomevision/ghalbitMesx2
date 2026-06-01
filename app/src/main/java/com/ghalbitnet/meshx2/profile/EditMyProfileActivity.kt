package com.ghalbitnet.meshx2.profile

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditMyProfileActivity : AppCompatActivity() {
    private var selectedAvatarUri: String? = null

    private val avatarPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            selectedAvatarUri = uri?.toString()
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                Log.d("GHALBIT-PROFILE", "avatar changed")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GhalbitTheme.applyWindow(this, "edit-my-profile")
        setContentView(R.layout.activity_edit_my_profile)
        title = "Edit Kartu Nama"

        val statusChoices =
            listOf(
                CommunityStatusType.AVAILABLE.wireValue,
                CommunityStatusType.BUSY.wireValue,
                CommunityStatusType.EMERGENCY_HELPER.wireValue,
                CommunityStatusType.RELAY_OPERATOR.wireValue,
                CommunityStatusType.OFFLINE.wireValue,
                CommunityStatusType.CUSTOM.wireValue
            )
        val themeChoices = ContactCardTheme.entries.map { it.themeId }
        findViewById<AutoCompleteTextView>(R.id.edtStatusType).setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusChoices)
        )
        findViewById<AutoCompleteTextView>(R.id.edtThemeId).setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themeChoices)
        )

        findViewById<Button>(R.id.btnPickAvatar).setOnClickListener {
            avatarPicker.launch(arrayOf("image/*"))
        }
        findViewById<Button>(R.id.btnSaveMyProfile).setOnClickListener {
            saveProfile()
        }
        findViewById<Button>(R.id.btnCancelMyProfile).setOnClickListener {
            finish()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val profile = ProfileRepository.getOrCreateMyProfile(this@EditMyProfileActivity)
            withContext(Dispatchers.Main) {
                bind(profile)
            }
        }
    }

    private fun bind(profile: CommunityProfile) {
        findViewById<EditText>(R.id.edtDisplayName).setText(profile.displayName)
        findViewById<EditText>(R.id.edtNickname).setText(profile.nickname)
        findViewById<EditText>(R.id.edtCommunityName).setText(profile.communityName)
        findViewById<EditText>(R.id.edtRoleTitle).setText(profile.roleTitle)
        findViewById<EditText>(R.id.edtBio).setText(profile.bio)
        findViewById<EditText>(R.id.edtRegion).setText(profile.region)
        findViewById<EditText>(R.id.edtOrganization).setText(profile.organization ?: "")
        findViewById<EditText>(R.id.edtSkillTags).setText(profile.skillTags.joinToString(", "))
        findViewById<EditText>(R.id.edtStatusMessage).setText(profile.statusMessage)
        findViewById<AutoCompleteTextView>(R.id.edtStatusType).setText(profile.statusType.wireValue, false)
        findViewById<AutoCompleteTextView>(R.id.edtThemeId).setText(profile.cardTheme.themeId, false)
        findViewById<CheckBox>(R.id.checkPublicProfile).isChecked = profile.isPublicProfile
        findViewById<CheckBox>(R.id.checkShowRegion).isChecked = profile.isRegionVisible
        findViewById<CheckBox>(R.id.checkShowStatus).isChecked = profile.isStatusVisible
        findViewById<CheckBox>(R.id.checkRelayDiscovery).isChecked = profile.isRelayDiscoveryEnabled
        findViewById<CheckBox>(R.id.checkAvatarSync).isChecked = profile.isAvatarSyncEnabled
        selectedAvatarUri = profile.avatarUri
    }

    private fun saveProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            val updated =
                ProfileRepository.updateMyProfile(this@EditMyProfileActivity) { current ->
                    current.copy(
                        displayName = findViewById<EditText>(R.id.edtDisplayName).text.toString().trim(),
                        nickname = findViewById<EditText>(R.id.edtNickname).text.toString().trim(),
                        communityName = findViewById<EditText>(R.id.edtCommunityName).text.toString().trim(),
                        roleTitle = findViewById<EditText>(R.id.edtRoleTitle).text.toString().trim(),
                        bio = findViewById<EditText>(R.id.edtBio).text.toString().trim(),
                        region = findViewById<EditText>(R.id.edtRegion).text.toString().trim(),
                        organization = findViewById<EditText>(R.id.edtOrganization).text.toString().trim().ifBlank { null },
                        skillTagsCsv = findViewById<EditText>(R.id.edtSkillTags).text.toString(),
                        avatarUri = selectedAvatarUri,
                        cardThemeId = findViewById<AutoCompleteTextView>(R.id.edtThemeId).text.toString().ifBlank { current.cardThemeId },
                        statusMessage = findViewById<EditText>(R.id.edtStatusMessage).text.toString().trim(),
                        statusType = findViewById<AutoCompleteTextView>(R.id.edtStatusType).text.toString().ifBlank { current.statusType },
                        statusUpdatedAt = System.currentTimeMillis(),
                        isPublicProfile = findViewById<CheckBox>(R.id.checkPublicProfile).isChecked,
                        showRegionPublicly = findViewById<CheckBox>(R.id.checkShowRegion).isChecked,
                        showStatusPublicly = findViewById<CheckBox>(R.id.checkShowStatus).isChecked,
                        relayDiscoveryEnabled = findViewById<CheckBox>(R.id.checkRelayDiscovery).isChecked,
                        avatarSyncEnabled = findViewById<CheckBox>(R.id.checkAvatarSync).isChecked,
                        relaySyncEnabled = findViewById<CheckBox>(R.id.checkRelayDiscovery).isChecked
                    )
                }
            Log.d("GHALBIT-PROFILE", "theme changed")
            Log.d("GHALBIT-PROFILE-PRIVACY", "updated")
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
