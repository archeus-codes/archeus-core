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
import codes.archeus.core.util.Rc4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Shared writer for the EDGE {@code cheats.dat} and CycloDS {@code user.evoCheats} formats.
 *
 * <p>Both formats use the same pipeline: USAY encode -> zlib compress -> MD5 hash -> RC4 encrypt.
 * Subclasses supply the format's {@link CycloDsLayout} (its two RC4 keys and sizes) and its magic bytes.
 *
 * <p>Container layout written (after {@code fileMagic()} bytes):
 * <pre>
 *   [headerSize bytes]  container header, encrypted with the index key
 *     [0:4]    format magic (CHT\0 or \x01EVO)
 *     [4:8]    version (0 for EDGE, 1 for CEVO)
 *     [8:16]   zeros
 *     [16:20]  game count (uint32 LE)
 *     [20:36]  MD5 of the plaintext game index
 *     [36:40]  USAY tag
 *     [40:headerSize]  zeros (CEVO only, 8 extra bytes)
 *   [gameCount * 16 bytes]  game index, encrypted in the same RC4 stream as the header
 *     each entry: serial(4) + crc32(uint32 LE) + zeros(4) + fileOffset(uint32 LE)
 * </pre>
 *
 * <p>Each game's block, at its fileOffset:
 * <pre>
 *   [0:32]  block header, encrypted with the index key
 *     [0:4]   compressed size (uint32 LE)
 *     [4:8]   decompressed size (uint32 LE)
 *     [8:24]  MD5 of the plaintext compressed data
 *     [24:28] file offset of the enabled state region (uint32 LE)
 *     [28:32] size of the enabled state region, S (uint32 LE)
 *   [32:32+compressedSize]  zlib data, encrypted with the data key
 *   [32+compressedSize:32+compressedSize+S]  enabled state region, encrypted with a new data key stream
 *     where S = 17 + (total code count), rounded up to a multiple of 4, plus 4
 *     [0:16]  MD5 of bytes [16:S]
 *     [16]    0xc0 (constant)
 *     [17+i]  enabled flag for code i in USAY traversal order: 0x80 = enabled, 0x00 = disabled
 *             order: each folder's codes, folder by folder, then the top level codes
 *     [17+codes:S]  zeros
 * </pre>
 */
abstract class AbstractCycloDsWriter implements CheatFileWriter {
	private static final Logger log = LoggerFactory.getLogger(AbstractCycloDsWriter.class);

	/** How many split folders or left out codes the log names. */
	private static final int LOGGED_ENTRIES = 20;

	private static final int BLOCK_HEADER_SIZE = 32;
	private static final int MD5_LENGTH = 16;
	/** The enabled region's MD5 and its 0xc0 byte, before the flags. */
	private static final int ENABLED_REGION_HEADER = 17;
	/** Zeros R4CCE adds after the enabled region's flags once they're padded to a multiple of 4. */
	private static final int ENABLED_REGION_PADDING = 4;
	private static final int GAME_INDEX_ENTRY_SIZE = 16;
	private static final byte[] USAY_TAG = {0x55, 0x53, 0x41, 0x59}; // "USAY"

	private final CycloDsLayout layout;

	AbstractCycloDsWriter(CycloDsLayout layout) {
		this.layout = layout;
	}

	/**
	 * Raw bytes written at the very start of the file (before the encrypted container).
	 */
	protected abstract byte[] fileMagic();

	/**
	 * First 4 bytes of the container header (CHT\0 for EDGE, \x01EVO for CEVO).
	 */
	protected abstract byte[] containerMagic();

	/**
	 * Value written at container header bytes [4:8] (0 for EDGE, 1 for CEVO).
	 */
	protected abstract int containerVersion();

	/**
	 * Each game's encrypted block and its file offset.
	 */
	private record GameBlocksResult(List<byte[]> blocks, int[] fileOffsets) {
	}

	@Override
	public void write(CheatFile cheatFile, List<Game> games, OutputStream out) throws IOException {
		byte[] key0 = layout.indexKey();
		byte[] key1 = layout.dataKey();

		// The format counts a folder's codes and a code's lines in one byte each
		UsayLimits.Result applied = UsayLimits.apply(games);
		List<Game> output = applied.games();

		if (!applied.split().isEmpty()) {
			log.info(
					"Split {} folders over {} codes into parts: {}",
					applied.split().size(),
					UsayLimits.MAX_FOLDER_CODES,
					first(applied.split())
			);
		}

		if (!applied.leftOut().isEmpty()) {
			log.info(
					"Left {} codes over {} lines out of the file: {}",
					applied.leftOut().size(),
					UsayLimits.MAX_CODE_LINES,
					first(applied.leftOut())
			);
		}

		GameBlocksResult result = buildGameBlocks(output, key0, key1);
		byte[] indexBytes = buildGameIndex(output, result.fileOffsets());
		byte[] encContainer = buildEncryptedContainer(indexBytes, output.size(), key0);

		out.write(fileMagic());
		out.write(encContainer);

		for (byte[] block : result.blocks()) {
			out.write(block);
		}
	}

	private static String first(List<String> entries) {
		return String.join("; ", entries.subList(0, Math.min(entries.size(), LOGGED_ENTRIES)));
	}

	/**
	 * Encodes, compresses and encrypts each game into its block.
	 */
	private GameBlocksResult buildGameBlocks(List<Game> games, byte[] key0, byte[] key1) throws IOException {
		int gameCount = games.size();
		int indexRegionStart = layout.skip() + layout.headerSize() + gameCount * GAME_INDEX_ENTRY_SIZE;
		var usayWriter = new UsayWriter();

		List<byte[]> blocks = new ArrayList<>(gameCount);
		int[] fileOffsets = new int[gameCount];
		int currentOffset = indexRegionStart;

		for (int i = 0; i < gameCount; ++i) {
			byte[] usay = usayWriter.write(games.get(i));
			byte[] compressed = zlibCompress(usay);
			byte[] md5 = md5(compressed);

			byte[] encEnabledRegion = buildEnabledRegion(games.get(i), key1);
			int enabledRegionOffset = currentOffset + BLOCK_HEADER_SIZE + compressed.length;

			ByteBuffer blockHeader = ByteBuffer.allocate(BLOCK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
			blockHeader.putInt(compressed.length);
			blockHeader.putInt(usay.length);
			blockHeader.put(md5);
			blockHeader.putInt(enabledRegionOffset);
			blockHeader.putInt(encEnabledRegion.length);

			byte[] encblockHeader = Rc4.decrypt(key0, blockHeader.array());
			byte[] encCompressed = Rc4.decrypt(key1, compressed);

			fileOffsets[i] = currentOffset;
			currentOffset += BLOCK_HEADER_SIZE + compressed.length + encEnabledRegion.length;

			ByteArrayOutputStream block = new ByteArrayOutputStream(BLOCK_HEADER_SIZE + compressed.length + encEnabledRegion.length);
			block.write(encblockHeader);
			block.write(encCompressed);
			block.write(encEnabledRegion);
			blocks.add(block.toByteArray());
		}

		return new GameBlocksResult(blocks, fileOffsets);
	}

	/**
	 * The plaintext game index, 16 bytes a game.
	 */
	private byte[] buildGameIndex(List<Game> games, int[] fileOffsets) {
		int gameCount = games.size();
		ByteBuffer index = ByteBuffer.allocate(gameCount * GAME_INDEX_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

		for (int i = 0; i < gameCount; ++i) {
			Game g = games.get(i);
			byte[] serialBytes = g.getSerial().getBytes(StandardCharsets.US_ASCII);
			index.put(serialBytes, 0, 4);
			index.putInt((int) Long.parseLong(g.getCrc32(), 16));
			index.putInt(0); // reserved zeros
			index.putInt(fileOffsets[i]);
		}

		return index.array();
	}

	/**
	 * The container header and the game index, encrypted together as one RC4 stream.
	 */
	private byte[] buildEncryptedContainer(byte[] indexBytes, int gameCount, byte[] key0) throws IOException {
		ByteBuffer containerHeader = ByteBuffer.allocate(layout.headerSize()).order(ByteOrder.LITTLE_ENDIAN);
		containerHeader.put(containerMagic());
		containerHeader.putInt(containerVersion());
		containerHeader.putLong(0L); // [8:16] zeros
		containerHeader.putInt(gameCount); // [16:20]
		containerHeader.put(md5(indexBytes)); // [20:36]
		containerHeader.put(USAY_TAG); // [36:40]
		// CEVO's last 8 bytes stay zero

		ByteArrayOutputStream plainContainer = new ByteArrayOutputStream(layout.headerSize() + indexBytes.length);
		plainContainer.write(containerHeader.array());
		plainContainer.write(indexBytes);

		return Rc4.decrypt(key0, plainContainer.toByteArray());
	}

	/**
	 * The game's encrypted enabled state region, laid out as the class describes. Like R4CCE, it pads the flags with
	 * zeros to a multiple of 4 bytes, then 4 more, and the MD5 covers the padding.
	 */
	private byte[] buildEnabledRegion(Game game, byte[] key1) {
		int folderCodeCount = game.getFolders().stream()
				.mapToInt(f -> f.getCodes().size())
				.sum();
		int totalCodeCount = folderCodeCount + game.getCodes().size();

		int regionLength = ((ENABLED_REGION_HEADER + totalCodeCount + 3) & ~3) + ENABLED_REGION_PADDING;
		byte[] statePart = new byte[regionLength - MD5_LENGTH];
		statePart[0] = (byte) 0xc0;
		int idx = 0;

		for (Folder folder : game.getFolders()) {
			for (Code code : folder.getCodes()) {
				statePart[1 + idx] = code.isEnabled() ? (byte) 0x80 : 0x00;
				++idx;
			}
		}

		for (Code code : game.getCodes()) {
			statePart[1 + idx] = code.isEnabled() ? (byte) 0x80 : 0x00;
			++idx;
		}

		byte[] hash = md5(statePart);
		byte[] plain = new byte[regionLength];
		System.arraycopy(hash, 0, plain, 0, MD5_LENGTH);
		System.arraycopy(statePart, 0, plain, MD5_LENGTH, statePart.length);

		return Rc4.decrypt(key1, plain);
	}

	/**
	 * Compresses at zlib's default level, as R4CCE does: its streams start {@code 78 9c}, and at the same level Java's
	 * zlib gives the same bytes.
	 */
	private static byte[] zlibCompress(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
		byte[] buf = new byte[8192];

		try (Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION)) {
			deflater.setInput(data);
			deflater.finish();
			while (!deflater.finished()) {
				int n = deflater.deflate(buf);
				out.write(buf, 0, n);
			}
		}

		return out.toByteArray();
	}

	private static byte[] md5(byte[] data) {
		try {
			return MessageDigest.getInstance("MD5").digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
