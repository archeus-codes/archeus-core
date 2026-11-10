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

/**
 * The RC4 stream cipher. Each {@link #decrypt} call starts the key stream again, since the EDGE and CycloDS formats
 * encrypt each part on its own from the start of the stream.
 */
public final class Rc4 {

	private Rc4() {
	}

	/**
	 * XORs the data with the key's stream and returns the result. RC4 is symmetric, so this also encrypts.
	 */
	public static byte[] decrypt(byte[] key, byte[] data) {
		int[] S = new int[256];

		for (int i = 0; i < 256; ++i) {
			S[i] = i;
		}

		int j = 0;

		for (int i = 0; i < 256; ++i) {
			j = (j + S[i] + (key[i % key.length] & 0xFF)) & 0xFF;
			int tmp = S[i];
			S[i] = S[j];
			S[j] = tmp;
		}

		byte[] out = new byte[data.length];

		int i = 0;
		j = 0;

		for (int n = 0; n < data.length; ++n) {
			i = (i + 1) & 0xFF;
			j = (j + S[i]) & 0xFF;
			int tmp = S[i];
			S[i] = S[j];
			S[j] = tmp;
			out[n] = (byte) (data[n] ^ S[(S[i] + S[j]) & 0xFF]);
		}

		return out;
	}
}
