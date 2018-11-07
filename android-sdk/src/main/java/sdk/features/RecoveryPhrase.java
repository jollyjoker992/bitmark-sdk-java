package sdk.features;

import apiservice.configuration.GlobalConfiguration;
import apiservice.configuration.Network;
import apiservice.utils.error.UnexpectedException;
import cryptography.error.ValidateException;
import sdk.utils.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static apiservice.utils.ArrayUtil.*;
import static cryptography.utils.Validator.checkValid;
import static sdk.utils.FileUtils.getResourceAsFile;
import static sdk.utils.SdkUtils.randomEntropy;
import static sdk.utils.Version.TWELVE;

/**
 * @author Hieu Pham
 * @since 8/23/18
 * Email: hieupham@bitmark.com
 * Copyright © 2018 Bitmark. All rights reserved.
 */

public class RecoveryPhrase {

    private final String[] mnemonicWords;

    private static String[] EN_WORDS;

    private static String[] CN_WORDS;

    private static final int[] MASKS = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023};

    private static String[] getWords(Locale locale) {
        checkValid(() -> locale == Locale.ENGLISH || locale == Locale.CHINESE, "Does not support " +
                "this locale");
        if (locale == Locale.ENGLISH && EN_WORDS != null) return EN_WORDS;
        if (locale == Locale.CHINESE && CN_WORDS != null) return CN_WORDS;

        try {
            final int size = 2048;
            File file = getResourceAsFile(locale == Locale.ENGLISH ? "bip/bip39_eng.txt" : "bip" +
                    "/bip39_cn.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String[] words = new String[size];
            for (int i = 0; i < size; i++) {
                String word = reader.readLine();
                if (word == null) break;
                words[i] = word;
            }
            if (locale == Locale.ENGLISH) EN_WORDS = words;
            else CN_WORDS = words;
            return words;
        } catch (IOException e) {
            throw new UnexpectedException(e.getMessage());
        }
    }

    public static RecoveryPhrase fromSeed(Seed seed) throws ValidateException {
        return fromSeed(seed, Locale.ENGLISH);
    }

    public static RecoveryPhrase fromSeed(Seed seed, Locale locale) throws ValidateException {
        checkValid(() -> seed != null && Version.matchesCore(seed.getSeed()), "Invalid Seed");
        final byte[] core = seed.getSeed();
        final Version version = Version.fromCore(core);
        return version == TWELVE ? new RecoveryPhrase(generateMnemonic(core, locale)) :
                new RecoveryPhrase(generateMnemonic(concat(toByteArray(seed.getNetwork().value())
                        , core), locale));
    }

    public static RecoveryPhrase fromMnemonicWords(String... mnemonicWords) throws ValidateException {
        return new RecoveryPhrase(mnemonicWords);
    }

    public RecoveryPhrase() {
        final byte[] entropy = randomEntropy(GlobalConfiguration.network());
        this.mnemonicWords = generateMnemonic(entropy);
    }

    private RecoveryPhrase(String... mnemonicWords) throws ValidateException {
        validate(mnemonicWords);
        this.mnemonicWords = mnemonicWords;
    }

    public String[] getMnemonicWords() {
        return mnemonicWords;
    }

    public Seed recoverSeed() {
        return recoverSeed(mnemonicWords);
    }

    public static Seed recoverSeed(String[] mnemonicWord) throws ValidateException {
        validate(mnemonicWord);
        final Version version = Version.fromMnemonicWords(mnemonicWord);
        final int wordsLength = version.getMnemonicWordsLength();
        final int coreLength = version.getCoreLength();
        final int entropyLength = version == TWELVE ? coreLength : coreLength + 1;
        final Locale locale = detectLocale(mnemonicWord[0]);
        if (locale == null) throw new ValidateException("Does not support this language");

        String[] words = getWords(locale);
        int[] data = new int[]{};
        int remainder = 0;
        int bits = 0;

        for (int i = 0; i < wordsLength; i++) {
            final String word = mnemonicWord[i];
            final int index = indexOf(words, word);
            remainder = (remainder << 11) + index;
            for (bits += 11; bits >= 8; bits -= 8) {
                final int a = 0xFF & (remainder >> (bits - 8));
                data = concat(data, new int[]{a});
            }
            remainder &= MASKS[bits];
        }
        final byte[] entropy = version == TWELVE ? concat(toByteArray(data),
                new byte[]{(byte) (remainder << 4)}) : toByteArray(data);
        checkValid(() -> entropy.length == entropyLength, "Invalid mnemonic words");
        if (version == TWELVE) {
            return new Seed(entropy);
        } else {
            final Network network = Network.valueOf(entropy[0]);
            final byte[] core = slice(entropy, 1, entropyLength);
            return new Seed(core, network);
        }
    }

    public static String[] generateMnemonic(byte[] entropy) throws ValidateException {
        return generateMnemonic(entropy, Locale.ENGLISH);
    }

    public static String[] generateMnemonic(byte[] entropy, Locale locale) throws ValidateException {
        checkValid(() -> entropy != null && Version.matchesEntropy(entropy), "Invalid entropy");
        final String[] words = getWords(locale);
        final Version version = Version.fromEntropy(entropy);
        final int mnenonicWordsLength = version.getMnemonicWordsLength();
        final int entropyLength = version.getEntropyLength();

        final List<String> mnemonicWords = new ArrayList<>(mnenonicWordsLength);
        final int[] unsignedEntropy = toUInt(entropy);
        int accumulator = 0;
        int bits = 0;
        for (int i = 0; i < entropyLength; i++) {
            accumulator = (accumulator << 8) + unsignedEntropy[i];
            bits += 8;
            if (bits >= 11) {
                bits -= 11;
                int index = accumulator >> bits;
                accumulator &= MASKS[bits];
                mnemonicWords.add(words[index]);
            }
        }
        return mnemonicWords.toArray(new String[mnenonicWordsLength]);
    }

    private static void validate(String... mnemonicWords) {
        checkValid(() -> mnemonicWords != null && Version.matchesMnemonicWords(mnemonicWords)
                && (contains(getWords(Locale.ENGLISH), mnemonicWords) || contains(getWords(Locale.CHINESE), mnemonicWords)));
    }

    private static Locale detectLocale(String examined) {
        return contains(getWords(Locale.ENGLISH), examined) ? Locale.ENGLISH :
                contains(getWords(Locale.CHINESE), examined) ? Locale.CHINESE : null;
    }
}