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

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.parser.MelonDsWriter.Description;

class MelonDsWriterTest {
	private static final String LONG_COMMENT = "(Press Start+Select to put a level 50 dragon in Box 3 Slot #1): "
			+ "(Species: Dragon) - (Level: 50) - (Nature: Calm) - (Held Item: Potion) - (Ability: Blaze): "
			+ "Press and hold L while the box opens so the game saves the new creature before the next battle "
			+ "starts in the cave.";

	@Test
	void write_rootCodesAndFolders_writesThemInOrderInMelonDsFormat() throws IOException {
		var game = new Game();
		game.getCodes().add(code(0, "Infinite Health", "By Datel", true, "52007DEC AA000018", "02007DEC 11C100B0"));
		var weather = folder("Weather Mod Codes", "Choose one.", true);
		weather.setSortOrder(1);
		weather.getCodes().add(code(0, "Sunny", "", false, "12345678 00000001"));
		game.getFolders().add(weather);
		game.getCodes().add(code(2, "Moon Jump", "", false, "22000000 00000001"));

		assertThat(write(game)).isEqualTo("""
				ROOT

				CODE 1 Infinite Health
				DESC By Datel
				52007DEC AA000018
				02007DEC 11C100B0

				CAT 1 Weather Mod Codes
				DESC Choose one.

				CODE 0 Sunny
				12345678 00000001

				ROOT

				CODE 0 Moon Jump
				22000000 00000001

				""");
	}

	@Test
	void write_placeholderCode_leavesItOut() throws IOException {
		var game = new Game();
		game.getCodes().add(code(0, "Item Modifier", "", false, "0000DD58 0000????"));
		game.getCodes().add(code(1, "Moon Jump", "", false, "22000000 00000001"));

		assertThat(write(game)).doesNotContain("Item Modifier").contains("CODE 0 Moon Jump");
	}

	@Test
	void write_oneChoiceFolderWithSeveralCodesOn_leavesOnlyTheFirstOn() throws IOException {
		var game = new Game();
		var weather = folder("Weather Mod Codes", "", true);
		weather.getCodes().add(code(0, "Sunny", "", true, "12345678 00000001"));
		weather.getCodes().add(code(1, "Rain", "", true, "12345678 00000002"));
		game.getFolders().add(weather);

		assertThat(write(game)).contains("CODE 1 Sunny").contains("CODE 0 Rain");
	}

	@Test
	void name_blankTitle_givesAFallbackName() {
		assertThat(MelonDsWriter.name("  ")).isEqualTo(MelonDsWriter.UNTITLED);
	}

	@Test
	void describe_creditAndCommentThatFit_keepsBothInTheDescription() {
		assertThat(MelonDsWriter.describe("By Datel | Hold L"))
				.isEqualTo(new Description("By Datel | Hold L", List.of()));
	}

	@Test
	void describe_noComment_writesNoDescription() {
		assertThat(MelonDsWriter.describe("  ")).isEqualTo(new Description(null, List.of()));
	}

	@Test
	void describe_lineBreaks_joinsThemIntoOneLine() {
		assertThat(MelonDsWriter.describe("Hold L\r\n  then press A").text()).isEqualTo("Hold L then press A");
	}

	@Test
	void describe_tooLongOnlyWithTheCredit_movesTheCreditToANote() {
		String comment = "x".repeat(250);

		assertThat(MelonDsWriter.describe("By Datel | " + comment))
				.isEqualTo(new Description(comment, List.of("By Datel")));
	}

	@Test
	void describe_commentTooLongOnItsOwn_cutsItAtAWordAndKeepsEverythingInNotes() {
		Description description = MelonDsWriter.describe("By Contributor | " + LONG_COMMENT);

		assertThat(description.notes()).containsExactly("By Contributor", LONG_COMMENT);
		assertThat(description.text()).endsWith("…");
		assertThat(bytes(description.text())).isLessThanOrEqualTo(MelonDsWriter.MAX_DESCRIPTION_BYTES);
		assertThat(LONG_COMMENT).startsWith(description.text().replace("…", ""));
		assertThat(description.text()).doesNotEndWith(" …");
	}

	@Test
	void name_overTheLimitInMultiByteText_cutsOnACharacterBoundary() {
		String title = "ポケモン".repeat(20);

		String name = MelonDsWriter.name(title);

		assertThat(bytes(name)).isLessThanOrEqualTo(MelonDsWriter.MAX_NAME_BYTES);
		assertThat(name).endsWith("…").doesNotContain("�");
		assertThat(title).startsWith(name.replace("…", ""));
	}

	@Test
	void fileName_titleWithUnsafeCharacters_replacesThemAndAddsTheSerialAndCrc32() {
		var game = new Game();
		game.setTitle("Pokemon: Version Noire? (France)");
		game.setSerial("IRBF");
		game.setCrc32("4F15BB44");

		assertThat(MelonDsWriter.fileName(game)).isEqualTo("Pokemon_ Version Noire_ (France) [IRBF 4F15BB44].mch");
	}

	@Test
	void write_longNotes_keepsEveryLineShortEnoughForMelonDs() throws IOException {
		var game = new Game();
		game.getCodes().add(code(0, "Notes", "word ".repeat(400), false, "22000000 00000001"));

		String file = write(game);

		assertThat(file.lines())
				.allSatisfy(line -> assertThat(bytes(line)).isLessThanOrEqualTo(MelonDsParser.MAX_LINE_BYTES));
		assertThat(file.lines().filter(line -> line.startsWith("# "))).hasSizeGreaterThan(1);
	}

	@Test
	void write_thenParse_readsBackTheSameFoldersAndCodes() throws IOException {
		var game = new Game();
		game.getCodes().add(code(0, "Female Lead", "By Contributor | " + LONG_COMMENT, true, "5216125C EBFAFC56"));
		var folder = folder("Slot 1 Item Modifier codes (1/8)", "", false);
		folder.getCodes().add(code(0, "Potion", "", false, "12345678 00000001", "D2000000 00000000"));
		game.getFolders().add(folder);

		Game parsed = new MelonDsParser().parse(new ByteArrayInputStream(write(game).getBytes(StandardCharsets.UTF_8)));

		Code female = parsed.getCodes().getFirst();
		assertThat(female.getTitle()).isEqualTo("Female Lead");
		assertThat(female.isEnabled()).isTrue();
		assertThat(female.getComment()).isEqualTo(MelonDsWriter.describe("By Contributor | " + LONG_COMMENT).text());
		assertThat(female.getCodeLines()).containsExactly("5216125C EBFAFC56");
		Folder parsedFolder = parsed.getFolders().getFirst();
		assertThat(parsedFolder.getTitle()).isEqualTo("Slot 1 Item Modifier codes (1/8)");
		assertThat(parsedFolder.isModifier()).isFalse();
		assertThat(parsedFolder.getCodes().getFirst().getCodeLines())
				.containsExactly("12345678 00000001", "D2000000 00000000");
	}

	private static String write(Game game) throws IOException {
		var out = new ByteArrayOutputStream();
		new MelonDsWriter().write(game, out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static Code code(int sortOrder, String title, String comment, boolean enabled, String... lines) {
		var code = new Code();
		code.setSortOrder(sortOrder);
		code.setTitle(title);
		code.setComment(comment);
		code.setEnabled(enabled);
		code.setCodeLines(new ArrayList<>(List.of(lines)));
		return code;
	}

	private static Folder folder(String title, String comment, boolean oneChoice) {
		var folder = new Folder();
		folder.setTitle(title);
		folder.setComment(comment);
		folder.setModifier(oneChoice);
		return folder;
	}

	private static int bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}
}
