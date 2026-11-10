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
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;
import codes.archeus.core.util.ByteUtils;
import codes.archeus.core.util.Rc4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads EDGE {@code cheats.dat} and CycloDS {@code user.evoCheats} files: decrypt, check the MD5s, decompress, then
 * read each game's USAY buffer. Subclasses supply the format's {@link CycloDsLayout}. The file layout is described on
 * {@link AbstractCycloDsWriter}.
 */
abstract class AbstractCycloDsParser implements CheatFileParser {
	private static final Logger log = LoggerFactory.getLogger(AbstractCycloDsParser.class);

	private static final int BLOCK_HEADER_SIZE = 32;
	private static final int GAME_INDEX_ENTRY_SIZE = 16;
	private static final int GAME_COUNT_OFFSET = 16;
	private static final int MD5_OFFSET = 20;
	private static final int MD5_LENGTH = 16;
	private static final int ENABLED_REGION_HEADER = 17; // its MD5 and the 0xc0 byte
	/** Far more than any game's USAY buffer, so a larger size is a damaged header. */
	private static final int MAX_DECOMPRESSED_SIZE = 64 * 1024 * 1024;

	private final CycloDsLayout layout;

	AbstractCycloDsParser(CycloDsLayout layout) {
		this.layout = layout;
	}

	/**
	 * Reads the file.
	 *
	 * @throws UsercheatParserException if it's cut short, fails an MD5 check or doesn't decompress
	 */
	@Override
	public ParseResult parse(InputStream in) throws IOException {
		byte[] data = in.readAllBytes();
		return parse(data);
	}

	ParseResult parse(byte[] data) {
		int skip = layout.skip();
		int headerSize = layout.headerSize();
		byte[] key0 = layout.indexKey();
		byte[] key1 = layout.dataKey();

		if (data.length < skip + headerSize) {
			throw fail("is %,d bytes, shorter than its header".formatted(data.length));
		}

		// The header alone first, for the game count
		byte[] decryptedHeader = Rc4.decrypt(key0, Arrays.copyOfRange(data, skip, skip + headerSize));
		int gameCount = ByteBuffer.wrap(decryptedHeader, GAME_COUNT_OFFSET, 4)
				.order(ByteOrder.LITTLE_ENDIAN).getInt();
		byte[] indexMd5Stored = Arrays.copyOfRange(decryptedHeader, MD5_OFFSET, MD5_OFFSET + MD5_LENGTH);

		if (gameCount < 0 || (long) gameCount * GAME_INDEX_ENTRY_SIZE > data.length - skip - headerSize) {
			throw fail("says it holds %,d games, more than its %,d bytes can".formatted(gameCount, data.length));
		}

		// Then the header and index again, since they're one RC4 stream
		int fullBlockSize = headerSize + gameCount * GAME_INDEX_ENTRY_SIZE;
		byte[] decryptedHeaderAndIndex = Rc4.decrypt(key0, Arrays.copyOfRange(data, skip, skip + fullBlockSize));
		byte[] decryptedIndex = Arrays.copyOfRange(decryptedHeaderAndIndex, headerSize, decryptedHeaderAndIndex.length);

		if (!Arrays.equals(md5(decryptedIndex), indexMd5Stored)) {
			throw fail("fails its game index's MD5 check");
		}

		List<Game> games = parseGameIndex(data, decryptedIndex, gameCount, key0, key1);
		var cheatFile = new CheatFile();
		cheatFile.setGames(games);
		return new ParseResult(cheatFile, games);
	}

	private record BlockResult(byte[] usay, int compressedSize) {
	}

	private List<Game> parseGameIndex(byte[] data, byte[] decryptedIndex, int gameCount, byte[] key0, byte[] key1) {
		var usayParser = new UsayParser();
		var games = new ArrayList<Game>(gameCount);
		var indexBuf = ByteBuffer.wrap(decryptedIndex).order(ByteOrder.LITTLE_ENDIAN);

		// Every offset first, since each game's enabled region ends where the next game starts
		String[] serials = new String[gameCount];
		String[] crc32s = new String[gameCount];
		int[] fileOffsets = new int[gameCount];

		for (int i = 0; i < gameCount; ++i) {
			byte[] serialBytes = new byte[4];
			indexBuf.get(serialBytes);
			serials[i] = new String(serialBytes, StandardCharsets.US_ASCII);
			crc32s[i] = ByteUtils.toHex(indexBuf.getInt());
			indexBuf.getInt(); // reserved zeros
			fileOffsets[i] = indexBuf.getInt();
		}

		for (int i = 0; i < gameCount; ++i) {
			int nextFileOffset = (i + 1 < gameCount) ? fileOffsets[i + 1] : data.length;

			try {
				BlockResult block = decryptAndDecompress(data, fileOffsets[i], nextFileOffset, key0, key1);
				Game game = usayParser.parse(block.usay());
				game.setSerial(serials[i]);
				game.setCrc32(crc32s[i]);
				applyEnabledStates(data, fileOffsets[i], block.compressedSize(), key1, game, nextFileOffset);
				games.add(game);
				log.debug("Parsed game {}/{}: {} [{}]", i + 1, gameCount, game.getTitle(), serials[i]);
			} catch (DataFormatException | RuntimeException e) {
				throw fail(
						"game %,d of %,d (%s %s) %s".formatted(
								i + 1,
								gameCount,
								serials[i],
								crc32s[i],
								e instanceof DataFormatException
										? "doesn't decompress"
										: e.getMessage() != null ? e.getMessage() : "doesn't read"
						),
						e
				);
			}
		}

		return games;
	}

	private BlockResult decryptAndDecompress(
			byte[] data,
			int fileOffset,
			int end,
			byte[] key0,
			byte[] key1
	) throws DataFormatException {
		if (fileOffset < 0 || (long) fileOffset + BLOCK_HEADER_SIZE > end || end > data.length) {
			throw new UsercheatParserException("starts outside the file");
		}

		byte[] blockHeader = Rc4.decrypt(key0, Arrays.copyOfRange(data, fileOffset, fileOffset + BLOCK_HEADER_SIZE));

		ByteBuffer headerBuffer = ByteBuffer.wrap(blockHeader).order(ByteOrder.LITTLE_ENDIAN);
		int compressedSize = headerBuffer.getInt();
		int decompressedSize = headerBuffer.getInt();
		byte[] md5Stored = Arrays.copyOfRange(blockHeader, 8, 8 + MD5_LENGTH);
		int compressedDataOffset = fileOffset + BLOCK_HEADER_SIZE;

		if (compressedSize < 0
				|| compressedSize > end - compressedDataOffset
				|| decompressedSize < 0
				|| decompressedSize > MAX_DECOMPRESSED_SIZE
		) {
			throw new UsercheatParserException("has sizes that don't fit the file");
		}

		byte[] decryptedCompressedData = Rc4.decrypt(
				key1,
				Arrays.copyOfRange(data, compressedDataOffset, compressedDataOffset + compressedSize)
		);

		if (!Arrays.equals(md5(decryptedCompressedData), md5Stored)) {
			throw new UsercheatParserException("fails its MD5 check");
		}

		byte[] out = new byte[decompressedSize];

		try (Inflater inflater = new Inflater()) {
			inflater.setInput(decryptedCompressedData);

			if (inflater.inflate(out) != decompressedSize || !inflater.finished()) {
				throw new DataFormatException("decompressed to the wrong size");
			}
		}

		return new BlockResult(out, compressedSize);
	}

	/**
	 * Reads the game's enabled state region, which runs from the end of its compressed data to the next game, checks
	 * its MD5, and sets each code's enabled flag from it.
	 */
	private void applyEnabledStates(
			byte[] data,
			int fileOffset,
			int compressedSize,
			byte[] key1,
			Game game,
			int nextFileOffset
	) {
		int enabledRegionStart = fileOffset + BLOCK_HEADER_SIZE + compressedSize;

		if (enabledRegionStart >= nextFileOffset) {
			return;
		}

		// A new data key stream, not the compressed data's
		byte[] dec = Rc4.decrypt(key1, Arrays.copyOfRange(data, enabledRegionStart, nextFileOffset));

		boolean md5Matches = dec.length >= ENABLED_REGION_HEADER
				&& Arrays.equals(md5(Arrays.copyOfRange(dec, MD5_LENGTH, dec.length)), Arrays.copyOf(dec, MD5_LENGTH));

		if (!md5Matches) {
			throw new UsercheatParserException("fails its enabled flags' MD5 check");
		}

		int cumulativeIndex = 0;

		for (var folder : game.getFolders()) {
			for (var code : folder.getCodes()) {
				int pos = ENABLED_REGION_HEADER + cumulativeIndex;
				if (pos < dec.length) {
					code.setEnabled(dec[pos] != 0);
				}
				++cumulativeIndex;
			}
		}

		for (var code : game.getCodes()) {
			int pos = ENABLED_REGION_HEADER + cumulativeIndex;
			if (pos < dec.length) {
				code.setEnabled(dec[pos] != 0);
			}
			++cumulativeIndex;
		}
	}

	private UsercheatParserException fail(String problem) {
		return new UsercheatParserException(fileName() + " " + problem);
	}

	private UsercheatParserException fail(String problem, Throwable cause) {
		return new UsercheatParserException(fileName() + " " + problem, cause);
	}

	private String fileName() {
		return layout == CycloDsLayout.EDGE ? "cheats.dat" : "user.evoCheats";
	}

	private static byte[] md5(byte[] data) {
		try {
			return MessageDigest.getInstance("MD5").digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("every JDK has MD5", e);
		}
	}
}
