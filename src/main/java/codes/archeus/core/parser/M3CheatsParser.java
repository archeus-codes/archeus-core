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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

/**
 * Checks an M3 cheat database ({@code cheat_EN.db}) as {@link M3CheatsWriter} writes it, failing on anything else.
 * Titles and notes are kept as written, so a code titled {@code [L+R]All Cars} reads back as {@code <L+R>All Cars}.
 * The file doesn't show which bracket the writer swapped, so the swap can't be undone. A folder ends only at its count,
 * so a count too low reads its last codes as top level codes.
 */
public class M3CheatsParser implements CheatFileParser {
	private static final Charset GBK = Charset.forName("GBK");
	private static final Pattern GAME_ID = Pattern.compile(";Game ID: (\\S{4}) ([0-9A-F]{8})");
	/** At most 5 digits, so a longer count fails as a bad line rather than overflowing {@code parseInt}. */
	private static final Pattern FOLDER = Pattern.compile("<(.*):> = (\\d{1,5})");
	private static final Pattern NOTE = Pattern.compile("<(.*):>");
	private static final Pattern CODE = Pattern.compile("\\[(.*)]");
	private static final Pattern CODE_LINE = Pattern.compile("[0-9A-F]{8} [0-9A-F]{8}");

	/**
	 * Reads and checks the file, throwing {@link UsercheatParserException} on anything the writer doesn't write.
	 */
	@Override
	public ParseResult parse(InputStream in) throws IOException {
		byte[] file = in.readAllBytes();

		if (file.length < 2) {
			throw new UsercheatParserException("M3 cheat file is too short to hold its game count");
		}

		ByteBuffer bytes = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
		int count = Short.toUnsignedInt(bytes.getShort(0));
		int indexEnd = 2 + M3CheatsWriter.INDEX_ENTRY_BYTES * count;

		if (file.length < indexEnd + 2 || file[indexEnd] != '\r' || file[indexEnd + 1] != '\n') {
			throw new UsercheatParserException(
					"M3 cheat file doesn't hold its index of %d games and the line after it".formatted(count)
			);
		}

		List<Game> games = new ArrayList<>(count);
		var pool = new StringPool();
		long expectedOffset = indexEnd + 2L;

		for (int i = 0; i < count; ++i) {
			int entry = 2 + M3CheatsWriter.INDEX_ENTRY_BYTES * i;
			String serial = new String(file, entry, 4, StandardCharsets.US_ASCII);
			String tag = new String(file, entry + 4, 3, StandardCharsets.US_ASCII);
			String crc32 = new String(file, entry + 7, 8, StandardCharsets.US_ASCII);
			long offset = Integer.toUnsignedLong(bytes.getInt(entry + 15));
			long length = Integer.toUnsignedLong(bytes.getInt(entry + 19));

			if (!tag.equals("DHT") || !crc32.matches("[0-9A-F]{8}")) {
				throw new UsercheatParserException("M3 cheat file index entry %d is malformed".formatted(i));
			}

			if (offset != expectedOffset || length == 0 || offset + length > file.length) {
				throw new UsercheatParserException(
						"M3 cheat file game %s %s doesn't follow the game before it".formatted(serial, crc32)
				);
			}

			games.add(new GameReader(serial, crc32, text(file, (int) offset, (int) length), pool).read());
			expectedOffset = offset + length;
		}

		if (expectedOffset != file.length) {
			throw new UsercheatParserException("M3 cheat file has bytes after its last game");
		}

		var cheatFile = new CheatFile();
		cheatFile.setGames(games);
		return new ParseResult(cheatFile, games);
	}

	/**
	 * A game's text, which must end in {@code \0} and be GBK, as lines without their CRLF.
	 */
	private static List<String> text(byte[] file, int offset, int length) {
		if (file[offset + length - 1] != 0) {
			throw new UsercheatParserException("M3 cheat file game at %d doesn't end in \\0".formatted(offset));
		}

		String text;

		try {
			text = GBK.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(file, offset, length - 1))
					.toString();
		} catch (CharacterCodingException e) {
			throw new UsercheatParserException("M3 cheat file game at %d isn't GBK".formatted(offset), e);
		}

		if (!text.endsWith("\r\n")) {
			throw new UsercheatParserException(
					"M3 cheat file game at %d doesn't end its last line".formatted(offset)
			);
		}

		return Arrays.asList(text.substring(0, text.length() - 2).split("\r\n", -1));
	}

	/**
	 * One game's lines and the position in them.
	 */
	private static final class GameReader {
		private final String serial;
		private final String crc32;
		private final List<String> lines;
		private final Game game = new Game();
		/** Shares repeated titles, notes and code lines across the whole file. */
		private final StringPool pool;
		private int index;

		GameReader(String serial, String crc32, List<String> lines, StringPool pool) {
			this.serial = serial;
			this.crc32 = crc32;
			this.lines = lines;
			this.pool = pool;
		}

		Game read() {
			Matcher id = matching(GAME_ID, "isn't the game's ID line");

			if (!id.group(1).equals(serial) || !id.group(2).equals(crc32)) {
				throw error("names a different game from the index");
			}

			String title = next();

			if (!title.startsWith(";")) {
				throw error("isn't the game's title");
			}

			// The format has no game flag, so every game is on, as in EDGE and CycloDS.
			game.setEnabled(true);
			game.setSerial(serial);
			game.setCrc32(crc32);
			game.setTitle(title.substring(1));
			blank();
			blank();

			Object previous = null;

			while (index < lines.size()) {
				previous = readEntry(previous);
			}

			return game;
		}

		/**
		 * Reads a top level code or a folder with any separator before it, returning it. A separator comes wherever a
		 * folder meets another entry.
		 */
		private Object readEntry(Object previous) {
			boolean separated = lines.get(index).equals(M3CheatsWriter.SEPARATOR);

			if (separated) {
				if (previous == null) {
					throw error("separates nothing from the entry after it");
				}

				++index;

				if (previous instanceof Folder) {
					blank();
				}
			}

			String note = note();
			Matcher folder = FOLDER.matcher(current());

			if (folder.matches()) {
				if (previous != null && !separated) {
					throw error("is a folder without the separator before it");
				}

				++index;
				return readFolder(pool.text(folder.group(1)), Integer.parseInt(folder.group(2)), note);
			}

			if (previous instanceof Folder && !separated) {
				throw error("is a code after a folder without the separator");
			}

			if (previous instanceof Code && separated) {
				throw error("follows a code's separator but isn't a folder");
			}

			Code code = readCode(note);
			code.setSortOrder(game.getCodes().size() + game.getFolders().size());
			game.getCodes().add(code);
			return code;
		}

		private Folder readFolder(String title, int count, String note) {
			var folder = new Folder();
			folder.setTitle(title);
			folder.setComment(note);
			folder.setSortOrder(game.getCodes().size() + game.getFolders().size());

			for (int i = 0; i < count; ++i) {
				Code code = readCode(note());
				code.setSortOrder(i);
				folder.getCodes().add(code);
			}

			game.getFolders().add(folder);
			return folder;
		}

		private Code readCode(String note) {
			Matcher title = matching(CODE, "isn't a code's title");
			var code = new Code();
			code.setTitle(pool.text(title.group(1)));
			code.setComment(note);

			while (index < lines.size() && !lines.get(index).isEmpty()) {
				code.getCodeLines().add(pool.text(matching(CODE_LINE, "isn't a code line").group()));
			}

			blank();
			return code;
		}

		/**
		 * The note on the line, moving past it, or empty when the line isn't a note.
		 */
		private String note() {
			Matcher note = NOTE.matcher(current());

			if (!note.matches()
					|| FOLDER.matcher(current()).matches()
					|| current().equals(M3CheatsWriter.SEPARATOR)
			) {
				return "";
			}

			++index;
			return pool.text(note.group(1));
		}

		private void blank() {
			if (!next().isEmpty()) {
				--index;
				throw error("should be blank");
			}
		}

		private Matcher matching(Pattern pattern, String problem) {
			Matcher matcher = pattern.matcher(current());

			if (!matcher.matches()) {
				throw error(problem);
			}

			++index;
			return matcher;
		}

		private String current() {
			if (index >= lines.size()) {
				throw new UsercheatParserException(
						"M3 cheat file game %s %s ends where a line was expected".formatted(serial, crc32)
				);
			}

			return lines.get(index);
		}

		private String next() {
			String line = current();
			++index;
			return line;
		}

		private UsercheatParserException error(String problem) {
			String line = index < lines.size() ? lines.get(index) : "";
			String start = line.length() > 60 ? line.substring(0, 60) + "…" : line;
			String where = "M3 cheat file game %s %s line %d".formatted(serial, crc32, index + 1);
			return new UsercheatParserException("%s \"%s\" %s".formatted(where, start, problem));
		}
	}
}
