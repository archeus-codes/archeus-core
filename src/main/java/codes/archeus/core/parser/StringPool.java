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

import codes.archeus.core.util.ByteUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Shares one {@link String} per distinct value, since most code lines, titles and comments in a cheat database are
 * repeats. Create a new one for each file parsed, so it's dropped when that parse returns. Not thread safe.
 */
final class StringPool {
	private final LongTable lines = new LongTable();
	private final LongTable words = new LongTable();
	private final Map<String, String> texts = new HashMap<>();

	/**
	 * The code line {@code "AAAAAAAA VVVVVVVV"} for two words, built only the first time the pair is seen.
	 */
	String line(int address, int value) {
		return lines.get(
				((long) address << 32) | (value & 0xFFFFFFFFL),
				key -> ByteUtils.toHex((int) (key >>> 32)) + " " + ByteUtils.toHex((int) key)
		);
	}

	/**
	 * The code line for the next two words in {@code buf}, read address first.
	 */
	String line(ByteBuffer buf) {
		int address = buf.getInt();
		return line(address, buf.getInt());
	}

	/**
	 * A word as 8 uppercase hex digits, built only the first time it's seen.
	 */
	String word(int word) {
		return words.get(word & 0xFFFFFFFFL, key -> ByteUtils.toHex((int) key));
	}

	/**
	 * Reads UTF-8 bytes from {@code buf} up to a zero byte, which it consumes, and shares the text as {@link #text}
	 * does.
	 */
	String zeroTerminated(ByteBuffer buf) {
		return text(decodeZeroTerminated(buf));
	}

	/**
	 * Reads UTF-8 bytes from {@code buf} up to a zero byte, which it consumes, without sharing the text.
	 */
	static String decodeZeroTerminated(ByteBuffer buf) {
		int start = buf.position();
		int end = start;

		while (buf.get() != 0) {
			++end;
		}

		return new String(buf.array(), buf.arrayOffset() + start, end - start, StandardCharsets.UTF_8);
	}

	/**
	 * The first string equal to {@code text} that this pool was given.
	 */
	String text(String text) {
		return text.isEmpty() ? "" : texts.computeIfAbsent(text, t -> t);
	}

	/**
	 * A map from a {@code long} key to its string, in parallel arrays so no key is boxed. Uses Fibonacci hashing with
	 * linear probing, and a null value marks an empty slot.
	 */
	private static final class LongTable {
		/** 2^64 divided by the golden ratio. */
		private static final long GOLDEN_RATIO = 0x9E3779B97F4A7C15L;

		private long[] keys = new long[1 << 10];
		private String[] values = new String[1 << 10];
		private int shift = Long.SIZE - 10;
		private int size;

		String get(long key, LongFunction<String> make) {
			int mask = values.length - 1;
			int i = slot(key);

			while (values[i] != null) {
				if (keys[i] == key) {
					return values[i];
				}
				i = (i + 1) & mask;
			}

			String value = make.apply(key);
			keys[i] = key;
			values[i] = value;

			if (++size * 2 > values.length) {
				grow();
			}

			return value;
		}

		private int slot(long key) {
			return (int) ((key * GOLDEN_RATIO) >>> shift);
		}

		private void grow() {
			long[] oldKeys = keys;
			String[] oldValues = values;
			keys = new long[oldKeys.length * 2];
			values = new String[oldValues.length * 2];
			--shift;
			int mask = values.length - 1;

			for (int j = 0; j < oldValues.length; ++j) {
				if (oldValues[j] != null) {
					int i = slot(oldKeys[j]);
					while (values[i] != null) {
						i = (i + 1) & mask;
					}
					keys[i] = oldKeys[j];
					values[i] = oldValues[j];
				}
			}
		}
	}
}
