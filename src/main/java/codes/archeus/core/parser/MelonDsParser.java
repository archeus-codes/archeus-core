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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

/**
 * Checks a melonDS cheat file ({@code .mch}) that Archeus wrote, by melonDS's own rules ({@code ARCodeFile.cpp}), failing
 * wherever melonDS would refuse the file or quietly change it. It's stricter than melonDS, refusing some lines it
 * accepts (such as short hex groups), so it isn't a reader for any {@code .mch}. The file has no game title, serial or
 * CRC32, so the game it returns has only folders and codes.
 */
public class MelonDsParser {
	/** melonDS reads lines into 1,024 bytes, newline and terminator included. */
	static final int MAX_LINE_BYTES = 1022;

	/** melonDS reads a {@code CAT} line as the newer form when these are its fourth to sixth characters. */
	private static final Pattern NEW_CATEGORY_START = Pattern.compile("... [01] .*");
	/** melonDS finds these keywords in any case, but reads them in capitals only. */
	private static final Pattern CATEGORY = Pattern.compile("CAT ([01]) (\\S.*)");
	private static final Pattern OLD_CATEGORY = Pattern.compile("CAT (\\S.*)");
	private static final Pattern CODE = Pattern.compile("CODE (-?\\d+) (\\S.*)");
	private static final Pattern DESCRIPTION = Pattern.compile("DESC (.*)");
	private static final Pattern DATA = Pattern.compile("[0-9A-Fa-f]{8} [0-9A-Fa-f]{8}");

	/**
	 * Reads and checks the file, throwing {@link UsercheatParserException} wherever melonDS would refuse or change it.
	 */
	public Game parse(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		Game game = new Reading().read(reader);
		requireOneCodeOnPerOneChoiceFolder(game);
		return game;
	}

	/**
	 * melonDS switches off all but the first enabled code in a one at a time folder, so more than one is a change.
	 */
	private static void requireOneCodeOnPerOneChoiceFolder(Game game) {
		for (Folder folder : game.getFolders()) {
			if (folder.isModifier() && folder.getCodes().stream().filter(Code::isEnabled).count() > 1) {
				throw new UsercheatParserException(
						"melonDS cheat file folder \"" + folder.getTitle()
								+ "\" is one at a time but has several codes on"
				);
			}
		}
	}

	private static int bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

	/**
	 * One file being read: the game so far and the reader's position in it. The {@code enter} methods set the fields that
	 * change together.
	 */
	private static final class Reading {
		private final Game game = new Game();
		/** Where codes go; null for the top level. */
		private Folder folder;
		private boolean inCategory;
		private Folder lastFolder;
		private Code lastCode;
		/** Shared by top level codes and folders, as in the other parsers. */
		private int nextSortOrder;
		private int lineNumber;

		Game read(BufferedReader reader) throws IOException {
			String line;

			while ((line = reader.readLine()) != null) {
				++lineNumber;

				if (bytes(line) > MAX_LINE_BYTES) {
					throw error("is longer than melonDS reads as one line");
				}

				String text = line.stripLeading();

				if (!text.isEmpty() && !text.startsWith("#")) {
					readLine(text);
				}
			}

			return game;
		}

		private void readLine(String text) {
			String keyword = text.toUpperCase(Locale.ROOT);

			if (keyword.startsWith("ROOT")) {
				enterRoot();
			} else if (keyword.startsWith("CAT")) {
				readCategory(text);
			} else if (keyword.startsWith("CODE")) {
				readCode(text);
			} else if (keyword.startsWith("DESC")) {
				readDescription(text);
			} else {
				readData(text);
			}
		}

		private void readCategory(String text) {
			Matcher category = CATEGORY.matcher(text);
			Matcher oldCategory = OLD_CATEGORY.matcher(text);
			Folder newFolder = new Folder();

			if (NEW_CATEGORY_START.matcher(text).matches()) {
				if (!category.matches()) {
					throw error("is a malformed CAT line");
				}
				newFolder.setModifier(category.group(1).equals("1"));
				newFolder.setTitle(checkedName(category.group(2)));
			} else if (oldCategory.matches()) {
				newFolder.setTitle(checkedName(oldCategory.group(1)));
			} else {
				throw error("is a malformed CAT line");
			}

			newFolder.setSortOrder(nextSortOrder++);
			game.getFolders().add(newFolder);
			enterFolder(newFolder);
		}

		private void readCode(String text) {
			Matcher matcher = CODE.matcher(text);

			if (!matcher.matches()) {
				throw error("is a malformed CODE line");
			}

			if (!inCategory) {
				throw error("is a CODE line with no CAT or ROOT before it");
			}

			Code code = new Code();
			code.setEnabled(!matcher.group(1).matches("-?0+"));
			code.setTitle(checkedName(matcher.group(2)));

			if (folder == null) {
				code.setSortOrder(nextSortOrder++);
				game.getCodes().add(code);
			} else {
				code.setSortOrder(folder.getCodes().size());
				folder.getCodes().add(code);
			}

			enterCode(code);
		}

		private void readDescription(String text) {
			Matcher matcher = DESCRIPTION.matcher(text);

			if (!matcher.matches()) {
				throw error("is a DESC line melonDS ignores");
			}

			String description = matcher.group(1);

			if (description.isEmpty()) {
				return;
			}

			if (bytes(description) > MelonDsWriter.MAX_DESCRIPTION_BYTES) {
				throw error("has a description melonDS would cut");
			}

			if (lastCode != null) {
				lastCode.setComment(description);
			} else if (lastFolder != null) {
				lastFolder.setComment(description);
			} else {
				throw error("is a DESC line that belongs to nothing");
			}
		}

		private void readData(String text) {
			String data = text.strip();

			if (!DATA.matcher(data).matches()) {
				throw error("is a malformed data line");
			}

			if (lastCode == null) {
				throw error("is a data line with no CODE before it");
			}

			lastCode.getCodeLines().add(data.toUpperCase(Locale.ROOT));
		}

		private void enterRoot() {
			folder = null;
			inCategory = true;
			lastFolder = null;
			lastCode = null;
		}

		private void enterFolder(Folder newFolder) {
			folder = newFolder;
			inCategory = true;
			lastFolder = newFolder;
			lastCode = null;
		}

		private void enterCode(Code code) {
			lastCode = code;
			lastFolder = null;
		}

		private String checkedName(String name) {
			if (bytes(name) > MelonDsWriter.MAX_NAME_BYTES) {
				throw error("has a name melonDS would cut");
			}

			return name;
		}

		private UsercheatParserException error(String problem) {
			return new UsercheatParserException("melonDS cheat file line " + lineNumber + " " + problem);
		}
	}
}
