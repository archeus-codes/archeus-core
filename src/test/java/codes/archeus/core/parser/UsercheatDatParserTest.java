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
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsercheatDatParserTest {

	@TempDir
	Path tempDir;

	@Test
	void parse_usrcheatDat_returnsExpectedGameCount() throws IOException {
		ParseResult result = parseRealFile();
		assertThat(result.games()).hasSize(4);
	}

	@Test
	void parse_usrcheatDat_returnsExpectedFirstGame() throws IOException {
		ParseResult result = parseRealFile();
		Game jeopardy = result.games().getFirst();

		assertThat(jeopardy.getSerial()).isEqualTo("BJDE");
		assertThat(jeopardy.getCrc32()).isEqualTo("D4EC931D");
		assertThat(jeopardy.getTitle()).isEqualTo("Jeopardy! (USA)");
		assertThat(jeopardy.getCodes()).hasSize(5);
		assertThat(jeopardy.getFolders()).hasSize(1);
	}

	@Test
	void parse_usrcheatDat_firstGameHasMasterCode() throws IOException {
		ParseResult result = parseRealFile();
		Game jeopardy = result.games().getFirst();
		assertThat(jeopardy.getMasterCode()).hasSize(8);
		assertThat(jeopardy.getMasterCode().getFirst()).isEqualTo("00000000");
	}

	@Test
	void parse_withoutTheSignature_throwsSayingItIsNotAUsrcheatDat() throws IOException {
		byte[] bytes = written(game("AAAA", "First"));
		bytes[0] = 'X';

		assertThatThrownBy(() -> parse(bytes))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't a usrcheat.dat");
	}

	@Test
	void parse_shorterThanItsHeader_throws() {
		assertThatThrownBy(() -> parse(Arrays.copyOf(written(game("AAAA", "First")), 100)))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("shorter than its 256 byte header");
	}

	@Test
	void parse_cutShortInsideAGame_throwsNamingTheGame() throws IOException {
		byte[] bytes = written(game("AAAA", "First"), game("BBBB", "Second"));

		assertThatThrownBy(() -> parse(Arrays.copyOf(bytes, bytes.length - 8)))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("ends inside game 2 of 2 (BBBB 0BADC0DE)");
	}

	private ParseResult parseRealFile() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/usrcheat.dat")) {
			assertThat(in).isNotNull();
			byte[] bytes = in.readAllBytes();
			Path file = tempDir.resolve("usrcheat.dat");
			Files.write(file, bytes);
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				return new UsercheatDatParser().parse(raf);
			}
		}
	}

	@Test
	void parse_oddCodeLength_consumesTrailingWordAndAlignsNextCode() throws IOException {
		Path file = tempDir.resolve("usrcheat.dat");
		Files.write(file, buildMinimalUsrcheat());

		UsercheatDatParser parser = new UsercheatDatParser();
		try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
			ParseResult result = parser.parse(raf);

			assertThat(result.games()).hasSize(1);
			Game game = result.games().getFirst();
			assertThat(game.getCodes()).hasSize(2);

			var code1 = game.getCodes().getFirst();
			assertThat(code1.getTitle()).isEqualTo("A");
			assertThat(code1.getCodeLines()).containsExactly("0000000A 94000130");

			// If the trailing word was not consumed, code2 title would be garbled.
			var code2 = game.getCodes().get(1);
			assertThat(code2.getTitle()).isEqualTo("B");
			assertThat(code2.getCodeLines()).containsExactly("12345678 9ABCDEF0");
		}
	}

	/**
	 * Builds a minimal valid usrcheat.dat binary:
	 *   - 256-byte header: R4CCE's signature, then zeros
	 *   - One game index entry (serial="TEST", fileOffset=288)
	 *   - Index terminator
	 *   - Game data with two codes; code1 has odd codeLength=3 (two lines + one trailing word)
	 */
	private byte[] buildMinimalUsrcheat() {
		ByteBuffer buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

		// Header: the signature, then zeros (already zeroed by allocate)
		buf.put("R4 CheatCode".getBytes(StandardCharsets.US_ASCII));
		buf.position(256);

		// Game index entry (16 bytes)
		buf.put("TEST".getBytes(StandardCharsets.US_ASCII)); // serial
		buf.putInt(0x00000000); // crc32
		buf.putInt(288); // fileOffset
		buf.putInt(0x00000000); // padding

		// Index terminator (16 zero bytes)
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);

		// === Game data at offset 288 ===
		// Title chunk 1: "Test"
		buf.put((byte) 'T');
		buf.put((byte) 'e');
		buf.put((byte) 's');
		buf.put((byte) 't');
		// Title chunk 2: all zeros — buffer[3]==0 signals end of title
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.put((byte) 0);
		// Game header: entry count=2, disabled
		buf.put((byte) 2);
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.put((byte) 0);
		// Master code: 8 zero words
		for (int i = 0; i < 8; ++i) {
			buf.putInt(0);
		}

		// Code 1: codeLength=3 (odd) — 1 visible pair + 1 trailing alignment word
		buf.put((byte) 0); // header byte 0
		buf.put((byte) 0); // header byte 1
		buf.put((byte) 0); // header byte 2
		buf.put((byte) 0x01); // header byte 3: enabled
		buf.put((byte) 'A'); // title byte 0
		buf.put((byte) 0); // title byte 1
		buf.put((byte) 0); // title byte 2
		buf.put((byte) 0); // title byte 3: null terminator
		buf.put((byte) 3); // codeLength=3
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.putInt(0x0000000A); // word 1 (addr)
		buf.putInt(0x94000130); // word 2 (value)
		buf.putInt(0x00000000); // word 3 (trailing alignment — must be consumed)

		// Code 2: codeLength=2 (even) — title must parse correctly if alignment was consumed
		buf.put((byte) 0); // header byte 0
		buf.put((byte) 0); // header byte 1
		buf.put((byte) 0); // header byte 2
		buf.put((byte) 0x01); // header byte 3: enabled
		buf.put((byte) 'B'); // title byte 0
		buf.put((byte) 0); // title byte 1
		buf.put((byte) 0); // title byte 2
		buf.put((byte) 0); // title byte 3: null terminator
		buf.put((byte) 2); // codeLength=2
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.putInt(0x12345678); // word 1 (addr)
		buf.putInt(0x9ABCDEF0); // word 2 (value)

		int size = buf.position();
		byte[] result = new byte[size];
		buf.rewind();
		buf.get(result);
		return result;
	}

	@Test
	void parse_repeatedLinesTitlesAndMasterCodeWords_shareOneString() throws IOException {
		ParseResult result = parse(written(game("AAAA", "First"), game("BBBB", "Second")));
		Code first = result.games().getFirst().getCodes().getFirst();
		Code second = result.games().get(1).getCodes().getFirst();

		assertThat(first.getCodeLines()).containsExactly("12345678 00000001");
		assertThat(second.getCodeLines().getFirst()).isSameAs(first.getCodeLines().getFirst());
		assertThat(second.getTitle()).isEqualTo("Infinite Health").isSameAs(first.getTitle());
		assertThat(result.games().get(1).getMasterCode().getFirst())
				.isSameAs(result.games().getFirst().getMasterCode().getFirst());
	}

	@Test
	void parse_emptyComment_isTheEmptyLiteral() throws IOException {
		ParseResult result = parse(written(game("AAAA", "First")));

		assertThat(result.games().getFirst().getCodes().getFirst().getComment()).isSameAs("");
	}

	@Test
	void parse_twice_doesNotShareStringsBetweenParses() throws IOException {
		byte[] bytes = written(game("AAAA", "First"));

		String first = parse(bytes).games().getFirst().getCodes().getFirst().getCodeLines().getFirst();
		String second = parse(bytes).games().getFirst().getCodes().getFirst().getCodeLines().getFirst();

		assertThat(second).isEqualTo(first).isNotSameAs(first);
	}

	private ParseResult parse(byte[] bytes) throws IOException {
		Path file = tempDir.resolve("written.dat");
		Files.write(file, bytes);

		try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
			return new UsercheatDatParser().parse(raf);
		}
	}

	private static byte[] written(Game... games) throws IOException {
		var out = new ByteArrayOutputStream();
		new UsercheatDatWriter().write(new CheatFile(), List.of(games), out);
		return out.toByteArray();
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
