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

import static codes.archeus.core.parser.M3CheatsWriterTest.code;
import static codes.archeus.core.parser.M3CheatsWriterTest.game;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Game;
import codes.archeus.core.util.CheatFileConstants;

class EzArCodeWriterTest {
	/** Where the only game's data starts: after the header, its index entry and the closing entry. */
	private static final int GAME = 0x120;
	/** "Game" takes 4 bytes and 4 of padding, then the game's header word comes before the master code. */
	private static final int MASTER_CODE = GAME + 8 + 4;
	private static final int FIRST_ENTRY = MASTER_CODE + 32;
	private static final byte[] MAGIC = "EZ ARDS CheatCode Data Base\0".getBytes(StandardCharsets.US_ASCII);

	@Test
	void write_header_holdsTheEzMagicAndTitleWithoutTheEnabledFlag() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("Archeus v12");
		cheatFile.setEnabled(true);

		byte[] file = write(cheatFile, oneCode());

		assertThat(Arrays.copyOf(file, 28)).isEqualTo(MAGIC);
		assertThat(Arrays.copyOfRange(file, 28, 0x20)).containsOnly((byte) 0);
		assertThat(new String(file, 0x20, 11, StandardCharsets.US_ASCII)).isEqualTo("Archeus v12");
		assertThat(Arrays.copyOfRange(file, 0x2B, 0x100)).containsOnly((byte) 0);
	}

	@Test
	void write_body_isXoredByFileOffsetButTheIndexsClosingEntry() throws IOException {
		byte[] file = write(new CheatFile(), oneCode());

		assertThat(file[0x100]).isEqualTo((byte) ('A' ^ 'D'));
		assertThat(Arrays.copyOfRange(file, 0x110, GAME)).containsOnly((byte) 0);
		assertThat(new String(decoded(file), 0x100, 4, StandardCharsets.US_ASCII)).isEqualTo("ABCD");
		assertThat(new String(decoded(file), GAME, 4, StandardCharsets.US_ASCII)).isEqualTo("Game");
	}

	@Test
	void write_codeAndMasterCode_writesTheCodesSizeAsZeroKeepsItsFlagAndLeavesTheMasterCodeOut() throws IOException {
		Game game = oneCode();
		game.setMasterCode(new ArrayList<>(List.of("023F0000", "00000002")));
		game.getCodes().getFirst().setEnabled(true);

		ByteBuffer body = ByteBuffer.wrap(decoded(write(new CheatFile(), game))).order(ByteOrder.LITTLE_ENDIAN);

		assertThat(Arrays.copyOfRange(body.array(), MASTER_CODE, FIRST_ENTRY)).containsOnly((byte) 0);
		assertThat(body.getInt(FIRST_ENTRY)).isEqualTo(0x01000000);
	}

	@Test
	void write_longTitle_keepsTheWholeCharactersWithin43Bytes() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("é".repeat(30));

		byte[] file = write(cheatFile, oneCode());

		assertThat(new String(file, 0x20, 42, StandardCharsets.UTF_8)).isEqualTo("é".repeat(21));
		assertThat(Arrays.copyOfRange(file, 0x20 + 42, 0x100)).containsOnly((byte) 0);
	}

	@Test
	void gamesWithMasterCodes_customStandardAndNone_listsOnlyTheCustom() {
		Game custom = game("AAAA", "00000000", "Custom");
		custom.setMasterCode(new ArrayList<>(List.of("023F0000", "00000002")));

		Game standard = game("AAAB", "00000000", "Standard");
		standard.setMasterCode(new ArrayList<>(CheatFileConstants.NULL_MASTER_CODE));

		Game none = game("AAAC", "00000000", "None");

		assertThat(EzArCodeWriter.gamesWithMasterCodes(List.of(custom, standard, none))).containsExactly("Custom");
	}

	static byte[] write(CheatFile cheatFile, Game... games) throws IOException {
		var out = new ByteArrayOutputStream();
		new EzArCodeWriter().write(cheatFile, List.of(games), out);
		return out.toByteArray();
	}

	static Game oneCode() {
		Game game = game("ABCD", "12345678", "Game");
		Code maxMoney = code("Max Money", "", "0200000A 000000FF");
		game.getCodes().add(maxMoney);
		return game;
	}

	/**
	 * A file of one game with the XOR undone.
	 */
	private static byte[] decoded(byte[] file) {
		long indexEnd = UsercheatDatWriter.HEADER_SIZE + UsercheatDatWriter.INDEX_ENTRY_SIZE;
		byte[] decoded = file.clone();

		for (int i = 0; i < decoded.length; ++i) {
			decoded[i] ^= EzArCodeWriter.keyAt(i, indexEnd);
		}

		return decoded;
	}
}
