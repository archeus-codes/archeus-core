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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Game;

class UsayPartsTest {
	@Test
	void split_gamesThatFit_returnsThemAllInOnePart() {
		List<Game> games = games("!Credits", "1942", "Apple", "Zebra");

		List<UsayParts.Part> parts = UsayParts.split(games);

		assertThat(parts).hasSize(1);
		assertThat(parts.getFirst().name()).isEqualTo("#-Z");
		assertThat(parts.getFirst().games()).isEqualTo(games);
	}

	@Test
	void split_moreGamesThanFit_splitsBetweenLettersWithTheBangEntriesInEach() {
		List<Game> games = games("!Credits", "Apple", "Banana", "Cherry", "Mango", "Zebra");

		List<UsayParts.Part> parts = UsayParts.split(games, 4);

		assertThat(parts).extracting(UsayParts.Part::name).containsExactly("A-C", "M-Z");
		assertThat(titles(parts.get(0))).containsExactly("!Credits", "Apple", "Banana", "Cherry");
		assertThat(titles(parts.get(1))).containsExactly("!Credits", "Mango", "Zebra");
	}

	@Test
	void split_titlesNotStartingWithALetter_goUnderHashBeforeTheLetters() {
		List<Game> games = games("007 Racing", ".hack", "apple", "Banana");

		List<UsayParts.Part> parts = UsayParts.split(games, 2);

		assertThat(parts).extracting(UsayParts.Part::name).containsExactly("#", "A-B");
		assertThat(titles(parts.get(0))).containsExactly("007 Racing", ".hack");
		assertThat(titles(parts.get(1))).containsExactly("apple", "Banana");
	}

	@Test
	void split_partHoldingOneLetter_isNamedForIt() {
		List<Game> games = games("Mango", "Melon", "Zebra");

		assertThat(UsayParts.split(games, 2)).extracting(UsayParts.Part::name).containsExactly("M", "Z");
	}

	@Test
	void split_letterWithMoreGamesThanAPartHolds_isRefused() {
		List<Game> games = games("!Credits", "Mango", "Melon");

		assertThatThrownBy(() -> UsayParts.split(games, 2))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("2 games start with M, more than a part of 2 games can hold");
	}

	@Test
	void split_noGames_returnsOneEmptyPart() {
		List<UsayParts.Part> parts = UsayParts.split(List.of());

		assertThat(parts).hasSize(1);
		assertThat(parts.getFirst().games()).isEmpty();
	}

	private static List<Game> games(String... titles) {
		return Arrays.stream(titles).map(title -> {
			var game = new Game();
			game.setTitle(title);
			return game;
		}).toList();
	}

	private static List<String> titles(UsayParts.Part part) {
		return part.games().stream().map(Game::getTitle).toList();
	}
}
