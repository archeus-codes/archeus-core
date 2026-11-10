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

import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the EDGE and CycloDS Evolution files read the same counts, and some games against the
 * {@code usrcheat.dat}'s.
 */
class CrossFormatCodeCountTest {
	@TempDir
	Path tempDir;

	@Test
	void parse_edgeAndEvoFiles_readTheSameGameFolderAndCodeCounts() throws IOException {
		List<Game> edge = parseEdge();
		List<Game> cevo = parseCevo();

		assertThat(edge).hasSameSizeAs(cevo);

		int edgeFolders = totalFolders(edge);
		int cevoFolders = totalFolders(cevo);

		int edgeCodes = totalCodes(edge);
		int cevoCodes = totalCodes(cevo);

		assertThat(edgeFolders).isEqualTo(cevoFolders);
		assertThat(edgeCodes).isEqualTo(cevoCodes);
	}

	@Test
	void parse_edgeAndUsrcheatDat_readTheSameFolderAndCodeCountsForEveryGame() throws IOException {
		List<String> edge = parseEdge().stream().map(CrossFormatCodeCountTest::counts).toList();
		List<String> usrcheat;

		try (InputStream in = getClass().getResourceAsStream("/usrcheat.dat")) {
			assertThat(in).isNotNull();
			Path file = tempDir.resolve("usrcheat.dat");
			Files.write(file, in.readAllBytes());

			try (var raf = new RandomAccessFile(file.toFile(), "r")) {
				usrcheat = new UsercheatDatParser().parse(raf).games().stream()
						.map(CrossFormatCodeCountTest::counts)
						.toList();
			}
		}

		assertThat(edge).containsExactlyElementsOf(usrcheat).hasSize(4);
	}

	/**
	 * A game's serial with each folder's code count and its top level code count, such as {@code APHE 5 3 2 8 / 0}.
	 */
	private static String counts(Game game) {
		StringBuilder counts = new StringBuilder(game.getSerial());

		for (Folder folder : game.getFolders()) {
			counts.append(' ').append(folder.getCodes().size());
		}

		return counts.append(" / ").append(game.getCodes().size()).toString();
	}

	private int totalFolders(List<Game> games) {
		return games.stream().mapToInt(g -> g.getFolders().size()).sum();
	}

	private int totalCodes(List<Game> games) {
		return games.stream().mapToInt(g ->
			g.getCodes().size() + g.getFolders().stream().mapToInt(f -> f.getCodes().size()).sum()
		).sum();
	}

	private List<Game> parseEdge() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/cheats.dat")) {
			assertThat(in).isNotNull();
			return new EdgeCheatsParser().parse(in).games();
		}
	}

	private List<Game> parseCevo() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/user.evoCheats")) {
			assertThat(in).isNotNull();
			return new EvoCheatsParser().parse(in).games();
		}
	}
}
