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

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.util.CheatFileConstants;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsercheatXmlWriterTest {
	
	@Test
	void roundTrip_r4cceFile_keepsTheTitle() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("My Cheat Database");

		ParseResult result = roundTrip(XmlFormat.R4CCE, cheatFile, List.of());

		assertThat(result.cheatFile().getTitle()).isEqualTo("My Cheat Database");
	}

	@Test
	void roundTrip_r4cceFile_keepsGameTitleSerialAndCrc32() throws IOException {
		var game = buildMinimalGame();
		game.setTitle("Pokemon Diamond (USA)");
		game.setSerial("ADAE");
		game.setCrc32("5CF3F098");

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		assertThat(result.games()).hasSize(1);
		var parsed = result.games().getFirst();
		assertThat(parsed.getTitle()).isEqualTo("Pokemon Diamond (USA)");
		assertThat(parsed.getSerial()).isEqualTo("ADAE");
		assertThat(parsed.getCrc32()).isEqualTo("5CF3F098");
	}
	
	@Test
	void roundTrip_actionReplayFile_keepsTheTitle() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("ARDS Database");

		ParseResult result = roundTrip(XmlFormat.ACTION_REPLAY, cheatFile, List.of());

		assertThat(result.cheatFile().getTitle()).isEqualTo("ARDS Database");
	}

	@Test
	void roundTrip_actionReplayFile_keepsTheGame() throws IOException {
		var game = buildMinimalGame();
		game.setTitle("Soul Silver (USA)");

		ParseResult result = roundTrip(XmlFormat.ACTION_REPLAY, new CheatFile(), List.of(game));

		assertThat(result.games()).hasSize(1);
		assertThat(result.games().getFirst().getTitle()).isEqualTo("Soul Silver (USA)");
	}
	
	@Test
	void roundTrip_r4cceMasterCode_comesBackFromTheMasterCheat() throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"DEADBEEF", "00000000",
				"00000000", "00000000",
				"00000000", "00000000"
		));

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsed = result.games().getFirst();
		assertThat(parsed.getMasterCode()).containsExactly(
				"00000000", "00000001",
				"DEADBEEF", "00000000",
				"00000000", "00000000",
				"00000000", "00000000"
		);
		assertThat(parsed.getCodes()).isEmpty();
	}

	@Test
	void write_masterCode_interleavesItsValuesAfterMaster() throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		));

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.R4CCE).write(new CheatFile(), List.of(game), baos);
		String xml = baos.toString();

		assertThat(xml).contains("<codes>master 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 023FF300 00000000 00000000 00000000 02000010 00000000 00000001</codes>");
	}

	@Test
	void write_standardMasterCode_omitsTheMasterCheat() throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(new ArrayList<>(CheatFileConstants.NULL_MASTER_CODE));

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.R4CCE).write(new CheatFile(), List.of(game), baos);
		String xml = baos.toString();

		assertThat(xml).doesNotContain("(M)MasterCode");
	}

	@Test
	void write_ardsSubscriptionWithActionReplayMasterCode_writesItAsTheMasterCheatInsteadOfTheFlashCartOne()
			throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		));
		game.getCodes().add(code("Master Code", "00000000 023F0000", "00000000 00000001", "00000000 023FFE88"));
		game.getCodes().add(code("Infinite Bells", "12345678 0000FFFF"));

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.ACTION_REPLAY).write(new CheatFile(), List.of(game), baos);
		String xml = baos.toString();

		assertThat(xml).containsSubsequence(
				"<name>(M)</name>",
				"<codes>master 00000000 023F0000 00000000 00000001 00000000 023FFE88</codes>"
		);
		assertThat(xml).doesNotContain("<name>Master Code</name>").doesNotContain("023FF300");
		assertThat(xml).contains("<name>Infinite Bells</name>");
	}

	@Test
	void write_actionReplayWithoutActionReplayMasterCode_writesNoMasterCheat() throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		));

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.ACTION_REPLAY).write(new CheatFile(), List.of(game), baos);

		assertThat(baos.toString()).doesNotContain("<name>(M)");
	}

	@Test
	void roundTrip_actionReplayMasterCode_comesBackAsTheSameCode() throws IOException {
		var game = buildMinimalGame();
		game.getCodes().add(code("Infinite Bells", "12345678 0000FFFF"));
		game.getCodes().add(code("Master Code", "00000000 023F0000", "00000000 00000001", "00000000 023FFE88"));

		var parsed = roundTrip(XmlFormat.ACTION_REPLAY, new CheatFile(), List.of(game)).games().getFirst();

		assertThat(parsed.getMasterCode()).isEmpty();
		assertThat(parsed.getCodes()).extracting(Code::getTitle).containsExactly("Master Code", "Infinite Bells");
		assertThat(parsed.getCodes().getFirst().getCodeLines())
				.containsExactly("00000000 023F0000", "00000000 00000001", "00000000 023FFE88");
	}

	@Test
	void roundTrip_r4cceWithActionReplayMasterCode_keepsTheFlashCartOneAndTheCode() throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		));
		game.getCodes().add(code("Master Code", "00000000 023F0000", "00000000 00000001", "00000000 023FFE88"));

		var parsed = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game)).games().getFirst();

		assertThat(parsed.getMasterCode()).containsExactly(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		);
		assertThat(parsed.getCodes()).extracting(Code::getTitle).containsExactly("Master Code");
	}

	@Test
	void write_emptyMasterCode_omitsTheMasterCheat() throws IOException {
		var game = buildMinimalGame();
		game.setMasterCode(List.of());

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.R4CCE).write(new CheatFile(), List.of(game), baos);

		assertThat(baos.toString()).doesNotContain("(M)MasterCode");
	}
	
	@Test
	void roundTrip_topLevelCode_keepsTitleAndLines() throws IOException {
		var code = new Code();
		code.setTitle("Infinite Lives");
		code.setCodeLines(List.of("12345678 9ABCDEF0", "DEADBEEF 00000001"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsedCode = result.games().getFirst().getCodes().getFirst();
		assertThat(parsedCode.getTitle()).isEqualTo("Infinite Lives");
		assertThat(parsedCode.getCodeLines()).containsExactly("12345678 9ABCDEF0", "DEADBEEF 00000001");
	}

	@Test
	void roundTrip_codeComment_isKept() throws IOException {
		var code = new Code();
		code.setTitle("Max Gold");
		code.setComment("Sets gold to maximum");
		code.setCodeLines(List.of("02000100 0001869F"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsedCode = result.games().getFirst().getCodes().getFirst();
		assertThat(parsedCode.getComment()).isEqualTo("Sets gold to maximum");
	}

	@Test
	void roundTrip_blankComment_omitsTheNote() throws IOException {
		var code = new Code();
		code.setTitle("No Comment Code");
		code.setComment("");
		code.setCodeLines(List.of("00000000 00000000"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsedCode = result.games().getFirst().getCodes().getFirst();
		assertThat(parsedCode.getComment()).isBlank();
	}
	
	@Test
	void roundTrip_modifierFolder_staysAModifier() throws IOException {
		var folder = new Folder();
		folder.setTitle("Speed Options");
		folder.setModifier(true);

		var game = buildMinimalGame();
		game.getFolders().add(folder);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsedFolder = result.games().getFirst().getFolders().getFirst();
		assertThat(parsedFolder.getTitle()).isEqualTo("Speed Options");
		assertThat(parsedFolder.isModifier()).isTrue();
	}

	@Test
	void roundTrip_standardFolder_staysStandard() throws IOException {
		var folder = new Folder();
		folder.setTitle("Misc Cheats");
		folder.setModifier(false);

		var game = buildMinimalGame();
		game.getFolders().add(folder);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsedFolder = result.games().getFirst().getFolders().getFirst();
		assertThat(parsedFolder.isModifier()).isFalse();
	}

	@Test
	void roundTrip_folderWithCodes_keepsItsCodes() throws IOException {
		var codeA = new Code();
		codeA.setTitle("Option A");
		codeA.setCodeLines(List.of("01000000 00000001"));

		var codeB = new Code();
		codeB.setTitle("Option B");
		codeB.setCodeLines(List.of("01000000 00000002"));

		var folder = new Folder();
		folder.setTitle("Speed");
		folder.setModifier(true);
		folder.getCodes().add(codeA);
		folder.getCodes().add(codeB);

		var game = buildMinimalGame();
		game.getFolders().add(folder);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		var parsedFolder = result.games().getFirst().getFolders().getFirst();
		assertThat(parsedFolder.getCodes()).hasSize(2);
		assertThat(parsedFolder.getCodes().getFirst().getTitle()).isEqualTo("Option A");
		assertThat(parsedFolder.getCodes().get(1).getTitle()).isEqualTo("Option B");
	}
	
	@Test
	void roundTrip_multipleGames_keepsTheirOrder() throws IOException {
		var game1 = buildMinimalGame();
		game1.setSerial("AAA1");
		game1.setTitle("Game One");

		var game2 = buildMinimalGame();
		game2.setSerial("BBB2");
		game2.setTitle("Game Two");

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game1, game2));

		assertThat(result.games()).hasSize(2);
		assertThat(result.games().getFirst().getTitle()).isEqualTo("Game One");
		assertThat(result.games().get(1).getTitle()).isEqualTo("Game Two");
	}

	@Test
	void write_anyFormat_putsTheCodelistAtTheRoot() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("DB");

		for (XmlFormat format : XmlFormat.values()) {
			var baos = new ByteArrayOutputStream();
			new UsercheatXmlWriter(format).write(cheatFile, List.of(), baos);

			assertThat(baos.toString())
					.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<codelist>\r\n\t<name>DB</name>")
					.doesNotContain("<subscription>");
		}
	}

	@Test
	void write_anyFormat_endsEveryLineWithCrlf() throws IOException {
		var game = buildMinimalGame();
		game.getCodes().add(code("Max Gold", "02000100 0001869F"));

		for (XmlFormat format : XmlFormat.values()) {
			var baos = new ByteArrayOutputStream();
			new UsercheatXmlWriter(format).write(new CheatFile(), List.of(game), baos);

			String xml = baos.toString();
			assertThat(xml).contains("\r\n").doesNotContainPattern("[^\r]\n");
		}
	}

	@Test
	void write_commentOverSeveralLines_writesItOnOneLine() throws IOException {
		var code = code("sunny", "94000130 FFFB0000");
		code.setComment("works after turn change \r\nx=\r\n0 - sunny\n1 - snow");
		var game = buildMinimalGame();
		game.getCodes().add(code);

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.R4CCE).write(new CheatFile(), List.of(game), baos);

		assertThat(baos.toString()).contains("<note>works after turn change x= 0 - sunny 1 - snow</note>");
	}

	@Test
	void roundTrip_titleEndingInALineBreak_comesBackWithout() throws IOException {
		var folder = new Folder();
		folder.setTitle("Game ID: CFAX E4AC8A5A\r");
		folder.getCodes().add(code("Max Gold", "02000100 0001869F"));
		var game = buildMinimalGame();
		game.getFolders().add(folder);

		ParseResult result = roundTrip(XmlFormat.R4CCE, new CheatFile(), List.of(game));

		assertThat(result.games().getFirst().getFolders().getFirst().getTitle()).isEqualTo("Game ID: CFAX E4AC8A5A");
	}

	@Test
	void write_titleOfOnlyLineBreaks_writesItEmpty() throws IOException {
		var folder = new Folder();
		folder.setTitle("\r\n");
		folder.getCodes().add(code("Max Gold", "02000100 0001869F"));
		var game = buildMinimalGame();
		game.getFolders().add(folder);

		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(XmlFormat.R4CCE).write(new CheatFile(), List.of(game), baos);

		assertThat(baos.toString()).contains("<folder>\r\n\t\t\t<name></name>");
	}

	private static Code code(String title, String... lines) {
		var code = new Code();
		code.setTitle(title);
		code.setCodeLines(new ArrayList<>(List.of(lines)));
		return code;
	}

	private Game buildMinimalGame() {
		var game = new Game();
		game.setSerial("TEST");
		game.setCrc32("00000000");
		game.setTitle("Test Game");
		return game;
	}

	private ParseResult roundTrip(XmlFormat format, CheatFile cheatFile, List<Game> games) throws IOException {
		var baos = new ByteArrayOutputStream();
		new UsercheatXmlWriter(format).write(cheatFile, games, baos);
		return new UsercheatXmlParser(format).parse(new ByteArrayInputStream(baos.toByteArray()));
	}
}
