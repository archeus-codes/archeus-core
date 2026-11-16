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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

class GameFileFormatTest {
	private static final String LINE = "02000000 00000063";
	private static final String TEMPLATE = "0200???? 00000063";

	@Test
	void of_eachKey_findsItsFormat() {
		assertThat(GameFileFormat.of("mch")).contains(GameFileFormat.MELONDS);
		assertThat(GameFileFormat.of("cht")).contains(GameFileFormat.RETROARCH);
		assertThat(GameFileFormat.of("dct")).contains(GameFileFormat.DESMUME);
		assertThat(GameFileFormat.of("dat")).isEmpty();
	}

	@Test
	void fileName_eachFormat_usesTheTitleSerialCrc32AndItsExtension() {
		Game game = game();

		assertThat(GameFileFormat.values()).extracting(format -> format.fileName(game)).containsExactly(
				"Mario Kart DS (USA) [AMCE 12345678].mch",
				"Mario Kart DS (USA) [AMCE 12345678].cht",
				"Mario Kart DS (USA) [AMCE 12345678].dct"
		);
	}

	@Test
	void held_templateAndTheFolderItEmpties_leavesThemOutAndKeepsTheOrder() {
		Game game = game();
		var templates = folder("Templates", 1);
		templates.getCodes().add(code("Item Modifier", TEMPLATE));
		game.getFolders().add(templates);
		game.getFolders().getFirst().setSortOrder(2);
		game.getCodes().add(code("Max Money", 3, LINE));

		Game held = GameFileFormat.RETROARCH.held(game);

		assertThat(held.getTopLevelEntriesInOrder())
				.extracting(entry -> entry instanceof Code code ? code.getTitle() : ((Folder) entry).getTitle())
				.containsExactly("Infinite Health", "Items", "Max Money");
		assertThat(held.getTitle()).isEqualTo("Mario Kart DS (USA)");
	}

	@Test
	void held_codeWithNoLines_keptByMelonDsAlone() {
		Game game = game();
		game.getCodes().add(code("Datel", 3));

		assertThat(GameFileFormat.values()).extracting(format -> format.held(game).getCodes().size())
				.containsExactly(2, 1, 1);
	}

	@Test
	void holdsAny_onlyATemplate_isFalseForEveryFormat() {
		var game = new Game();
		game.getCodes().add(code("Item Modifier", 0, TEMPLATE));

		assertThat(GameFileFormat.values()).noneMatch(format -> format.holdsAny(game));
	}

	@Test
	void holdsAny_aCodeInAFolder_isTrueForEveryFormat() {
		var game = new Game();
		var folder = folder("Items", 0);
		folder.getCodes().add(code("Always Star", LINE));
		game.getFolders().add(folder);

		assertThat(GameFileFormat.values()).allMatch(format -> format.holdsAny(game));
	}

	@Test
	void write_melonDs_writesOnlyTheHeldCodes() throws IOException {
		Game read = new MelonDsParser().parse(new ByteArrayInputStream(written(GameFileFormat.MELONDS)));

		assertThat(read.getCodes()).extracting(Code::getTitle).containsExactly("Infinite Health");
		assertThat(read.getFolders().getFirst().getCodes()).extracting(Code::getTitle).containsExactly("Always Star");
	}

	@Test
	void write_retroArch_writesOnlyTheHeldCodes() throws IOException {
		Game read = new RetroArchParser().parse(new ByteArrayInputStream(written(GameFileFormat.RETROARCH)));

		assertThat(read.getCodes()).extracting(Code::getTitle).containsExactly("Infinite Health");
		assertThat(read.getFolders().getFirst().getCodes()).extracting(Code::getTitle).containsExactly("Always Star");
	}

	@Test
	void held_nullMasterCode_givesAnEmptyOne() {
		Game game = game();
		game.setMasterCode(null);

		assertThat(GameFileFormat.MELONDS.held(game).getMasterCode()).isEmpty();
	}

	@Test
	void write_desmume_writesOnlyTheHeldCodes() throws IOException {
		Game read = new DesmumeParser().parse(new ByteArrayInputStream(written(GameFileFormat.DESMUME)));
		assertThat(read.getCodes()).extracting(Code::getTitle)
				.containsExactly("Infinite Health", "Items: Always Star");
	}

	/**
	 * The format's file for {@link #game()} with a template added to its folder.
	 */
	private static byte[] written(GameFileFormat format) throws IOException {
		Game game = game();
		game.getFolders().getFirst().getCodes().add(code("Item Modifier", TEMPLATE));
		var out = new ByteArrayOutputStream();
		format.write(game, out);
		return out.toByteArray();
	}

	/**
	 * A game with a top level code and a folder holding one code.
	 */
	private static Game game() {
		var game = new Game();
		game.setTitle("Mario Kart DS (USA)");
		game.setSerial("AMCE");
		game.setCrc32("12345678");
		game.getCodes().add(code("Infinite Health", 0, LINE));

		var items = folder("Items", 1);
		items.getCodes().add(code("Always Star", "02000004 00000001"));
		game.getFolders().add(items);
		return game;
	}

	private static Folder folder(String title, int sortOrder) {
		var folder = new Folder();
		folder.setTitle(title);
		folder.setSortOrder(sortOrder);
		return folder;
	}

	private static Code code(String title, String... lines) {
		return code(title, 0, lines);
	}

	private static Code code(String title, int sortOrder, String... lines) {
		var code = new Code();
		code.setTitle(title);
		code.setSortOrder(sortOrder);
		code.setCodeLines(new ArrayList<>(List.of(lines)));
		return code;
	}
}
