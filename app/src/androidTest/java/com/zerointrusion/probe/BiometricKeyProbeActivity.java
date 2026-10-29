package com.zerointrusion.probe;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;
import android.widget.TextView;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class BiometricKeyProbeActivity extends Activity {
    private static final String TAG = "ZeroBioProbe";
    private final String alias = "zero_isolated_biometric_probe_" + UUID.randomUUID();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CancellationSignal cancellation = new CancellationSignal();
    private final byte[] message = new byte[32];
    private KeyPair keyPair;
    private byte[] encrypted;
    private TextView status;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setTextSize(20);
        status.setPadding(32, 64, 32, 32);
        setContentView(status);
        try {
            BiometricManager manager = getSystemService(BiometricManager.class);
            if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    != BiometricManager.BIOMETRIC_SUCCESS) {
                throw new IllegalStateException("Strong biometrics are not available");
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(
                    new KeyGenParameterSpec.Builder(
                                    alias,
                                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                            .setKeySize(2048)
                            .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                            .setUserAuthenticationRequired(true)
                            .setUserAuthenticationParameters(
                                    10, KeyProperties.AUTH_BIOMETRIC_STRONG)
                            .build());
            keyPair = generator.generateKeyPair();
            KeyInfo info =
                    KeyFactory.getInstance("RSA", "AndroidKeyStore")
                            .getKeySpec(keyPair.getPrivate(), KeyInfo.class);
            if (info.getSecurityLevel() != KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                    && info.getSecurityLevel() != KeyProperties.SECURITY_LEVEL_STRONGBOX) {
                throw new IllegalStateException("Key is not hardware backed");
            }
            if (!info.isUserAuthenticationRequired()
                    || info.getUserAuthenticationType() != KeyProperties.AUTH_BIOMETRIC_STRONG
                    || info.getUserAuthenticationValidityDurationSeconds() != 10
                    || !info.isUserAuthenticationRequirementEnforcedBySecureHardware()) {
                throw new IllegalStateException("Authentication policy is not hardware enforced");
            }
            Cipher encryptor = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            encryptor.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaep());
            encrypted = encryptor.doFinal(message);
            report("Hardware policy verified. Waiting for any previous authentication to expire.");
            handler.postDelayed(this::requestAuthentication, 12_000);
        } catch (Exception failure) {
            fail(failure);
        }
    }

    private void requestAuthentication() {
        try {
            assertLocked();
            report("PRE_AUTH_DENIED. Authenticate with your fingerprint.");
            new BiometricPrompt.Builder(this)
                    .setTitle("Isolated key protection test")
                    .setSubtitle("Authenticate to test hardware RSA access")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButton(
                            "Cancel",
                            getMainExecutor(),
                            (dialog, which) -> fail(new IllegalStateException("Probe cancelled")))
                    .build()
                    .authenticate(
                            cancellation,
                            getMainExecutor(),
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationSucceeded(
                                        BiometricPrompt.AuthenticationResult result) {
                                    verifyUnlocked();
                                }

                                @Override
                                public void onAuthenticationError(int code, CharSequence error) {
                                    fail(
                                            new IllegalStateException(
                                                    "Biometric error " + code + ": " + error));
                                }
                            });
        } catch (Exception failure) {
            fail(failure);
        }
    }

    private void verifyUnlocked() {
        try {
            byte[] signed = sign();
            Signature verifier = signature();
            verifier.initVerify(keyPair.getPublic());
            verifier.update(message);
            if (!verifier.verify(signed) || !Arrays.equals(message, decrypt())) {
                throw new IllegalStateException("RSA roundtrip failed");
            }
            report("POST_AUTH_SIGN_DECRYPT_PASS. Waiting for expiry.");
            handler.postDelayed(
                    () -> {
                        try {
                            assertLocked();
                            cleanup();
                            report(
                                    "PASS: hardware policy, denial before authentication, RSA"
                                        + " sign/decrypt after fingerprint, denial after expiry."
                                        + " Test key deleted.");
                        } catch (Exception failure) {
                            fail(failure);
                        }
                    },
                    12_000);
        } catch (Exception failure) {
            fail(failure);
        }
    }

    private void assertLocked() throws Exception {
        try {
            sign();
            throw new IllegalStateException("Signing was allowed without fresh biometrics");
        } catch (UserNotAuthenticatedException expected) {
            Log.i(TAG, "Signing denied as expected");
        }
        try {
            decrypt();
            throw new IllegalStateException("Decryption was allowed without fresh biometrics");
        } catch (UserNotAuthenticatedException expected) {
            Log.i(TAG, "Decryption denied as expected");
        }
    }

    private Signature signature() throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA/PSS");
        signer.setParameter(
                new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        return signer;
    }

    private byte[] sign() throws Exception {
        Signature signer = signature();
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        return signer.sign();
    }

    private byte[] decrypt() throws Exception {
        Cipher decryptor = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        decryptor.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaep());
        return decryptor.doFinal(encrypted);
    }

    private OAEPParameterSpec oaep() {
        return new OAEPParameterSpec(
                "SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
    }

    private void report(String text) {
        Log.i(TAG, text);
        status.setText(text);
    }

    private void fail(Exception failure) {
        Log.e(TAG, "FAIL", failure);
        cleanup();
        report("FAIL: " + failure);
    }

    private void cleanup() {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(alias);
        } catch (Exception failure) {
            Log.e(TAG, "Could not delete test key", failure);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cancellation.cancel();
        cleanup();
        super.onDestroy();
    }
}
