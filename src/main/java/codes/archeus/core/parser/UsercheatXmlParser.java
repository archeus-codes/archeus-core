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

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.util.CheatFileConstants;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Parses XML cheat files into a {@link ParseResult}, the inverse of {@link UsercheatXmlWriter}. Both
 * {@link XmlFormat}s are a {@code <codelist>}, so the caller says which one a file is: it decides whose master code the
 * {@code (M)} cheat holds. Files from older Archeus releases wrap the list in {@code <subscription>}; those are read as
 * Action Replay files whatever the format given.
 */
public class UsercheatXmlParser implements CheatFileParser {
	private static final String MASTER_PREFIX = "master ";
	private static final Pattern HEX_WORD = Pattern.compile("[0-9A-Fa-f]{8}");

	private final XmlFormat format;

	/**
	 * A parser for R4CCE files.
	 */
	public UsercheatXmlParser() {
		this(XmlFormat.R4CCE);
	}

	/**
	 * A parser for files in the given format.
	 */
	public UsercheatXmlParser(XmlFormat format) {
		this.format = format;
	}

	@Override
	public ParseResult parse(InputStream in) throws IOException {
		try {
			XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(in);
			return doParse(xml);
		} catch (XMLStreamException e) {
			throw new IOException("Failed to parse XML cheat file", e);
		}
	}

	private ParseResult doParse(XMLStreamReader xml) throws XMLStreamException {
		var cheatFile = new CheatFile();
		List<Game> games = new ArrayList<>();
		boolean actionReplay = format == XmlFormat.ACTION_REPLAY;

		while (xml.hasNext()) {
			if (xml.next() == START_ELEMENT) {
				switch (xml.getLocalName()) {
				case "subscription" -> actionReplay = true;
				case "codelist" -> parseCodelist(xml, cheatFile, games, actionReplay);
				}
			}
		}

		cheatFile.setGames(games);
		return new ParseResult(cheatFile, games);
	}

	private void parseCodelist(
			XMLStreamReader xml,
			CheatFile cheatFile,
			List<Game> games,
			boolean actionReplay
	) throws XMLStreamException {
		while (xml.hasNext()) {
			int event = xml.next();
			if (event == START_ELEMENT) {
				switch (xml.getLocalName()) {
				case "name" -> cheatFile.setTitle(readText(xml));
				case "game" -> games.add(parseGame(xml, actionReplay));
				}
			} else if (event == END_ELEMENT && "codelist".equals(xml.getLocalName())) {
				return;
			}
		}
	}

	private Game parseGame(XMLStreamReader xml, boolean actionReplay) throws XMLStreamException {
		var game = new Game();

		while (xml.hasNext()) {
			int event = xml.next();
			if (event == START_ELEMENT) {
				switch (xml.getLocalName()) {
				case "name" -> game.setTitle(readText(xml));
				case "gameid" -> applyGameId(game, readText(xml));
				case "date" -> readText(xml); // not kept
				case "cheat" -> applyGameCheat(xml, game, actionReplay);
				case "folder" -> game.getFolders().add(parseFolder(xml));
				}
			} else if (event == END_ELEMENT && "game".equals(xml.getLocalName())) {
				return game;
			}
		}

		return game;
	}

	private void applyGameId(Game game, String gameId) {
		String[] parts = gameId.trim().split(" ", 2);

		if (parts.length == 2) {
			game.setSerial(parts[0]);
			game.setCrc32(parts[1]);
		}
	}

	/**
	 * Adds a top level cheat to the game. The master code cheat holds the flash cart master code in an R4CCE file, and
	 * the Action Replay one in an Action Replay file, which becomes a top level "Master Code" code.
	 */
	private void applyGameCheat(XMLStreamReader xml, Game game, boolean actionReplay) throws XMLStreamException {
		String name = "";
		String note = "";
		String codesText = "";

		while (xml.hasNext()) {
			int event = xml.next();
			if (event == START_ELEMENT) {
				switch (xml.getLocalName()) {
				case "name" -> name = readText(xml);
				case "note" -> note = readText(xml);
				case "codes" -> codesText = readText(xml);
				}
			} else if (event == END_ELEMENT && "cheat".equals(xml.getLocalName())) {
				break;
			}
		}

		if (!isMasterCode(name, codesText)) {
			game.getCodes().add(code(name, note, parseCodeLines(codesText)));
		} else if (actionReplay) {
			game.getCodes().add(code(
					CheatFileConstants.ACTION_REPLAY_MASTER_CODE_TITLE,
					note,
					parseCodeLines(withoutMasterPrefix(codesText))
			));
		} else {
			game.setMasterCode(parseFlashCartMasterCode(codesText));
		}
	}

	private static Code code(String title, String comment, List<String> lines) {
		var code = new Code();
		code.setTitle(title);
		code.setComment(comment);
		code.setCodeLines(lines);
		return code;
	}

	private Folder parseFolder(XMLStreamReader xml) throws XMLStreamException {
		var folder = new Folder();

		while (xml.hasNext()) {
			int event = xml.next();
			if (event == START_ELEMENT) {
				switch (xml.getLocalName()) {
				case "name" -> folder.setTitle(readText(xml));
				case "note" -> folder.setComment(readText(xml));
				case "allowedon" -> folder.setModifier("1".equals(readText(xml)));
				case "cheat" -> folder.getCodes().add(parseCheat(xml));
				}
			} else if (event == END_ELEMENT && "folder".equals(xml.getLocalName())) {
				return folder;
			}
		}

		return folder;
	}

	private Code parseCheat(XMLStreamReader xml) throws XMLStreamException {
		var code = new Code();

		while (xml.hasNext()) {
			int event = xml.next();
			if (event == START_ELEMENT) {
				switch (xml.getLocalName()) {
				case "name" -> code.setTitle(readText(xml));
				case "note" -> code.setComment(readText(xml));
				case "codes" -> code.setCodeLines(parseCodeLines(readText(xml)));
				}
			} else if (event == END_ELEMENT && "cheat".equals(xml.getLocalName())) {
				return code;
			}
		}

		return code;
	}

	/**
	 * The code's lines, as {@code AAAAAAAA VVVVVVVV} pairs.
	 */
	private List<String> parseCodeLines(String text) {
		if (text == null || text.isBlank()) {
			return new ArrayList<>();
		}

		String[] tokens = text.trim().split("\\s+");
		var lines = new ArrayList<String>(tokens.length / 2);

		for (int i = 0; i + 1 < tokens.length; i += 2) {
			lines.add(tokens[i] + " " + tokens[i + 1]);
		}

		return lines;
	}

	/**
	 * Whether a top level cheat holds the game's master code: written as {@code master} and its values, or a
	 * {@code (M)MasterCode} cheat holding a plain flash cart master code. Any other cheat is a code, whatever its name,
	 * so an "(M)" code such as a must be on code stays a code.
	 */
	private static boolean isMasterCode(String name, String codesText) {
		return hasMasterPrefix(codesText)
				|| CheatFileConstants.XML_MASTER_CHEAT_NAME.equals(name) && isPlainFlashCartMasterCode(codesText);
	}

	private static boolean hasMasterPrefix(String codesText) {
		return codesText != null && codesText.trim().regionMatches(true, 0, MASTER_PREFIX, 0, MASTER_PREFIX.length());
	}

	private static boolean isPlainFlashCartMasterCode(String codesText) {
		String[] tokens = codesText.trim().split("\\s+");
		return tokens.length == CheatFileConstants.MASTER_CODE_WORDS
				&& Arrays.stream(tokens).allMatch(token -> HEX_WORD.matcher(token).matches());
	}

	private static String withoutMasterPrefix(String codesText) {
		return hasMasterPrefix(codesText) ? codesText.trim().substring(MASTER_PREFIX.length()) : codesText;
	}

	/**
	 * The 8 values of a flash cart master code. R4CCE writes a zero spacer pair before each data pair and a trailing
	 * pair, so after {@code master} the values are at positions 2–3, 6–7, 10–11 and 14–15 (0-based). A plain one is
	 * taken as it is.
	 */
	private static List<String> parseFlashCartMasterCode(String codesText) {
		String[] tokens = withoutMasterPrefix(codesText).trim().split("\\s+");

		if (!hasMasterPrefix(codesText)) {
			return new ArrayList<>(List.of(tokens));
		}

		int[] dataIndices = {2, 3, 6, 7, 10, 11, 14, 15};
		var result = new ArrayList<String>(CheatFileConstants.MASTER_CODE_WORDS);

		for (int idx : dataIndices) {
			result.add(idx < tokens.length ? tokens[idx] : "00000000");
		}

		return result;
	}

	private String readText(XMLStreamReader xml) throws XMLStreamException {
		var sb = new StringBuilder();

		while (xml.hasNext()) {
			int event = xml.next();
			if (event == CHARACTERS) {
				sb.append(xml.getText());
			} else if (event == END_ELEMENT) {
				break;
			}
		}

		return sb.toString();
	}
}
