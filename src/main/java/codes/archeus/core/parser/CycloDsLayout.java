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
 * The RC4 keys and container sizes of the two USAY based cheat databases, shared by their parsers and writers.
 *
 * <p>The keys and sizes were found by disassembling {@code r4cce.exe} in Ghidra. Each format has three 16 byte keys in
 * a row in the program's {@code .data} section. The first is the index key and the second the data key. These classes
 * don't use the third.
 */
enum CycloDsLayout {
	/** EDGE's {@code cheats.dat}, with its keys at file offset 0xed50e in {@code r4cce.exe} (address 0x4ee30e). */
	EDGE(
			new byte[] {
				(byte)0x08,(byte)0xd7,(byte)0xb9,(byte)0x2a,
				(byte)0x4c,(byte)0xc5,(byte)0x91,(byte)0xa7,
				(byte)0x0e,(byte)0x9b,(byte)0x24,(byte)0x9b,
				(byte)0xf7,(byte)0xdf,(byte)0xd7,(byte)0x44
			},
			new byte[] {
				(byte)0xc1,(byte)0x4e,(byte)0xb9,(byte)0xf9,
				(byte)0xb9,(byte)0x70,(byte)0xff,(byte)0xad,
				(byte)0xf1,(byte)0x97,(byte)0x1a,(byte)0x1c,
				(byte)0xe5,(byte)0x58,(byte)0xd9,(byte)0x3e
			},
			8,
			40
	),

	/** CycloDS Evolution's {@code user.evoCheats}, with its keys at file offset 0xed4de (address 0x4ee2de). */
	EVOLUTION(
			new byte[] {
				(byte)0xd3,(byte)0xbd,(byte)0x7c,(byte)0x3c,
				(byte)0x54,(byte)0x50,(byte)0x36,(byte)0x7e,
				(byte)0x40,(byte)0x74,(byte)0x38,(byte)0xa5,
				(byte)0x3e,(byte)0x1e,(byte)0xf1,(byte)0x40
			},
			new byte[] {
				(byte)0xf7,(byte)0x87,(byte)0x72,(byte)0x48,
				(byte)0xd3,(byte)0x4e,(byte)0x36,(byte)0x72,
				(byte)0x81,(byte)0x16,(byte)0x54,(byte)0xa9,
				(byte)0x24,(byte)0xdf,(byte)0x74,(byte)0xfd
			},
			32,
			48
	);

	private final byte[] indexKey;
	private final byte[] dataKey;
	private final int skip;
	private final int headerSize;

	CycloDsLayout(byte[] indexKey, byte[] dataKey, int skip, int headerSize) {
		this.indexKey = indexKey;
		this.dataKey = dataKey;
		this.skip = skip;
		this.headerSize = headerSize;
	}

	/**
	 * The RC4 key for the container header, the game index and each game's block header.
	 */
	byte[] indexKey() {
		return indexKey.clone();
	}

	/**
	 * The RC4 key for each game's compressed data and its enabled flags.
	 */
	byte[] dataKey() {
		return dataKey.clone();
	}

	/**
	 * The bytes before the encrypted container: the file magic.
	 */
	int skip() {
		return skip;
	}

	/**
	 * The size of the container header, before the game index entries.
	 */
	int headerSize() {
		return headerSize;
	}
}
