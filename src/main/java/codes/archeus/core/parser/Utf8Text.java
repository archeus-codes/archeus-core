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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import codes.archeus.core.exception.UsercheatParserException;

/**
 * Text for the line based emulator formats, which read a title or comment on one line and limit it in bytes of UTF-8.
 */
final class Utf8Text {
	static final String CUT_MARK = "…";

	private static final Pattern LINE_BREAKS = Pattern.compile("\\s*[\\r\\n]+\\s*");

	private Utf8Text() {
	}

	/**
	 * The text stripped and on one line, each line break made one space. Null becomes empty.
	 */
	static String oneLine(String text) {
		return text == null ? "" : LINE_BREAKS.matcher(text.strip()).replaceAll(" ");
	}

	/**
	 * The text if it fits in {@code maxBytes} of UTF-8, otherwise as much as fits with the cut mark, ending at a space
	 * when one falls in the second half.
	 */
	static String cut(String text, int maxBytes) {
		if (bytes(text) <= maxBytes) {
			return text;
		}

		int end = prefixWithin(text, maxBytes - bytes(CUT_MARK));
		int space = text.lastIndexOf(' ', end);

		if (space > end / 2) {
			end = space;
		}

		return text.substring(0, end).stripTrailing() + CUT_MARK;
	}

	/**
	 * The length of the longest prefix of whole characters that fits in {@code maxBytes} of UTF-8.
	 */
	static int prefixWithin(String text, int maxBytes) {
		int used = 0;
		int index = 0;

		while (index < text.length()) {
			int codePoint = text.codePointAt(index);
			int size = size(codePoint);

			if (used + size > maxBytes) {
				break;
			}

			used += size;
			index += Character.charCount(codePoint);
		}

		return index;
	}

	/**
	 * A file's bytes as UTF-8, throwing {@link UsercheatParserException} with the line of any bytes that aren't.
	 *
	 * @param file what the bytes are, such as {@code DeSmuME cheat file}, for the error
	 */
	static String decode(byte[] bytes, String file) {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		ByteBuffer in = ByteBuffer.wrap(bytes);
		CharBuffer out = CharBuffer.allocate(bytes.length);
		CoderResult result = decoder.decode(in, out, true);

		if (result.isError()) {
			throw new UsercheatParserException("%s line %d isn't UTF-8".formatted(file, lineAt(bytes, in.position())));
		}

		decoder.flush(out);
		return out.flip().toString();
	}

	/**
	 * The text's length in bytes of UTF-8.
	 */
	static int bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

	/**
	 * A character's size in UTF-8. A lone surrogate is written as {@code ?}, one byte.
	 */
	private static int size(int codePoint) {
		if (codePoint < 0x80 || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
			return 1;
		}

		if (codePoint < 0x800) {
			return 2;
		}

		return codePoint < 0x10000 ? 3 : 4;
	}

	private static int lineAt(byte[] bytes, int offset) {
		int line = 1;

		for (int i = 0; i < offset; ++i) {
			if (bytes[i] == '\n') {
				++line;
			}
		}

		return line;
	}
}
