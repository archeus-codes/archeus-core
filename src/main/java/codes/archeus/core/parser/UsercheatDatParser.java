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
import codes.archeus.core.exception.UsercheatParserException;
import codes.archeus.core.util.ByteUtils;
import codes.archeus.core.util.CheatFileConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses the binary {@code usrcheat.dat} format into a {@link CheatFile} and its games.
 */
public class UsercheatDatParser {
	private static final Logger log = LoggerFactory.getLogger(UsercheatDatParser.class);

	private static final int HEADER_SIZE = 256;
	private static final byte[] SIGNATURE = "R4 CheatCode".getBytes(StandardCharsets.US_ASCII);

	/**
	 * Reads the file.
	 *
	 * @throws UsercheatParserException if it's cut short or isn't a {@code usrcheat.dat}
	 */
	public ParseResult parse(RandomAccessFile file) throws IOException {
		return parse(file, null);
	}

	/**
	 * Reads the file as {@link #parse(RandomAccessFile)} does, telling the listener after each game.
	 */
	public ParseResult parse(RandomAccessFile file, ParseProgressListener listener) throws IOException {
		if (file.length() > Integer.MAX_VALUE) {
			throw new UsercheatParserException("usrcheat.dat is %,d bytes, too big to read".formatted(file.length()));
		}

		file.seek(0);
		var data = new byte[(int) file.length()];
		file.readFully(data);

		if (data.length < HEADER_SIZE) {
			throw new UsercheatParserException(
					"usrcheat.dat is %,d bytes, shorter than its %d byte header".formatted(data.length, HEADER_SIZE)
			);
		}

		if (!Arrays.equals(data, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)) {
			throw new UsercheatParserException("This isn't a usrcheat.dat: it doesn't start with \"R4 CheatCode\"");
		}

		ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		CheatFile cheatFile = parseHeader(buf);
		List<Game> games = parseGameIndex(buf);
		games = parseGameCodes(buf, games, listener);
		cheatFile.setGames(games);
		return new ParseResult(cheatFile, games);
	}

	/**
	 * Reads the 256-byte header: the title and the enabled flag.
	 */
	private CheatFile parseHeader(ByteBuffer buf) {
		final var header = new byte[HEADER_SIZE];

		final var titleOffset = 0x10;
		final var titleLength = 60;
		final var title = new byte[titleLength];

		final var cheatFile = new CheatFile();

		buf.get(header, 0, HEADER_SIZE);
		System.arraycopy(header, titleOffset, title, 0, titleLength);
		cheatFile.setTitle(new String(title, StandardCharsets.UTF_8).replace("\0", ""));
		cheatFile.setEnabled(header[0x50] == 1);

		return cheatFile;
	}

	/**
	 * Reads the game index: each game's serial, CRC32 and file offset, up to the all zero entry that ends it.
	 */
	private List<Game> parseGameIndex(ByteBuffer buf) {
		var games = new ArrayList<Game>();
		var serialBytes = new byte[4];

		while (buf.remaining() >= 16) {
			buf.get(serialBytes);

			if (serialBytes[0] == 0 && serialBytes[1] == 0 && serialBytes[2] == 0 && serialBytes[3] == 0) {
				return games;
			}

			var game = new Game();
			game.setSerial(new String(serialBytes, StandardCharsets.US_ASCII));
			game.setCrc32(readHex(buf));
			game.setFileOffset(buf.getInt());
			games.add(game);

			buf.position(buf.position() + 4); // skip unused bytes
		}

		throw new UsercheatParserException(
				"usrcheat.dat ends inside its game index, after %,d games".formatted(games.size())
		);
	}

	/**
	 * Reads each game's title, codes and folders from its offset.
	 */
	private List<Game> parseGameCodes(ByteBuffer buf, List<Game> games, ParseProgressListener listener) {
		var result = new ArrayList<Game>(games.size());

		for (Game game : games) {
			try {
				parseGame(buf, game);
			} catch (BufferUnderflowException | IllegalArgumentException e) {
				throw new UsercheatParserException(
						"usrcheat.dat ends inside game %,d of %,d (%s %s)"
								.formatted(result.size() + 1, games.size(), game.getSerial(), game.getCrc32()),
						e
				);
			}

			result.add(game);

			if (listener != null) {
				listener.onProgress(result.size(), games.size());
			}
		}

		return result;
	}

	private void parseGame(ByteBuffer buf, Game game) {
		var buffer = new byte[4];

		buf.position(game.getFileOffset());
		game.setTitle(parseGameTitle(buf));
		log.debug("Parsing {} [{}]", game.getTitle(), game.getSerial());
		buf.get(buffer);

		// The last byte is 0xF0 for an enabled game
		game.setEnabled((buffer[3] & 0xF0) != 0);

		// The first three are the number of folders and codes
		int data = (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8) | ((buffer[2] & 0xFF) << 16);

		game.setMasterCode(parseMasterCode(buf));

		// Folders and codes share one order, so they're written back interleaved as they were
		int topLevelOrder = 0;

		for (int i = 0; i < data; ++i) {
			int entryStart = buf.position();
			buf.get(buffer);

			// The last byte is 0x10 for a folder and 0x11 for a modifier folder
			if ((buffer[3] >> 4) == 1) {
				var folder = new Folder();
				folder.setModifier((buffer[3] & 0xF) == 1);
				folder.setSortOrder(topLevelOrder++);
				// Two bytes of code count, since some folders hold over 255 codes
				int folderCodeCount = (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8);
				TitleComment titleComment = parseTitleAndComment(buf);
				folder.setTitle(titleComment.title());
				folder.setComment(titleComment.comment());
				for (int j = 0; j < folderCodeCount; ++j) {
					folder.getCodes().add(parseCode(buf));
					++i;
				}
				game.getFolders().add(folder);
			} else {
				// A code, so read the entry again from its start
				buf.position(entryStart);
				Code gameCode = parseCode(buf);
				gameCode.setSortOrder(topLevelOrder++);
				game.getCodes().add(gameCode);
			}
		}
	}

	/**
	 * Reads the game title: a string ending in a zero byte, padded to a multiple of 4 bytes.
	 */
	private String parseGameTitle(ByteBuffer buf) {
		int start = buf.position();
		String title = readString(buf);
		skipToWordBoundary(buf, start);
		return title;
	}

	private List<String> parseMasterCode(ByteBuffer buf) {
		final var masterCode = new String[CheatFileConstants.MASTER_CODE_WORDS];

		for (int i = 0; i < CheatFileConstants.MASTER_CODE_WORDS; ++i) {
			masterCode[i] = readHex(buf);
		}

		return List.of(masterCode);
	}

	/**
	 * Reads a folder's or code's title and comment: two strings ending in zero bytes, padded together to a multiple of
	 * 4 bytes. Each is decoded whole, so a character that spans two chunks stays intact.
	 */
	private TitleComment parseTitleAndComment(ByteBuffer buf) {
		int start = buf.position();
		String title = readString(buf);
		String comment = readString(buf);
		skipToWordBoundary(buf, start);
		return new TitleComment(title, comment);
	}

	/**
	 * Reads UTF-8 bytes up to a zero byte, which it consumes.
	 */
	private static String readString(ByteBuffer buf) {
		int start = buf.position();
		int end = start;

		while (buf.get() != 0) {
			++end;
		}

		return new String(buf.array(), buf.arrayOffset() + start, end - start, StandardCharsets.UTF_8);
	}

	/**
	 * Moves past the padding to the next multiple of 4 bytes from {@code start}.
	 */
	private static void skipToWordBoundary(ByteBuffer buf, int start) {
		int read = buf.position() - start;
		buf.position(start + ((read + 3) & ~3));
	}

	private Code parseCode(ByteBuffer buf) {
		var buffer = new byte[4];
		buf.get(buffer);

		var code = new Code();
		code.setEnabled((buffer[3] & 0xF) == 1);

		TitleComment titleComment = parseTitleAndComment(buf);
		code.setTitle(titleComment.title());
		code.setComment(titleComment.comment());

		buf.get(buffer);

		int codeLength = (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8);
		var codeLines = new ArrayList<String>(codeLength / 2);
		var codeTextBuilder = new StringBuilder();

		for (int i = 0; i + 1 < codeLength; i += 2) {
			codeTextBuilder.append(readHex(buf));
			codeTextBuilder.append(" ").append(readHex(buf));
			codeLines.add(codeTextBuilder.toString());
			codeTextBuilder.setLength(0);
		}

		if (codeLength % 2 != 0) {
			readHex(buf); // the padding word
		}

		code.setCodeLines(codeLines);
		return code;
	}

	private String readHex(ByteBuffer buf) {
		return ByteUtils.toHex(buf.getInt());
	}
}
