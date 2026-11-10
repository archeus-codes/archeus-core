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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

class RetroArchParserTest {

	@Test
	void parse_codesBeforeAnyHeading_areTopLevel() throws IOException {
		Game game = parse("""
				cheats = 2

				cheat0_desc = "Infinite Health"
				cheat0_code = "52007DEC+AA000018"
				cheat0_enable = false

				cheat1_desc = "Weather Mod Codes"
				""");

		assertThat(game.getCodes()).hasSize(1);
		assertThat(game.getFolders()).hasSize(1);
		assertThat(game.getFolders().getFirst().getCodes()).isEmpty();
	}

	@Test
	void parse_noCheats_givesAnEmptyGame() throws IOException {
		Game game = parse("cheats = 0\n");

		assertThat(game.getCodes()).isEmpty();
		assertThat(game.getFolders()).isEmpty();
	}

	@Test
	void parse_countThatDoesNotMatch_fails() {
		assertThatThrownBy(() -> parse("""
				cheats = 2

				cheat0_desc = "Infinite Health"
				cheat0_code = "52007DEC+AA000018"
				cheat0_enable = false
				""")).isInstanceOf(UsercheatParserException.class).hasMessageContaining("ends where a line was expected");
	}

	@Test
	void parse_entriesOutOfOrder_fails() {
		assertThatThrownBy(() -> parse("""
				cheats = 1

				cheat1_desc = "Infinite Health"
				""")).isInstanceOf(UsercheatParserException.class).hasMessageContaining("isn't cheat 0");
	}

	@Test
	void parse_oddNumberOfWords_fails() {
		assertThatThrownBy(() -> parse("""
				cheats = 1

				cheat0_desc = "Infinite Health"
				cheat0_code = "52007DEC+AA000018+D2000000"
				cheat0_enable = false
				""")).isInstanceOf(UsercheatParserException.class).hasMessageContaining("odd number of words");
	}

	@Test
	void parse_descriptionEndingInABackslash_fails() {
		assertThatThrownBy(() -> parse("""
				cheats = 1

				cheat0_desc = "Infinite Health\\"
				""")).isInstanceOf(UsercheatParserException.class).hasMessageContaining("reads whole");
	}

	@Test
	void parse_lineAfterTheLastCheat_fails() {
		assertThatThrownBy(() -> parse("""
				cheats = 0

				cheat0_desc = "Infinite Health"
				""")).isInstanceOf(UsercheatParserException.class).hasMessageContaining("follows the last");
	}

	private static Game parse(String text) throws IOException {
		return new RetroArchParser().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}
}
