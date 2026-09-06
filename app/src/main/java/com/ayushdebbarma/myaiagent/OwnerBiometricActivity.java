package com.ayushdebbarma.myaiagent;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.Executor;

/**
 * Hardware-backed owner gate. The snap is only a trigger; it is never treated
 * as proof of identity. Every strict trigger must pass a BIOMETRIC_STRONG
 * auth-per-use Keystore signature before Axtor starts command recognition.
 */
public final class OwnerBiometricActivity extends Activity {
    public static final String ACTION_OWNER_BIOMETRIC_OK = "com.ayushdebbarma.myaiagent.OWNER_BIOMETRIC_OK";
    public static final String EXTRA_CHALLENGE = "challenge";
    public static final String EXTRA_SIGNATURE = "signature";
    private static final String ALIAS = "axtor_owner_ec_v1";
    private static final String KS = "AndroidKeyStore";
    private byte[] challenge;
    private Signature crypto;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String raw = getIntent().getStringExtra(EXTRA_CHALLENGE);
        if (raw == null || raw.isEmpty()) { finish(); return; }
        try { challenge = Base64.getDecoder().decode(raw); } catch (Exception e) { finish(); return; }
        authenticate();
    }

    private void authenticate() {
        if (android.os.Build.VERSION.SDK_INT < 30) { fail("BIOMETRIC_API_UNAVAILABLE"); return; }
        BiometricManager bm = getSystemService(BiometricManager.class);
        if (bm == null || bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            fail("STRONG_BIOMETRIC_UNAVAILABLE"); return;
        }
        try {
            crypto = Signature.getInstance("SHA256withECDSA");
            crypto.initSign(getOrCreateKey());
        } catch (Exception e) { fail("BIOMETRIC_KEY_INIT_FAILED"); return; }

        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Verify Axtor owner")
                .setSubtitle("Your snap was detected. Confirm your identity to activate Axtor.")
                .setDescription("A copied or replayed snap cannot authorize Axtor without your strong biometric.")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(true)
                .build();
        Executor executor = getMainExecutor();
        CancellationSignal cancel = new CancellationSignal();
        prompt.authenticate(new BiometricPrompt.CryptoObject(crypto), cancel, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        try {
                            Signature signed = result.getCryptoObject() != null && result.getCryptoObject().getSignature() != null
                                    ? result.getCryptoObject().getSignature() : crypto;
                            signed.update(challenge);
                            byte[] sig = signed.sign();
                            Intent i = new Intent(OwnerBiometricActivity.this, VoiceAssistantService.class);
                            i.setAction(ACTION_OWNER_BIOMETRIC_OK);
                            i.putExtra(EXTRA_CHALLENGE, Base64.getEncoder().encodeToString(challenge));
                            i.putExtra(EXTRA_SIGNATURE, Base64.getEncoder().encodeToString(sig));
                            startService(i);
                            finish();
                        } catch (Exception e) { fail("BIOMETRIC_SIGNATURE_FAILED"); }
                    }
                    @Override public void onAuthenticationError(int code, CharSequence message) { fail("BIOMETRIC_REJECTED_" + code); }
                });
    }

    private java.security.PrivateKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS); ks.load(null);
        if (ks.containsAlias(ALIAS)) return (java.security.PrivateKey) ks.getKey(ALIAS, null);
        KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KS);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build();
        g.initialize(spec); g.generateKeyPair();
        return (java.security.PrivateKey) ks.getKey(ALIAS, null);
    }

    private void fail(String reason) {
        getSharedPreferences("axtor", 0).edit().putString("voice_last_error", reason).putString("owner_gate", "rejected").apply();
        finish();
    }
}
