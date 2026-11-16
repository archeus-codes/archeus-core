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
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

/**
 * Checks a DeSmuME cheat file ({@code .dct}) by DeSmuME's own rules ({@code CHEATS::load}), failing wherever DeSmuME
 * would skip a code or quietly change it. It accepts only {@code AR} codes as {@link DesmumeWriter} writes them. The
 * game it returns has no title, serial, CRC32 or folders, only codes titled with their whole description.
 */
public class DesmumeParser {
	/**
	 * The most bytes before a line's {@code \n}, any {@code \r} included, that fit DeSmuME's 32,768 byte read.
	 */
	static final int MAX_LINE_BYTES = 32766;

	/** DOTALL, since a description runs past any lone {@code \r} to the {@code \n}. */
	private static final Pattern CODE = Pattern.compile(
			"AR ([01]) ([0-9A-F]{16}(?:,[0-9A-F]{16})*) ;(.*)",
			Pattern.DOTALL
	);
	private static final char BYTE_ORDER_MARK = '\uFEFF';

	/**
	 * Reads and checks the file, throwing {@link UsercheatParserException} wherever DeSmuME would skip or change a
	 * code.
	 */
	public Game parse(InputStream in) throws IOException {
		String text = Utf8Text.decode(in.readAllBytes(), "DeSmuME cheat file");
		Game game = new Game();
		// Lines end at \n alone, as DeSmuME reads them, not at a lone \r.
		String[] lines = text.split("\n", -1);

		for (int i = 0; i < lines.length; ++i) {
			int lineNumber = i + 1;

			if (Utf8Text.bytes(lines[i]) > MAX_LINE_BYTES) {
				throw error(lineNumber, "is over %d bytes, so DeSmuME reads it as two".formatted(MAX_LINE_BYTES));
			}

			String line = trimEnd(lines[i]);

			if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK) {
				// DeSmuME skips the whole line, which only matters if it's a code.
				if (mustBeCode(line.substring(1))) {
					throw error(lineNumber, line, "starts with a byte order mark, so DeSmuME skips it");
				}
			} else if (mustBeCode(line)) {
				game.getCodes().add(code(lineNumber, line, game.getCodes().size()));
			}
		}

		return game;
	}

	/**
	 * Whether the line has to be a code: anything but a blank line, a {@code ;} comment, {@code Name=} or
	 * {@code Serial=}. DeSmuME quietly skips other lines that aren't codes, so the parser refuses them.
	 */
	private static boolean mustBeCode(String line) {
		return !line.isEmpty()
				&& !line.startsWith(";")
				&& !line.regionMatches(true, 0, "name=", 0, 5)
				&& !line.regionMatches(true, 0, "serial=", 0, 7);
	}

	/**
	 * The line as DeSmuME's {@code trim} leaves it: trailing whitespace removed, leading kept.
	 */
	private static String trimEnd(String line) {
		int end = line.length();

		while (end > 0 && " \t\n\u000B\f\r".indexOf(line.charAt(end - 1)) >= 0) {
			--end;
		}

		return line.substring(0, end);
	}

	private static Code code(int lineNumber, String line, int sortOrder) {
		Matcher matcher = CODE.matcher(line);

		if (!matcher.matches()) {
			throw error(lineNumber, line, "isn't an Action Replay code DeSmuME reads as written");
		}

		String[] words = matcher.group(2).split(",");

		if (words.length > DesmumeWriter.MAX_LINES) {
			throw error(lineNumber, line, "has over %d lines".formatted(DesmumeWriter.MAX_LINES));
		}

		String description = matcher.group(3);

		if (Utf8Text.bytes(description) > DesmumeWriter.MAX_DESCRIPTION_BYTES) {
			throw error(lineNumber, line, "has a description DeSmuME would cut");
		}

		var code = new Code();
		code.setTitle(description);
		code.setEnabled(matcher.group(1).equals("1"));
		code.setSortOrder(sortOrder);

		for (String word : words) {
			code.getCodeLines().add(word.substring(0, 8) + " " + word.substring(8));
		}

		return code;
	}

	private static UsercheatParserException error(int lineNumber, String line, String problem) {
		String start = line.length() > 60 ? line.substring(0, 60) + "…" : line;
		return error(lineNumber, "\"%s\" %s".formatted(start, problem));
	}

	private static UsercheatParserException error(int lineNumber, String problem) {
		return new UsercheatParserException("DeSmuME cheat file line %d %s".formatted(lineNumber, problem));
	}
}
