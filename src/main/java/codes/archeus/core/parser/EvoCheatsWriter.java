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
package codes.archeus.core.parser;

/**
 * Writes CycloDS Evolution's {@code user.evoCheats}.
 */
public class EvoCheatsWriter extends AbstractCycloDsWriter {

	private static final byte[] FILE_MAGIC = {
		'E','v','o','l','u','t','i','o','n','C','h','e','a','t','s', 0x00,  // "EvolutionCheats\0"
		0x01, 0x00, 0x00, 0x00,                                              // version metadata
		0x00, 0x00, 0x00, 0x00,
		0x00, 0x00, 0x00, 0x00,
		0x00, 0x00, 0x00, 0x00
	};

	private static final byte[] CONTAINER_MAGIC = {
		0x01, 0x45, 0x56, 0x4f  // "\x01EVO"
	};

	public EvoCheatsWriter() {
		super(CycloDsLayout.EVOLUTION);
	}

	@Override
	protected byte[] fileMagic() {
		return FILE_MAGIC;
	}

	@Override
	protected byte[] containerMagic() {
		return CONTAINER_MAGIC;
	}

	@Override
	protected int containerVersion() {
		return 1;
	}
}
