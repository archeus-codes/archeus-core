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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

/**
 * Checks a RetroArch cheat file ({@code .cht}) written by {@link RetroArchWriter}, failing on anything the writer
 * doesn't produce, such as a wrong count or entries out of order. It isn't a general {@code .cht} reader. The game it
 * returns holds only folders and codes: its title, serial and CRC32 are in the file's name, not its contents.
 */
public class RetroArchParser {
	private static final Pattern COUNT = Pattern.compile("cheats = (\\d+)");
	private static final Pattern DESCRIPTION = Pattern.compile("cheat(\\d+)_desc = \"([^\"]*[^\"\\\\])\"");
	private static final Pattern CODE = Pattern.compile("cheat(\\d+)_code = \"([0-9A-F]{8}(?:\\+[0-9A-F]{8})*)\"");
	private static final Pattern ENABLE = Pattern.compile("cheat(\\d+)_enable = (true|false)");

	/**
	 * Reads and checks the file, throwing {@link UsercheatParserException} on anything {@link RetroArchWriter} doesn't
	 * write.
	 */
	public Game parse(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		List<String> lines = new ArrayList<>();
		String line;

		while ((line = reader.readLine()) != null) {
			if (!line.isBlank()) {
				lines.add(line);
			}
		}

		return new Reading(lines).read();
	}

	/**
	 * The file's lines without blanks, and the position in them.
	 */
	private static final class Reading {
		private final List<String> lines;
		private final Game game = new Game();
		/** Where codes go; null for the top level. */
		private Folder folder;
		private int index;

		Reading(List<String> lines) {
			this.lines = lines;
		}

		Game read() {
			int count = Integer.parseInt(next(COUNT, "isn't the cheat count").group(1));

			for (int entry = 0; entry < count; ++entry) {
				readEntry(entry);
			}

			if (index < lines.size()) {
				throw error("follows the last of the file's " + count + " cheats");
			}

			return game;
		}

		private void readEntry(int entry) {
			String title = numbered(next(DESCRIPTION, "isn't a description RetroArch reads whole"), entry).group(2);

			if (index < lines.size() && lines.get(index).startsWith("cheat" + entry + "_code")) {
				String code = numbered(next(CODE, "isn't a code of 8-digit words"), entry).group(2);
				boolean enabled = numbered(next(ENABLE, "isn't the code's enable line"), entry)
						.group(2)
						.equals("true");
				addCode(title, code, enabled);
			} else {
				enterFolder(title);
			}
		}

		private void addCode(String title, String joined, boolean enabled) {
			String[] words = joined.split("\\+");

			if (words.length % 2 != 0) {
				throw new UsercheatParserException(
						"RetroArch cheat file code \"" + title + "\" has an odd number of words"
				);
			}

			var code = new Code();
			code.setTitle(title);
			code.setEnabled(enabled);

			for (int i = 0; i < words.length; i += 2) {
				code.getCodeLines().add(words[i] + " " + words[i + 1]);
			}

			if (folder == null) {
				code.setSortOrder(game.getCodes().size());
				game.getCodes().add(code);
			} else {
				code.setSortOrder(folder.getCodes().size());
				folder.getCodes().add(code);
			}
		}

		private void enterFolder(String title) {
			folder = new Folder();
			folder.setTitle(title);
			folder.setSortOrder(game.getCodes().size() + game.getFolders().size());
			game.getFolders().add(folder);
		}

		private Matcher next(Pattern pattern, String problem) {
			if (index >= lines.size()) {
				throw new UsercheatParserException("RetroArch cheat file ends where a line was expected");
			}

			Matcher matcher = pattern.matcher(lines.get(index));

			if (!matcher.matches()) {
				throw error(problem);
			}

			++index;
			return matcher;
		}

		private Matcher numbered(Matcher matcher, int entry) {
			if (Integer.parseInt(matcher.group(1)) != entry) {
				throw new UsercheatParserException(
						"RetroArch cheat file line \"" + lines.get(index - 1) + "\" isn't cheat " + entry
				);
			}

			return matcher;
		}

		private UsercheatParserException error(String problem) {
			return new UsercheatParserException("RetroArch cheat file line \"" + lines.get(index) + "\" " + problem);
		}
	}
}
