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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

class M3CheatsWriterTest {
	private static final Charset GBK = Charset.forName("GBK");

	@Test
	void write_codesAndFolders_writesTheirNotesAndASeparatorWhereverAFolderMeetsAnotherEntry() throws IOException {
		var game = game("C32P", "BE547C63", "Ace Attorney Investigations - Miles Edgeworth (Europe)");

		var misc = folder("Miscellaneous Codes", "", 0);
		misc.getCodes().add(code("Increase Text Speed", "", "02000000 00000001"));
		misc.getCodes().add(code("All Chapters Unlocked", "Effect Comes After Saving", "02000004 00000002"));
		game.getFolders().add(misc);

		var logic = folder("Logic Codes", "WARNING: This may not work on all chapters.", 1);
		logic.getCodes().add(code("All Logic Clues", "(Press Select+Up)", "94000130 FFBB0000", "D2000000 00000000"));
		game.getFolders().add(logic);

		Code maxMoney = code("Max Money", "", "02000008 0001869F");
		maxMoney.setSortOrder(2);
		game.getCodes().add(maxMoney);

		var last = folder("Backlight Codes", "", 3);
		last.getCodes().add(code("Backlight", "", "02000010 00000001"));
		game.getFolders().add(last);

		assertThat(texts(write(game)).getFirst()).isEqualTo(String.join("\r\n",
				";Game ID: C32P BE547C63",
				";Ace Attorney Investigations - Miles Edgeworth (Europe)",
				"",
				"",
				"<Miscellaneous Codes:> = 2",
				"[Increase Text Speed]",
				"02000000 00000001",
				"",
				"<Effect Comes After Saving:>",
				"[All Chapters Unlocked]",
				"02000004 00000002",
				"",
				"<----------:>",
				"",
				"<WARNING: This may not work on all chapters.:>",
				"<Logic Codes:> = 1",
				"<(Press Select+Up):>",
				"[All Logic Clues]",
				"94000130 FFBB0000",
				"D2000000 00000000",
				"",
				"<----------:>",
				"",
				"[Max Money]",
				"02000008 0001869F",
				"",
				"<----------:>",
				"<Backlight Codes:> = 1",
				"[Backlight]",
				"02000010 00000001",
				"",
				""
		) + "\0");
	}

	@Test
	void write_games_indexesEachGameWhereTheOneBeforeItEnds() throws IOException {
		var first = game("AAAA", "00000000", "!Credits");
		first.getCodes().add(code("Datel", ""));
		var second = game("ADME", "8f7851cb", "Animal Crossing - Wild World (USA)");
		second.getCodes().add(code("Max Bells", "", "02000000 0098967F"));

		byte[] file = write(first, second);
		ByteBuffer bytes = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);

		assertThat(bytes.getShort(0)).isEqualTo((short) 2);
		assertThat(new String(file, 2, 15, StandardCharsets.US_ASCII)).isEqualTo("AAAADHT00000000");
		assertThat(new String(file, 25, 15, StandardCharsets.US_ASCII)).isEqualTo("ADMEDHT8F7851CB");
		assertThat(new String(file, 48, 2, StandardCharsets.US_ASCII)).isEqualTo("\r\n");
		assertThat(bytes.getInt(17)).isEqualTo(50);
		assertThat(bytes.getInt(40)).isEqualTo(bytes.getInt(17) + bytes.getInt(21));
		assertThat(bytes.getInt(40) + bytes.getInt(44)).isEqualTo(file.length);
	}

	@Test
	void write_codeWithNoLines_writesItsTitleAlone() throws IOException {
		var game = game("AAAA", "00000000", "!Credits");
		game.getCodes().add(code("Datel", "For their codes."));

		assertThat(texts(write(game)).getFirst()).endsWith("\r\n<For their codes.:>\r\n[Datel]\r\n\r\n\0");
	}

	@Test
	void write_placeholderCodeAndTheFolderItEmpties_leavesThemOut() throws IOException {
		var game = game("AMCE", "12345678", "Mario Kart DS (USA)");
		game.getCodes().add(code("Item Modifier", "", "0200???? 00000063"));

		var templates = folder("Templates", "", 1);
		templates.getCodes().add(code("Character Modifier", "", "02000004 000000XX"));
		game.getFolders().add(templates);

		var items = folder("Items", "", 2);
		items.getCodes().add(code("Always Star", "", "02000004 00000001"));
		game.getFolders().add(items);

		assertThat(texts(write(game)).getFirst()).isEqualTo(String.join("\r\n",
				";Game ID: AMCE 12345678",
				";Mario Kart DS (USA)",
				"",
				"",
				"<Items:> = 1",
				"[Always Star]",
				"02000004 00000001",
				"",
				""
		) + "\0");
	}

	@Test
	void write_folderEmptyToBeginWith_leavesItOut() throws IOException {
		var game = game("AMCE", "12345678", "Mario Kart DS (USA)");
		game.getFolders().add(folder("Level Codes", "", 0));

		assertThat(texts(write(game)).getFirst()).doesNotContain("Level Codes");
	}

	@Test
	void title_squareBrackets_becomeAngleBrackets() {
		assertThat(M3CheatsWriter.title("[L+R]All Cars [ASM]")).isEqualTo("<L+R>All Cars <ASM>");
	}

	@Test
	void note_angleBracketsAndLineBreaks_becomeSquareBracketsOnOneLine() {
		assertThat(M3CheatsWriter.note("Select <Step> Jump\r\nthen Start"))
				.isEqualTo("Select [Step] Jump then Start");
	}

	@Test
	void note_separatorsDashes_getsASpaceSoItIsntTheSeparator() {
		assertThat(M3CheatsWriter.note("----------")).isEqualTo("---------- ");
	}

	@Test
	void write_serialNotFourCharacters_throws() {
		Game game = game("ABC", "00000000", "Short Serial");

		assertThatThrownBy(() -> write(game))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Short Serial");
	}

	@Test
	void gbk_characterGbkHolds_keepsIt() {
		assertThat(new String(M3CheatsWriter.gbk("Pokémon"), GBK)).isEqualTo("Pokémon");
	}

	@Test
	void gbk_halfWidthKatakana_becomesFullWidth() {
		assertThat(new String(M3CheatsWriter.gbk("ﾃﾞｼﾖｳ"), GBK)).isEqualTo("デシヨウ");
	}

	@Test
	void gbk_characterGbkLacks_becomesAQuestionMark() {
		assertThat(new String(M3CheatsWriter.gbk("Star 🌟"), GBK)).isEqualTo("Star ?");
	}

	@Test
	void holdsWhole_textGbkHoldsOrHalfWidthKana_isTrue() {
		assertThat(List.of("Max Money", "Pokémon", "ﾃﾞｼﾖｳ", "")).allMatch(M3CheatsWriter::holdsWhole);
		assertThat(M3CheatsWriter.holdsWhole(null)).isTrue();
	}

	@Test
	void holdsWhole_textGbkLacks_isFalse() {
		assertThat(List.of("한국어", "Star \uD83C\uDF1F")).noneMatch(M3CheatsWriter::holdsWhole);
	}

	@Test
	void write_tooManyGames_throws() {
		List<Game> games = Collections.nCopies(M3CheatsWriter.MAX_GAMES + 1, game("AAAA", "00000000", "Game"));

		assertThatThrownBy(() -> new M3CheatsWriter().write(new CheatFile(), games, new ByteArrayOutputStream()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	static byte[] write(Game... games) throws IOException {
		var out = new ByteArrayOutputStream();
		new M3CheatsWriter().write(new CheatFile(), List.of(games), out);
		return out.toByteArray();
	}

	/**
	 * Each game's text from the index, in GBK.
	 */
	private static List<String> texts(byte[] file) {
		ByteBuffer bytes = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
		List<String> texts = new ArrayList<>();

		for (int i = 0; i < Short.toUnsignedInt(bytes.getShort(0)); ++i) {
			int entry = 2 + M3CheatsWriter.INDEX_ENTRY_BYTES * i;
			texts.add(new String(file, bytes.getInt(entry + 15), bytes.getInt(entry + 19), GBK));
		}

		return texts;
	}

	static Game game(String serial, String crc32, String title) {
		var game = new Game();
		game.setSerial(serial);
		game.setCrc32(crc32);
		game.setTitle(title);
		return game;
	}

	static Folder folder(String title, String comment, int sortOrder) {
		var folder = new Folder();
		folder.setTitle(title);
		folder.setComment(comment);
		folder.setSortOrder(sortOrder);
		return folder;
	}

	static Code code(String title, String comment, String... lines) {
		var code = new Code();
		code.setTitle(title);
		code.setComment(comment);
		code.setCodeLines(new ArrayList<>(List.of(lines)));
		return code;
	}
}
