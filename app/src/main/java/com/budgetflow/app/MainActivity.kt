package com.budgetflow.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.ThemeMode
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.util.ReminderScheduler
import com.budgetflow.app.ui.navigation.BudgetFlowNavHost
import com.budgetflow.app.ui.onboarding.OnboardingScreen
import com.budgetflow.app.ui.theme.BudgetFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var repository: PreferencesRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by repository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.LIGHT)
            val securityEnabled by repository.observeSecurityEnabled()
                .collectAsStateWithLifecycle(initialValue = false)
            val onboardingCompleted by repository.observeOnboardingCompleted()
                .collectAsStateWithLifecycle(initialValue = true)
            val billRemindersEnabled by repository.observeBillRemindersEnabled()
                .collectAsStateWithLifecycle(initialValue = false)

            var isUnlocked by remember(securityEnabled) { mutableStateOf(!securityEnabled) }

            LaunchedEffect(billRemindersEnabled) {
                if (billRemindersEnabled) {
                    reminderScheduler.setBillRemindersEnabled(true)
                }
            }

            LaunchedEffect(securityEnabled) {
                if (securityEnabled && !isUnlocked) {
                    authenticateUser(onSuccess = { isUnlocked = true })
                }
            }

            BudgetFlowTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        !isUnlocked -> LockScreen(onUnlock = { authenticateUser(onSuccess = { isUnlocked = true }) })
                        !onboardingCompleted -> OnboardingScreen()
                        else -> BudgetFlowNavHost()
                    }
                }
            }
        }
    }

    private fun authenticateUser(onSuccess: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_unlock_title))
            .setSubtitle(getString(R.string.biometric_unlock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.lock_screen_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.lock_screen_unlock))
        }
    }
}
