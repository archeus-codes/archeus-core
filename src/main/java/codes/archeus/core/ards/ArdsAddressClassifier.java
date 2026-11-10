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
package codes.archeus.core.ards;

import java.util.List;

/**
 * Finds the memory region an Action Replay instruction's address falls in.
 *
 * <p>The ranges come from GBATEK (<a href="https://problemkaputt.de/gbatek-ds-memory-maps.htm">DS
 * Memory Maps</a>, <a href="https://problemkaputt.de/gbatek-ds-i-o-maps.htm">DS I/O Maps</a>).
 */
public final class ArdsAddressClassifier {

	public enum Region {
		/**
		 * Main RAM: 4MB at {@code 0x02000000}, mirrored across the 16MB block up to {@code 0x02FFFFFF}. Most codes
		 * write here.
		 */
		MAIN_RAM,
		/**
		 * The Action Replay code handler's own addresses, such as {@code 023FE074} and {@code 023FE4D8}. Codes that run
		 * their own routine patch one to {@code bx r1}.
		 */
		KNOWN_CODE_HANDLER_PATCH,
		/**
		 * The ARM7's 64KB work RAM, mirrored across {@code 0x03800000}-{@code 0x03FFFFFF}. Datel's own codes use it as
		 * scratch space.
		 */
		ARM7_WRAM,
		/**
		 * The I/O registers, {@code 0x04000000}-{@code 0x0410FFFF}, such as {@code 04000130} (KEYINPUT), which button
		 * conditionals read.
		 */
		HARDWARE_IO,
		/**
		 * The Wi-Fi registers: WS0 at {@code 0x04800000}-{@code 0x04807FFF} and its WS1 mirror up to
		 * {@code 0x0480FFFF}.
		 */
		WIFI_IO,
		/** In no known region, so likely not a real address. */
		UNMAPPED
	}

	private record Range(long start, long endInclusive, Region region) {
		boolean contains(long address) {
			return address >= start && address <= endInclusive;
		}
	}

	/** In order: {@link #classify(long)} takes the first match, so the code handler comes before main RAM around it. */
	private static final List<Range> KNOWN_RANGES = List.of(
			new Range(0x023F_E000L, 0x023F_FFFFL, Region.KNOWN_CODE_HANDLER_PATCH),
			new Range(0x0200_0000L, 0x02FF_FFFFL, Region.MAIN_RAM),
			new Range(0x0380_0000L, 0x03FF_FFFFL, Region.ARM7_WRAM),
			new Range(0x0400_0000L, 0x0410_FFFFL, Region.HARDWARE_IO),
			new Range(0x0480_0000L, 0x0480_FFFFL, Region.WIFI_IO)
	);

	private ArdsAddressClassifier() {
	}

	public static Region classify(long address) {
		for (Range range : KNOWN_RANGES) {
			if (range.contains(address)) {
				return range.region();
			}
		}

		return Region.UNMAPPED;
	}
}
