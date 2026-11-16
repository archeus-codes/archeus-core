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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Game;

/**
 * Writes the original EZ-Flash V menu's cheat database ({@code ezarcode.dat}): a {@code usrcheat.dat} with its own
 * header, every byte after the header XORed with a 16 byte key, no master codes, and 0 in each code header's size
 * field. The title keeps at most {@value UsercheatDatWriter#EZ_TITLE_BYTES} bytes of UTF-8.
 */
public class EzArCodeWriter implements CheatFileWriter {
	/** The key, applied by file offset: the byte at offset {@code i} is XORed with {@code KEY[i % 16]}. */
	static final byte[] KEY = "DSEZBOFHMOON\0\0\0\0".getBytes(StandardCharsets.US_ASCII);

	/**
	 * Writes every game given, in their order.
	 */
	@Override
	public void write(CheatFile cheatFile, List<Game> games, OutputStream out) throws IOException {
		long indexEnd = UsercheatDatWriter.HEADER_SIZE + (long) UsercheatDatWriter.INDEX_ENTRY_SIZE * games.size();
		var xored = new KeyedXorStream(out, indexEnd);
		new UsercheatDatWriter(UsercheatDatWriter.Layout.EZARCODE).write(cheatFile, games, xored);
		xored.flush();
	}

	/**
	 * The titles of the games with a master code, which the file leaves out.
	 */
	public static List<String> gamesWithMasterCodes(List<Game> games) {
		return games.stream().filter(game -> MasterCode.isCustom(game.getMasterCode())).map(Game::getTitle).toList();
	}

	/**
	 * The key's byte for this file offset, or 0 for the header and the index's closing entry, which stay as they are.
	 */
	static byte keyAt(long offset, long indexEnd) {
		if (offset < UsercheatDatWriter.HEADER_SIZE
				|| (offset >= indexEnd && offset < indexEnd + UsercheatDatWriter.INDEX_ENTRY_SIZE)
		) {
			return 0;
		}

		return KEY[(int) (offset % KEY.length)];
	}

	/**
	 * XORs each byte with the key for its offset in the file.
	 */
	private static final class KeyedXorStream extends FilterOutputStream {
		private final long indexEnd;
		private long offset;

		KeyedXorStream(OutputStream out, long indexEnd) {
			super(out);
			this.indexEnd = indexEnd;
		}

		@Override
		public void write(int b) throws IOException {
			out.write(b ^ keyAt(offset++, indexEnd));
		}

		@Override
		public void write(byte[] bytes, int from, int length) throws IOException {
			byte[] xored = new byte[length];

			for (int i = 0; i < length; ++i) {
				xored[i] = (byte) (bytes[from + i] ^ keyAt(offset++, indexEnd));
			}

			out.write(xored);
		}
	}
}
