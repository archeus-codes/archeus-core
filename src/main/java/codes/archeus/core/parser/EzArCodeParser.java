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
import java.util.ArrayList;
import java.util.Arrays;

import codes.archeus.core.exception.UsercheatParserException;

/**
 * Reads the original EZ-Flash V menu's cheat database ({@code ezarcode.dat}): it undoes the XOR {@link EzArCodeWriter}
 * applies after the 256 byte header, then reads the {@code usrcheat.dat} inside. Every game reads without a master
 * code, since the format holds none.
 */
public class EzArCodeParser implements CheatFileParser {
	private static final int HEADER_SIZE = UsercheatDatWriter.HEADER_SIZE;
	private static final int INDEX_ENTRY_SIZE = UsercheatDatWriter.INDEX_ENTRY_SIZE;

	/**
	 * Reads the file, throwing {@link UsercheatParserException} when it isn't an {@code ezarcode.dat} or doesn't read.
	 */
	@Override
	public ParseResult parse(InputStream in) throws IOException {
		byte[] file = in.readAllBytes();

		if (file.length < HEADER_SIZE || !startsWithMagic(file)) {
			throw new UsercheatParserException(
					"This isn't an ezarcode.dat: it doesn't start with \"EZ ARDS CheatCode Data Base\""
			);
		}

		decode(file);
		ParseResult result;

		try {
			result = new UsercheatDatParser().parse(file, null);
		} catch (UsercheatParserException e) {
			throw new UsercheatParserException("ezarcode.dat, read as the usrcheat.dat inside: " + e.getMessage(), e);
		}

		result.games().forEach(game -> game.setMasterCode(new ArrayList<>()));
		return result;
	}

	/**
	 * Turns the file, in place, into the {@code usrcheat.dat} it holds: an R4 header with its title, marked enabled
	 * since the EZ header has no such flag, then every byte after the header with the XOR undone. The index ends at the
	 * first entry that's all zeros as written, which the writer leaves unXORed.
	 */
	private static void decode(byte[] file) {
		int titleStart = UsercheatDatWriter.EZ_TITLE_OFFSET;
		int titleEnd = titleStart;

		while (titleEnd < 0x4C && file[titleEnd] != 0) {
			++titleEnd;
		}

		byte[] title = Arrays.copyOfRange(file, titleStart, titleEnd);
		long indexEnd = HEADER_SIZE;

		while (indexEnd + INDEX_ENTRY_SIZE <= file.length && !isZeroEntry(file, (int) indexEnd)) {
			indexEnd += INDEX_ENTRY_SIZE;
		}

		Arrays.fill(file, 0, HEADER_SIZE, (byte) 0);
		System.arraycopy(UsercheatDatWriter.FILE_MAGIC, 0, file, 0, UsercheatDatWriter.FILE_MAGIC.length);
		System.arraycopy(title, 0, file, 0x10, title.length);
		file[0x50] = 1;

		for (int i = HEADER_SIZE; i < file.length; ++i) {
			file[i] ^= EzArCodeWriter.keyAt(i, indexEnd);
		}
	}

	private static boolean startsWithMagic(byte[] file) {
		byte[] magic = UsercheatDatWriter.EZ_MAGIC;
		return Arrays.equals(file, 0, magic.length, magic, 0, magic.length);
	}

	private static boolean isZeroEntry(byte[] bytes, int from) {
		for (int i = from; i < from + INDEX_ENTRY_SIZE; ++i) {
			if (bytes[i] != 0) {
				return false;
			}
		}

		return true;
	}
}
