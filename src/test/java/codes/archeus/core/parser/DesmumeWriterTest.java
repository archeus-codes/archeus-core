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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

class DesmumeWriterTest {

	@Test
	void write_topLevelCodesAndFolders_writesThemInTheGamesOrderWithTheFolderInEachDescription() throws IOException {
		var game = game("AJLE");
		game.getCodes().add(code("Infinite Health", true, 0, "52007DEC AA000018", "D2000000 00000000"));

		var weather = folder("Weather Mod Codes", "Choose one.", 1);
		weather.getCodes().add(code("Sunny", false, 0, "12345678 00000001"));
		game.getFolders().add(weather);

		game.getCodes().add(code("Max Money", false, 2, "02000000 0001869F"));

		assertThat(write(game)).isEqualTo("""
				; DeSmuME cheats file. VERSION 2.000\r
				Serial=NTR-AJLE-USA\r
				\r
				; cheats list\r
				AR 1 52007DECAA000018,D200000000000000 ;Infinite Health\r
				AR 0 1234567800000001 ;Weather Mod Codes [Choose one.]: Sunny\r
				AR 0 020000000001869F ;Max Money\r
				\r
				""");
	}

	@Test
	void write_desmumesOwnFile_matchesItApartFromTheHeaderTitle() throws IOException {
		var game = game("AJLE");
		game.getCodes().add(code("One Hit Kills", false, 0, "02007424 E3A01000"));
		game.getCodes().add(code(
				"Infinite Health",
				true,
				1,
				"52007DEC AA000018",
				"02007DEC 11C100B0",
				"D2000000 00000000"
		));
		game.getCodes().add(code(
				"One Hit Kills original",
				true,
				2,
				"52007424 E1D210F0",
				"02007424 EBFFE2F5",
				"02008BB4 E0400000",
				"02000000 E3A01000",
				"02000004 E1C210B0",
				"02000008 E12FFF1E",
				"D2000000 00000000"
		));
		game.getCodes().add(code(
				"Infinite Energy",
				true,
				3,
				"52007E60 AA000019",
				"02007E60 11C100B4",
				"D2000000 00000000"
		));

		assertThat(write(game)).isEqualTo(justiceLeagueHeroes().replace("Name=JUSTLEAGHERO\r\n", ""));
	}

	@Test
	void write_oneAtATimeFolderWithTwoCodesOn_writesOnlyTheFirstAsOn() throws IOException {
		var game = game("AJLE");
		var weather = folder("Weather Mod Codes", "", 0);
		weather.setModifier(true);
		weather.getCodes().add(code("Sunny", true, 0, "12345678 00000001"));
		weather.getCodes().add(code("Rain", true, 1, "12345678 00000002"));
		game.getFolders().add(weather);

		assertThat(write(game)).contains("AR 1 1234567800000001 ;", "AR 0 1234567800000002 ;");
	}

	@Test
	void write_placeholderCode_leavesItOut() throws IOException {
		var game = game("AJLE");
		game.getCodes().add(code("Item Modifier", false, 0, "0000DD58 0000????"));
		game.getCodes().add(code("Max Money", false, 1, "02000000 0001869F"));

		assertThat(codeLines(write(game))).containsExactly("AR 0 020000000001869F ;Max Money");
	}

	@Test
	void write_codeWithNoLines_leavesItOut() throws IOException {
		var game = game("AJLE");
		game.getCodes().add(code("Datel", false, 0));

		assertThat(codeLines(write(game))).isEmpty();
	}

	@Test
	void write_codeOverMaxLines_leavesItOut() throws IOException {
		var game = game("AJLE");
		game.getCodes().add(code("Huge", false, 0, lines(DesmumeWriter.MAX_LINES + 1)));
		game.getCodes().add(code("Largest", false, 1, lines(DesmumeWriter.MAX_LINES)));

		assertThat(codeLines(write(game))).singleElement().asString().endsWith(";Largest");
	}

	@Test
	void description_folderWithNoteAndCodeWithComment_joinsThemAsDesmumesImportDoes() {
		Folder folder = folder("Weather Mod Codes", "Choose one.", 0);

		assertThat(DesmumeWriter.description(folder, comment(code("Sunny", false, 0), "By Datel | Clear skies")))
				.isEqualTo("Weather Mod Codes [Choose one.]: Sunny | By Datel | Clear skies");
	}

	@Test
	void description_emptyParts_leavesOutTheirSeparators() {
		assertThat(DesmumeWriter.description(folder("Weather", "", 0), code("Sunny", false, 0)))
				.isEqualTo("Weather: Sunny");
		assertThat(DesmumeWriter.description(folder("", "Choose one.", 0), code("Sunny", false, 0)))
				.isEqualTo("[Choose one.]: Sunny");
		assertThat(DesmumeWriter.description(comment(code("", false, 0), "By Datel"))).isEqualTo("By Datel");
		assertThat(DesmumeWriter.description(folder("Weather", "", 0), code("", false, 0)))
				.isEqualTo("Weather:");
	}

	@Test
	void description_lineBreaks_joinsEachPartOntoOneLine() {
		Folder folder = folder("Weather", "Choose\r\none.", 0);

		assertThat(DesmumeWriter.description(folder, comment(code("Sunny", false, 0), "Clear\n  skies")))
				.isEqualTo("Weather [Choose one.]: Sunny | Clear skies");
	}

	@Test
	void description_overMaxBytes_cutsItAtAWordWithTheCutMark() {
		String comment = "word ".repeat(300).strip();

		String description = DesmumeWriter.description(comment(code("Sunny", false, 0), comment));

		assertThat(description.getBytes(StandardCharsets.UTF_8))
				.hasSizeLessThanOrEqualTo(DesmumeWriter.MAX_DESCRIPTION_BYTES);
		assertThat(description).startsWith("Sunny | word word").endsWith("word…");
	}

	@Test
	void description_folderCodeWithNoTitle_leavesOutTheBar() {
		assertThat(DesmumeWriter.description(folder("Weather", "", 0), comment(code("", false, 0), "By Datel")))
				.isEqualTo("Weather: By Datel");
	}

	@Test
	void description_nullFolder_throws() {
		assertThatThrownBy(() -> DesmumeWriter.description(null, code("Sunny", false, 0)))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void serial_gameCode_addsTheRegionItsLastLetterStandsFor() {
		assertThat(DesmumeWriter.serial("AJLE")).isEqualTo("NTR-AJLE-USA");
		assertThat(DesmumeWriter.serial("YIHP")).isEqualTo("NTR-YIHP-EUR");
		assertThat(DesmumeWriter.serial("BOJJ")).isEqualTo("NTR-BOJJ-JPN");
	}

	@Test
	void serial_unknownRegionLetter_writesQuestionMarks() {
		assertThat(DesmumeWriter.serial("ABCZ")).isEqualTo("NTR-ABCZ-???");
	}

	@Test
	void fileName_game_usesTheTitleSerialAndCrc32() {
		var game = game("ADME");
		game.setTitle("Animal Crossing - Wild World (USA)");
		game.setCrc32("8F7851CB");

		assertThat(DesmumeWriter.fileName(game)).isEqualTo("Animal Crossing - Wild World (USA) [ADME 8F7851CB].dct");
	}

	@Test
	void write_thenParse_readsBackEachCodeWithItsDescription() throws IOException {
		var game = game("AJLE");
		game.getCodes().add(code("Infinite Health", true, 0, "52007DEC AA000018"));
		var weather = folder("Weather Mod Codes", "Choose one.", 1);
		weather.getCodes().add(code("Sunny", false, 0, "12345678 00000001", "D2000000 00000000"));
		game.getFolders().add(weather);

		Game parsed = new DesmumeParser().parse(new ByteArrayInputStream(write(game).getBytes(StandardCharsets.UTF_8)));

		assertThat(parsed.getFolders()).isEmpty();
		assertThat(parsed.getCodes()).extracting(Code::getTitle, Code::getCodeLines, Code::isEnabled).containsExactly(
				Tuple.tuple("Infinite Health", List.of("52007DEC AA000018"), true),
				Tuple.tuple(
						"Weather Mod Codes [Choose one.]: Sunny",
						List.of("12345678 00000001", "D2000000 00000000"),
						false
				)
		);
	}

	private static String write(Game game) throws IOException {
		var out = new ByteArrayOutputStream();
		new DesmumeWriter().write(game, out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static List<String> codeLines(String file) {
		return file.lines().filter(line -> line.startsWith("AR ")).toList();
	}

	/**
	 * DeSmuME's own file for Justice League Heroes (USA).
	 */
	private String justiceLeagueHeroes() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/desmume/justice-league-heroes.dct")) {
			assertThat(in).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Game game(String serial) {
		var game = new Game();
		game.setTitle("Test Game");
		game.setSerial(serial);
		game.setCrc32("00000000");
		return game;
	}

	private static Folder folder(String title, String comment, int sortOrder) {
		var folder = new Folder();
		folder.setTitle(title);
		folder.setComment(comment);
		folder.setSortOrder(sortOrder);
		return folder;
	}

	private static Code code(String title, boolean enabled, int sortOrder, String... lines) {
		return code(title, enabled, sortOrder, List.of(lines));
	}

	private static Code code(String title, boolean enabled, int sortOrder, List<String> lines) {
		var code = new Code();
		code.setTitle(title);
		code.setEnabled(enabled);
		code.setSortOrder(sortOrder);
		code.setCodeLines(new ArrayList<>(lines));
		return code;
	}

	private static List<String> lines(int count) {
		return Collections.nCopies(count, "02000000 00000000");
	}

	private static Code comment(Code code, String comment) {
		code.setComment(comment);
		return code;
	}
}
