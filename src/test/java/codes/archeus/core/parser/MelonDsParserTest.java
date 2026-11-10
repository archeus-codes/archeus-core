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

class MelonDsParserTest {
	@Test
	void parse_fileAsMelonDsSavesIt_readsFoldersCodesAndDescriptions() throws IOException {
		Game game = parse("""
				CAT 1 Weather Mod Codes
				DESC Choose one.

				CODE 0 Sunny
				12345678 00000001

				ROOT

				# By Datel
				CODE 1 Infinite Health
				DESC Hold L
				52007dec aa000018
				""");

		assertThat(game.getFolders()).singleElement().satisfies(folder -> {
			assertThat(folder.getTitle()).isEqualTo("Weather Mod Codes");
			assertThat(folder.isModifier()).isTrue();
			assertThat(folder.getComment()).isEqualTo("Choose one.");
			assertThat(folder.getCodes()).singleElement().satisfies(code -> assertThat(code.isEnabled()).isFalse());
		});
		assertThat(game.getCodes()).singleElement().satisfies(code -> {
			assertThat(code.getTitle()).isEqualTo("Infinite Health");
			assertThat(code.getComment()).isEqualTo("Hold L");
			assertThat(code.getCodeLines()).containsExactly("52007DEC AA000018");
		});
		assertThat(game.getTopLevelEntriesInOrder()).first().isEqualTo(game.getFolders().getFirst());
	}

	@Test
	void parse_olderCategoryLine_readsTheWholeRestAsTheName() throws IOException {
		Game game = parse("CAT Weather Mod Codes\nCODE 0 Sunny\n12345678 00000001\n");

		assertThat(game.getFolders().getFirst().getTitle()).isEqualTo("Weather Mod Codes");
		assertThat(game.getFolders().getFirst().isModifier()).isFalse();
	}

	@Test
	void parse_lowercaseCategoryOrCode_throws() {
		assertRefused("cat 0 Weather\n", "line 1 is a malformed CAT line");
		assertRefused("ROOT\ncode 1 Sunny\n", "line 2 is a malformed CODE line");
	}

	@Test
	void parse_lowercaseDescription_throws() {
		assertRefused("ROOT\nCODE 0 Sunny\ndesc Hold L\n", "line 3 is a DESC line melonDS ignores");
	}

	@Test
	void parse_newerCategoryLineWithNoName_throws() {
		assertRefused("CAT 1 \n", "line 1 is a malformed CAT line");
	}

	@Test
	void parse_oneChoiceFolderWithSeveralCodesOn_throws() {
		assertRefused(
				"CAT 1 Weather\nCODE 1 Sunny\n12345678 00000001\nCODE 1 Rain\n12345678 00000002\n",
				"folder \"Weather\" is one at a time but has several codes on"
		);
	}

	@Test
	void parse_codeBeforeAnyCategoryOrRoot_throws() {
		assertRefused("CODE 0 Sunny\n12345678 00000001\n", "line 1 is a CODE line with no CAT or ROOT");
	}

	@Test
	void parse_descriptionAfterRoot_throws() {
		assertRefused("ROOT\nDESC Orphan\n", "line 2 is a DESC line that belongs to nothing");
	}

	@Test
	void parse_dataLineThatIsNotHex_throws() {
		assertRefused("ROOT\nCODE 0 Item\n0000DD58 0000????\n", "line 3 is a malformed data line");
	}

	@Test
	void parse_dataLineWithNoCode_throws() {
		assertRefused("ROOT\n12345678 00000001\n", "line 2 is a data line with no CODE");
	}

	@Test
	void parse_lineLongerThanMelonDsReadsAtOnce_throws() {
		assertRefused("ROOT\n# " + "x".repeat(1100) + "\n", "line 2 is longer than melonDS reads as one line");
	}

	@Test
	void parse_nameMelonDsWouldCut_throws() {
		assertRefused("ROOT\nCODE 0 " + "x".repeat(128) + "\n", "line 2 has a name melonDS would cut");
	}

	@Test
	void parse_descriptionMelonDsWouldCut_throws() {
		assertRefused("ROOT\nCODE 0 Item\nDESC " + "x".repeat(256) + "\n", "line 3 has a description melonDS would cut");
	}

	private static Game parse(String file) throws IOException {
		return new MelonDsParser().parse(new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)));
	}

	private static void assertRefused(String file, String problem) {
		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining(problem);
	}
}
