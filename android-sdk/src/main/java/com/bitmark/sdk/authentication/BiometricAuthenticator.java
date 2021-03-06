package com.bitmark.sdk.authentication;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import com.bitmark.sdk.R;
import com.bitmark.sdk.utils.annotation.Experimental;

import javax.crypto.Cipher;
import java.util.Arrays;

/**
 * @author Hieu Pham
 * @since 12/10/18
 * Email: hieupham@bitmark.com
 * Copyright © 2018 Bitmark. All rights reserved.
 */

@RequiresApi(api = Build.VERSION_CODES.P)
@Experimental
class BiometricAuthenticator extends AbsAuthenticator {

    private static final String FEATURE_IRIS = "android.hardware.iris";

    private static final String FEATURE_FACE = "android.hardware.face";

    private String title;

    private String description;

    private static final String[] SUPPORTED_FEATURES =
            new String[]{PackageManager.FEATURE_FINGERPRINT, FEATURE_IRIS, FEATURE_FACE};

    BiometricAuthenticator(@NonNull Activity activity, String title, String description,
                           @NonNull AuthenticationCallback callback) {
        super(activity, callback);
        this.title = title;
        this.description = description;
    }

    static boolean isHardwareDeteced(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return Arrays.stream(SUPPORTED_FEATURES).anyMatch(packageManager::hasSystemFeature);
    }

    static boolean isFingerprintHardwareDetected(Context context) {
        FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(context);
        return fingerprintManager.isHardwareDetected();
    }

    static boolean isFingerprintEnrolled(Context context) {
        FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(context);
        return fingerprintManager.hasEnrolledFingerprints();
    }

    @Override
    public void authenticate(@NonNull Cipher cipher) {
        new BiometricAuthenticationHandler(activity, title, description, callback)
                .authenticate(cipher);
    }

    private static class BiometricAuthenticationHandler extends
                                                        BiometricPrompt.AuthenticationCallback {

        private Activity activity;

        private AuthenticationCallback callback;

        private String title;

        private String description;

        BiometricAuthenticationHandler(Activity activity, String title, String description,
                                       AuthenticationCallback callback) {
            this.activity = activity;
            this.title = title;
            this.description = description;
            this.callback = callback;
        }

        void authenticate(Cipher cipher) {
            BiometricPrompt biometricPrompt = getBiometricPrompt();
            final CancellationSignal cancellationSignal = new CancellationSignal();
            biometricPrompt
                    .authenticate(new BiometricPrompt.CryptoObject(cipher), cancellationSignal,
                                  activity.getMainExecutor(), this);
        }

        @Override
        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);
            callback.onSucceeded(result.getCryptoObject().getCipher());
        }

        @Override
        public void onAuthenticationFailed() {
            super.onAuthenticationFailed();
            callback.onFailed();
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);
            if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                callback.onCancelled();
            } else
                callback.onError(errString.toString());
        }

        private BiometricPrompt getBiometricPrompt() {
            Context context = activity.getApplicationContext();
            return new BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setDescription(description)
                    .setNegativeButton(context.getString(R.string.cancel),
                                       context.getMainExecutor(),
                                       (dialogInterface, i) -> callback.onCancelled())
                    .build();
        }

    }


}
