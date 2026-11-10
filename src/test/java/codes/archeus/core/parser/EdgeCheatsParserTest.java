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

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class EdgeCheatsParserTest {
	/** The codes switched on in the enabled fixtures, as game serial, then folder and code. */
	private static final List<String> SWITCHED_ON = List.of(
			"APHE: Miscellaneous Codes / 100% Recruit Rate",
			"APHE: Experience Multiplier Codes / x4",
			"VS3E: Refill Hunger Bar"
	);

	@Test
	void parse_edgeCheatsFile_returnsExpectedGames() throws IOException {
		List<Game> games = parse("/cheats.dat");

		assertThat(games).extracting(Game::getSerial).containsExactly("BJDE", "APHE", "VS3E", "A3YE");

		Game jeopardy = games.getFirst();
		assertThat(jeopardy.getCrc32()).isEqualTo("D4EC931D");
		assertThat(jeopardy.getTitle()).isEqualTo("Jeopardy! (USA)");
		assertThat(jeopardy.getCodes()).hasSize(5);
		assertThat(jeopardy.getFolders()).hasSize(1);

		Code alwaysCorrect = jeopardy.getCodes().getFirst();
		assertThat(alwaysCorrect.getTitle()).isEqualTo("Always Correct Answer");
		assertThat(alwaysCorrect.getComment()).isEqualTo("By Demonic722");
		assertThat(alwaysCorrect.getCodeLines()).hasSize(4);

		Code getMoney = jeopardy.getCodes().get(1);
		assertThat(getMoney.getTitle()).isEqualTo("Get Money From Correct And Incorrect Answers");
		assertThat(getMoney.getComment()).isEqualTo("By Demonic722 | Doesn't matter who answers.");
	}

	@Test
	void parse_evoCheatsFile_returnsExpectedGames() throws IOException {
		List<Game> games = parse("/user.evoCheats");

		assertThat(games).extracting(Game::getSerial).containsExactly("BJDE", "APHE", "VS3E", "A3YE");
		assertThat(games.getFirst().getTitle()).isEqualTo("Jeopardy! (USA)");
		assertThat(games.getFirst().getCodes()).hasSize(5);
	}

	@Test
	void parse_edgeFileWithFolders_readsTheFoldersAndCodes() throws IOException {
		Game pokemon = pokemon(parse("/cheats.dat"));

		assertThat(pokemon.getTitle()).isEqualTo("Pokemon Mystery Dungeon - Blue Rescue Team (USA)");
		assertThat(pokemon.getCodes()).isEmpty();
		assertThat(pokemon.getFolders())
				.extracting(Folder::getTitle, folder -> folder.getCodes().size())
				.containsExactly(
						tuple("Miscellaneous Codes", 5),
						tuple("Dungeon Codes", 3),
						tuple("Dungeon Party Codes", 2),
						tuple("Experience Multiplier Codes", 8)
				);
	}

	@Test
	void parse_edgeFilePokemon_readsOnlyItsLastFolderAsAModifier() throws IOException {
		Game pokemon = pokemon(parse("/cheats.dat"));

		assertThat(pokemon.getFolders()).extracting(Folder::isModifier).containsExactly(false, false, false, true);
	}

	@Test
	void parse_edgeFile_switchesNoCodeOn() throws IOException {
		assertThat(switchedOn(parse("/cheats.dat"))).isEmpty();
	}

	@Test
	void parse_edgeEnabledFile_switchesOnExactlyTheCodesSavedOn() throws IOException {
		assertThat(switchedOn(parse("/cheats-enabled.dat"))).containsExactlyElementsOf(SWITCHED_ON);
	}

	@Test
	void parse_edgeEnabledFileModifierFolder_switchesOnOnlyTheChosenCode() throws IOException {
		Folder multiplier = pokemon(parse("/cheats-enabled.dat")).getFolders().getLast();

		assertThat(multiplier.getCodes())
				.filteredOn(Code::isEnabled)
				.extracting(Code::getTitle)
				.containsExactly("x4");
	}

	@Test
	void parse_evoEnabledFile_switchesOnExactlyTheCodesSavedOn() throws IOException {
		assertThat(switchedOn(parse("/user-enabled.evoCheats"))).containsExactlyElementsOf(SWITCHED_ON);
	}

	private List<Game> parse(String resource) throws IOException {
		try (InputStream in = getClass().getResourceAsStream(resource)) {
			assertThat(in).isNotNull();
			return resource.endsWith(".evoCheats")
					? new EvoCheatsParser().parse(in).games()
					: new EdgeCheatsParser().parse(in).games();
		}
	}

	private static Game pokemon(List<Game> games) {
		return games.stream().filter(g -> "APHE".equals(g.getSerial())).findFirst().orElseThrow();
	}

	private static List<String> switchedOn(List<Game> games) {
		List<String> on = new ArrayList<>();

		for (Game game : games) {
			for (Folder folder : game.getFolders()) {
				for (Code code : folder.getCodes()) {
					if (code.isEnabled()) {
						on.add(game.getSerial() + ": " + folder.getTitle() + " / " + code.getTitle());
					}
				}
			}

			for (Code code : game.getCodes()) {
				if (code.isEnabled()) {
					on.add(game.getSerial() + ": " + code.getTitle());
				}
			}
		}

		return on;
	}
}
