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
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsercheatDatWriterTest {

	@TempDir
	Path tempDir;

	@Test
	void canWrite_hexLines_isTrue() {
		assertThat(UsercheatDatWriter.canWrite(List.of("94000130 FCFF0000", "0000038c 00000001"))).isTrue();
	}

	@Test
	void canWrite_noLines_isTrue() {
		assertThat(UsercheatDatWriter.canWrite(List.of())).isTrue();
	}

	@Test
	void canWrite_aPlaceholderInAnyLine_isFalse() {
		assertThat(UsercheatDatWriter.canWrite(List.of("94000130 FCFF0000", "22365C6C 000000??"))).isFalse();
		assertThat(UsercheatDatWriter.canWrite(List.of("E3A01CXX E28110XX"))).isFalse();
	}

	@Test
	void canWrite_aLineWithoutTwoHexGroups_isFalse() {
		assertThat(UsercheatDatWriter.canWrite(List.of("12345678"))).isFalse();
		assertThat(UsercheatDatWriter.canWrite(List.of("123456789 00000000"))).isFalse();
		assertThat(UsercheatDatWriter.canWrite(List.of("1234 00000000"))).isFalse();
		assertThat(UsercheatDatWriter.canWrite(List.of("12345678\t00000000"))).isFalse();
	}

	@Test
	void write_roundTrip_headerFieldsPreserved() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("My Cheat Database");
		cheatFile.setEnabled(true);

		ParseResult result = writeAndParse(cheatFile, List.of());

		assertThat(result.cheatFile().getTitle()).startsWith("My Cheat Database");
		assertThat(result.cheatFile().isEnabled()).isTrue();
	}

	@Test
	void write_anyFile_holdsTheSignatureAndTag() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("My Cheat Database");
		cheatFile.setEnabled(true);

		var baos = new ByteArrayOutputStream();
		new UsercheatDatWriter().write(cheatFile, List.of(), baos);
		byte[] written = baos.toByteArray();

		// R4CCE rejects files that don't start with this exact 16-byte signature.
		assertThat(written).startsWith(
				'R', '4', ' ', 'C', 'h', 'e', 'a', 't', 'C', 'o', 'd', 'e', 0x00, 0x01, 0x00, 0x00
		);

		// Fixed 4-byte tag immediately after the 60-byte title field (offset 0x4C), always
		// present in R4CCE generated files regardless of title content.
		byte[] tailTag = new byte[4];
		System.arraycopy(written, 0x4C, tailTag, 0, 4);
		assertThat(tailTag).containsExactly((byte) 0xD5, 0x53, 0x41, 0x59);
	}

	@Test
	void write_roundTrip_headerDisabledFlag() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setEnabled(false);

		ParseResult result = writeAndParse(cheatFile, List.of());

		assertThat(result.cheatFile().isEnabled()).isFalse();
	}

	@Test
	void write_roundTrip_gameIndexFieldsPreserved() throws IOException {
		var game = new Game();
		game.setSerial("ABCD");
		game.setCrc32("12345678");
		game.setTitle("Test Game");
		game.setEnabled(true);
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "00000000",
				"00000000", "00000000"
		));

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		assertThat(result.games()).hasSize(1);
		Game parsed = result.games().getFirst();
		assertThat(parsed.getSerial()).isEqualTo("ABCD");
		assertThat(parsed.getCrc32()).isEqualTo("12345678");
		assertThat(parsed.getTitle()).isEqualTo("Test Game");
		assertThat(parsed.isEnabled()).isTrue();
		assertThat(parsed.getMasterCode()).containsExactly(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "00000000",
				"00000000", "00000000"
		);
	}

	@Test
	void write_roundTrip_topLevelCodes() throws IOException {
		var code = new Code();
		code.setTitle("Infinite Lives");
		code.setEnabled(true);
		code.setCodeLines(List.of("12345678 9ABCDEF0", "DEADBEEF 00000001"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		Game parsed = result.games().getFirst();
		assertThat(parsed.getCodes()).hasSize(1);
		Code parsedCode = parsed.getCodes().getFirst();
		assertThat(parsedCode.getTitle()).isEqualTo("Infinite Lives");
		assertThat(parsedCode.isEnabled()).isTrue();
		assertThat(parsedCode.getCodeLines()).containsExactly("12345678 9ABCDEF0", "DEADBEEF 00000001");
	}

	@Test
	void write_roundTrip_codeWithComment() throws IOException {
		var code = new Code();
		code.setTitle("Max Gold");
		code.setComment("Sets gold to max");
		code.setCodeLines(List.of("02000100 0001869F"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		Code parsedCode = result.games().getFirst().getCodes().getFirst();
		assertThat(parsedCode.getTitle()).isEqualTo("Max Gold");
		assertThat(parsedCode.getComment()).isEqualTo("Sets gold to max");
	}

	@Test
	void write_roundTrip_multiByteCharactersAcrossChunkBoundaries() throws IOException {
		var game = buildMinimalGame();
		var folder = new Folder();
		folder.setTitle("Drømmespejlet");
		folder.setComment("Vælg én");
		game.getFolders().add(folder);
		List<String> texts = new ArrayList<>();

		// "ø" and "é" take two bytes and "ポ" three, placed at every offset around the 4-byte chunks.
		for (int offset = 0; offset < 5; ++offset) {
			String title = "x".repeat(offset) + "ø Pokémon ポケモン";
			String comment = "y".repeat(offset) + "Tryk på ポ";
			texts.add(title + "|" + comment);
			var code = new Code();
			code.setTitle(title);
			code.setComment(comment);
			code.setCodeLines(List.of("02000100 0001869F"));
			folder.getCodes().add(code);
		}

		Game parsed = writeAndParse(new CheatFile(), List.of(game)).games().getFirst();

		Folder parsedFolder = parsed.getFolders().getFirst();
		assertThat(parsedFolder.getTitle()).isEqualTo("Drømmespejlet");
		assertThat(parsedFolder.getComment()).isEqualTo("Vælg én");
		assertThat(parsedFolder.getCodes()).extracting(code -> code.getTitle() + "|" + code.getComment())
				.containsExactlyElementsOf(texts);
	}

	@Test
	void write_roundTrip_folder() throws IOException {
		var folderCode = new Code();
		folderCode.setTitle("Option A");
		folderCode.setCodeLines(List.of("01000000 00000001"));

		var folder = new Folder();
		folder.setTitle("Speed Options");
		folder.setModifier(true);
		folder.getCodes().add(folderCode);

		var game = buildMinimalGame();
		game.getFolders().add(folder);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		Game parsed = result.games().getFirst();
		assertThat(parsed.getFolders()).hasSize(1);
		Folder parsedFolder = parsed.getFolders().getFirst();
		assertThat(parsedFolder.getTitle()).isEqualTo("Speed Options");
		assertThat(parsedFolder.isModifier()).isTrue();
		assertThat(parsedFolder.getCodes()).hasSize(1);
		assertThat(parsedFolder.getCodes().getFirst().getTitle()).isEqualTo("Option A");
	}

	@Test
	void write_roundTrip_multipleGames() throws IOException {
		var game1 = buildMinimalGame();
		game1.setSerial("AAA1");
		game1.setTitle("Game One");

		var game2 = buildMinimalGame();
		game2.setSerial("BBB2");
		game2.setTitle("Game Two");

		ParseResult result = writeAndParse(new CheatFile(), List.of(game1, game2));

		assertThat(result.games()).hasSize(2);
		assertThat(result.games().getFirst().getSerial()).isEqualTo("AAA1");
		assertThat(result.games().get(1).getSerial()).isEqualTo("BBB2");
	}

	@Test
	void write_roundTrip_titleLengthMultipleOfFour() throws IOException {
		// Title length = 4 chars -> nullPos=0 -> terminating chunk is all zero
		var code = new Code();
		code.setTitle("ABCD"); // exactly 4 chars
		code.setCodeLines(List.of("00000000 00000000"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		assertThat(result.games().getFirst().getCodes().getFirst().getTitle()).isEqualTo("ABCD");
	}

	@Test
	void write_roundTrip_titleLengthModThreeWithoutComment() throws IOException {
		// Title length % 4 == 3 -> nullPos=3 -> buffer[2] is a title char, needs extra terminator
		var code = new Code();
		code.setTitle("ABC"); // 3 chars
		code.setCodeLines(List.of("00000000 00000000"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		assertThat(result.games().getFirst().getCodes().getFirst().getTitle()).isEqualTo("ABC");
	}

	@Test
	void write_roundTrip_commentWithTitleLengthModTwo() throws IOException {
		// nullPos=2: null at position 2 -> buffer[2]=0, buffer[3] must be non-zero for comment
		var code = new Code();
		code.setTitle("AB"); // 2 chars, nullPos=2
		code.setComment("hello");
		code.setCodeLines(List.of("00000000 00000000"));

		var game = buildMinimalGame();
		game.getCodes().add(code);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		Code parsedCode = result.games().getFirst().getCodes().getFirst();
		assertThat(parsedCode.getTitle()).isEqualTo("AB");
		assertThat(parsedCode.getComment()).isEqualTo("hello");
	}

	@Test
	void write_roundTrip_preservesInterleavedOrderOfTopLevelCodesAndFolders() throws IOException {
		var firstCode = new Code();
		firstCode.setTitle("First Code");
		firstCode.setSortOrder(0);
		firstCode.setCodeLines(List.of("00000000 00000000"));

		var folderCode = new Code();
		folderCode.setTitle("Folder Code");
		folderCode.setCodeLines(List.of("00000000 00000000"));

		var middleFolder = new Folder();
		middleFolder.setTitle("Middle Folder");
		middleFolder.setSortOrder(1);
		middleFolder.getCodes().add(folderCode);

		var lastCode = new Code();
		lastCode.setTitle("Last Code");
		lastCode.setSortOrder(2);
		lastCode.setCodeLines(List.of("00000000 00000000"));

		// Deliberately added in a different order than sortOrder implies, mirroring how the
		// domain model stores top-level codes and folders in two separate lists.
		var game = buildMinimalGame();
		game.getCodes().add(firstCode);
		game.getCodes().add(lastCode);
		game.getFolders().add(middleFolder);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		// Re-parsing assigns sortOrder from true file position, so this also proves the writer
		// actually interleaved the entries in the file rather than just re-sorting in memory.
		Game parsed = result.games().getFirst();
		assertThat(parsed.getCodes()).extracting(Code::getTitle).containsExactly("First Code", "Last Code");
		assertThat(parsed.getFolders()).extracting(Folder::getTitle).containsExactly("Middle Folder");
		assertThat(parsed.getCodes().getFirst().getSortOrder()).isZero();
		assertThat(parsed.getFolders().getFirst().getSortOrder()).isOne();
		assertThat(parsed.getCodes().get(1).getSortOrder()).isEqualTo(2);
	}

	@Test
	void write_roundTrip_folderWithMoreThan255Codes() throws IOException {
		var folder = new Folder();
		folder.setTitle("Item Modifier Codes");

		int codeCount = 300;

		for (int i = 0; i < codeCount; ++i) {
			var code = new Code();
			code.setTitle("Item " + i);
			code.setCodeLines(List.of("%08X".formatted(i) + " 00000000"));
			folder.getCodes().add(code);
		}

		var game = buildMinimalGame();
		game.getFolders().add(folder);

		ParseResult result = writeAndParse(new CheatFile(), List.of(game));

		Folder parsedFolder = result.games().getFirst().getFolders().getFirst();
		assertThat(parsedFolder.getCodes()).hasSize(codeCount);
		assertThat(parsedFolder.getCodes().getFirst().getTitle()).isEqualTo("Item 0");
		assertThat(parsedFolder.getCodes().getLast().getTitle()).isEqualTo("Item 299");
		// No leftover bytes should be misread as top-level codes.
		assertThat(result.games().getFirst().getCodes()).isEmpty();
	}

	private Game buildMinimalGame() {
		var game = new Game();
		game.setSerial("TEST");
		game.setCrc32("00000000");
		game.setTitle("Test Game");
		game.setEnabled(false);
		game.setMasterCode(List.of(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "00000000",
				"00000000", "00000000"
		));
		return game;
	}

	private ParseResult writeAndParse(CheatFile cheatFile, List<Game> games) throws IOException {
		var baos = new ByteArrayOutputStream();
		new UsercheatDatWriter().write(cheatFile, games, baos);

		Path file = tempDir.resolve("usrcheat.dat");
		Files.write(file, baos.toByteArray());

		try (var raf = new RandomAccessFile(file.toFile(), "r")) {
			return new UsercheatDatParser().parse(raf);
		}
	}
}
