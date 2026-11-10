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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import codes.archeus.core.util.Rc4;

/**
 * Compares our writers' output with R4CCE's from the same data. The fixture {@code usrcheat.dat}, which R4CCE wrote, is
 * written back byte for byte. Its EDGE and CycloDS Evolution files are compared game by game (each game's decompressed
 * USAY data, its enabled region and the block header fields that locate that region), then byte for byte, since both
 * compress at zlib's default level.
 */
class R4cceParityTest {
	private static final int BLOCK_HEADER_SIZE = 32;
	private static final int INDEX_ENTRY_SIZE = 16;
	/** The zlib header R4CCE's streams start with: deflate at the default level. */
	private static final byte[] DEFAULT_LEVEL_HEADER = {0x78, (byte) 0x9c};
	private static final String SAME_ZLIB = "the whole file, which also needs the JDK's zlib to deflate as R4CCE's does "
			+ "(zlib 1.x at level 6; zlib-ng, as some Linux distributions ship, differs)";

	@TempDir
	Path tempDir;

	@Test
	void write_usrcheatDatReadFromTheR4cceFixture_isByteForByteTheSame() throws Exception {
		byte[] r4cce = resource("/usrcheat.dat");

		var out = new ByteArrayOutputStream();
		ParseResult parsed = parseFixture();
		new UsercheatDatWriter().write(parsed.cheatFile(), parsed.games(), out);

		assertThat(out.toByteArray()).isEqualTo(r4cce);
	}

	@Test
	void write_edgeFromTheR4cceFixture_matchesR4cceGameByGame() throws Exception {
		EdgeCheatsWriter writer = new EdgeCheatsWriter();

		byte[] ours = write(writer);
		byte[] r4cce = resource("/cheats.dat");

		assertMatches(CycloDsLayout.EDGE, ours, r4cce);
		assertThat(ours).as(SAME_ZLIB).isEqualTo(r4cce);
	}

	@Test
	void write_cycloDsFromTheR4cceFixture_matchesR4cceGameByGame() throws Exception {
		EvoCheatsWriter writer = new EvoCheatsWriter();

		byte[] ours = write(writer);
		byte[] r4cce = resource("/user.evoCheats");

		assertMatches(CycloDsLayout.EVOLUTION, ours, r4cce);
		assertThat(ours).as(SAME_ZLIB).isEqualTo(r4cce);
	}

	private void assertMatches(CycloDsLayout layout, byte[] ours, byte[] r4cce) throws DataFormatException {
		List<Block> ourBlocks = blocks(layout, ours);
		List<Block> r4cceBlocks = blocks(layout, r4cce);

		assertThat(ourBlocks).hasSize(r4cceBlocks.size()).isNotEmpty();

		for (int i = 0; i < ourBlocks.size(); ++i) {
			Block our = ourBlocks.get(i);
			Block theirs = r4cceBlocks.get(i);
			assertThat(our.indexEntry()).as("game %d's index entry", i).isEqualTo(theirs.indexEntry());
			assertThat(our.zlibHeader()).as("game %d's zlib header", i).isEqualTo(DEFAULT_LEVEL_HEADER);
			assertThat(our.usay()).as("game %d's USAY data", i).isEqualTo(theirs.usay());
			assertThat(our.enabledRegion()).as("game %d's enabled region", i).isEqualTo(theirs.enabledRegion());
			assertThat(theirs.headerRegionOffset()).as("R4CCE's region offset field, game %d", i)
					.isEqualTo(theirs.regionOffset());
			assertThat(our.headerRegionOffset()).as("game %d's region offset field", i).isEqualTo(our.regionOffset());
			assertThat(our.headerRegionSize()).as("game %d's region size field", i)
					.isEqualTo(theirs.headerRegionSize())
					.isEqualTo(our.enabledRegion().length);
		}
	}

	private byte[] write(AbstractCycloDsWriter writer) throws IOException {
		ParseResult parsed = parseFixture();

		var out = new ByteArrayOutputStream();
		writer.write(parsed.cheatFile(), parsed.games(), out);
		return out.toByteArray();
	}

	private ParseResult parseFixture() throws IOException {
		Path dat = tempDir.resolve("usrcheat.dat");
		Files.write(dat, resource("/usrcheat.dat"));

		try (RandomAccessFile file = new RandomAccessFile(dat.toFile(), "r")) {
			return new UsercheatDatParser().parse(file);
		}
	}

	private byte[] resource(String name) throws IOException {
		try (InputStream in = getClass().getResourceAsStream(name)) {
			assertThat(in).as(name).isNotNull();
			return in.readAllBytes();
		}
	}

	/**
	 * Decrypts each game's index entry, block header, USAY data and enabled region.
	 */
	private static List<Block> blocks(CycloDsLayout layout, byte[] file) throws DataFormatException {
		int start = layout.skip();
		byte[] header = Rc4.decrypt(layout.indexKey(), Arrays.copyOfRange(file, start, start + layout.headerSize()));
		int count = ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
		int indexEnd = start + layout.headerSize() + count * INDEX_ENTRY_SIZE;
		byte[] headerAndIndex = Rc4.decrypt(layout.indexKey(), Arrays.copyOfRange(file, start, indexEnd));

		int[] offsets = new int[count];

		for (int i = 0; i < count; ++i) {
			int entry = layout.headerSize() + i * INDEX_ENTRY_SIZE;
			offsets[i] = ByteBuffer.wrap(headerAndIndex, entry + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
		}

		List<Block> blocks = new ArrayList<>(count);

		for (int i = 0; i < count; ++i) {
			int offset = offsets[i];
			byte[] blockHeader = Rc4.decrypt(
					layout.indexKey(),
					Arrays.copyOfRange(file, offset, offset + BLOCK_HEADER_SIZE)
			);
			ByteBuffer fields = ByteBuffer.wrap(blockHeader).order(ByteOrder.LITTLE_ENDIAN);

			int compressedSize = fields.getInt(0);
			int decompressedSize = fields.getInt(4);
			int dataStart = offset + BLOCK_HEADER_SIZE;

			byte[] compressed = Rc4.decrypt(
					layout.dataKey(),
					Arrays.copyOfRange(file, dataStart, dataStart + compressedSize)
			);

			int regionOffset = dataStart + compressedSize;
			int regionEnd = i + 1 < count ? offsets[i + 1] : file.length;
			int entry = layout.headerSize() + i * INDEX_ENTRY_SIZE;

			blocks.add(new Block(
					Arrays.copyOfRange(headerAndIndex, entry, entry + 12),
					Arrays.copyOf(compressed, 2),
					inflate(compressed, decompressedSize),
					Rc4.decrypt(layout.dataKey(), Arrays.copyOfRange(file, regionOffset, regionEnd)),
					regionOffset,
					fields.getInt(24),
					fields.getInt(28)
			));
		}
		return blocks;
	}

	private static byte[] inflate(byte[] compressed, int size) throws DataFormatException {
		try (Inflater inflater = new Inflater()) {
			inflater.setInput(compressed);
			byte[] out = new byte[size];
			inflater.inflate(out);
			return out;
		}
	}

	/**
	 * One game as stored: its index entry without the file offset, and its block below the compression.
	 */
	private record Block(
			byte[] indexEntry,
			byte[] zlibHeader,
			byte[] usay,
			byte[] enabledRegion,
			int regionOffset,
			int headerRegionOffset,
			int headerRegionSize
	) {
	}
}
