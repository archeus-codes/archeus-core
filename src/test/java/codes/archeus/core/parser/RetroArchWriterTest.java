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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

class RetroArchWriterTest {

	@Test
	void write_topLevelCodesAndFolders_writesTheCodesFirstThenEachFolderUnderItsHeading() throws IOException {
		var game = new Game();
		var weather = folder("Weather Mod Codes");
		weather.getCodes().add(code("Sunny", false, "12345678 00000001", "D2000000 00000000"));
		game.getFolders().add(weather);
		game.getCodes().add(code("Infinite Health", true, "52007DEC AA000018"));

		assertThat(write(game)).isEqualTo("""
				cheats = 3

				cheat0_desc = "Infinite Health"
				cheat0_code = "52007DEC+AA000018"
				cheat0_enable = true

				cheat1_desc = "Weather Mod Codes"

				cheat2_desc = "Sunny"
				cheat2_code = "12345678+00000001+D2000000+00000000"
				cheat2_enable = false
				""");
	}

	@Test
	void write_placeholderCode_leavesItOut() throws IOException {
		var game = new Game();
		game.getCodes().add(code("Item Modifier", false, "0000DD58 0000????"));
		game.getCodes().add(code("Max Money", false, "02000000 0001869F"));

		assertThat(write(game)).startsWith("cheats = 1\n\ncheat0_desc = \"Max Money\"");
	}

	@Test
	void write_codeWithNoLines_leavesItOut() throws IOException {
		var game = new Game();
		game.getCodes().add(code("Datel", false));

		assertThat(write(game)).isEqualTo("cheats = 0\n");
	}

	@Test
	void write_noCodes_writesAnEmptyCheatList() throws IOException {
		assertThat(write(new Game())).isEqualTo("cheats = 0\n");
	}

	@Test
	void description_doubleQuotesAndLineBreaks_makesThemSafeForRetroArch() {
		assertThat(RetroArchWriter.description("Press \"A\" to\r\nwarp")).isEqualTo("Press 'A' to warp");
	}

	@Test
	void description_backslashAtTheEnd_dropsIt() {
		assertThat(RetroArchWriter.description("C:\\Games\\")).isEqualTo("C:\\Games");
	}

	@Test
	void description_blankTitle_givesAFallbackName() {
		assertThat(RetroArchWriter.description("  ")).isEqualTo(RetroArchWriter.UNTITLED);
	}

	@Test
	void fileName_game_usesTheTitleSerialAndCrc32() {
		var game = new Game();
		game.setTitle("Animal Crossing - Wild World (USA)");
		game.setSerial("ADME");
		game.setCrc32("8F7851CB");

		assertThat(RetroArchWriter.fileName(game)).isEqualTo("Animal Crossing - Wild World (USA) [ADME 8F7851CB].cht");
	}

	@Test
	void write_thenParse_readsBackTheSameFoldersAndCodes() throws IOException {
		var game = new Game();
		game.getCodes().add(code("Infinite Health", true, "52007DEC AA000018"));
		var weather = folder("Weather Mod Codes");
		weather.getCodes().add(code("Sunny", false, "12345678 00000001", "D2000000 00000000"));
		weather.getCodes().add(code("Snow", false, "12345678 00000002"));
		game.getFolders().add(weather);
		game.getFolders().add(folder("Empty"));

		Game parsed = new RetroArchParser().parse(new ByteArrayInputStream(write(game).getBytes(StandardCharsets.UTF_8)));

		assertThat(parsed.getCodes()).extracting(Code::getTitle, Code::getCodeLines, Code::isEnabled)
				.containsExactly(Tuple.tuple("Infinite Health", List.of("52007DEC AA000018"), true));
		assertThat(parsed.getFolders()).extracting(Folder::getTitle).containsExactly("Weather Mod Codes", "Empty");
		assertThat(parsed.getFolders().getFirst().getCodes()).extracting(Code::getTitle).containsExactly("Sunny", "Snow");
		assertThat(parsed.getFolders().getFirst().getCodes().getFirst().getCodeLines())
				.containsExactly("12345678 00000001", "D2000000 00000000");
	}

	private static String write(Game game) throws IOException {
		var out = new ByteArrayOutputStream();
		new RetroArchWriter().write(game, out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static Folder folder(String title) {
		var folder = new Folder();
		folder.setTitle(title);
		return folder;
	}

	private static Code code(String title, boolean enabled, String... lines) {
		var code = new Code();
		code.setTitle(title);
		code.setEnabled(enabled);
		code.setCodeLines(new ArrayList<>(List.of(lines)));
		return code;
	}
}
