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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

/**
 * Writes the M3 menus' cheat database ({@code cheat_EN.db}): an index of each game's text, then the text in GBK.
 * <pre>
 * ;Game ID: C32P BE547C63
 * ;Ace Attorney Investigations - Miles Edgeworth (Europe)
 *
 *
 * &lt;Miscellaneous Codes:&gt; = 1
 * &lt;Effect Comes After Saving and Restart:&gt;
 * [All Chapters Unlocked (Cleared)]
 * 02000000 00000001
 * </pre>
 * It writes the games as {@link M3Limits} leaves them, without their master codes, enabled flags or one at a time
 * settings, which the format has no place for.
 */
public class M3CheatsWriter implements CheatFileWriter {
	static final int INDEX_ENTRY_BYTES = 23;
	static final int MAX_GAMES = 0xFFFF;
	static final String SEPARATOR = "<----------:>";
	private static final String SEPARATOR_NOTE = "----------";

	private static final Charset GBK = Charset.forName("GBK");
	private static final String NEWLINE = "\r\n";

	/**
	 * Writes every game given, as {@link M3Limits#apply(List)} makes them, in their order.
	 */
	@Override
	public void write(CheatFile cheatFile, List<Game> given, OutputStream out) throws IOException {
		if (given.size() > MAX_GAMES) {
			throw new IllegalArgumentException("An M3 cheat file holds at most %d games".formatted(MAX_GAMES));
		}

		List<Game> games = M3Limits.held(given);
		// Each game's text is built twice, once to measure it for the index and again to write it, so the whole file is
		// never in memory.
		ByteBuffer index = ByteBuffer.allocate(2 + INDEX_ENTRY_BYTES * games.size()).order(ByteOrder.LITTLE_ENDIAN);
		index.putShort((short) games.size());
		long offset = index.capacity() + NEWLINE.length();

		for (Game game : games) {
			requireId(game);
			int length = text(game).length;
			index.put(game.getSerial().getBytes(StandardCharsets.US_ASCII));
			index.put("DHT".getBytes(StandardCharsets.US_ASCII));
			index.put(crc32(game).getBytes(StandardCharsets.US_ASCII));
			index.putInt((int) offset);
			index.putInt(length);
			offset += length;
		}

		out.write(index.array());
		out.write(NEWLINE.getBytes(StandardCharsets.US_ASCII));

		for (Game game : games) {
			out.write(text(game));
		}

		out.flush();
	}

	/**
	 * A code's title for its square brackets, its own {@code [} and {@code ]} made {@code <} and {@code >}.
	 */
	static String title(String title) {
		return Utf8Text.oneLine(title).replace('[', '<').replace(']', '>');
	}

	/**
	 * A note or folder title for its angle brackets, its own {@code <} and {@code >} made {@code [} and {@code ]}. A
	 * note of only the separator's dashes gets a space.
	 */
	static String note(String note) {
		String written = Utf8Text.oneLine(note).replace('<', '[').replace('>', ']');
		return written.equals(SEPARATOR_NOTE) ? written + " " : written;
	}

	/**
	 * Whether the file holds the text without a {@code ?}. Half width katakana count, since they're written full width.
	 */
	public static boolean holdsWhole(String text) {
		return text == null || gbkText(text).whole();
	}

	/**
	 * The text in GBK, as {@link #gbkText} makes it.
	 */
	static byte[] gbk(String text) {
		if (isAscii(text)) {
			return text.getBytes(StandardCharsets.US_ASCII);
		}

		return gbkText(text).text().getBytes(GBK);
	}

	/**
	 * The text GBK can hold: characters it lacks become their compatibility forms, such as half width katakana made
	 * full width, or {@code ?} when it lacks those too.
	 */
	private static GbkText gbkText(String text) {
		if (isAscii(text)) {
			return new GbkText(text, true);
		}

		CharsetEncoder encoder = GBK.newEncoder();
		StringBuilder kept = new StringBuilder(text.length());
		boolean whole = true;
		int i = 0;

		while (i < text.length()) {
			int end = i;

			// The characters GBK lacks
			while (end < text.length() && !encoder.canEncode(Character.toString(text.codePointAt(end)))) {
				end += Character.charCount(text.codePointAt(end));
			}

			if (end == i) {
				kept.appendCodePoint(text.codePointAt(i));
				i += Character.charCount(text.codePointAt(i));
				continue;
			}

			String compatible = Normalizer.normalize(text.substring(i, end), Normalizer.Form.NFKC);

			for (int codePoint : compatible.codePoints().toArray()) {
				String character = Character.toString(codePoint);

				if (encoder.canEncode(character)) {
					kept.append(character);
				} else {
					kept.append('?');
					whole = false;
				}
			}

			i = end;
		}

		return new GbkText(kept.toString(), whole);
	}

	private static boolean isAscii(String text) {
		return text.chars().allMatch(character -> character < 0x80);
	}

	/**
	 * A game's text: its header, then each top level code and folder, ending in a {@code \0}.
	 */
	private static byte[] text(Game game) {
		StringBuilder text = new StringBuilder();
		line(text, ";Game ID: %s %s".formatted(game.getSerial(), crc32(game)));
		line(text, ";" + Utf8Text.oneLine(game.getTitle()));
		line(text, "");
		line(text, "");

		Object previous = null;

		for (Object entry : game.getTopLevelEntriesInOrder()) {
			// A separator wherever a folder meets another entry, with a blank line after it when a folder ends there.
			if (previous instanceof Folder || (previous != null && entry instanceof Folder)) {
				line(text, SEPARATOR);

				if (previous instanceof Folder) {
					line(text, "");
				}
			}

			if (entry instanceof Folder folder) {
				writeFolder(text, folder);
			} else {
				writeCode(text, (Code) entry);
			}

			previous = entry;
		}

		byte[] encoded = gbk(text.toString());
		byte[] withEnd = new byte[encoded.length + 1];
		System.arraycopy(encoded, 0, withEnd, 0, encoded.length);
		return withEnd;
	}

	private static void writeFolder(StringBuilder text, Folder folder) {
		writeNote(text, folder.getComment());
		line(text, "<%s:> = %d".formatted(note(folder.getTitle()), folder.getCodes().size()));

		for (Code code : folder.getCodes()) {
			writeCode(text, code);
		}
	}

	private static void writeCode(StringBuilder text, Code code) {
		writeNote(text, code.getComment());
		line(text, "[" + title(code.getTitle()) + "]");

		for (String codeLine : code.getCodeLines()) {
			line(text, codeLine.toUpperCase(Locale.ROOT));
		}

		line(text, "");
	}

	private static void writeNote(StringBuilder text, String note) {
		String written = note(note);

		if (!written.isEmpty()) {
			line(text, "<" + written + ":>");
		}
	}

	private static void requireId(Game game) {
		if (game.getSerial() == null
				|| !game.getSerial().matches("[\\x21-\\x7E]{4}")
				|| game.getCrc32() == null
				|| !game.getCrc32().matches("[0-9A-Fa-f]{8}")
		) {
			throw new IllegalArgumentException(
					"Game \"%s\" needs a 4 character serial and an 8 digit CRC32".formatted(game.getTitle())
			);
		}
	}

	private static String crc32(Game game) {
		return game.getCrc32().toUpperCase(Locale.ROOT);
	}

	private static void line(StringBuilder text, String line) {
		text.append(line).append(NEWLINE);
	}

	/**
	 * Text as GBK holds it, and whether no character became {@code ?}.
	 */
	private record GbkText(String text, boolean whole) {
	}
}
