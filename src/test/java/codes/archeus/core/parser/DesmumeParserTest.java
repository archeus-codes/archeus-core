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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

class DesmumeParserTest {
	private final DesmumeParser parser = new DesmumeParser();

	@Test
	void parse_desmumesOwnFile_readsEachCodeInOrder() throws IOException {
		Game game = parseResource("justice-league-heroes.dct");

		assertThat(game.getFolders()).isEmpty();
		assertThat(game.getCodes()).extracting(Code::getTitle, Code::isEnabled, Code::getSortOrder).containsExactly(
				Tuple.tuple("One Hit Kills", false, 0),
				Tuple.tuple("Infinite Health", true, 1),
				Tuple.tuple("One Hit Kills original", true, 2),
				Tuple.tuple("Infinite Energy", true, 3)
		);
		assertThat(game.getCodes().get(1).getCodeLines())
				.containsExactly("52007DEC AA000018", "02007DEC 11C100B0", "D2000000 00000000");
	}

	@Test
	void parse_desmumesOwnFiles_readsCodesWithNoDescription() throws IOException {
		assertThat(parseResource("cory-in-the-house.dct").getCodes()).extracting(Code::getTitle)
				.containsExactly("Walk Through Walls", "");
		assertThat(parseResource("happy-feet-two.dct").getCodes()).extracting(Code::getTitle)
				.containsExactly("", "Moon Jump");
		assertThat(parseResource("one-piece-gigant-battle.dct").getCodes()).singleElement()
				.extracting(code -> code.getCodeLines().size())
				.isEqualTo(8);
	}

	@Test
	void parse_rawWriteCode_throws() {
		assertThatThrownBy(() -> parseResource("spider-man-shattered-dimensions.dct"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("DS 0 321444DC00001000");
	}

	@Test
	void parse_lineFeedsOnly_readsTheSameCodes() throws IOException {
		String file = resource("justice-league-heroes.dct").replace("\r\n", "\n");

		assertThat(parse(file).getCodes()).hasSize(4);
	}

	@Test
	void parse_lowercaseHex_throws() {
		assertThatThrownBy(() -> parse("AR 0 02007424e3a01000 ;One Hit Kills\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't an Action Replay code");
	}

	@Test
	void parse_noDescriptionSeparator_throws() {
		assertThatThrownBy(() -> parse("AR 0 02007424E3A01000\r\n")).isInstanceOf(UsercheatParserException.class);
	}

	@Test
	void parse_codeOverMaxLines_throws() {
		String words = String.join(",", Collections.nCopies(DesmumeWriter.MAX_LINES + 1, "0200000000000000"));

		assertThatThrownBy(() -> parse("AR 0 " + words + " ;Huge\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("lines");
	}

	@Test
	void parse_descriptionOverMaxBytes_throws() {
		String description = "x".repeat(DesmumeWriter.MAX_DESCRIPTION_BYTES + 1);

		assertThatThrownBy(() -> parse("AR 0 0200000000000000 ;" + description + "\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("cut");
	}

	@Test
	void parse_lineOverMaxBytes_throws() {
		String words = String.join(",", Collections.nCopies(2000, "0200000000000000"));

		assertThatThrownBy(() -> parse("AR 0 " + words + " ;Huge\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("bytes");
	}

	@Test
	void parse_indentedCode_throws() {
		assertThatThrownBy(() -> parse("  AR 1 0200000000000001 ;Foo\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("line 1");
	}

	@Test
	void parse_trailingIdeographicSpace_keepsIt() throws IOException {
		assertThat(parse("AR 1 0200000000000001 ;Foo\u3000\r\n").getCodes()).extracting(Code::getTitle)
				.containsExactly("Foo\u3000");
	}

	@Test
	void parse_loneCarriageReturn_keepsItInTheDescription() throws IOException {
		assertThat(parse("AR 1 0200000000000001 ;Foo\r; bar\n").getCodes()).extracting(Code::getTitle)
				.containsExactly("Foo\r; bar");
	}

	@Test
	void parse_byteOrderMarkBeforeAComment_readsTheFile() throws IOException {
		String file = "\uFEFF" + resource("justice-league-heroes.dct");

		assertThat(parse(file).getCodes()).hasSize(4);
	}

	@Test
	void parse_byteOrderMarkBeforeACode_throws() {
		assertThatThrownBy(() -> parse("\uFEFFAR 1 0200000000000001 ;Foo\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("byte order mark");
	}

	@Test
	void parse_bytesThatArentUtf8_throws() {
		byte[] file = "AR 1 0200000000000001 ;Caf\u00E9\r\n".getBytes(StandardCharsets.ISO_8859_1);

		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(file)))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessage("DeSmuME cheat file line 1 isn't UTF-8");
	}

	@Test
	void parse_badLine_namesItsLineNumber() {
		assertThatThrownBy(() -> parse("; DeSmuME cheats file\r\n\r\nAR 0 0200 ;Short\r\n"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageStartingWith("DeSmuME cheat file line 3 ");
	}

	@Test
	void parse_headerLinesInAnyCase_skipsThem() throws IOException {
		String file = "NAME=JUSTLEAGHERO\r\nserial=NTR-AJLE-USA\r\nAR 1 0200000000000000 ;Test\r\n";

		List<Code> codes = parse(file).getCodes();

		assertThat(codes).extracting(Code::getTitle).containsExactly("Test");
	}

	private Game parse(String file) throws IOException {
		return parser.parse(new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)));
	}

	private Game parseResource(String name) throws IOException {
		return parse(resource(name));
	}

	private String resource(String name) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/desmume/" + name)) {
			assertThat(in).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
