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

import static codes.archeus.core.parser.EzArCodeWriterTest.oneCode;
import static codes.archeus.core.parser.EzArCodeWriterTest.write;
import static codes.archeus.core.parser.M3CheatsWriterTest.code;
import static codes.archeus.core.parser.M3CheatsWriterTest.folder;
import static codes.archeus.core.parser.M3CheatsWriterTest.game;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

class EzArCodeParserTest {
	private final EzArCodeParser parser = new EzArCodeParser();

	@Test
	void parse_writtenFile_readsBackItsTitleGamesFoldersAndCodes() throws IOException {
		var cheatFile = new CheatFile();
		cheatFile.setTitle("Archeus v12");

		Game game = game("C32P", "BE547C63", "Ace Attorney Investigations - Miles Edgeworth (Europe)");
		game.setMasterCode(new ArrayList<>(List.of("023F0000", "00000002")));

		Code maxMoney = code("Max Money", "By Datel", "02000008 0001869F");
		maxMoney.setEnabled(true);
		game.getCodes().add(maxMoney);

		Folder logic = folder("Logic Codes", "Pick one", 1);
		logic.setModifier(true);
		logic.getCodes().add(code("All Logic Clues", "", "94000130 FFBB0000", "D2000000 00000000"));
		game.getFolders().add(logic);

		ParseResult read = parser.parse(new ByteArrayInputStream(write(cheatFile, oneCode(), game)));

		assertThat(read.cheatFile().getTitle()).isEqualTo("Archeus v12");
		assertThat(read.cheatFile().isEnabled()).isTrue();
		assertThat(read.games()).extracting(Game::getSerial, Game::getCrc32, Game::getTitle).containsExactly(
				Tuple.tuple("ABCD", "12345678", "Game"),
				Tuple.tuple("C32P", "BE547C63", "Ace Attorney Investigations - Miles Edgeworth (Europe)")
		);
		Game ace = read.games().getLast();
		assertThat(ace.getMasterCode()).isEmpty();
		assertThat(ace.getCodes()).extracting(Code::getTitle, Code::getComment, Code::isEnabled)
				.containsExactly(Tuple.tuple("Max Money", "By Datel", true));
		Folder readLogic = ace.getFolders().getFirst();
		assertThat(readLogic.getTitle()).isEqualTo("Logic Codes");
		assertThat(readLogic.getComment()).isEqualTo("Pick one");
		assertThat(readLogic.isModifier()).isTrue();
		assertThat(readLogic.getCodes().getFirst().getCodeLines())
				.containsExactly("94000130 FFBB0000", "D2000000 00000000");
	}

	@Test
	void parse_usrcheatDat_throws() throws IOException {
		var out = new ByteArrayOutputStream();
		new UsercheatDatWriter().write(new CheatFile(), List.of(oneCode()), out);

		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(out.toByteArray())))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't an ezarcode.dat");
	}

	@Test
	void parse_fileCutInsideItsIndex_throwsNamingTheFile() throws IOException {
		byte[] file = Arrays.copyOf(write(new CheatFile(), oneCode()), 0x108);

		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(file)))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("ezarcode.dat");
	}
}
