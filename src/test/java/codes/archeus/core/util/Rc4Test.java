/*
 * Copyright 2021-2026 Deiondre Andrews (Demonic722)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package codes.archeus.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class Rc4Test {
	/**
	 * Well known RC4 known answer test vectors, independently recomputed before use here rather
	 * than trusted from memory.
	 */
	@Test
	void decrypt_knownAnswerVectors_matchTheExpectedOutput() {
		assertThat(decryptToHex("Key", "Plaintext")).isEqualTo("BBF316E8D940AF0AD3");
		assertThat(decryptToHex("Wiki", "pedia")).isEqualTo("1021BF0420");
		assertThat(decryptToHex("Secret", "Attack at dawn")).isEqualTo("45A01F645FC35B383552544B9BF5");
	}

	@Test
	void decrypt_isSymmetric_applyingTwiceWithTheSameKeyRecoversTheOriginal() {
		byte[] key = "Key".getBytes(StandardCharsets.US_ASCII);
		byte[] plaintext = "Plaintext".getBytes(StandardCharsets.US_ASCII);

		byte[] ciphertext = Rc4.decrypt(key, plaintext);
		byte[] recovered = Rc4.decrypt(key, ciphertext);

		assertThat(recovered).isEqualTo(plaintext);
	}

	@Test
	void decrypt_twoCalls_eachStartTheKeyStreamAgain() {
		// The class Javadoc documents this as intentional: the S-box resets on every call, so two
		// separate calls with the same key/data must produce identical output rather than the second
		// call continuing the key stream where the first left off.
		byte[] key = "Key".getBytes(StandardCharsets.US_ASCII);
		byte[] data = "Plaintext".getBytes(StandardCharsets.US_ASCII);

		byte[] first = Rc4.decrypt(key, data);
		byte[] second = Rc4.decrypt(key, data);

		assertThat(second).isEqualTo(first);
	}

	@Test
	void decrypt_differentKeys_giveDifferentOutput() {
		byte[] data = "Plaintext".getBytes(StandardCharsets.US_ASCII);

		byte[] first = Rc4.decrypt("KeyOne".getBytes(StandardCharsets.US_ASCII), data);
		byte[] second = Rc4.decrypt("KeyTwo".getBytes(StandardCharsets.US_ASCII), data);

		assertThat(first).isNotEqualTo(second);
	}

	private String decryptToHex(String key, String plaintext) {
		byte[] result = Rc4.decrypt(key.getBytes(StandardCharsets.US_ASCII), plaintext.getBytes(StandardCharsets.US_ASCII));
		return HexFormat.of().withUpperCase().formatHex(result);
	}
}
