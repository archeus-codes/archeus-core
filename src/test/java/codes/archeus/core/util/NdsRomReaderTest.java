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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NdsRomReaderTest {

	@Test
	void readSerial_validHeader_returnsGameCode() {
		var header = new byte[0x10];
		header[0x0C] = 'A';
		header[0x0D] = 'B';
		header[0x0E] = 'C';
		header[0x0F] = 'D';

		assertThat(NdsRomReader.readSerial(header)).isEqualTo("ABCD");
	}

	@Test
	void readSerial_headerTooShort_throwsIllegalArgumentException() {
		assertThatThrownBy(() -> NdsRomReader.readSerial(new byte[15]))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void readCrc32_validHeader_returnsXoredCrc32Hex() {
		var header = new byte[0x200];
		Arrays.fill(header, (byte) 0x42);

		var crc = new CRC32();
		crc.update(header, 0, 0x200);
		var expected = "%08X".formatted(crc.getValue() ^ 0xFFFFFFFFL);

		assertThat(NdsRomReader.readCrc32(header)).isEqualTo(expected);
	}
}
