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

import java.util.Optional;

/**
 * The operation a Nitrohax {@code D400000x} data operation applies to the stored register.
 */
public enum DataOperator {
	ADD,
	OR,
	AND,
	XOR,
	SHIFT_LEFT,
	SHIFT_RIGHT,
	ROTATE_RIGHT,
	ARITHMETIC_SHIFT_RIGHT,
	MULTIPLY;

	/**
	 * The operator for a {@code D400000x} instruction's {@code x}, or empty for one it doesn't know.
	 */
	public static Optional<DataOperator> fromSelector(int selector) {
		return switch (selector) {
			case 0x0 -> Optional.of(ADD);
			case 0x1 -> Optional.of(OR);
			case 0x2 -> Optional.of(AND);
			case 0x3 -> Optional.of(XOR);
			case 0x4 -> Optional.of(SHIFT_LEFT);
			case 0x5 -> Optional.of(SHIFT_RIGHT);
			case 0x6 -> Optional.of(ROTATE_RIGHT);
			case 0x7 -> Optional.of(ARITHMETIC_SHIFT_RIGHT);
			case 0x8 -> Optional.of(MULTIPLY);
			default -> Optional.empty();
		};
	}
}
