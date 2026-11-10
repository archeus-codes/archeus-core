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

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public final class NdsRomReader {
	private NdsRomReader() {}

	/**
	 * Returns the 4-byte game code at header offset 0xC, decoded as ASCII.
	 *
	 * @param romBytes the raw ROM bytes; must be at least 16 (0x10) bytes long
	 * @return the game code as a 4-character ASCII string
	 * @throws IllegalArgumentException if {@code romBytes} is shorter than 16 bytes
	 */
	public static String readSerial(byte[] romBytes) {
		if (romBytes.length < 0x10) {
			throw new IllegalArgumentException("ROM header too short to contain a game code");
		}
		return new String(romBytes, 0xC, 4, StandardCharsets.US_ASCII);
	}

	/**
	 * Returns the game ID's CRC32: the CRC32 of the ROM's first 0x200 bytes, XORed with 0xFFFFFFFF.
	 *
	 * @param romBytes the raw ROM bytes; must be at least 0x200 bytes long
	 * @return the CRC32 value as an 8-character uppercase hex string
	 */
	public static String readCrc32(byte[] romBytes) {
		var crc = new CRC32();
		crc.update(romBytes, 0, 0x200);
		long value = crc.getValue() ^ 0xFFFFFFFFL;
		return "%08X".formatted(value);
	}
}
