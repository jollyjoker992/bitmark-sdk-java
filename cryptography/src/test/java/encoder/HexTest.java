package encoder;

import error.ValidateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static crypto.encoder.Hex.HEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Hieu Pham
 * @since 8/24/18
 * Email: hieupham@bitmark.com
 * Copyright © 2018 Bitmark. All rights reserved.
 */

public class HexTest extends BaseEncoderTest {

    @DisplayName("Verify function Hex.encode(byte[]) works well with happy condition")
    @ParameterizedTest
    @MethodSource("createBytesHex")
    public void testEncode_ValidByteArrayInput_CorrectHexIsReturn(byte[] input,
                                                                  String expectedHex) {
        String output = HEX.encode(input);
        assertEquals(output, expectedHex);
    }

    @DisplayName("Verify function Hex.encode(byte[]) throws exception when byte array input is " +
            "null or empty")
    @ParameterizedTest
    @MethodSource("createInvalidBytes")
    public void testEncode_InvalidByteArrayInput_ErrorIsThrow(byte[] input) {
        assertThrows(ValidateException.class, () -> HEX.encode(input));
    }

    @DisplayName("Verify function Hex.decode(String) works well with happy condition")
    @ParameterizedTest
    @MethodSource("createHexBytes")
    public void testDecode_ValidHexString_CorrectByteArrayReturn(String hex, byte[] expectedBytes) {
        final byte[] output = HEX.decode(hex);
        assertTrue(Arrays.equals(output, expectedBytes));
    }

    @DisplayName("Verify function Hex.decode(String) throws exception when the hex string is " +
            "odd")
    @ParameterizedTest
    @ValueSource(strings = {"ABC3345ABEFFC", "97361A58AED147A2AB6345678", "A"})
    public void testDecode_HexIsOdd_ErrorIsThrow(String hex) {
        assertThrows(RuntimeException.class, () -> HEX.decode(hex));
    }

    @DisplayName("Verify function Hex.decode(String) throws exception when the hex string is " +
            "invalid")
    @ParameterizedTest
    @ValueSource(strings = {"@!#$@23ASAfFHFT", "1233453,./123//34.", " "})
    public void testDecode_HexIsInvalid_ErrorIsThrow(String hex) {
        assertThrows(ValidateException.InvalidHex.class, () -> HEX.decode(hex));
    }

    private static Stream<Arguments> createBytesHex() {
        return Stream.of(Arguments.of(new byte[]{15, 13, 38, 47, 51, 0, 73, 80},
                "0F0D262F33004950"),
                Arguments.of(new byte[]{13, 33, 50, 7, 120}, "0D21320778"),
                Arguments.of(new byte[]{2, 4, 6, 8, 10}, "020406080A"));
    }

    private static Stream<byte[]> createInvalidBytes() {
        return Stream.of(null, new byte[]{});
    }

    private static Stream<Arguments> createHexBytes() {
        return Stream.of(Arguments.of("0F0D262F33004950", new byte[]{15, 13, 38, 47, 51, 0, 73, 80}),
                Arguments.of("0D21320778", new byte[]{13, 33, 50, 7, 120}),
                Arguments.of("020406080A", new byte[]{2, 4, 6, 8, 10}));
    }

}