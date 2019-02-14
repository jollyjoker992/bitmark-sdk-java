package com.bitmark.sdk.keymanagement;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import com.bitmark.apiservice.utils.Pair;
import com.bitmark.apiservice.utils.callback.Callback0;
import com.bitmark.apiservice.utils.callback.Callback1;
import com.bitmark.apiservice.utils.error.UnexpectedException;
import com.bitmark.sdk.authentication.AuthenticationCallback;
import com.bitmark.sdk.authentication.Authenticator;
import com.bitmark.sdk.authentication.AuthenticatorFactory;
import com.bitmark.sdk.authentication.KeyAuthenticationSpec;
import com.bitmark.sdk.authentication.error.AuthenticationException;
import com.bitmark.sdk.authentication.error.AuthenticationRequiredException;
import io.reactivex.annotations.Nullable;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

import static com.bitmark.cryptography.crypto.encoder.Base58.BASE_58;
import static com.bitmark.cryptography.crypto.encoder.Raw.RAW;
import static com.bitmark.sdk.authentication.error.AuthenticationException.Type.*;
import static com.bitmark.sdk.authentication.error.AuthenticationRequiredException.FINGERPRINT;
import static com.bitmark.sdk.utils.DeviceUtils.isAboveP;
import static com.bitmark.sdk.utils.FileUtils.read;
import static com.bitmark.sdk.utils.FileUtils.write;

/**
 * @author Hieu Pham
 * @since 12/6/18
 * Email: hieupham@bitmark.com
 * Copyright © 2018 Bitmark. All rights reserved.
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class KeyManagerImpl implements KeyManager {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private static final String AES_KEY_TRANSFORMATION = KeyProperties.KEY_ALGORITHM_AES + "/" +
                                                         KeyProperties.BLOCK_MODE_CBC + "/" +
                                                         KeyProperties.ENCRYPTION_PADDING_PKCS7;

    private static final String SEPARATE_CHARACTER = "/";

    private Activity activity;

    public KeyManagerImpl(@NonNull Activity activity) {
        this.activity = activity;
    }

    @Override
    public void getKey(String alias, KeyAuthenticationSpec keyAuthSpec,
                       Callback1<byte[]> callback) {

        try {

            if (!isEncryptionKeyExisted(keyAuthSpec.getKeyAlias())) {
                callback.onError(
                        new IllegalArgumentException("Encryption key alias is not existing"));
                return;
            }

            final SecretKey encryptionKey =
                    (SecretKey) getLoadedAndroidKeyStore().getKey(keyAuthSpec.getKeyAlias(), null);

            // Retrieve the key info of encryption key
            SecretKeyFactory factory =
                    SecretKeyFactory
                            .getInstance(encryptionKey.getAlgorithm(), ANDROID_KEYSTORE);
            KeyInfo info = (KeyInfo) factory.getKeySpec(encryptionKey, KeyInfo.class);

            KeyAuthenticationSpec newKeyAuthSpec = keyAuthSpec.newBuilder(activity)
                                                              .setAuthenticationRequired(
                                                                      info.isUserAuthenticationRequired())
                                                              .setAuthenticationValidityDuration(
                                                                      info.getUserAuthenticationValidityDurationSeconds())
                                                              .build();

            try {
                Cipher cipher = getDecryptCipher(alias, encryptionKey);

                if (newKeyAuthSpec.isAuthenticationRequired() &&
                    !newKeyAuthSpec.willInvalidateInTimeFrame()) {

                    // The key authentication is required and user didn't set the validity time frame for it
                    authForGetKey(alias, newKeyAuthSpec, cipher, callback);
                } else {

                    // Don't require for authentication
                    callback.onSuccess(getKey(alias, cipher));
                }
            } catch (UserNotAuthenticatedException e) {

                // The user has not authenticated within the specified time frame
                triggerDeviceAuthentication(keyAuthSpec, getAuthCallback(new Callback1<Cipher>() {
                    @Override
                    public void onSuccess(Cipher cipher) {
                        try {
                            callback.onSuccess(
                                    getKey(alias, getDecryptCipher(alias, encryptionKey)));
                        } catch (IOException | BadPaddingException | IllegalBlockSizeException
                                | NoSuchPaddingException | NoSuchAlgorithmException
                                | InvalidAlgorithmParameterException | InvalidKeyException e1) {
                            e1.printStackTrace();
                            callback.onError(e1);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callback.onError(throwable);
                    }
                }));
            }

        } catch (KeyStoreException | InvalidAlgorithmParameterException | CertificateException
                | IOException | NoSuchAlgorithmException | NoSuchProviderException
                | BadPaddingException | InvalidKeySpecException | IllegalBlockSizeException
                | NoSuchPaddingException e) {
            // Unexpected error
            e.printStackTrace();
            callback.onError(new UnexpectedException(e));
        } catch (UnrecoverableEntryException | InvalidKeyException e) {
            // Cannot get the key because if it might be broken
            e.printStackTrace();
            callback.onError(new InvalidKeyException(e));
        } catch (AuthenticationRequiredException e) {
            // Missing key requirement
            e.printStackTrace();
            callback.onError(e);
        }
    }

    private void authForGetKey(String alias, KeyAuthenticationSpec spec, Cipher cipher,
                               Callback1<byte[]> callback) {

        try {

            final AuthenticationCallback authCallback = new AuthenticationCallback() {
                @Override
                public void onSucceeded(@NonNull Cipher cipher) {
                    try {
                        callback.onSuccess(getKey(alias, cipher));
                    } catch (IllegalBlockSizeException
                            | IOException | BadPaddingException e) {
                        e.printStackTrace();
                        callback.onError(e);
                    }
                }

                @Override
                public void onFailed() {
                    callback.onError(new AuthenticationException(FAILED));
                }

                @Override
                public void onError(String error) {
                    callback.onError(new AuthenticationException(ERROR, error));
                }

                @Override
                public void onCancelled() {
                    callback.onError(new AuthenticationException(CANCELLED));
                }
            };

            Authenticator authenticator =
                    spec.willInvalidateInTimeFrame() ? getDeviceAuthenticator(spec,
                                                                              authCallback) : getBiometricAuthenticator(
                            spec, authCallback);
            authenticator.authenticate(cipher);
        } catch (AuthenticationRequiredException e) {
            e.printStackTrace();
            callback.onError(e);
        }

    }

    private byte[] getKey(String alias, Cipher cipher)
            throws IOException, BadPaddingException, IllegalBlockSizeException {
        byte[] encryptedKey = getEncryptedKeyInfo(alias).first();
        return cipher.doFinal(encryptedKey);
    }

    private Cipher getDecryptCipher(String alias, SecretKey key)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
                   InvalidAlgorithmParameterException, InvalidKeyException {

        byte[] iv = getEncryptedKeyInfo(alias).second();
        Cipher cipher = Cipher.getInstance(AES_KEY_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher;
    }

    private Pair<byte[], byte[]> getEncryptedKeyInfo(String alias) throws IOException {
        File file = getEncryptedKeyFile(alias);
        if (!file.exists()) throw new IOException("File is not existing");
        String encryptedKeyString = RAW.encode(read(file));
        byte[] encryptedKey = BASE_58.decode(encryptedKeyString.split(SEPARATE_CHARACTER)[0]);
        byte[] iv = BASE_58.decode(encryptedKeyString.split(SEPARATE_CHARACTER)[1]);
        return new Pair<>(encryptedKey, iv);
    }

    @Override
    public void saveKey(String alias, KeyAuthenticationSpec keyAuthSpec, byte[] key,
                        Callback0 callback) {

        try {

            final SecretKey encryptionKey =
                    isEncryptionKeyExisted(
                            keyAuthSpec.getKeyAlias()) ? (SecretKey) getLoadedAndroidKeyStore()
                            .getKey(keyAuthSpec.getKeyAlias(), null) : generateEncryptionKey(
                            keyAuthSpec.getKeyAlias(),
                            keyAuthSpec.isAuthenticationRequired(),
                            keyAuthSpec.getAuthenticationValidityDuration());

            // Retrieve the key info of encryption key
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(encryptionKey.getAlgorithm(), ANDROID_KEYSTORE);
            KeyInfo info = (KeyInfo) factory.getKeySpec(encryptionKey, KeyInfo.class);

            // Regenerate key spec
            KeyAuthenticationSpec newKeyAuthSpec = keyAuthSpec.newBuilder(activity)
                                                              .setAuthenticationRequired(
                                                                      info.isUserAuthenticationRequired())
                                                              .setAuthenticationValidityDuration(
                                                                      info.getUserAuthenticationValidityDurationSeconds())
                                                              .build();

            try {
                Cipher cipher = getEncryptCipher(encryptionKey);

                if (newKeyAuthSpec.isAuthenticationRequired() &&
                    !keyAuthSpec.willInvalidateInTimeFrame()) {
                    // The key authentication is required and user didn't set the validity time frame for it
                    authForSaveKey(alias, keyAuthSpec, key, cipher, callback);
                } else {
                    // Do not require for authentication
                    saveKey(alias, key, cipher);
                    callback.onSuccess();
                }

            } catch (UserNotAuthenticatedException e) {

                // The user has not authenticated within the specified time frame
                triggerDeviceAuthentication(keyAuthSpec, getAuthCallback(new Callback1<Cipher>() {
                    @Override
                    public void onSuccess(Cipher cipher) {
                        try {
                            saveKey(alias, key, getEncryptCipher(encryptionKey));
                            callback.onSuccess();
                        } catch (BadPaddingException | IllegalBlockSizeException | IOException
                                | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e1) {
                            e1.printStackTrace();
                            callback.onError(e1);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callback.onError(throwable);
                    }
                }));
            }

        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException
                | NoSuchProviderException | NoSuchPaddingException
                | BadPaddingException | InvalidKeySpecException | IllegalBlockSizeException e) {
            e.printStackTrace();
            callback.onError(new UnexpectedException(e));
        } catch (InvalidKeyException | UnrecoverableEntryException e) {
            // Cannot get the key because if it might be broken
            e.printStackTrace();
            callback.onError(new InvalidKeyException(e));
        } catch (AuthenticationRequiredException e) {
            // Missing key requirement
            e.printStackTrace();
            callback.onError(e);
        }
    }

    private AuthenticationCallback getAuthCallback(Callback1<Cipher> callback) {
        return new AuthenticationCallback() {
            @Override
            public void onSucceeded(@Nullable Cipher cipher) {
                callback.onSuccess(cipher);
            }

            @Override
            public void onFailed() {
                callback.onError(new AuthenticationException(FAILED));
            }

            @Override
            public void onError(String error) {
                callback.onError(new AuthenticationException(ERROR, error));
            }

            @Override
            public void onCancelled() {
                callback.onError(new AuthenticationException(CANCELLED));
            }
        };
    }

    private void authForSaveKey(String alias, KeyAuthenticationSpec spec, byte[] key, Cipher cipher,
                                Callback0 callback) {

        try {
            final AuthenticationCallback authCallback = getAuthCallback(
                    new Callback1<Cipher>() {
                        @Override
                        public void onSuccess(Cipher cipher) {
                            try {
                                saveKey(alias, key, cipher);
                                callback.onSuccess();
                            } catch (BadPaddingException | IllegalBlockSizeException | IOException e) {
                                e.printStackTrace();
                                callback.onError(e);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }
                    });
            Authenticator authenticator =
                    spec.willInvalidateInTimeFrame() ? getDeviceAuthenticator(spec,
                                                                              authCallback) : getBiometricAuthenticator(
                            spec, authCallback);
            authenticator.authenticate(cipher);
        } catch (AuthenticationRequiredException e) {
            e.printStackTrace();
            callback.onError(e);
        }
    }

    private void triggerDeviceAuthentication(KeyAuthenticationSpec spec,
                                             AuthenticationCallback authCallback)
            throws AuthenticationRequiredException {

        getDeviceAuthenticator(spec, authCallback).authenticate(null);
    }

    private void saveKey(String alias, byte[] key, Cipher cipher)
            throws BadPaddingException, IllegalBlockSizeException, IOException {

        byte[] encryptedKey = cipher.doFinal(key);
        byte[] iv = cipher.getIV();
        String result = BASE_58.encode(encryptedKey) + SEPARATE_CHARACTER + BASE_58.encode(iv);

        // Write encrypted key to local storage
        write(createEncryptedKeyFile(alias), RAW.decode(result));
    }

    private Cipher getEncryptCipher(SecretKey key)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance(AES_KEY_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher;
    }

    @SuppressLint("NewApi")
    private Authenticator getBiometricAuthenticator(KeyAuthenticationSpec spec,
                                                    AuthenticationCallback callback)
            throws AuthenticationRequiredException {
        return isAboveP() ? AuthenticatorFactory
                .getBiometricAuthenticator(activity, spec.getAuthenticationTitleResId(),
                                           spec.getAuthenticationDescriptionResId(),
                                           callback) : AuthenticatorFactory
                .getFingerprintAuthenticator(activity, spec.getAuthenticationTitleResId(),
                                             spec.getAuthenticationDescriptionResId(), callback);
    }

    private Authenticator getDeviceAuthenticator(KeyAuthenticationSpec spec,
                                                 AuthenticationCallback callback)
            throws AuthenticationRequiredException {
        return AuthenticatorFactory
                .getDeviceAuthenticator(activity, spec.getAuthenticationTitleResId(),
                                        spec.getAuthenticationDescriptionResId(), callback);
    }

    @Override
    public void removeKey(String alias, KeyAuthenticationSpec keyAuthSpec, Callback0 callback) {
        try {
            triggerDeviceAuthentication(keyAuthSpec, getAuthCallback(new Callback1<Cipher>() {
                @Override
                public void onSuccess(Cipher cipher) {
                    try {
                        KeyStore keyStore = getLoadedAndroidKeyStore();
                        keyStore.deleteEntry(keyAuthSpec.getKeyAlias());
                        getEncryptedKeyFile(alias).delete();
                        callback.onSuccess();
                    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
                        e.printStackTrace();
                        callback.onError(e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    callback.onError(throwable);
                }
            }));
        } catch (AuthenticationRequiredException e) {
            e.printStackTrace();
            callback.onError(e);
        }
    }


    @SuppressLint("NewApi")
    private SecretKey generateEncryptionKey(String keyAlias, boolean isAuthenticationRequired,
                                            int keyAuthValidityDuration)
            throws NoSuchProviderException, NoSuchAlgorithmException,
                   InvalidAlgorithmParameterException, AuthenticationRequiredException {
        try {
            final KeyGenerator keyGenerator = KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

            final KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);

            if (isAuthenticationRequired) {
                builder.setUserAuthenticationRequired(true);
                if (keyAuthValidityDuration != -1)
                    builder.setUserAuthenticationValidityDurationSeconds(
                            keyAuthValidityDuration);
            }
            if (isAboveP()) builder.setIsStrongBoxBacked(true); // Enable hardware secure module

            keyGenerator.init(builder.build());
            return keyGenerator.generateKey();
        } catch (InvalidAlgorithmParameterException e) {
            if (e.getCause() instanceof IllegalStateException) {
                throw new AuthenticationRequiredException(FINGERPRINT);
            }
            throw e;
        }
    }

    private boolean isEncryptionKeyExisted(String keyAlias) {
        try {
            return getLoadedAndroidKeyStore().containsAlias(keyAlias);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    private KeyStore getLoadedAndroidKeyStore()
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    private File createEncryptedKeyFile(String fileName) throws IOException {
        File file = getEncryptedKeyFile(fileName);
        if (!file.exists()) file.createNewFile();
        return file;
    }

    private File getEncryptedKeyFile(String fileName) {
        return new File(activity.getApplicationContext().getFilesDir(), fileName + ".key");
    }
}
