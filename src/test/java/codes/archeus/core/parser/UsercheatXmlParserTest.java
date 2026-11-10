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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import codes.archeus.core.domain.Code;

import static org.assertj.core.api.Assertions.assertThat;

class UsercheatXmlParserTest {

	private ParseResult parse(String xml) throws IOException {
		byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
		return new UsercheatXmlParser().parse(new ByteArrayInputStream(bytes));
	}
	
	@Test
	void parse_r4cceFile_readsTheCodelistAtTheRoot() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>My Database</name>
				</codelist>
				""";

		ParseResult result = parse(xml);

		assertThat(result.cheatFile().getTitle()).isEqualTo("My Database");
		assertThat(result.games()).isEmpty();
	}

	@Test
	void parse_olderArcheusFile_readsTheCodelistInsideSubscription() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<subscription>
					<name>My Database</name>
					<codelist>
						<name>My Database</name>
					</codelist>
				</subscription>
				""";

		ParseResult result = parse(xml);

		assertThat(result.cheatFile().getTitle()).isEqualTo("My Database");
	}
	
	@Test
	void parse_gameElement_readsTitleSerialAndCrc32() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Pokemon Diamond (USA)</name>
						<gameid>ADAE 5CF3F098</gameid>
						<date>2026/06/24 06:35</date>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		assertThat(result.games()).hasSize(1);
		var game = result.games().getFirst();
		assertThat(game.getTitle()).isEqualTo("Pokemon Diamond (USA)");
		assertThat(game.getSerial()).isEqualTo("ADAE");
		assertThat(game.getCrc32()).isEqualTo("5CF3F098");
	}

	@Test
	void parse_dateElement_isDiscarded() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>1999/01/01 00:00</date>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		assertThat(result.games()).hasSize(1);
	}
	
	@Test
	void parse_actionReplayMasterCode_isACode() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>Master Code</name>
							<codes>00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var game = result.games().getFirst();
		assertThat(game.getMasterCode()).isEmpty();
		assertThat(game.getCodes()).extracting(Code::getTitle).containsExactly("Master Code");
		assertThat(game.getCodes().getFirst().getCodeLines()).containsExactly(
				"00000000 00000001",
				"00000000 00000000",
				"00000000 00000000",
				"00000000 00000000"
		);
	}

	@Test
	void parse_masterPrefixedMasterCode_extractsTheInterleavedValues() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>(M)MasterCode</name>
							<codes>master 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 023FF300 00000000 00000000 00000000 02000010 00000000 00000001</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var game = result.games().getFirst();
		assertThat(game.getMasterCode()).containsExactly(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		);
		assertThat(game.getCodes()).isEmpty();
	}

	@Test
	void parse_mPrefixedCheatWithoutMasterFormat_isACode() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>(M) Must Be On</name>
							<codes>DEADBEEF 00000001</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var game = result.games().getFirst();
		assertThat(game.getMasterCode()).isEmpty();
		assertThat(game.getCodes()).extracting(Code::getTitle).containsExactly("(M) Must Be On");
	}

	@Test
	void parse_plainFlashCartMasterCode_isTheMasterCode() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>(M)MasterCode</name>
							<codes>00000000 00000001 00000000 00000000 00000000 023FF300 00000000 02000010</codes>
						</cheat>
					</game>
				</codelist>
				""";

		var game = parse(xml).games().getFirst();

		assertThat(game.getMasterCode()).containsExactly(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		);
		assertThat(game.getCodes()).isEmpty();
	}

	@Test
	void parse_actionReplayCodelist_readsItsMasterCheatAsAMasterCodeCode() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>(M)</name>
							<codes>master 00000000 023F0000 00000000 00000001 </codes>
						</cheat>
					</game>
				</codelist>
				""";

		var game = new UsercheatXmlParser(XmlFormat.ACTION_REPLAY)
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
				.games().getFirst();

		assertThat(game.getMasterCode()).isEmpty();
		assertThat(game.getCodes()).extracting(Code::getTitle).containsExactly("Master Code");
		assertThat(game.getCodes().getFirst().getCodeLines()).containsExactly("00000000 023F0000", "00000000 00000001");
	}

	@Test
	void parse_olderArcheusFileMasterCheat_isAMasterCodeCodeWithEveryLine() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<subscription>
					<name>DB</name>
					<codelist>
						<name>DB</name>
						<game>
							<name>Game</name>
							<gameid>ABCD 12345678</gameid>
							<date>2026/01/01 00:00</date>
							<cheat>
								<name>(M)MasterCode</name>
								<codes>master 00000000 023F0000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 023FFE88 00000000 023FF090 00000000 00000000 00000000 00000001</codes>
							</cheat>
						</game>
					</codelist>
				</subscription>
				""";

		var game = parse(xml).games().getFirst();

		assertThat(game.getMasterCode()).isEmpty();
		assertThat(game.getCodes()).extracting(Code::getTitle).containsExactly("Master Code");
		assertThat(game.getCodes().getFirst().getCodeLines()).containsExactly(
				"00000000 023F0000",
				"00000000 00000001",
				"00000000 00000000",
				"00000000 00000000",
				"00000000 00000000",
				"00000000 023FFE88",
				"00000000 023FF090",
				"00000000 00000000",
				"00000000 00000001"
		);
	}

	@Test
	void parse_flashCartAndActionReplayMasterCodes_keepsTheFlashCartOneAndTheOtherAsACode() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>(M)MasterCode</name>
							<codes>master 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 023FF300 00000000 00000000 00000000 02000010 00000000 00000001</codes>
						</cheat>
						<cheat>
							<name>Master Code</name>
							<codes>00000000 023F0000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 023FFE88 00000000 023FF090 00000000 00000000 00000000 00000001</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var game = result.games().getFirst();
		assertThat(game.getMasterCode()).containsExactly(
				"00000000", "00000001",
				"00000000", "00000000",
				"00000000", "023FF300",
				"00000000", "02000010"
		);
		assertThat(game.getCodes()).extracting(Code::getTitle).containsExactly("Master Code");
		assertThat(game.getCodes().getFirst().getCodeLines()).hasSize(9);
	}
	
	@Test
	void parse_regularCheat_readsItAsACode() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>Infinite Lives</name>
							<codes>12345678 9ABCDEF0</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var codes = result.games().getFirst().getCodes();
		assertThat(codes).hasSize(1);
		var code = codes.getFirst();
		assertThat(code.getTitle()).isEqualTo("Infinite Lives");
		assertThat(code.getCodeLines()).containsExactly("12345678 9ABCDEF0");
		assertThat(code.getComment()).isBlank();
	}

	@Test
	void parse_noteElement_becomesTheCodeComment() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>Max Money</name>
							<note>Sets money to max value</note>
							<codes>02000100 0001869F</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var code = result.games().getFirst().getCodes().getFirst();
		assertThat(code.getComment()).isEqualTo("Sets money to max value");
	}

	@Test
	void parse_codeLines_splitOnWhitespace() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<cheat>
							<name>Multi-Line</name>
							<codes>AAAABBBB CCCCDDDD EEEEFFFF 00000000</codes>
						</cheat>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var code = result.games().getFirst().getCodes().getFirst();
		assertThat(code.getCodeLines()).containsExactly("AAAABBBB CCCCDDDD", "EEEEFFFF 00000000");
	}
	
	@Test
	void parse_folderNote_setsTheFolderComment() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<folder>
							<name>Emotion Action 1 Mod Codes</name>
							<note>Emotion Actions can be found in the Keyboard menu.</note>
							<allowedon>1</allowedon>
						</folder>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var folder = result.games().getFirst().getFolders().getFirst();
		assertThat(folder.getComment()).isEqualTo("Emotion Actions can be found in the Keyboard menu.");
	}

	@Test
	void parse_allowedOnOne_makesAModifierFolder() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<folder>
							<name>Speed Options</name>
							<allowedon>1</allowedon>
						</folder>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var folder = result.games().getFirst().getFolders().getFirst();
		assertThat(folder.isModifier()).isTrue();
	}

	@Test
	void parse_allowedOnZero_makesAStandardFolder() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<folder>
							<name>Misc Cheats</name>
							<allowedon>0</allowedon>
						</folder>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var folder = result.games().getFirst().getFolders().getFirst();
		assertThat(folder.isModifier()).isFalse();
	}

	@Test
	void parse_folderCodes_nestUnderTheirFolder() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game</name>
						<gameid>ABCD 12345678</gameid>
						<date>2026/01/01 00:00</date>
						<folder>
							<name>My Folder</name>
							<allowedon>0</allowedon>
							<cheat>
								<name>Option A</name>
								<codes>01000000 00000001</codes>
							</cheat>
							<cheat>
								<name>Option B</name>
								<codes>01000000 00000002</codes>
							</cheat>
						</folder>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		var folder = result.games().getFirst().getFolders().getFirst();
		assertThat(folder.getCodes()).hasSize(2);
		assertThat(folder.getCodes().getFirst().getTitle()).isEqualTo("Option A");
		assertThat(folder.getCodes().get(1).getTitle()).isEqualTo("Option B");
	}
	
	@Test
	void parse_multipleGames_keepsTheirOrder() throws IOException {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<codelist>
					<name>DB</name>
					<game>
						<name>Game One</name>
						<gameid>AAA1 11111111</gameid>
						<date>2026/01/01 00:00</date>
					</game>
					<game>
						<name>Game Two</name>
						<gameid>BBB2 22222222</gameid>
						<date>2026/01/01 00:00</date>
					</game>
				</codelist>
				""";

		ParseResult result = parse(xml);

		assertThat(result.games()).hasSize(2);
		assertThat(result.games().getFirst().getTitle()).isEqualTo("Game One");
		assertThat(result.games().get(1).getTitle()).isEqualTo("Game Two");
	}
}
