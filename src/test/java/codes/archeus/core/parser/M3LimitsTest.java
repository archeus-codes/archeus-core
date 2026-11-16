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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.util.CheatFileConstants;

class M3LimitsTest {

	@Test
	void apply_gameTheFileHoldsWhole_changesNothing() {
		Game game = game("Mario Kart DS (USA)");

		M3Limits.Result result = M3Limits.apply(List.of(game));

		assertThat(result.changed()).isFalse();
		assertThat(result.games().getFirst().getCodes()).extracting(Code::getTitle).containsExactly("Max Money");
	}

	@Test
	void apply_placeholderCodesAndTheFolderTheyEmpty_leavesThemOutAndListsThem() {
		Game game = game("Mario Kart DS (USA)");
		game.getCodes().add(code("Item Modifier", false, "0200???? 00000063"));
		var templates = folder("Templates", false);
		templates.getCodes().add(code("Character Modifier", false, "02000004 000000XX"));
		game.getFolders().add(templates);

		M3Limits.Result result = M3Limits.apply(List.of(game));

		assertThat(result.leftOut()).containsExactly(
				"Mario Kart DS (USA): Item Modifier",
				"Mario Kart DS (USA): Character Modifier"
		);
		assertThat(result.emptiedByLeftOut()).containsExactly("Mario Kart DS (USA): Templates");
		assertThat(result.games().getFirst().getCodes()).extracting(Code::getTitle).containsExactly("Max Money");
		assertThat(result.games().getFirst().getFolders()).isEmpty();
	}

	@Test
	void apply_folderEmptyToBeginWith_leavesItOutAndListsIt() {
		Game game = game("Mario Kart DS (USA)");
		game.getFolders().add(folder("New Folder", false));

		M3Limits.Result result = M3Limits.apply(List.of(game));

		assertThat(result.changed()).isTrue();
		assertThat(result.emptyToBeginWith()).containsExactly("Mario Kart DS (USA): New Folder");
		assertThat(result.emptiedByLeftOut()).isEmpty();
		assertThat(result.games().getFirst().getFolders()).isEmpty();
	}

	@Test
	void apply_whatTheFileHasNoPlaceFor_listsEachButKeepsTheCodes() {
		Game game = game("한국어 게임");
		game.setMasterCode(new ArrayList<>(List.of("1234ABCD", "00000001")));
		var weather = folder("Weather", true);
		weather.getCodes().add(code("Sunny", true, "02000004 00000001"));
		game.getFolders().add(weather);

		M3Limits.Result result = M3Limits.apply(List.of(game));

		assertThat(result.masterCodes()).containsExactly("한국어 게임");
		assertThat(result.oneAtATime()).containsExactly("한국어 게임: Weather");
		assertThat(result.switchedOn()).containsExactly("한국어 게임: Sunny");
		assertThat(result.lostText()).containsExactly("한국어 게임");
		assertThat(result.leftOut()).isEmpty();
		assertThat(result.games().getFirst().getFolders().getFirst().getCodes()).hasSize(1);
	}

	@Test
	void held_games_matchWhatApplyKeeps() {
		Game game = game("한국어 게임");
		game.getCodes().add(code("Item Modifier", false, "0200???? 00000063"));

		var templates = folder("Templates", false);
		templates.getCodes().add(code("Character Modifier", false, "02000004 000000XX"));
		game.getFolders().add(templates);
		game.getFolders().add(folder("New Folder", false));

		var weather = folder("Weather", true);
		weather.getCodes().add(code("Sunny", true, "02000004 00000001"));
		game.getFolders().add(weather);

		Game held = M3Limits.held(List.of(game)).getFirst();
		Game applied = M3Limits.apply(List.of(game)).games().getFirst();

		assertThat(held.getCodes())
				.isEqualTo(applied.getCodes())
				.extracting(Code::getTitle)
				.containsExactly("Max Money");
		assertThat(held.getFolders()).extracting(Folder::getTitle).containsExactly("Weather");
		assertThat(applied.getFolders()).extracting(Folder::getTitle).containsExactly("Weather");
	}

	@Test
	void apply_halfWidthKatakana_isntLostText() {
		Game game = game("Game");
		game.getCodes().getFirst().setComment("ﾃﾞｼﾖｳ");

		assertThat(M3Limits.apply(List.of(game)).lostText()).isEmpty();
	}

	@Test
	void apply_games_leavesThemAsTheyWere() {
		Game game = game("Mario Kart DS (USA)");
		game.getCodes().add(code("Item Modifier", false, "0200???? 00000063"));

		M3Limits.apply(List.of(game));

		assertThat(game.getCodes()).hasSize(2);
	}

	private static Game game(String title) {
		var game = new Game();
		game.setTitle(title);
		game.setSerial("AMCE");
		game.setCrc32("12345678");
		game.setMasterCode(new ArrayList<>(CheatFileConstants.NULL_MASTER_CODE));
		game.getCodes().add(code("Max Money", false, "02000000 0001869F"));
		return game;
	}

	private static Folder folder(String title, boolean modifier) {
		var folder = new Folder();
		folder.setTitle(title);
		folder.setModifier(modifier);
		folder.setSortOrder(1);
		return folder;
	}

	private static Code code(String title, boolean enabled, String line) {
		var code = new Code();
		code.setTitle(title);
		code.setEnabled(enabled);
		code.setCodeLines(new ArrayList<>(List.of(line)));
		return code;
	}
}
