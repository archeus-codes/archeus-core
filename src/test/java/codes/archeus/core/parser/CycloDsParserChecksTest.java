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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

class CycloDsParserChecksTest {
	@Test
	void parse_unchangedFile_readsEveryGame() throws IOException {
		assertThat(new EdgeCheatsParser().parse(written()).games())
				.extracting(Game::getSerial)
				.containsExactly("AAAA", "BBBB");
	}

	@Test
	void parse_byteChangedInTheGameIndex_throws() throws IOException {
		byte[] bytes = written();
		++bytes[indexStart()];

		assertThatThrownBy(() -> new EdgeCheatsParser().parse(bytes))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessage("cheats.dat fails its game index's MD5 check");
	}

	@Test
	void parse_byteChangedInAGamesData_throwsNamingTheGame() throws IOException {
		byte[] bytes = written();
		++bytes[indexStart() + 2 * 16 + 32 + 4];

		assertThatThrownBy(() -> new EdgeCheatsParser().parse(bytes))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessage("cheats.dat game 1 of 2 (AAAA 0BADC0DE) fails its MD5 check");
	}

	@Test
	void parse_byteChangedInTheEnabledFlags_throwsNamingTheGame() throws IOException {
		byte[] bytes = written();
		++bytes[bytes.length - 1];

		assertThatThrownBy(() -> new EdgeCheatsParser().parse(bytes))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessage("cheats.dat game 2 of 2 (BBBB 0BADC0DE) fails its enabled flags' MD5 check");
	}

	@Test
	void parse_cutShort_throws() throws IOException {
		byte[] bytes = written();

		assertThatThrownBy(() -> new EdgeCheatsParser().parse(Arrays.copyOf(bytes, indexStart() + 8)))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageStartingWith("cheats.dat says it holds 2 games");
	}

	@Test
	void parse_evoCheatsFileChanged_namesItsOwnFile() throws IOException {
		var out = new ByteArrayOutputStream();
		new EvoCheatsWriter().write(new CheatFile(), games(), out);
		byte[] bytes = out.toByteArray();
		++bytes[bytes.length - 1];

		assertThatThrownBy(() -> new EvoCheatsParser().parse(bytes))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageStartingWith("user.evoCheats game 2 of 2");
	}

	private static byte[] written() throws IOException {
		var out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(new CheatFile(), games(), out);
		return out.toByteArray();
	}

	/**
	 * Where the encrypted game index starts in an EDGE file.
	 */
	private static int indexStart() {
		return CycloDsLayout.EDGE.skip() + CycloDsLayout.EDGE.headerSize();
	}

	private static List<Game> games() {
		return List.of(game("AAAA", "First"), game("BBBB", "Second"));
	}

	private static Game game(String serial, String title) {
		var code = new Code();
		code.setTitle("Infinite Health");
		code.setCodeLines(new ArrayList<>(List.of("12345678 00000001")));
		var game = new Game();
		game.setTitle(title);
		game.setSerial(serial);
		game.setCrc32("0BADC0DE");
		game.getCodes().add(code);
		return game;
	}
}
